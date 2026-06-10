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

    // ── customButtons parsing ─────────────────────────────────────────────────

    @Test
    fun `parses customButtons with all fields including enabled=false`() {
        val json = """
            {
              "apps": [
                {
                  "packageName": "com.example.app",
                  "customButtons": [
                    {
                      "key": "a/b",
                      "name": "Dismiss nag",
                      "viewIds": ["a/b"],
                      "labels": ["Maybe later"],
                      "enabled": false
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val c = RemoteConfigParser.parse(json).single()
        assertEquals(1, c.customButtons.size)
        val btn = c.customButtons.first()
        assertEquals("a/b", btn.key)
        assertEquals("Dismiss nag", btn.name)
        assertEquals(listOf("a/b"), btn.viewIds)
        assertEquals(listOf("Maybe later"), btn.labels)
        assertEquals(false, btn.enabled)
    }

    @Test
    fun `customButton enabled defaults to true when field absent`() {
        val json = """
            {
              "apps": [
                {
                  "packageName": "com.example.app",
                  "customButtons": [
                    { "key": "x/y", "name": "Close", "viewIds": ["x/y"], "labels": [] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val btn = RemoteConfigParser.parse(json).single().customButtons.single()
        assertEquals(true, btn.enabled)
    }

    @Test
    fun `customButton entries missing key or name are skipped`() {
        val json = """
            {
              "apps": [
                {
                  "packageName": "com.example.app",
                  "customButtons": [
                    { "name": "No key here", "viewIds": [], "labels": [] },
                    { "key": "k/v", "viewIds": [], "labels": [] },
                    { "key": "good/key", "name": "Valid", "viewIds": ["good/key"], "labels": [] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val c = RemoteConfigParser.parse(json).single()
        assertEquals(1, c.customButtons.size)
        assertEquals("good/key", c.customButtons.first().key)
    }

    @Test
    fun `malformed customButtons array elements are skipped`() {
        val json = """
            {
              "apps": [
                {
                  "packageName": "com.example.app",
                  "customButtons": [
                    "not an object",
                    42,
                    { "key": "ok/key", "name": "Valid", "viewIds": [], "labels": [] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val c = RemoteConfigParser.parse(json).single()
        assertEquals(1, c.customButtons.size)
        assertEquals("ok/key", c.customButtons.first().key)
    }

    @Test
    fun `config without customButtons field parses exactly as before`() {
        val json = """
            {
              "apps": [
                {
                  "packageName": "com.example.app",
                  "skipIntroViewIds": ["intro_btn"],
                  "enabled": true
                }
              ]
            }
        """.trimIndent()
        val c = RemoteConfigParser.parse(json).single()
        assertTrue(c.customButtons.isEmpty())
        assertEquals(listOf("intro_btn"), c.skipIntroViewIds)
    }

    @Test
    fun `customButton strings are trimmed`() {
        val json = """
            {
              "apps": [
                {
                  "packageName": "com.example.app",
                  "customButtons": [
                    {
                      "key": "  a/b  ",
                      "name": "  Dismiss  ",
                      "viewIds": ["  a/b  "],
                      "labels": ["  Maybe later  "]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val btn = RemoteConfigParser.parse(json).single().customButtons.single()
        assertEquals("a/b", btn.key)
        assertEquals("Dismiss", btn.name)
        assertEquals(listOf("a/b"), btn.viewIds)
        assertEquals(listOf("Maybe later"), btn.labels)
    }

    @Test
    fun `customButton name is capped at 50 chars`() {
        val longName = "A".repeat(60)
        val json = """
            {
              "apps": [
                {
                  "packageName": "com.example.app",
                  "customButtons": [
                    { "key": "k", "name": "$longName", "viewIds": [], "labels": [] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val btn = RemoteConfigParser.parse(json).single().customButtons.single()
        assertEquals(50, btn.name.length)
    }

    @Test
    fun `customButton key and viewId and label strings are capped at 256 chars`() {
        val long = "B".repeat(300)
        val json = """
            {
              "apps": [
                {
                  "packageName": "com.example.app",
                  "customButtons": [
                    { "key": "$long", "name": "Valid", "viewIds": ["$long"], "labels": ["$long"] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val btn = RemoteConfigParser.parse(json).single().customButtons.single()
        assertEquals(256, btn.key.length)
        assertEquals(256, btn.viewIds.single().length)
        assertEquals(256, btn.labels.single().length)
    }
}
