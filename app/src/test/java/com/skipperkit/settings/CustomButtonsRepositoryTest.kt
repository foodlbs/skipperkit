package com.skipperkit.settings

import com.skipperkit.config.ConfigRepository
import com.skipperkit.config.CustomButton
import com.skipperkit.data.customButtonsToJson
import com.skipperkit.data.parseCustomButtons
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomButtonsRepositoryTest {

    private val pkg = "com.example.app"
    private val button = CustomButton(
        key = "com.example:id/rateLater",
        name = "Dismiss nag",
        viewIds = listOf("com.example:id/rateLater"),
        labels = listOf("Maybe later"),
        enabled = true,
    )

    @Before @After fun reset() {
        CustomButtonsRepository.onChanged = null
        CustomButtonsRepository.restore(emptyMap())
        TaughtAppsRepository.onChanged = null
        TaughtAppsRepository.restore(emptyList())
    }

    // ── propagation into ConfigRepository ────────────────────────────────────

    @Test fun `add propagates into ConfigRepository customButtons`() {
        // seed a stub config for the package
        ConfigRepository.setTaughtApps(listOf(pkg))
        CustomButtonsRepository.add(pkg, button)
        val config = ConfigRepository.forPackage(pkg)!!
        assertEquals(1, config.customButtons.size)
        assertEquals(button.key, config.customButtons[0].key)
    }

    @Test fun `add with duplicate key is a no-op`() {
        ConfigRepository.setTaughtApps(listOf(pkg))
        var fireCount = 0
        CustomButtonsRepository.onChanged = { fireCount++ }
        CustomButtonsRepository.add(pkg, button)
        CustomButtonsRepository.add(pkg, button.copy(name = "Different name"))
        assertEquals(1, fireCount)
        assertEquals(1, ConfigRepository.forPackage(pkg)!!.customButtons.size)
    }

    @Test fun `setEnabled(false) propagates to config`() {
        ConfigRepository.setTaughtApps(listOf(pkg))
        CustomButtonsRepository.add(pkg, button)
        CustomButtonsRepository.setEnabled(pkg, button.key, false)
        val config = ConfigRepository.forPackage(pkg)!!
        val b = config.customButtons.single { it.key == button.key }
        assertEquals(false, b.enabled)
    }

    @Test fun `remove drops the button from config`() {
        ConfigRepository.setTaughtApps(listOf(pkg))
        CustomButtonsRepository.add(pkg, button)
        CustomButtonsRepository.remove(pkg, button.key)
        val config = ConfigRepository.forPackage(pkg)!!
        assertTrue(config.customButtons.isEmpty())
    }

    @Test fun `remove when absent is a no-op`() {
        CustomButtonsRepository.remove(pkg, "no-such-key")
        // no exception, no change
        assertTrue(CustomButtonsRepository.buttons.value.isEmpty())
    }

    @Test fun `removeForPackage clears all buttons and fires onChanged once`() {
        ConfigRepository.setTaughtApps(listOf(pkg))
        CustomButtonsRepository.add(pkg, button)
        var fireCount = 0
        CustomButtonsRepository.onChanged = { fireCount++ }
        CustomButtonsRepository.removeForPackage(pkg)
        assertEquals(1, fireCount)
        assertTrue(CustomButtonsRepository.forPackage(pkg).isEmpty())
    }

    @Test fun `removeForPackage when package absent does not fire onChanged`() {
        var fireCount = 0
        CustomButtonsRepository.onChanged = { fireCount++ }
        CustomButtonsRepository.removeForPackage("com.not.present")
        assertEquals(0, fireCount)
    }

    @Test fun `restore populates without firing onChanged`() {
        var fired = false
        CustomButtonsRepository.onChanged = { fired = true }
        CustomButtonsRepository.restore(mapOf(pkg to listOf(button)))
        assertEquals(false, fired)
        assertEquals(button, CustomButtonsRepository.forPackage(pkg).single())
    }

    @Test fun `forPackage returns empty list for unknown package`() {
        assertTrue(CustomButtonsRepository.forPackage("com.unknown").isEmpty())
    }

    // ── onChanged wiring ──────────────────────────────────────────────────────

    @Test fun `add fires onChanged with updated map`() {
        var got: Map<String, List<CustomButton>>? = null
        CustomButtonsRepository.onChanged = { got = it }
        CustomButtonsRepository.add(pkg, button)
        assertEquals(1, got?.get(pkg)?.size)
    }

    @Test fun `setEnabled fires onChanged`() {
        CustomButtonsRepository.add(pkg, button)
        var fireCount = 0
        CustomButtonsRepository.onChanged = { fireCount++ }
        CustomButtonsRepository.setEnabled(pkg, button.key, false)
        assertEquals(1, fireCount)
    }

    // ── JSON serialization round-trips (pure, no Android) ────────────────────

    @Test fun `round-trip an empty map`() {
        val json = customButtonsToJson(emptyMap())
        val parsed = parseCustomButtons(json)
        assertTrue(parsed.isEmpty())
    }

    @Test fun `round-trip multiple buttons for multiple packages`() {
        val map = mapOf(
            pkg to listOf(button),
            "com.other.app" to listOf(
                CustomButton(
                    key = "label:skip",
                    name = "Skip",
                    viewIds = emptyList(),
                    labels = listOf("Skip"),
                    enabled = false,
                )
            ),
        )
        val parsed = parseCustomButtons(customButtonsToJson(map))
        assertEquals(1, parsed[pkg]?.size)
        val b = parsed[pkg]!!.single()
        assertEquals(button.key, b.key)
        assertEquals(button.name, b.name)
        assertEquals(button.viewIds, b.viewIds)
        assertEquals(button.labels, b.labels)
        assertEquals(button.enabled, b.enabled)
        val other = parsed["com.other.app"]!!.single()
        assertEquals(false, other.enabled)
    }

    @Test fun `parseCustomButtons ignores entries missing key or name`() {
        // key missing
        val badJson = """{"com.example":[{"name":"X","viewIds":[],"labels":[],"enabled":true}]}"""
        val parsed = parseCustomButtons(badJson)
        assertTrue(parsed["com.example"]?.isEmpty() ?: true)
    }

    @Test fun `parseCustomButtons returns empty map on corrupt JSON`() {
        val result = parseCustomButtons("not valid json {{{{")
        assertTrue(result.isEmpty())
    }

    @Test fun `string values are capped at 256 characters`() {
        val longString = "x".repeat(300)
        val b = button.copy(key = longString, name = longString, labels = listOf(longString), viewIds = listOf(longString))
        val map = mapOf(pkg to listOf(b))
        val parsed = parseCustomButtons(customButtonsToJson(map))
        val result = parsed[pkg]!!.single()
        assertEquals(256, result.key.length)
        assertEquals(256, result.name.length)
        assertEquals(256, result.labels.single().length)
        assertEquals(256, result.viewIds.single().length)
    }
}
