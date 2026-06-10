package com.skipperkit.teach

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TeachModeRepositoryTest {

    private val pkg = "com.netflix.mediaclient"
    private val other = "com.amazon.avod.thirdpartyclient"

    @Before
    fun setUp() {
        TeachModeRepository.disarm()
    }

    @After
    fun tearDown() {
        TeachModeRepository.disarm()
        // Reset clock to wall clock after each test.
        TeachModeRepository.clock = { System.currentTimeMillis() }
    }

    // --- arm / disarm ---

    @Test
    fun `arm sets the armed package`() {
        TeachModeRepository.arm(pkg)
        assertTrue(TeachModeRepository.isArmedFor(pkg))
    }

    @Test
    fun `arm clears previous candidates`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = null))
        assertEquals(1, TeachModeRepository.candidates.value.size)

        TeachModeRepository.arm(pkg)
        assertEquals(0, TeachModeRepository.candidates.value.size)
    }

    @Test
    fun `arm for different package clears previous candidates`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = null))

        TeachModeRepository.arm(other)
        assertEquals(0, TeachModeRepository.candidates.value.size)
        assertFalse(TeachModeRepository.isArmedFor(pkg))
        assertTrue(TeachModeRepository.isArmedFor(other))
    }

    @Test
    fun `disarm clears armed package and candidates`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = null))

        TeachModeRepository.disarm()

        assertNull(TeachModeRepository.armedPackage.value)
        assertEquals(0, TeachModeRepository.candidates.value.size)
        assertFalse(TeachModeRepository.isArmedFor(pkg))
    }

    // --- offer: non-armed package ---

    @Test
    fun `offer for non-armed package is ignored`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(other, TeachCandidate(viewId = "com.x:id/skip", text = null))
        assertEquals(0, TeachModeRepository.candidates.value.size)
    }

    @Test
    fun `offer when nothing is armed is ignored`() {
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = null))
        assertEquals(0, TeachModeRepository.candidates.value.size)
    }

    // --- offer: blank candidates ---

    @Test
    fun `offer with null viewId and null text is ignored`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = null, text = null))
        assertEquals(0, TeachModeRepository.candidates.value.size)
    }

    @Test
    fun `offer with blank viewId and blank text is ignored`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "  ", text = "  "))
        assertEquals(0, TeachModeRepository.candidates.value.size)
    }

    @Test
    fun `offer with null viewId but valid text is accepted`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = null, text = "SKIP INTRO"))
        assertEquals(1, TeachModeRepository.candidates.value.size)
    }

    @Test
    fun `offer with valid viewId but null text is accepted`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = null))
        assertEquals(1, TeachModeRepository.candidates.value.size)
    }

    // --- dedupe ---

    @Test
    fun `offer deduplicates by key`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = null))
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = "any"))
        assertEquals(1, TeachModeRepository.candidates.value.size)
    }

    @Test
    fun `offer deduplicates text-only candidates by lowercased label key`() {
        TeachModeRepository.arm(pkg)
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = null, text = "SKIP INTRO"))
        TeachModeRepository.offer(pkg, TeachCandidate(viewId = null, text = "SKIP INTRO"))
        assertEquals(1, TeachModeRepository.candidates.value.size)
    }

    // --- cap at 100 ---

    @Test
    fun `offer caps at MAX_CANDIDATES and silently drops beyond`() {
        TeachModeRepository.arm(pkg)
        for (i in 1..110) {
            TeachModeRepository.offer(pkg, TeachCandidate(viewId = "id/$i", text = null))
        }
        assertEquals(100, TeachModeRepository.candidates.value.size)
    }

    // --- auto-disarm after 3 minutes ---

    @Test
    fun `offer auto-disarms and is ignored after 3 minutes`() {
        var fakeTime = 0L
        TeachModeRepository.clock = { fakeTime }

        TeachModeRepository.arm(pkg)
        fakeTime = TeachModeRepository.AUTO_DISARM_MS + 1

        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = null))

        assertFalse(TeachModeRepository.isArmedFor(pkg))
        assertNull(TeachModeRepository.armedPackage.value)
        assertEquals(0, TeachModeRepository.candidates.value.size)
    }

    @Test
    fun `isArmedFor auto-disarms and returns false after 3 minutes`() {
        var fakeTime = 0L
        TeachModeRepository.clock = { fakeTime }

        TeachModeRepository.arm(pkg)
        fakeTime = TeachModeRepository.AUTO_DISARM_MS + 1

        assertFalse(TeachModeRepository.isArmedFor(pkg))
        assertNull(TeachModeRepository.armedPackage.value)
    }

    @Test
    fun `offer within 3 minutes is accepted`() {
        var fakeTime = 0L
        TeachModeRepository.clock = { fakeTime }

        TeachModeRepository.arm(pkg)
        fakeTime = TeachModeRepository.AUTO_DISARM_MS - 1

        TeachModeRepository.offer(pkg, TeachCandidate(viewId = "com.x:id/skip", text = null))
        assertEquals(1, TeachModeRepository.candidates.value.size)
    }

    // --- TeachCandidate.key ---

    @Test
    fun `key prefers viewId over text label`() {
        val c = TeachCandidate(viewId = "com.x:id/skip", text = "SKIP INTRO")
        assertEquals("com.x:id/skip", c.key)
    }

    @Test
    fun `key falls back to lowercased label when viewId is null`() {
        val c = TeachCandidate(viewId = null, text = "SKIP INTRO")
        assertEquals("label:skip intro", c.key)
    }
}
