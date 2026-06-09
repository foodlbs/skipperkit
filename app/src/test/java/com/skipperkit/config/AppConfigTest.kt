package com.skipperkit.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun `forPackage resolves Netflix`() {
        assertEquals(DefaultConfigs.NETFLIX, DefaultConfigs.forPackage("com.netflix.mediaclient"))
    }

    @Test
    fun `forPackage resolves Prime`() {
        assertEquals(DefaultConfigs.PRIME, DefaultConfigs.forPackage("com.amazon.avod.thirdpartyclient"))
    }

    @Test
    fun `forPackage resolves Disney+`() {
        assertEquals(DefaultConfigs.DISNEY, DefaultConfigs.forPackage("com.disney.disneyplus"))
    }

    @Test
    fun `forPackage returns null for unsupported package`() {
        assertNull(DefaultConfigs.forPackage("com.netflix.ninja"))
    }

    @Test
    fun `activeMatchers excludes next episode when auto-next off`() {
        val targets = DefaultConfigs.NETFLIX.copy(autoNextEnabled = false).activeMatchers().map { it.target }
        assertEquals(listOf(SkipTarget.SKIP_INTRO, SkipTarget.SKIP_RECAP), targets)
    }

    @Test
    fun `activeMatchers includes next episode when auto-next on`() {
        val targets = DefaultConfigs.NETFLIX.copy(autoNextEnabled = true).activeMatchers().map { it.target }
        assertEquals(
            listOf(SkipTarget.SKIP_INTRO, SkipTarget.SKIP_RECAP, SkipTarget.NEXT_EPISODE),
            targets,
        )
    }

    @Test
    fun `netflix skip uses the verified compose testTag`() {
        assertTrue(DefaultConfigs.NETFLIX.skipIntroViewIds.contains("skipCreditsButtonTestTag"))
    }

    @Test
    fun `prime skip uses the verified contextual button test_tag`() {
        assertTrue(
            DefaultConfigs.PRIME.skipIntroViewIds.any {
                it.endsWith("contextualButton_skip_intro_Button")
            },
        )
    }

    @Test
    fun `disney targets the up-next card button, not the control-bar next`() {
        assertTrue(DefaultConfigs.DISNEY.nextEpisodeViewIds.contains("com.disney.disneyplus:id/upNextLiteButton"))
        assertTrue(DefaultConfigs.DISNEY.nextEpisodeViewIds.none { it.endsWith(":id/nextButton") })
    }

    @Test
    fun `prime next-episode is supported via dynamic prefix`() {
        assertTrue(DefaultConfigs.PRIME.nextEpisodeLabelPrefixes.contains("Next up:"))
        // The prefix matcher is only active when auto-next is on.
        val targets = DefaultConfigs.PRIME.copy(autoNextEnabled = true).activeMatchers()
        val nextMatcher = targets.first { it.target == SkipTarget.NEXT_EPISODE }
        assertTrue(nextMatcher.labelPrefixes.contains("Next up:"))
    }

    @Test
    fun `label-only tier resolves by package and matches on skip labels`() {
        val labelOnly = listOf(
            "com.crunchyroll.crunchyroid",
            "com.wbd.stream",
            "com.hulu.plus",
            "com.cbs.app",
            "com.peacocktv.peacockandroid",
            "com.apple.atve.androidtv.appletv",
        )
        labelOnly.forEach { pkg ->
            val config = DefaultConfigs.forPackage(pkg)
            assertTrue("$pkg missing from DefaultConfigs", config != null)
            assertTrue("$pkg must match Skip Intro by label", config!!.skipIntroLabels.isNotEmpty())
            assertTrue("$pkg must match Skip Recap by label", config.skipRecapLabels.isNotEmpty())
        }
    }

    @Test
    fun `label-only tier has no auto-next configuration`() {
        val verified = setOf(
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus",
        )
        DefaultConfigs.ALL.filter { it.packageName !in verified }.forEach { config ->
            assertTrue(config.packageName, config.nextEpisodeViewIds.isEmpty())
            assertTrue(config.packageName, config.nextEpisodeLabels.isEmpty())
            assertTrue(config.packageName, config.nextEpisodeLabelPrefixes.isEmpty())
            assertTrue(config.packageName, !config.autoNextEnabled)
        }
    }

    @Test
    fun `all bundled packages are unique`() {
        val packages = DefaultConfigs.ALL.map { it.packageName }
        assertEquals(packages.size, packages.toSet().size)
    }

    @Test
    fun `prime never targets the ad upsell button`() {
        val allPrimeViewIds = DefaultConfigs.PRIME.run {
            skipIntroViewIds + skipRecapViewIds + nextEpisodeViewIds
        }
        assertTrue(allPrimeViewIds.none { it.contains("go_ad_free") })
    }
}
