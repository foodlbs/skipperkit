package com.skipperkit.matching

import com.skipperkit.config.AppConfig
import com.skipperkit.config.SkipTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipEngineTest {

    private val pkg = "com.netflix.mediaclient"

    private fun config(
        enabled: Boolean = true,
        autoNext: Boolean = false,
    ) = AppConfig(
        packageName = pkg,
        locale = "en",
        skipIntroViewIds = listOf("skipCreditsButtonTestTag"),
        skipIntroLabels = listOf("SKIP INTRO"),
        skipRecapViewIds = listOf("skipCreditsButtonTestTag"),
        skipRecapLabels = listOf("SKIP RECAP"),
        nextEpisodeViewIds = listOf("NextEpisodeButtonTestTag"),
        nextEpisodeLabels = listOf("Next Episode"),
        enabled = enabled,
        autoNextEnabled = autoNext,
    )

    private fun skipButton(): FakeNode {
        val text = FakeNode(text = "SKIP INTRO")
        val button = FakeNode(viewId = "skipCreditsButtonTestTag", isClickable = true, children = listOf(text))
        return FakeNode(children = listOf(FakeNode(text = "Audio & Subtitles"), button))
    }

    private fun fixedClock(timeRef: LongArray) = { timeRef[0] }

    @Test
    fun `clicks the skip button`() {
        val now = longArrayOf(10_000)
        val engine = SkipEngine(clock = fixedClock(now))
        val result = engine.onTree(pkg, skipButton(), config())
        assertEquals(SkipEngine.Result.Clicked(SkipTarget.SKIP_INTRO), result)
    }

    @Test
    fun `second pass within debounce window is suppressed`() {
        val now = longArrayOf(10_000)
        val engine = SkipEngine(debounceMs = 1500, clock = fixedClock(now))

        assertTrue(engine.onTree(pkg, skipButton(), config()) is SkipEngine.Result.Clicked)

        now[0] = 11_000 // +1000ms, still inside 1500ms window
        assertEquals(SkipEngine.Result.Debounced, engine.onTree(pkg, skipButton(), config()))
    }

    @Test
    fun `click allowed again after debounce window passes`() {
        val now = longArrayOf(10_000)
        val engine = SkipEngine(debounceMs = 1500, clock = fixedClock(now))

        assertTrue(engine.onTree(pkg, skipButton(), config()) is SkipEngine.Result.Clicked)

        now[0] = 12_000 // +2000ms, past the window
        assertTrue(engine.onTree(pkg, skipButton(), config()) is SkipEngine.Result.Clicked)
    }

    @Test
    fun `debounce is per package`() {
        val now = longArrayOf(10_000)
        val engine = SkipEngine(debounceMs = 1500, clock = fixedClock(now))

        engine.onTree(pkg, skipButton(), config())
        // A different package is not blocked by Netflix's recent action.
        val prime = "com.amazon.avod.thirdpartyclient"
        val result = engine.onTree(prime, skipButton(), config().copy(packageName = prime))
        assertTrue(result is SkipEngine.Result.Clicked)
    }

    @Test
    fun `disabled config is skipped`() {
        val engine = SkipEngine(clock = { 0L })
        assertEquals(SkipEngine.Result.Skipped, engine.onTree(pkg, skipButton(), config(enabled = false)))
    }

    @Test
    fun `null root is skipped`() {
        val engine = SkipEngine(clock = { 0L })
        assertEquals(SkipEngine.Result.Skipped, engine.onTree(pkg, null, config()))
    }

    @Test
    fun `no match returns NoMatch and does not arm debounce`() {
        val now = longArrayOf(10_000)
        val engine = SkipEngine(clock = fixedClock(now))
        val emptyTree = FakeNode(children = listOf(FakeNode(text = "Audio & Subtitles")))
        assertEquals(SkipEngine.Result.NoMatch, engine.onTree(pkg, emptyTree, config()))

        // Because no action was recorded, a subsequent real match clicks immediately.
        assertTrue(engine.onTree(pkg, skipButton(), config()) is SkipEngine.Result.Clicked)
    }

    @Test
    fun `match without clickable ancestor needs gesture at node center`() {
        // No clickable ancestor, but the matched node has on-screen bounds.
        val text = FakeNode(text = "SKIP INTRO", center = Point(120, 240))
        val root = FakeNode(children = listOf(text))
        val engine = SkipEngine(clock = { 5_000L })
        val result = engine.onTree(pkg, root, config())
        assertTrue(result is SkipEngine.Result.NeedsGesture)
        result as SkipEngine.Result.NeedsGesture
        assertEquals(SkipTarget.SKIP_INTRO, result.target)
        assertEquals(Point(120, 240), result.point)
    }

    @Test
    fun `gesture handoff arms the debounce`() {
        val now = longArrayOf(5_000)
        val engine = SkipEngine(debounceMs = 1500, clock = fixedClock(now))
        val gestureTree = FakeNode(children = listOf(FakeNode(text = "SKIP INTRO", center = Point(1, 2))))

        assertTrue(engine.onTree(pkg, gestureTree, config()) is SkipEngine.Result.NeedsGesture)
        now[0] = 5_800 // within window
        assertEquals(SkipEngine.Result.Debounced, engine.onTree(pkg, gestureTree, config()))
    }

    @Test
    fun `falls back to gesture when clickable ancestor rejects the click`() {
        // Clickable parent exists but performAction returns false -> tap its center.
        val text = FakeNode(text = "SKIP INTRO")
        val button = FakeNode(
            viewId = "skipCreditsButtonTestTag",
            isClickable = true,
            children = listOf(text),
            clickResult = false,
            center = Point(900, 100),
        )
        val root = FakeNode(children = listOf(button))
        val engine = SkipEngine(clock = { 5_000L })
        val result = engine.onTree(pkg, root, config())
        assertTrue(result is SkipEngine.Result.NeedsGesture)
        assertEquals(Point(900, 100), (result as SkipEngine.Result.NeedsGesture).point)
    }

    @Test
    fun `unactionable match with no bounds returns NoMatch`() {
        val text = FakeNode(text = "SKIP INTRO") // no clickable ancestor, no bounds
        val root = FakeNode(children = listOf(text))
        val engine = SkipEngine(clock = { 5_000L })
        assertEquals(SkipEngine.Result.NoMatch, engine.onTree(pkg, root, config()))
    }

    @Test
    fun `auto-next ignored when disabled`() {
        // Only a next-episode end-card present; auto-next off -> no match.
        val card = FakeNode(viewId = "NextEpisodeButtonTestTag", isClickable = true,
            children = listOf(FakeNode(text = "Next Episode")))
        val root = FakeNode(children = listOf(card))
        val engine = SkipEngine(clock = { 0L })
        assertEquals(SkipEngine.Result.NoMatch, engine.onTree(pkg, root, config(autoNext = false)))
    }

    @Test
    fun `auto-next matches the dynamic Prime next-up card by prefix`() {
        // Prime: clickable card container with no id/label, wrapping dynamic text.
        val text = FakeNode(text = "Next up: Matka King")
        val card = FakeNode(isClickable = true, children = listOf(text))
        val root = FakeNode(children = listOf(card))
        val primeConfig = config(autoNext = true).copy(
            nextEpisodeViewIds = emptyList(),
            nextEpisodeLabels = emptyList(),
            nextEpisodeLabelPrefixes = listOf("Next up:"),
        )
        val engine = SkipEngine(clock = { 5_000L })
        assertEquals(
            SkipEngine.Result.Clicked(SkipTarget.NEXT_EPISODE),
            engine.onTree(pkg, root, primeConfig),
        )
        assertTrue(card.clicked)
    }

    @Test
    fun `auto-next clicks end-card when enabled`() {
        val card = FakeNode(viewId = "NextEpisodeButtonTestTag", isClickable = true,
            children = listOf(FakeNode(text = "Next Episode")))
        val root = FakeNode(children = listOf(card))
        val engine = SkipEngine(clock = { 0L })
        assertEquals(
            SkipEngine.Result.Clicked(SkipTarget.NEXT_EPISODE),
            engine.onTree(pkg, root, config(autoNext = true)),
        )
    }
}
