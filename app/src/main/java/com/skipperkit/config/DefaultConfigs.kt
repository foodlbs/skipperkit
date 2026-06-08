package com.skipperkit.config

/**
 * Bundled English defaults. Netflix values are verified against a real
 * device dump (2026-06-08). Prime values are an unverified placeholder until we
 * capture a Prime Video accessibility dump (Phase 5) — text-only for now.
 */
object DefaultConfigs {

    val NETFLIX = AppConfig(
        packageName = "com.netflix.mediaclient",
        locale = "en",
        // skipCreditsButtonTestTag is the single clickable skip button for
        // Intro / Recap / Credits; verified on device.
        skipIntroViewIds = listOf("skipCreditsButtonTestTag"),
        skipIntroLabels = listOf("Skip Intro", "SKIP INTRO"),
        skipRecapViewIds = listOf("skipCreditsButtonTestTag"),
        skipRecapLabels = listOf("Skip Recap", "SKIP RECAP"),
        // Capital-N tag is the end-of-episode card, not the always-present
        // control-bar "Next Ep." (nextEpisodeButtonTestTag). Auto-next off by
        // default until the end-card is verified.
        nextEpisodeViewIds = listOf("NextEpisodeButtonTestTag"),
        nextEpisodeLabels = listOf("Next Episode"),
        enabled = true,
        autoNextEnabled = false,
    )

    val PRIME = AppConfig(
        packageName = "com.amazon.avod.thirdpartyclient",
        locale = "en",
        // Verified on device: the skip button is a Prime "contextualButton" with
        // a stable full test_tag, wrapping a "Skip Intro" text child.
        // A sibling contextualButton_go_ad_free_Button exists — we deliberately
        // match only the skip_* tags so we never tap the ad upsell.
        skipIntroViewIds = listOf(
            "test_tag:contextualButton_Overlay:contextualButton_Panel:contextualButton_skip_intro_Button",
        ),
        skipIntroLabels = listOf("Skip Intro"),
        // Recap tag inferred from the skip_intro pattern (recap not captured);
        // the "Skip Recap" text fallback covers it regardless.
        skipRecapViewIds = listOf(
            "test_tag:contextualButton_Overlay:contextualButton_Panel:contextualButton_skip_recap_Button",
        ),
        skipRecapLabels = listOf("Skip Recap"),
        // Prime's "Next up" card's clickable container has no view-id and no
        // static label — only the dynamic child text "Next up: <title>". Prefix
        // matching targets that text node; the ancestor walk then clicks the card
        // container. Auto-next stays OFF by default but is now supported.
        nextEpisodeViewIds = emptyList(),
        nextEpisodeLabels = emptyList(),
        nextEpisodeLabelPrefixes = listOf("Next up:"),
        enabled = true,
        autoNextEnabled = false,
    )

    // Verified on device: Disney+ uses native Android view-ids (not Compose
    // testTags), and the clickable node itself carries both the id and the text
    // (no ancestor walk needed). Skip Intro clicked 1x, Skip Recap clicked 2x.
    val DISNEY = AppConfig(
        packageName = "com.disney.disneyplus",
        locale = "en",
        skipIntroViewIds = listOf("com.disney.disneyplus:id/skipIntro"),
        skipIntroLabels = listOf("Skip Intro"),
        skipRecapViewIds = listOf("com.disney.disneyplus:id/skipRecap"),
        skipRecapLabels = listOf("Skip Recap"),
        // upNextLiteButton is the end-of-episode card's "NEXT EPISODE" button
        // (verified, stable id) — NOT the always-present control-bar
        // id/nextButton "PLAY NEXT". So Disney+ auto-next is genuinely supported
        // via view-id (off by default, but functional when enabled).
        nextEpisodeViewIds = listOf("com.disney.disneyplus:id/upNextLiteButton"),
        nextEpisodeLabels = listOf("Next Episode"),
        enabled = true,
        autoNextEnabled = false,
    )

    val ALL: List<AppConfig> = listOf(NETFLIX, PRIME, DISNEY)

    fun forPackage(packageName: String): AppConfig? =
        ALL.firstOrNull { it.packageName == packageName }
}
