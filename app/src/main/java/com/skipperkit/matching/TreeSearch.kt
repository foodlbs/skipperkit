package com.skipperkit.matching

/**
 * Tree traversal helpers, pure and testable against fake trees.
 */
object TreeSearch {

    /** Maximum depth walked; guards against pathological / cyclic trees. */
    const val MAX_DEPTH = 60

    /**
     * Depth-first search for the first node matching the given target. Returns
     * the matched node itself (which may be non-clickable text — the caller
     * resolves the clickable target via [firstClickableSelfOrAncestor]).
     */
    fun findFirst(root: NodeView, viewIds: List<String>, labels: List<String>): NodeView? {
        return search(root, viewIds, labels, depth = 0)
    }

    private fun search(
        node: NodeView,
        viewIds: List<String>,
        labels: List<String>,
        depth: Int,
    ): NodeView? {
        if (NodeMatcher.matches(node, viewIds, labels)) return node
        if (depth >= MAX_DEPTH) return null
        for (i in 0 until node.childCount) {
            val child = node.childAt(i) ?: continue
            val hit = search(child, viewIds, labels, depth + 1)
            if (hit != null) return hit
        }
        return null
    }

    /**
     * Walks up from [node] (inclusive) to the first clickable node. Netflix's
     * matched text node is itself non-clickable; its clickable Compose parent is
     * one level up. Returns null if nothing in the chain is clickable.
     */
    fun firstClickableSelfOrAncestor(node: NodeView): NodeView? {
        var current: NodeView? = node
        var hops = 0
        while (current != null && hops <= MAX_DEPTH) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return null
    }
}
