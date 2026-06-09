package com.skipperkit.contribute

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

    private fun build(entries: List<DiscoveredEntry>) = ContributionPort.build(
        packageName = "com.example.player",
        displayName = "Example Player",
        entries = entries,
        appVersionName = "9.9",
        skipperkitVersion = "0.1.0",
        locale = "en",
    )

    @Test
    fun `payload carries marker, app data, buttons, and metadata`() {
        val o = JSONObject(build(listOf(intro))!!)
        assertEquals(1, o.getInt("skipperkitContribution"))
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
}
