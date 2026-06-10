package com.skipperkit.discovery

import com.skipperkit.config.SkipTarget

/**
 * A skip control found by [DiscoveryEngine] and (once approved) promoted into the
 * active config. Identified by a stable view-id when available, otherwise by its
 * visible label. Only SKIP_INTRO / SKIP_RECAP are ever represented here —
 * NEXT_EPISODE is excluded because the heuristic can't tell the always-present
 * control-bar button from the end-of-episode card.
 */
data class DiscoveredEntry(
    val packageName: String,
    val target: SkipTarget,
    val viewId: String?,
    val label: String?,
) {
    /**
     * Stable identity for dedupe / approved / dismissed sets.
     * [packageName] is always the prefix up to the first `|`, so callers can match
     * all keys for a package with `key.startsWith("$packageName|")`.
     */
    val key: String get() = "$packageName|$target|${viewId ?: "label:${label?.lowercase()}"}"

    val displayLabel: String
        get() = when (target) {
            SkipTarget.SKIP_INTRO -> "Skip Intro"
            SkipTarget.SKIP_RECAP -> "Skip Recap"
            SkipTarget.NEXT_EPISODE -> "Next Episode"
            SkipTarget.CUSTOM -> "Custom"
        }
}
