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
    fun findFirst(
        root: NodeView,
        viewIds: List<String>,
        labels: List<String>,
        labelPrefixes: List<String> = emptyList(),
    ): NodeView? {
        return search(root, viewIds, labels, labelPrefixes, depth = 0)
    }

    private fun search(
        node: NodeView,
        viewIds: List<String>,
        labels: List<String>,
        labelPrefixes: List<String>,
        depth: Int,
    ): NodeView? {
        if (NodeMatcher.matches(node, viewIds, labels, labelPrefixes)) return node
        if (depth >= MAX_DEPTH) return null
        for (i in 0 until node.childCount) {
            val child = node.childAt(i) ?: continue
            val hit = search(child, viewIds, labels, labelPrefixes, depth + 1)
            if (hit != null) return hit
        }
        return null
    }

    /**
     * Visits every node in the subtree rooted at [root] (depth-first), invoking
     * [action] on each. Stops descending when [MAX_DEPTH] is reached.
     */
    fun forEach(root: NodeView, action: (NodeView) -> Unit) {
        visitAll(root, action, depth = 0)
    }

    private fun visitAll(node: NodeView, action: (NodeView) -> Unit, depth: Int) {
        action(node)
        if (depth >= MAX_DEPTH) return
        for (i in 0 until node.childCount) {
            val child = node.childAt(i) ?: continue
            visitAll(child, action, depth + 1)
        }
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
