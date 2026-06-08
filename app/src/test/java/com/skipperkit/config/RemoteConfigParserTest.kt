package com.skipperkit.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigParserTest {

    @Test
    fun `parses a full app entry`() {
        val json = """
            {
              "version": 1,
              "apps": [
                {
                  "packageName": "com.netflix.mediaclient",
                  "locale": "en",
                  "skipIntroViewIds": ["skipCreditsButtonTestTag"],
                  "skipIntroLabels": ["Skip Intro"],
                  "skipRecapViewIds": ["skipCreditsButtonTestTag"],
                  "skipRecapLabels": ["Skip Recap"],
                  "nextEpisodeViewIds": ["NextEpisodeButtonTestTag"],
                  "nextEpisodeLabels": ["Next Episode"],
                  "enabled": true,
                  "autoNextEnabled": false
                }
              ]
            }
        """.trimIndent()

        val configs = RemoteConfigParser.parse(json)
        assertEquals(1, configs.size)
        val c = configs.first()
        assertEquals("com.netflix.mediaclient", c.packageName)
        assertEquals(listOf("skipCreditsButtonTestTag"), c.skipIntroViewIds)
        assertEquals(listOf("Next Episode"), c.nextEpisodeLabels)
        assertTrue(c.enabled)
    }

    @Test
    fun `applies defaults for missing optional fields`() {
        val json = """{ "apps": [ { "packageName": "com.x.y" } ] }"""
        val c = RemoteConfigParser.parse(json).single()
        assertEquals("en", c.locale)
        assertTrue(c.skipIntroViewIds.isEmpty())
        assertTrue(c.enabled)          // defaults true
        assertEquals(false, c.autoNextEnabled) // defaults false
    }

    @Test
    fun `skips entries without a package name`() {
        val json = """{ "apps": [ { "locale": "en" }, { "packageName": "com.ok" } ] }"""
        val configs = RemoteConfigParser.parse(json)
        assertEquals(listOf("com.ok"), configs.map { it.packageName })
    }

    @Test
    fun `empty apps array yields empty list`() {
        assertTrue(RemoteConfigParser.parse("""{ "apps": [] }""").isEmpty())
    }

    @Test(expected = org.json.JSONException::class)
    fun `malformed json throws`() {
        RemoteConfigParser.parse("not json at all")
    }
}
