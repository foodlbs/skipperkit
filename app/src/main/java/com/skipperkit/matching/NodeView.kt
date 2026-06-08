package com.skipperkit.matching

/** A screen coordinate, in pixels. Pure (no android.graphics dependency). */
data class Point(val x: Int, val y: Int)

/**
 * A minimal, Android-free view of an accessibility node. The matching logic
 * ([NodeMatcher], [TreeSearch], [SkipEngine]) depends only on this interface so
 * it can be unit-tested with fake trees, with no AccessibilityNodeInfo or
 * Robolectric needed. The runtime adapter
 * [com.skipperkit.service.AccessibilityNodeView] wraps the real framework node.
 */
interface NodeView {
    val text: String?
    val contentDescription: String?
    val viewId: String?
    val isClickable: Boolean

    val parent: NodeView?
    val childCount: Int
    fun childAt(index: Int): NodeView?

    /** Performs ACTION_CLICK. Returns true if the framework accepted the action. */
    fun click(): Boolean

    /** Center of the node's on-screen bounds, for the gesture fallback. Null if unavailable/empty. */
    fun boundsCenter(): Point?
}
