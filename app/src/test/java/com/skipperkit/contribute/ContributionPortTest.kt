package com.skipperkit.contribute

import com.skipperkit.config.CustomButton
import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionPortTest {

    private val intro = DiscoveredEntry(
        "com.example.player", SkipTarget.SKIP_INTRO, viewId = "com.example.player:id/skip", label = null,
    )

    private fun build(
        entries: List<DiscoveredEntry>,
        customs: List<CustomButton> = emptyList(),
    ) = ContributionPort.build(
        packageName = "com.example.player",
        displayName = "Example Player",
        entries = entries,
        customButtons = customs,
        appVersionName = "9.9",
        skipperkitVersion = "0.1.0",
        locale = "en",
    )

    @Test
    fun `payload carries marker, app data, buttons, and metadata`() {
        val o = JSONObject(build(listOf(intro))!!)
        assertEquals(2, o.getInt("skipperkitContribution"))
        assertEquals("com.example.player", o.getString("packageName"))
        assertEquals("9.9", o.getString("appVersionName"))
        assertEquals("0.1.0", o.getString("skipperkitVersion"))
        assertEquals("en", o.getString("locale"))
        val b = o.getJSONArray("buttons").getJSONObject(0)
        assertEquals("SKIP_INTRO", b.getString("target"))
        assertEquals("com.example.player:id/skip", b.getString("viewId"))
    }

    @Test
    fun `next-episode entries are excluded and empty result is null`() {
        val next = DiscoveredEntry("com.example.player", SkipTarget.NEXT_EPISODE, "x/y", null)
        val o = JSONObject(build(listOf(intro, next))!!)
        assertEquals(1, o.getJSONArray("buttons").length())
        assertNull(build(listOf(next)))
        assertNull(build(emptyList()))
    }

    @Test
    fun `entries with neither id nor label are dropped and long names capped`() {
        val bare = DiscoveredEntry("com.example.player", SkipTarget.SKIP_INTRO, null, null)
        assertNull(build(listOf(bare)))

        val json = ContributionPort.build(
            "com.example.player", "x".repeat(200), listOf(intro),
            appVersionName = null, skipperkitVersion = "0.1.0", locale = "en",
        )!!
        assertEquals(50, JSONObject(json).getString("displayName").length)
    }

    @Test
    fun `null app version is omitted, buttons capped at 20`() {
        val json = ContributionPort.build(
            "com.example.player", "X", List(50) {
                DiscoveredEntry("com.example.player", SkipTarget.SKIP_INTRO, "id/$it", null)
            },
            appVersionName = null, skipperkitVersion = "0.1.0", locale = "en",
        )!!
        val o = JSONObject(json)
        assertTrue(!o.has("appVersionName"))
        assertEquals(20, o.getJSONArray("buttons").length())
    }

    @Test
    fun `custom buttons ride along and alone are enough`() {
        val custom = CustomButton(
            key = "com.example.player:id/dismiss",
            name = "Dismiss rating",
            viewIds = listOf("com.example.player:id/dismiss"),
            labels = emptyList(),
        )
        val o = JSONObject(build(emptyList(), listOf(custom))!!)
        assertEquals(0, o.getJSONArray("buttons").length())
        val c = o.getJSONArray("customButtons").getJSONObject(0)
        assertEquals("Dismiss rating", c.getString("name"))
        assertEquals("com.example.player:id/dismiss", c.getString("viewId"))
        assertTrue(c.isNull("label"))

        val both = JSONObject(build(listOf(intro), listOf(custom))!!)
        assertEquals(1, both.getJSONArray("buttons").length())
        assertEquals(1, both.getJSONArray("customButtons").length())
    }

    @Test
    fun `custom buttons are capped at 20 and names at 50`() {
        val many = List(50) {
            CustomButton("id/$it", "n$it", listOf("com.example.player:id/$it"), emptyList())
        }
        val o = JSONObject(build(emptyList(), many)!!)
        assertEquals(20, o.getJSONArray("customButtons").length())

        val longName = CustomButton("id/x", "x".repeat(200), listOf("com.example.player:id/x"), emptyList())
        val n = JSONObject(build(emptyList(), listOf(longName))!!)
            .getJSONArray("customButtons").getJSONObject(0).getString("name")
        assertEquals(50, n.length)
    }
}
