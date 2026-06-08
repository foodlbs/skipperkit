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
import com.skipperkit.matching.Point
import com.skipperkit.matching.SkipEngine
import com.skipperkit.settings.SettingsRepository

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
        Log.i(TAG, "Service connected (inspector=${inspector != null})")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in SUPPORTED_PACKAGES) return

        ServiceRuntime.recordActivity(System.currentTimeMillis())
        workerHandler?.post { runEngine(packageName) }
        inspector?.requestDump { rootInActiveWindow }
    }

    private fun runEngine(packageName: String) {
        val config = SettingsRepository.configFor(packageName) ?: return
        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            Log.w(TAG, "Could not obtain root window", t)
            null
        } ?: return

        val result = try {
            engine.onTree(packageName, AccessibilityNodeView(root), config)
        } catch (t: Throwable) {
            // The tree can be invalidated mid-walk while the player animates.
            Log.w(TAG, "Engine pass aborted (node invalidated)", t)
            return
        }

        when (result) {
            is SkipEngine.Result.Clicked ->
                Log.i(TAG, "Clicked ${result.target} in $packageName")
            is SkipEngine.Result.NeedsGesture -> {
                Log.i(TAG, "No clickable ancestor for ${result.target}; tapping via gesture")
                dispatchTap(result.point)
            }
            else -> Unit
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

        /** A short stroke registers as a single tap. */
        private const val TAP_DURATION_MS = 50L

        val SUPPORTED_PACKAGES: Set<String> = setOf(
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus",
        )
    }
}
