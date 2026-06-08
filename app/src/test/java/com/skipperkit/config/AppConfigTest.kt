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
    fun `prime never targets the ad upsell button`() {
        val allPrimeViewIds = DefaultConfigs.PRIME.run {
            skipIntroViewIds + skipRecapViewIds + nextEpisodeViewIds
        }
        assertTrue(allPrimeViewIds.none { it.contains("go_ad_free") })
    }
}
