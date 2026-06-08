package com.skipperkit.config

/** The three button kinds SkipperKit acts on. */
enum class SkipTarget { SKIP_INTRO, SKIP_RECAP, NEXT_EPISODE }

/** One resolved set of identifiers to search for, derived from [AppConfig]. */
data class Matcher(
    val target: SkipTarget,
    val viewIds: List<String>,
    val labels: List<String>,
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
) {
    /**
     * Search order: skip buttons first, then next-episode (only when auto-next
     * is enabled). Skip Intro and Skip Recap may share a view-id on some apps
     * (e.g. Netflix's skipCreditsButtonTestTag) — that's fine, the first hit
     * wins and we just click the skip button.
     */
    fun activeMatchers(): List<Matcher> = buildList {
        add(Matcher(SkipTarget.SKIP_INTRO, skipIntroViewIds, skipIntroLabels))
        add(Matcher(SkipTarget.SKIP_RECAP, skipRecapViewIds, skipRecapLabels))
        if (autoNextEnabled) {
            add(Matcher(SkipTarget.NEXT_EPISODE, nextEpisodeViewIds, nextEpisodeLabels))
        }
    }
}
