package com.skipperkit.service

import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Diagnostic-only tree dumper, gated by [com.skipperkit.BuildConfig.DEBUG_NODE_INSPECTOR].
 *
 * Purpose (Phase 2): on a real device, foreground Netflix / Prime Video and read
 * the dump in Logcat (tag [TAG]) to answer three questions before any click
 * logic exists:
 *   1. Does "Skip Intro" actually appear in the accessibility node tree?
 *   2. What stable identifiers (viewIdResourceName) do those nodes carry?
 *   3. Could View-ID matching replace fragile text matching?
 *
 * Design constraints driven by the performance requirements:
 *   - The work runs on a dedicated [HandlerThread], never the framework callback
 *     thread, so traversal can never ANR the foregrounded streaming app.
 *   - A leading-edge throttle ([MIN_INTERVAL_MS]) collapses the burst of
 *     typeWindowContentChanged events into at most one dump per interval.
 *   - Child nodes obtained during traversal are recycled; getChild() returning
 *     null (transient invalidation) is tolerated, and the whole walk is guarded
 *     so a stale subtree throwing can't crash the service.
 */
internal class NodeInspector {

    private val thread = HandlerThread("SkipperKitInspector").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var lastDumpUptimeMs = 0L

    /**
     * Throttled request to dump the current tree. [rootProvider] is invoked on
     * the inspector thread and should return a fresh root (e.g.
     * getRootInActiveWindow()); the returned node is recycled here.
     */
    fun requestDump(rootProvider: () -> AccessibilityNodeInfo?) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDumpUptimeMs < MIN_INTERVAL_MS) return
        lastDumpUptimeMs = now

        handler.post {
            val root = try {
                rootProvider()
            } catch (t: Throwable) {
                Log.w(TAG, "Could not obtain root window", t)
                null
            } ?: return@post

            try {
                dump(root)
            } catch (t: Throwable) {
                // Nodes can be invalidated mid-walk while the UI animates; never
                // let that take down the service.
                Log.w(TAG, "Tree dump aborted (node invalidated)", t)
            } finally {
                @Suppress("DEPRECATION") // no-op on API 33+, still required below it
                root.recycle()
            }
        }
    }

    fun shutdown() {
        thread.quitSafely()
    }

    private fun dump(root: AccessibilityNodeInfo) {
        Log.d(TAG, "==== tree dump start (pkg=${root.packageName}) ====")
        val rect = Rect()
        traverse(root, depth = 0, rect = rect)
        Log.d(TAG, "==== tree dump end ====")
    }

    private fun traverse(node: AccessibilityNodeInfo, depth: Int, rect: Rect) {
        if (isInteresting(node)) {
            logNode(node, depth, rect)
        }

        if (depth >= MAX_DEPTH) return

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverse(child, depth + 1, rect)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    /**
     * We log any node carrying text, a content description, a view id, or that is
     * clickable — rather than leaf nodes only. Leaf-only logging would hide the
     * clickable ancestor (where the button's id and clickability usually live),
     * which is exactly the information needed to evaluate View-ID matching.
     */
    private fun isInteresting(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable ||
            !node.text.isNullOrEmpty() ||
            !node.contentDescription.isNullOrEmpty() ||
            !node.viewIdResourceName.isNullOrEmpty()
    }

    private fun logNode(node: AccessibilityNodeInfo, depth: Int, rect: Rect) {
        node.getBoundsInScreen(rect)
        Log.d(
            TAG,
            "[SkipperKit] depth=$depth" +
                " TEXT=${node.text}" +
                " DESC=${node.contentDescription}" +
                " VIEW_ID=${node.viewIdResourceName}" +
                " CLICKABLE=${node.isClickable}" +
                " BOUNDS=${rect.toShortString()}",
        )
    }

    companion object {
        const val TAG = "SkipperKitInspector"

        /** Collapse bursts of content-changed events into one dump per second. */
        private const val MIN_INTERVAL_MS = 1000L

        /** Guards against pathological / cyclic trees. */
        private const val MAX_DEPTH = 60
    }
}
