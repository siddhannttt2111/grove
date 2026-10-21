package com.grove.autopilot.vibe

import com.grove.autopilot.model.Energy
import com.grove.autopilot.model.Source
import com.grove.autopilot.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeScorerTest {
    @Test
    fun sameArtistComesFirst() {
        val seed = song("1", "Asha", "Seed")
        val other = song("2", "Other", "Elsewhere")
        val same = song("3", "Asha", "Next")
        val plan = VibeScorer.plan(seed, listOf(seed, other, same), Energy.Steady, emptySet(), emptySet(), 5)
        assertEquals("3", plan.first().track.id)
    }

    @Test
    fun bannedArtistIsDropped() {
        val seed = song("1", "Asha", "Seed")
        val banned = song("2", "Asha", "Other")
        val plan = VibeScorer.plan(seed, listOf(seed, banned), Energy.Steady, setOf("asha"), emptySet(), 5)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun calmPrefersAcousticOverClub() {
        val seed = song("1", "Asha", "Seed", folder = "Night")
        val club = song("2", "Mina", "Club remix", folder = "Night")
        val soft = song("3", "Mina", "Acoustic version", folder = "Night")
        val plan = VibeScorer.plan(seed, listOf(seed, club, soft), Energy.Calm, emptySet(), emptySet(), 5)
        assertEquals("3", plan.first().track.id)
    }

    @Test
    fun liftPrefersRemix() {
        val seed = song("1", "Asha", "Seed", folder = "Night")
        val soft = song("2", "Mina", "Acoustic version", folder = "Night")
        val club = song("3", "Mina", "Club remix", folder = "Night")
        val plan = VibeScorer.plan(seed, listOf(seed, soft, club), Energy.Lift, emptySet(), emptySet(), 5)
        assertEquals("3", plan.first().track.id)
    }

    @Test
    fun seedIsNotQueuedAgain() {
        val seed = song("1", "Asha", "Seed")
        val plan = VibeScorer.plan(seed, listOf(seed), Energy.Lift, emptySet(), emptySet(), 5)
        assertFalse(plan.any { it.track.id == "1" })
    }

    @Test
    fun excludeIdsAreSkipped() {
        val seed = song("1", "Asha", "Seed")
        val same = song("2", "Asha", "Next")
        val plan = VibeScorer.plan(seed, listOf(seed, same), Energy.Steady, emptySet(), setOf("2"), 5)
        assertTrue(plan.none { it.track.id == "2" })
    }

    private fun song(
        id: String,
        artist: String,
        title: String,
        folder: String = "",
        album: String = "",
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        folder = folder,
        genre = "",
        durationMs = 180_000,
        uri = "file://$id",
        artUri = null,
        source = Source.Phone,
    )
}
