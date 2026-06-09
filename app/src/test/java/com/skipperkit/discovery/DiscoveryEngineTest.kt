package com.skipperkit.discovery

import com.skipperkit.config.SkipTarget
import com.skipperkit.matching.FakeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryEngineTest {

    @Test
    fun `discovers a skip intro control`() {
        val root = FakeNode(children = listOf(FakeNode(text = "Skip Intro", isClickable = true)))
        val candidates = DiscoveryEngine.discover(root)
        assertEquals(1, candidates.size)
        assertEquals(SkipTarget.SKIP_INTRO, candidates.first().target)
        assertTrue(candidates.first().hasClickableTarget)
    }

    @Test
    fun `discovers a dynamic next-up control`() {
        val text = FakeNode(text = "Next up: Some Show")
        val card = FakeNode(isClickable = true, children = listOf(text))
        val candidates = DiscoveryEngine.discover(FakeNode(children = listOf(card)))
        assertEquals(SkipTarget.NEXT_EPISODE, candidates.single().target)
    }

    @Test
    fun `classifies skip recap by content description`() {
        val node = FakeNode(contentDescription = "Skip recap", isClickable = true)
        assertEquals(SkipTarget.SKIP_RECAP, DiscoveryEngine.discover(node).single().target)
    }

    @Test
    fun `rejects ad and purchase actions`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(text = "Skip Ad", isClickable = true),
                FakeNode(text = "Go ad free", isClickable = true),
                FakeNode(text = "Subscribe to Premium", isClickable = true),
            ),
        )
        assertTrue(DiscoveryEngine.discover(root).isEmpty())
    }

    @Test
    fun `does not reject a dynamic title that merely contains 'free'`() {
        // "Free Guy" is a movie title, not an "ad free" action.
        val text = FakeNode(text = "Next up: Free Guy")
        val card = FakeNode(isClickable = true, children = listOf(text))
        assertEquals(SkipTarget.NEXT_EPISODE, DiscoveryEngine.discover(FakeNode(children = listOf(card))).single().target)
    }

    @Test
    fun `flags a match with no clickable ancestor`() {
        val text = FakeNode(text = "Skip Intro") // not clickable, no clickable parent
        val candidate = DiscoveryEngine.discover(FakeNode(children = listOf(text))).single()
        assertFalse(candidate.hasClickableTarget)
    }

    @Test
    fun `control-bar next-episode is NOT discovered`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(viewId = "playPauseButtonTestTag", isClickable = true),
                FakeNode(viewId = "nextEpisodeButtonTestTag", isClickable = true,
                    children = listOf(FakeNode(text = "Next Ep."))),
            ),
        )
        assertTrue(DiscoveryEngine.discover(root).none { it.target == SkipTarget.NEXT_EPISODE })
    }

    @Test
    fun `end-card next-episode IS discovered`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(text = "UP NEXT", viewId = "com.disney.disneyplus:id/upNextLiteTitle"),
                FakeNode(text = "NEXT EPISODE", viewId = "com.disney.disneyplus:id/upNextLiteButton", isClickable = true),
            ),
        )
        assertTrue(
            DiscoveryEngine.discover(root)
                .any { it.target == SkipTarget.NEXT_EPISODE && it.hasClickableTarget },
        )
    }

    @Test
    fun `ignores unrelated controls`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(text = "Audio & Subtitles", isClickable = true),
                FakeNode(text = "Settings", isClickable = true),
            ),
        )
        assertTrue(DiscoveryEngine.discover(root).isEmpty())
    }
}
