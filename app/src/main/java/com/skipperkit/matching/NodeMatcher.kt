package com.skipperkit.matching

/**
 * Decides whether a single node matches a configured target. View-id (Compose
 * testTag) is checked first because it is stable and locale-independent; text
 * and contentDescription are a case-insensitive fallback.
 *
 * All comparisons operate only on configured values — no labels are hardcoded
 * here.
 */
object NodeMatcher {

    fun matches(
        node: NodeView,
        viewIds: List<String>,
        labels: List<String>,
        labelPrefixes: List<String> = emptyList(),
    ): Boolean {
        val viewId = node.viewId
        if (!viewId.isNullOrEmpty() && viewIds.any { it == viewId }) return true

        if (labelMatches(node.text, labels)) return true
        if (labelMatches(node.contentDescription, labels)) return true

        // Prefix matching handles dynamic text where the meaningful part is fixed
        // but a variable suffix follows (e.g. Prime's "Next up: <show title>").
        if (prefixMatches(node.text, labelPrefixes)) return true
        if (prefixMatches(node.contentDescription, labelPrefixes)) return true

        return false
    }

    private fun labelMatches(value: String?, labels: List<String>): Boolean {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) return false
        return labels.any { it.equals(trimmed, ignoreCase = true) }
    }

    private fun prefixMatches(value: String?, prefixes: List<String>): Boolean {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) return false
        return prefixes.any { it.isNotEmpty() && trimmed.startsWith(it, ignoreCase = true) }
    }
}
