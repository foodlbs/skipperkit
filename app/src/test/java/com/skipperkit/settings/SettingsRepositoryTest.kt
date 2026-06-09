package com.skipperkit.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    private val netflix = "com.netflix.mediaclient"

    @After
    fun reset() {
        // Restore defaults so tests don't leak state through the singleton.
        SettingsRepository.setMaster(true)
        SettingsRepository.setDiscoverySuggestions(true)
        SettingsRepository.setAppEnabled(netflix, true)
        SettingsRepository.setFeature(netflix, "skipIntro", true)
        SettingsRepository.setFeature(netflix, "skipRecap", true)
        SettingsRepository.setFeature(netflix, "autoNext", false)
    }

    @Test
    fun `discovery suggestions default on and can be toggled`() {
        assertTrue(SettingsRepository.discoverySuggestionsEnabled())
        SettingsRepository.setDiscoverySuggestions(false)
        assertFalse(SettingsRepository.discoverySuggestionsEnabled())
    }

    @Test
    fun `defaults enable skip features for netflix`() {
        val config = SettingsRepository.configFor(netflix)!!
        assertTrue(config.enabled)
        assertTrue(config.skipIntroViewIds.isNotEmpty())
        assertTrue(config.skipRecapViewIds.isNotEmpty())
    }

    @Test
    fun `master off disables the config`() {
        SettingsRepository.setMaster(false)
        assertFalse(SettingsRepository.configFor(netflix)!!.enabled)
    }

    @Test
    fun `disabling skip intro empties its matchers`() {
        SettingsRepository.setFeature(netflix, "skipIntro", false)
        val config = SettingsRepository.configFor(netflix)!!
        assertTrue(config.skipIntroViewIds.isEmpty())
        assertTrue(config.skipIntroLabels.isEmpty())
        // Skip recap is untouched.
        assertTrue(config.skipRecapViewIds.isNotEmpty())
    }

    @Test
    fun `auto next toggle flows into config`() {
        SettingsRepository.setFeature(netflix, "autoNext", true)
        assertTrue(SettingsRepository.configFor(netflix)!!.autoNextEnabled)
    }

    @Test
    fun `unknown package returns null`() {
        assertEquals(null, SettingsRepository.configFor("com.unknown.app"))
    }
}
