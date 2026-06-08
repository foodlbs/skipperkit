package com.skipperkit.matching

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSearchTest {

    private val skipViewIds = listOf("skipCreditsButtonTestTag")
    private val skipLabels = listOf("SKIP INTRO")

    /** Mirrors the real Netflix shape: clickable parent (testTag) wraps a non-clickable text child. */
    private fun netflixSkipButton(): Pair<FakeNode, FakeNode> {
        val textChild = FakeNode(text = "SKIP INTRO")
        val clickableParent = FakeNode(
            viewId = "skipCreditsButtonTestTag",
            isClickable = true,
            children = listOf(textChild),
        )
        return clickableParent to textChild
    }

    @Test
    fun `findFirst locates node by view id`() {
        val (parent, _) = netflixSkipButton()
        val root = FakeNode(children = listOf(FakeNode(), parent))
        val hit = TreeSearch.findFirst(root, skipViewIds, skipLabels)
        assertSame(parent, hit)
    }

    @Test
    fun `findFirst locates text node when no view id configured`() {
        val (parent, text) = netflixSkipButton()
        val root = FakeNode(children = listOf(parent))
        val hit = TreeSearch.findFirst(root, emptyList(), skipLabels)
        assertSame(text, hit)
    }

    @Test
    fun `findFirst returns null when nothing matches`() {
        val root = FakeNode(children = listOf(FakeNode(text = "Audio & Subtitles")))
        assertNull(TreeSearch.findFirst(root, skipViewIds, skipLabels))
    }

    @Test
    fun `clickable ancestor returns self when node is clickable`() {
        val (parent, _) = netflixSkipButton()
        assertSame(parent, TreeSearch.firstClickableSelfOrAncestor(parent))
    }

    @Test
    fun `clickable ancestor walks up from non-clickable text child`() {
        val (parent, text) = netflixSkipButton()
        assertSame(parent, TreeSearch.firstClickableSelfOrAncestor(text))
    }

    @Test
    fun `clickable ancestor returns null when no clickable in chain`() {
        val text = FakeNode(text = "SKIP INTRO")
        val nonClickableParent = FakeNode(children = listOf(text))
        assertNull(TreeSearch.firstClickableSelfOrAncestor(text))
        assertTrue(!nonClickableParent.isClickable)
    }
}
