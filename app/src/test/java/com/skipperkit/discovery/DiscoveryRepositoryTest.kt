package com.skipperkit.discovery

import com.skipperkit.config.ConfigRepository
import com.skipperkit.config.SkipTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoveryRepositoryTest {

    private val netflix = "com.netflix.mediaclient"

    @Before
    fun reset() {
        DiscoveryRepository.pending.value.forEach { DiscoveryRepository.dismiss(it.key) }
        DiscoveryRepository.restore(emptyList(), emptySet())
        DiscoveryRepository.onApprovedChanged = null
        DiscoveryRepository.onDismissedChanged = null
        ConfigRepository.setDiscovered(emptyList())
    }

    private fun introByViewId(id: String) =
        DiscoveredEntry(netflix, SkipTarget.SKIP_INTRO, viewId = id, label = null)

    @Test
    fun `propose adds a pending suggestion`() {
        DiscoveryRepository.propose(introByViewId("a/b"))
        assertEquals(1, DiscoveryRepository.pending.value.size)
    }

    @Test
    fun `propose is idempotent on the same key`() {
        DiscoveryRepository.propose(introByViewId("a/b"))
        DiscoveryRepository.propose(introByViewId("a/b"))
        assertEquals(1, DiscoveryRepository.pending.value.size)
    }

    @Test
    fun `next-episode is never proposed`() {
        DiscoveryRepository.propose(
            DiscoveredEntry(netflix, SkipTarget.NEXT_EPISODE, viewId = "n", label = null),
        )
        assertTrue(DiscoveryRepository.pending.value.isEmpty())
    }

    @Test
    fun `approve promotes into the config and clears pending`() {
        var persisted: List<DiscoveredEntry>? = null
        DiscoveryRepository.onApprovedChanged = { persisted = it }
        val entry = introByViewId("com.netflix.mediaclient:id/newSkip")
        DiscoveryRepository.propose(entry)

        DiscoveryRepository.approve(entry.key)

        assertTrue(DiscoveryRepository.pending.value.isEmpty())
        assertTrue(
            ConfigRepository.forPackage(netflix)!!.skipIntroViewIds
                .contains("com.netflix.mediaclient:id/newSkip"),
        )
        assertEquals(1, persisted?.size)
    }

    @Test
    fun `dismiss removes pending and suppresses re-proposal`() {
        val entry = introByViewId("a/b")
        DiscoveryRepository.propose(entry)
        DiscoveryRepository.dismiss(entry.key)
        assertTrue(DiscoveryRepository.pending.value.isEmpty())

        DiscoveryRepository.propose(entry) // same key again
        assertTrue(DiscoveryRepository.pending.value.isEmpty())
    }

    @Test
    fun `restore re-applies approved entries to the config`() {
        DiscoveryRepository.restore(
            approved = listOf(
                DiscoveredEntry(netflix, SkipTarget.SKIP_RECAP, viewId = null, label = "Skip the recap"),
            ),
            dismissed = emptySet(),
        )
        assertTrue(ConfigRepository.forPackage(netflix)!!.skipRecapLabels.contains("Skip the recap"))
    }

    @Test
    fun `approved entry is not re-proposed`() {
        val entry = introByViewId("a/b")
        DiscoveryRepository.propose(entry)
        DiscoveryRepository.approve(entry.key)
        DiscoveryRepository.propose(entry)
        assertFalse(DiscoveryRepository.pending.value.any { it.key == entry.key })
    }
}
