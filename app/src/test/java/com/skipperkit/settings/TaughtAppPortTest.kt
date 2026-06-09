package com.skipperkit.settings

import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaughtAppPortTest {

    private val app = TaughtApp("com.example.player", "Example Player")
    private val intro = DiscoveredEntry(
        "com.example.player", SkipTarget.SKIP_INTRO, viewId = "com.example.player:id/skip", label = null,
    )
    private val recap = DiscoveredEntry(
        "com.example.player", SkipTarget.SKIP_RECAP, viewId = null, label = "Skip Recap",
    )

    @Test
    fun `export then parse round-trips app and buttons`() {
        val shared = TaughtAppPort.parse(TaughtAppPort.export(app, listOf(intro, recap)))!!
        assertEquals(app, shared.app)
        assertEquals(listOf(intro, recap), shared.entries)
    }

    @Test
    fun `parse rejects non-json and missing format marker`() {
        assertNull(TaughtAppPort.parse("not json"))
        assertNull(TaughtAppPort.parse("""{"packageName":"com.example.player","buttons":[]}"""))
    }

    @Test
    fun `parse rejects missing or malformed package names`() {
        assertNull(TaughtAppPort.parse("""{"skipperkitTaughtApp":1,"buttons":[]}"""))
        assertNull(TaughtAppPort.parse("""{"skipperkitTaughtApp":1,"packageName":"no-dots","buttons":[]}"""))
        assertNull(
            TaughtAppPort.parse("""{"skipperkitTaughtApp":1,"packageName":"com.example; rm -rf","buttons":[]}"""),
        )
    }

    @Test
    fun `next-episode buttons are never exported or imported`() {
        val next = DiscoveredEntry("com.example.player", SkipTarget.NEXT_EPISODE, viewId = "x/y", label = null)
        val exported = TaughtAppPort.export(app, listOf(intro, next))
        assertTrue(!exported.contains("NEXT_EPISODE"))

        val handCrafted = """
            {"skipperkitTaughtApp":1,"packageName":"com.example.player","displayName":"X",
             "buttons":[{"target":"NEXT_EPISODE","viewId":"x/y"}]}
        """.trimIndent()
        assertEquals(emptyList<DiscoveredEntry>(), TaughtAppPort.parse(handCrafted)!!.entries)
    }

    @Test
    fun `buttons with neither viewId nor label are dropped`() {
        val handCrafted = """
            {"skipperkitTaughtApp":1,"packageName":"com.example.player",
             "buttons":[{"target":"SKIP_INTRO"},{"target":"SKIP_RECAP","label":"Skip Recap"}]}
        """.trimIndent()
        assertEquals(listOf(recap), TaughtAppPort.parse(handCrafted)!!.entries)
    }

    @Test
    fun `imported entries are forced onto the declared package`() {
        val handCrafted = """
            {"skipperkitTaughtApp":1,"packageName":"com.example.player",
             "buttons":[{"target":"SKIP_INTRO","packageName":"com.evil.other","viewId":"a/b"}]}
        """.trimIndent()
        val entries = TaughtAppPort.parse(handCrafted)!!.entries
        assertEquals(1, entries.size)
        assertEquals("com.example.player", entries.single().packageName)
    }

    @Test
    fun `blank display name falls back to the package`() {
        val handCrafted = """{"skipperkitTaughtApp":1,"packageName":"com.example.player","buttons":[]}"""
        assertEquals("com.example.player", TaughtAppPort.parse(handCrafted)!!.app.displayName)
    }

    @Test
    fun `imported buttons are capped and oversized strings truncated`() {
        val buttons = (0 until 100).joinToString(",") {
            """{"target":"SKIP_INTRO","viewId":"com.example.player:id/skip$it"}"""
        }
        val flood = """{"skipperkitTaughtApp":1,"packageName":"com.example.player","buttons":[$buttons]}"""
        assertEquals(20, TaughtAppPort.parse(flood)!!.entries.size)

        val longLabel = "x".repeat(10_000)
        val oversized = """
            {"skipperkitTaughtApp":1,"packageName":"com.example.player",
             "buttons":[{"target":"SKIP_INTRO","label":"$longLabel"}]}
        """.trimIndent()
        assertEquals(256, TaughtAppPort.parse(oversized)!!.entries.single().label!!.length)
    }

    @Test
    fun `unknown future format version is rejected`() {
        val handCrafted = """{"skipperkitTaughtApp":2,"packageName":"com.example.player","buttons":[]}"""
        assertNull(TaughtAppPort.parse(handCrafted))
    }
}
