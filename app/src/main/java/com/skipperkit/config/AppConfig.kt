package com.skipperkit.config

/** The three button kinds SkipperKit acts on, plus user-taught custom buttons. */
enum class SkipTarget {
    SKIP_INTRO,
    SKIP_RECAP,
    NEXT_EPISODE,
    /** A user-taught named button (teach mode). */
    CUSTOM,
}

/** One resolved set of identifiers to search for, derived from [AppConfig]. */
data class Matcher(
    val target: SkipTarget,
    val viewIds: List<String>,
    val labels: List<String>,
    val labelPrefixes: List<String> = emptyList(),
    val customName: String? = null,
)

/**
 * Per-app, data-driven configuration. All identifiers live here, never in the
 * matching logic. View-id lists hold Compose testTags (preferred); label lists
 * are the case-insensitive text fallback.
 */
data class AppConfig(
    val packageName: String,
    val locale: String,
    val skipIntroViewIds: List<String>,
    val skipIntroLabels: List<String>,
    val skipRecapViewIds: List<String>,
    val skipRecapLabels: List<String>,
    val nextEpisodeViewIds: List<String>,
    val nextEpisodeLabels: List<String>,
    val enabled: Boolean,
    val autoNextEnabled: Boolean,
    // Optional prefix matchers for dynamic text (e.g. Prime "Next up: <title>").
    // Default empty so existing configs/tests are unaffected.
    val skipIntroLabelPrefixes: List<String> = emptyList(),
    val skipRecapLabelPrefixes: List<String> = emptyList(),
    val nextEpisodeLabelPrefixes: List<String> = emptyList(),
    val customButtons: List<CustomButton> = emptyList(),
) {
    /**
     * Search order: skip buttons first, then next-episode (only when auto-next
     * is enabled). Skip Intro and Skip Recap may share a view-id on some apps
     * (e.g. Netflix's skipCreditsButtonTestTag) — that's fine, the first hit
     * wins and we just click the skip button.
     */
    fun activeMatchers(): List<Matcher> = buildList {
        add(Matcher(SkipTarget.SKIP_INTRO, skipIntroViewIds, skipIntroLabels, skipIntroLabelPrefixes))
        add(Matcher(SkipTarget.SKIP_RECAP, skipRecapViewIds, skipRecapLabels, skipRecapLabelPrefixes))
        if (autoNextEnabled) {
            add(Matcher(SkipTarget.NEXT_EPISODE, nextEpisodeViewIds, nextEpisodeLabels, nextEpisodeLabelPrefixes))
        }
        customButtons.filter { it.enabled }.forEach { b ->
            add(Matcher(SkipTarget.CUSTOM, b.viewIds, b.labels, customName = b.name))
        }
    }
}
