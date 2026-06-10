package com.skipperkit.settings

import com.skipperkit.config.CustomButton
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
    private val customButton = CustomButton(
        key = "com.example.player:id/dismiss",
        name = "Dismiss Rating",
        viewIds = listOf("com.example.player:id/dismiss"),
        labels = emptyList(),
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
    fun `version 2 is accepted and version 3 is rejected`() {
        val v2 = """{"skipperkitTaughtApp":2,"packageName":"com.example.player","displayName":"X","buttons":[]}"""
        assertEquals("com.example.player", TaughtAppPort.parse(v2)!!.app.packageName)
        val v3 = """{"skipperkitTaughtApp":3,"packageName":"com.example.player","buttons":[]}"""
        assertNull(TaughtAppPort.parse(v3))
    }

    @Test
    fun `v2 round-trip with one custom button`() {
        val shared = TaughtAppPort.parse(
            TaughtAppPort.export(app, listOf(intro), listOf(customButton)),
        )!!
        assertEquals(app, shared.app)
        assertEquals(listOf(intro), shared.entries)
        assertEquals(1, shared.customButtons.size)
        val cb = shared.customButtons.single()
        assertEquals("Dismiss Rating", cb.name)
        assertEquals(listOf("com.example.player:id/dismiss"), cb.viewIds)
        assertTrue(cb.labels.isEmpty())
        assertEquals(true, cb.enabled)
    }

    @Test
    fun `v1 file parses with empty custom buttons`() {
        val v1 = """
            {"skipperkitTaughtApp":1,"packageName":"com.example.player","displayName":"Example Player",
             "buttons":[{"target":"SKIP_INTRO","viewId":"com.example.player:id/skip"}]}
        """.trimIndent()
        val shared = TaughtAppPort.parse(v1)!!
        assertTrue(shared.customButtons.isEmpty())
        assertEquals(listOf(intro), shared.entries)
    }

    @Test
    fun `incoming key field is ignored and recomputed`() {
        val json = """
            {"skipperkitTaughtApp":2,"packageName":"com.example.player","displayName":"X",
             "buttons":[],
             "customButtons":[{"key":"injected-key","name":"Foo","viewId":"com.example.player:id/foo","enabled":true}]}
        """.trimIndent()
        val shared = TaughtAppPort.parse(json)!!
        assertEquals("com.example.player:id/foo", shared.customButtons.single().key)
    }

    @Test
    fun `custom button without viewId and label is dropped`() {
        val json = """
            {"skipperkitTaughtApp":2,"packageName":"com.example.player","displayName":"X",
             "buttons":[],
             "customButtons":[{"name":"Bad","enabled":true},{"name":"Good","label":"press me","enabled":true}]}
        """.trimIndent()
        val shared = TaughtAppPort.parse(json)!!
        assertEquals(1, shared.customButtons.size)
        assertEquals("Good", shared.customButtons.single().name)
    }

    @Test
    fun `more than 20 custom buttons are capped`() {
        val items = (0 until 30).joinToString(",") {
            """{"name":"Btn$it","viewId":"com.example.player:id/btn$it","enabled":true}"""
        }
        val json = """
            {"skipperkitTaughtApp":2,"packageName":"com.example.player","displayName":"X",
             "buttons":[],"customButtons":[$items]}
        """.trimIndent()
        assertEquals(20, TaughtAppPort.parse(json)!!.customButtons.size)
    }
}
