package com.skipperkit.config

import com.skipperkit.discovery.DiscoveredEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaughtConfigTest {

    private val hbo = "com.hbo.hbonow"

    @Before @After fun reset() {
        ConfigRepository.setTaughtApps(emptyList())
        ConfigRepository.setDiscovered(emptyList())
    }

    @Test fun `taught app appears as an enabled empty-matcher config`() {
        ConfigRepository.setTaughtApps(listOf(hbo))
        val c = ConfigRepository.forPackage(hbo)!!
        assertTrue(c.enabled)
        assertTrue(c.skipIntroViewIds.isEmpty() && c.skipIntroLabels.isEmpty())
        assertEquals(false, c.autoNextEnabled)
    }

    @Test fun `discovered entries populate a taught app's matchers`() {
        ConfigRepository.setTaughtApps(listOf(hbo))
        ConfigRepository.setDiscovered(
            listOf(DiscoveredEntry(hbo, SkipTarget.SKIP_INTRO, viewId = null, label = "Skip Intro")),
        )
        assertTrue(ConfigRepository.forPackage(hbo)!!.skipIntroLabels.contains("Skip Intro"))
    }

    @Test fun `removing a taught app drops its config`() {
        ConfigRepository.setTaughtApps(listOf(hbo))
        ConfigRepository.setTaughtApps(emptyList())
        assertNull(ConfigRepository.forPackage(hbo))
    }

    @Test fun `a taught package that duplicates a built-in does not double it`() {
        ConfigRepository.setTaughtApps(listOf("com.netflix.mediaclient"))
        assertEquals(
            1,
            ConfigRepository.configs.value.count { it.packageName == "com.netflix.mediaclient" },
        )
    }
}
