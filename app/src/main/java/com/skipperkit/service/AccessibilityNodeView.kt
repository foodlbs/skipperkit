package com.skipperkit.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.skipperkit.matching.NodeView
import com.skipperkit.matching.Point

/**
 * Runtime adapter: presents a real [AccessibilityNodeInfo] as a [NodeView] so
 * the pure matching core can operate on it. Parent/child accessors wrap freshly
 * returned framework nodes.
 *
 * Recycling note: AccessibilityNodeInfo.recycle() is a deprecated no-op on
 * API 33+ (every target device here is newer). The traversed player subtree is
 * small and runs on a throttled background thread, so we let GC reclaim wrapped
 * nodes rather than thread recycle() through the abstraction. The verbose
 * inspector keeps its explicit recycling.
 */
class AccessibilityNodeView(
    private val node: AccessibilityNodeInfo,
) : NodeView {

    override val text: String? get() = node.text?.toString()
    override val contentDescription: String? get() = node.contentDescription?.toString()
    override val viewId: String? get() = node.viewIdResourceName
    override val isClickable: Boolean get() = node.isClickable

    override val parent: NodeView?
        get() = node.parent?.let { AccessibilityNodeView(it) }

    override val childCount: Int get() = node.childCount

    override fun childAt(index: Int): NodeView? =
        node.getChild(index)?.let { AccessibilityNodeView(it) }

    override fun click(): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    override fun boundsCenter(): Point? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return null
        return Point(rect.centerX(), rect.centerY())
    }
}
