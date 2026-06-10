package com.skipperkit.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.skipperkit.BuildConfig
import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import com.skipperkit.discovery.DiscoveryEngine
import com.skipperkit.discovery.DiscoveryRepository
import com.skipperkit.matching.NodeView
import com.skipperkit.matching.Point
import com.skipperkit.matching.SkipEngine
import com.skipperkit.matching.TreeSearch
import com.skipperkit.settings.SettingsRepository
import com.skipperkit.settings.TaughtAppsRepository
import com.skipperkit.teach.TeachCandidate
import com.skipperkit.teach.TeachModeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phase 3: detects and clicks skip buttons in the supported apps.
 *
 * [onAccessibilityEvent] runs on a hot framework thread, so it does no tree work
 * itself — it forwards to [SkipEngine] on a dedicated worker thread. The engine
 * resolves the config, searches the tree (view-id first, text fallback), walks
 * to the clickable ancestor, clicks, and enforces a package-aware 1500 ms
 * debounce.
 *
 * When [BuildConfig.DEBUG_NODE_INSPECTOR] is on, the verbose [NodeInspector]
 * also runs for diagnostics.
 */
class SkipAccessibilityService : AccessibilityService() {

    private val engine = SkipEngine(clock = { SystemClock.uptimeMillis() })

    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null

    private var inspector: NodeInspector? = null

    private val scopeWatcher = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var scopedPackages: Set<String> = BASELINE_PACKAGES

    @Volatile
    private var lastDiscoveryUptimeMs = 0L

