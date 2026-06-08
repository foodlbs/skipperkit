package com.skipperkit.matching

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeMatcherTest {

    private val viewIds = listOf("skipCreditsButtonTestTag")
    private val labels = listOf("Skip Intro", "SKIP INTRO")

    @Test
    fun `matches on view id`() {
        val node = FakeNode(viewId = "skipCreditsButtonTestTag")
        assertTrue(NodeMatcher.matches(node, viewIds, labels))
    }

    @Test
    fun `matches text case-insensitively`() {
        val node = FakeNode(text = "skip intro")
        assertTrue(NodeMatcher.matches(node, viewIds, labels))
    }

    @Test
    fun `matches text ignoring surrounding whitespace`() {
        val node = FakeNode(text = "  SKIP INTRO  ")
        assertTrue(NodeMatcher.matches(node, viewIds, labels))
    }

    @Test
    fun `matches on content description`() {
        val node = FakeNode(contentDescription = "Skip Intro")
        assertTrue(NodeMatcher.matches(node, viewIds, labels))
    }

    @Test
    fun `does not match unrelated node`() {
        val node = FakeNode(text = "Audio & Subtitles", viewId = "audioAndSubtitlesButtonTestTag")
        assertFalse(NodeMatcher.matches(node, viewIds, labels))
    }

    @Test
    fun `empty text does not match empty label list`() {
        val node = FakeNode(text = "")
        assertFalse(NodeMatcher.matches(node, emptyList(), emptyList()))
    }

    @Test
    fun `matches dynamic text by prefix`() {
        val node = FakeNode(text = "Next up: Matka King")
        assertTrue(NodeMatcher.matches(node, emptyList(), emptyList(), labelPrefixes = listOf("Next up:")))
    }

    @Test
    fun `prefix match is case-insensitive`() {
        val node = FakeNode(text = "NEXT UP: Jack Ryan")
        assertTrue(NodeMatcher.matches(node, emptyList(), emptyList(), labelPrefixes = listOf("Next up:")))
    }

    @Test
    fun `prefix does not match when text only contains it mid-string`() {
        val node = FakeNode(text = "See what's Next up: later")
        assertFalse(NodeMatcher.matches(node, emptyList(), emptyList(), labelPrefixes = listOf("Next up:")))
    }
}
