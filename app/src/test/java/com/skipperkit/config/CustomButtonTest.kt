package com.skipperkit.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomButtonTest {

    private val nag = CustomButton(
        key = "com.example:id/rateLater",
        name = "Dismiss rating nag",
        viewIds = listOf("com.example:id/rateLater"),
        labels = listOf("Maybe later"),
    )

    private fun config(buttons: List<CustomButton>) = AppConfig(
        packageName = "com.example.app",
        locale = "en",
        skipIntroViewIds = listOf("a/intro"),
        skipIntroLabels = emptyList(),
        skipRecapViewIds = emptyList(),
        skipRecapLabels = emptyList(),
        nextEpisodeViewIds = emptyList(),
        nextEpisodeLabels = emptyList(),
        enabled = true,
        autoNextEnabled = false,
        customButtons = buttons,
    )

    @Test
    fun `enabled custom buttons become CUSTOM matchers carrying their name`() {
        val matchers = config(listOf(nag)).activeMatchers()
        val custom = matchers.single { it.target == SkipTarget.CUSTOM }
        assertEquals("Dismiss rating nag", custom.customName)
        assertEquals(listOf("com.example:id/rateLater"), custom.viewIds)
        assertEquals(listOf("Maybe later"), custom.labels)
        assertTrue("custom buttons must never prefix-match", custom.labelPrefixes.isEmpty())
    }

    @Test
    fun `disabled custom buttons are excluded`() {
        val matchers = config(listOf(nag.copy(enabled = false))).activeMatchers()
        assertTrue(matchers.none { it.target == SkipTarget.CUSTOM })
    }

    @Test
    fun `custom matchers come after streaming matchers`() {
        val matchers = config(listOf(nag)).activeMatchers()
        assertEquals(SkipTarget.SKIP_INTRO, matchers.first().target)
        assertEquals(SkipTarget.CUSTOM, matchers.last().target)
    }

    @Test
    fun `configs without custom buttons are unchanged`() {
        val targets = config(emptyList()).activeMatchers().map { it.target }
        assertEquals(listOf(SkipTarget.SKIP_INTRO, SkipTarget.SKIP_RECAP), targets)
    }
}
