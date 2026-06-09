package com.skipperkit.settings

import com.skipperkit.config.ConfigRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaughtAppsRepositoryTest {

    @Before @After fun reset() {
        TaughtAppsRepository.onChanged = null
        TaughtAppsRepository.restore(emptyList())
        SettingsRepository.dropApp("com.hbo.hbonow")
    }

    @Test fun `add appends and exposes package names`() {
        TaughtAppsRepository.add(TaughtApp("com.hbo.hbonow", "Max"))
        assertEquals(listOf("com.hbo.hbonow"), TaughtAppsRepository.currentPackages())
    }

    @Test fun `add is idempotent on the same package`() {
        TaughtAppsRepository.add(TaughtApp("com.hbo.hbonow", "Max"))
        TaughtAppsRepository.add(TaughtApp("com.hbo.hbonow", "Max (dup)"))
        assertEquals(1, TaughtAppsRepository.taughtApps.value.size)
    }

    @Test fun `remove drops the app`() {
        TaughtAppsRepository.add(TaughtApp("com.hbo.hbonow", "Max"))
        TaughtAppsRepository.remove("com.hbo.hbonow")
        assertTrue(TaughtAppsRepository.taughtApps.value.isEmpty())
    }

    @Test fun `add pushes packages into ConfigRepository as a stub config`() {
        TaughtAppsRepository.add(TaughtApp("com.hbo.hbonow", "Max"))
        assertTrue(ConfigRepository.forPackage("com.hbo.hbonow") != null)
    }

    @Test fun `onChanged fires with the new list`() {
        var got: List<TaughtApp>? = null
        TaughtAppsRepository.onChanged = { got = it }
        TaughtAppsRepository.add(TaughtApp("com.hbo.hbonow", "Max"))
        assertEquals(1, got?.size)
    }

    @Test fun `restore replaces and re-pushes to ConfigRepository`() {
        TaughtAppsRepository.restore(listOf(TaughtApp("com.peacocktv.peacockandroid", "Peacock")))
        assertEquals(listOf("com.peacocktv.peacockandroid"), TaughtAppsRepository.currentPackages())
        assertTrue(ConfigRepository.forPackage("com.peacocktv.peacockandroid") != null)
    }

    @Test fun `adding an app makes its toggles editable`() {
        TaughtAppsRepository.add(TaughtApp("com.hbo.hbonow", "Max"))
        SettingsRepository.setAppEnabled("com.hbo.hbonow", false)
        assertEquals(false, SettingsRepository.settings.value.apps["com.hbo.hbonow"]?.enabled)
    }

    @Test fun `removing an app drops its toggles`() {
        TaughtAppsRepository.add(TaughtApp("com.hbo.hbonow", "Max"))
        TaughtAppsRepository.remove("com.hbo.hbonow")
        assertEquals(null, SettingsRepository.settings.value.apps["com.hbo.hbonow"])
    }
}