    @Volatile private var lastTeachSweepUptimeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        worker = HandlerThread("SkipperKitEngine").also {
            it.start()
            workerHandler = Handler(it.looper)
        }
        if (BuildConfig.DEBUG_NODE_INSPECTOR) {
            inspector = NodeInspector()
        }
        ServiceRuntime.setRunning(true)
        applyScope()
        scopeWatcher.launch { TaughtAppsRepository.taughtApps.collect { applyScope() } }
        Log.i(TAG, "Service connected (inspector=${inspector != null})")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in scopedPackages) return

        ServiceRuntime.recordActivity(System.currentTimeMillis())
        workerHandler?.post { runEngine(packageName) }
        inspector?.requestDump { rootInActiveWindow }
    }

    private fun applyScope() {
        scopedPackages = BASELINE_PACKAGES + TaughtAppsRepository.currentPackages()
        val info = serviceInfo ?: run { Log.w(TAG, "applyScope: serviceInfo null"); return }
        info.packageNames = scopedPackages.toTypedArray()
        serviceInfo = info
    }

    private fun runEngine(packageName: String) {
        val config = SettingsRepository.configFor(packageName) ?: return
        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            Log.w(TAG, "Could not obtain root window", t)
            null
        } ?: return

        // A scoped app can surface another app's window (Chrome Custom Tabs are
        // the common case) while events stay attributed to the host. Never
        // search, click, or teach from a window owned by a different package —
        // it's outside the user-approved scope and its ids are unusable anyway.
        val windowPackage = root.packageName?.toString()
        if (windowPackage != null && windowPackage != packageName) return

        val view = AccessibilityNodeView(root)
        maybeCollectTeachCandidates(packageName, view)
        val result = try {
            engine.onTree(packageName, view, config)
        } catch (t: Throwable) {
            // The tree can be invalidated mid-walk while the player animates.
            Log.w(TAG, "Engine pass aborted (node invalidated)", t)
            return
        }

        when (result) {
            is SkipEngine.Result.Clicked -> {
                val label = if (result.target == SkipTarget.CUSTOM && result.customName != null)
                    "CUSTOM \"${result.customName}\"" else result.target.toString()
                Log.i(TAG, "Clicked $label in $packageName")
            }
            is SkipEngine.Result.NeedsGesture -> {
                val label = if (result.target == SkipTarget.CUSTOM && result.customName != null)
                    "CUSTOM \"${result.customName}\"" else result.target.toString()
                Log.i(TAG, "No clickable ancestor for $label; tapping via gesture")
                dispatchTap(result.point)
            }
            is SkipEngine.Result.NoMatch -> maybeRunDiscovery(packageName, view)
            else -> Unit
        }
    }

    /** Teach mode: collect clickable candidates from the armed app. Never clicks. */
    private fun maybeCollectTeachCandidates(packageName: String, root: AccessibilityNodeView) {
        if (!TeachModeRepository.isArmedFor(packageName)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTeachSweepUptimeMs < TEACH_SWEEP_INTERVAL_MS) return
        lastTeachSweepUptimeMs = now
        try {
            collectClickable(root) { node ->
                TeachModeRepository.offer(
                    packageName,
                    TeachCandidate(viewId = node.viewId, text = node.text ?: node.contentDescription),
                )
            }
        } catch (t: Throwable) {
            // Tree can invalidate mid-walk; a lost sweep is fine.
        }
    }

    private fun collectClickable(root: NodeView, action: (NodeView) -> Unit) {
        TreeSearch.forEach(root) { node ->
            if (node.isClickable) {
                val hasId = !node.viewId.isNullOrBlank()
                val hasText = !node.text.isNullOrBlank()
                val hasDesc = !node.contentDescription.isNullOrBlank()
                if (hasId || hasText || hasDesc) action(node)
            }
        }
    }

    /**
     * Prototype discovery tier: when the configured matchers find nothing, run the
     * heuristic [DiscoveryEngine] and LOG what it would propose. It never clicks —
     * this is diagnostic only, gated by the [BuildConfig.DISCOVERY_ENGINE] flag and
     * throttled so it can't run on the hot path.
     */
    private fun maybeRunDiscovery(packageName: String, root: AccessibilityNodeView) {
        // Suggestions are a user-controlled feature; verbose candidate logging is a
        // debug-only diagnostic. Run discovery if either is active.
        val suggest = SettingsRepository.discoverySuggestionsEnabled()
        val logDiagnostics = BuildConfig.DISCOVERY_ENGINE
        if (!suggest && !logDiagnostics) return

        val now = SystemClock.uptimeMillis()
        if (now - lastDiscoveryUptimeMs < DISCOVERY_INTERVAL_MS) return
        lastDiscoveryUptimeMs = now

        val candidates = try {
            DiscoveryEngine.discover(root)
        } catch (t: Throwable) {
            return
        }
        if (candidates.isEmpty()) return

        if (logDiagnostics) {
            Log.d(DISCOVERY_TAG, "Candidates in $packageName (proposal only, not clicked):")
        }
        candidates.forEach { c ->
            if (logDiagnostics) {
                Log.d(
                    DISCOVERY_TAG,
                    "  ${c.target} text=\"${c.text}\" viewId=${c.viewId} clickable=${c.hasClickableTarget}",
                )
            }
            // Clickable skip controls, plus next-episode controls the engine has
            // already vetted as end-of-episode cards, become user-facing suggestions.
            if (suggest && c.hasClickableTarget) {
                DiscoveryRepository.propose(
                    DiscoveredEntry(
                        packageName = packageName,
                        target = c.target,
                        viewId = c.viewId,
                        label = if (c.viewId == null) c.text else null,
                    ),
                )
            }
        }
    }

    private fun dispatchTap(point: Point) {
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCancelled(g: GestureDescription?) {
                    Log.w(TAG, "Gesture tap cancelled at (${point.x},${point.y})")
                }
            },
            null,
        )
        if (!dispatched) Log.w(TAG, "dispatchGesture rejected at (${point.x},${point.y})")
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        scopeWatcher.cancel()
        ServiceRuntime.setRunning(false)
        inspector?.shutdown()
        inspector = null
        worker?.quitSafely()
        worker = null
        workerHandler = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SkipperKit"
        private const val DISCOVERY_TAG = "SkipperKitDiscovery"

        /** A short stroke registers as a single tap. */
        private const val TAP_DURATION_MS = 50L

        /** Discovery is diagnostic; throttle hard so it never loads the hot path. */
        private const val DISCOVERY_INTERVAL_MS = 5000L

        /** Teach mode candidate sweep rate; one sweep per second is plenty. */
        private const val TEACH_SWEEP_INTERVAL_MS = 1000L

        /** The built-in apps; always in scope. User-added apps extend this at runtime.
         *  Must stay in sync with accessibility_service_config.xml packageNames. */
        val BASELINE_PACKAGES: Set<String> = setOf(
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus",
            "com.crunchyroll.crunchyroid",
            "com.wbd.stream",
            "com.hulu.plus",
            "com.cbs.app",
            "com.peacocktv.peacockandroid",
            "com.apple.atve.androidtv.appletv",
        )
    }
}
