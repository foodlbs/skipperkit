package com.skipperkit.discovery

import com.skipperkit.matching.FakeNode
import org.junit.Assert.assertEquals
import org.junit.Test

class NextEpisodeClassifierTest {

    private val c = HeuristicNextEpisodeClassifier

    private fun verdict(root: FakeNode) = c.classify(root, root)

    // ── Netflix: Capital-N tag = end card; lowercase-n = control bar ──────────

    @Test
    fun `netflix end-card (capital-N tag) is END_CARD`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(viewId = "NextEpisodeButtonTestTag", isClickable = true,
                    children = listOf(FakeNode(text = "Next Episode"))),
                FakeNode(viewId = "WatchCreditsButtonTestTag", isClickable = true),
            ),
        )
        assertEquals(NextEpisodeVerdict.END_CARD, verdict(root))
    }

    @Test
    fun `netflix control-bar (lowercase-n tag) is CONTROL_BAR`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(viewId = "playPauseButtonTestTag", isClickable = true),
                FakeNode(viewId = "nextEpisodeButtonTestTag", isClickable = true,
                    children = listOf(FakeNode(text = "Next Ep."))),
            ),
        )
        assertEquals(NextEpisodeVerdict.CONTROL_BAR, verdict(root))
    }

    // ── Disney+: up_next_* container vs id/nextButton control ─────────────────

    @Test
    fun `disney up-next card is END_CARD`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(text = "UP NEXT", viewId = "com.disney.disneyplus:id/upNextLiteTitle"),
                FakeNode(text = "YOU MAY ALSO LIKE", viewId = "com.disney.disneyplus:id/up_next_header_text"),
                FakeNode(text = "NEXT EPISODE", viewId = "com.disney.disneyplus:id/upNextLiteButton", isClickable = true),
            ),
        )
        assertEquals(NextEpisodeVerdict.END_CARD, verdict(root))
    }

    @Test
    fun `disney control-bar next is CONTROL_BAR`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(contentDescription = "Skip ahead 10 seconds", viewId = "com.disney.disneyplus:id/jumpForwardButton", isClickable = true),
                FakeNode(contentDescription = "PLAY NEXT", viewId = "com.disney.disneyplus:id/nextButton", isClickable = true),
            ),
        )
        assertEquals(NextEpisodeVerdict.CONTROL_BAR, verdict(root))
    }

    // ── Prime: dynamic "Next up:" card ───────────────────────────────────────

    @Test
    fun `prime next-up card is END_CARD`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(isClickable = true, children = listOf(FakeNode(text = "Next up: Matka King"))),
            ),
        )
        assertEquals(NextEpisodeVerdict.END_CARD, verdict(root))
    }

    @Test
    fun `bare tree is UNKNOWN`() {
        val root = FakeNode(children = listOf(FakeNode(text = "Audio & Subtitles", isClickable = true)))
        assertEquals(NextEpisodeVerdict.UNKNOWN, verdict(root))
    }
}
