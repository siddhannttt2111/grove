package com.friendsvibe.autopilot.rank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeRankingTest {
    @Test
    fun sameArtistOutranksAnUnrelatedSong() {
        val seed = song(1, "Night Drive", "Ada", album = "Coast", genre = "Electronic", folder = "Music/Night")
        val same = song(2, "Harbor", "Ada", album = "Other", genre = "Jazz", folder = "Music/Day")
        val other = song(3, "Harbor Lights", "Bea", album = "Other", genre = "Jazz", folder = "Music/Day")
        val ranked = TrackScorer.rank(seed, listOf(seed, same, other), Vibe.Neutral, emptySet(), emptyMap(), emptyMap())
        assertEquals(listOf(2L, 3L), ranked.map { it.id })
    }

    @Test
    fun bannedArtistAndSeedAreLeftOut() {
        val seed = song(1, "Seed", "Ada")
        val banned = song(2, "Nope", "Bea")
        val kept = song(3, "Yes", "Ada")
        val ranked = TrackScorer.rank(
            seed,
            listOf(seed, banned, kept),
            Vibe.Neutral,
            bannedArtists = setOf("Bea"),
            skipCounts = emptyMap(),
            artistSkipCounts = emptyMap(),
        )
        assertEquals(listOf(3L), ranked.map { it.id })
    }

    @Test
    fun skipsPushASongDown() {
        val seed = song(1, "Seed", "Ada", bpm = 120)
        val skipped = song(2, "A", "Ada", bpm = 120)
        val fresh = song(3, "B", "Ada", bpm = 120)
        val ranked = TrackScorer.rank(
            seed,
            listOf(skipped, fresh),
            Vibe.Neutral,
            emptySet(),
            skipCounts = mapOf(2L to 1),
            artistSkipCounts = emptyMap(),
        )
        assertEquals(3L, ranked.first().id)
    }

    @Test
    fun calmerPrefersASlowerTempo() {
        val seed = song(1, "Seed", "Ada", bpm = 120)
        val slower = song(2, "Soft", "Bea", bpm = 100)
        val faster = song(3, "Hard", "Cee", bpm = 140)
        val calm = TrackScorer.score(seed, slower, Vibe(-1), emptyMap(), emptyMap())
        val hard = TrackScorer.score(seed, faster, Vibe(-1), emptyMap(), emptyMap())
        assertTrue(calm > hard)
        val calmHarder = TrackScorer.score(seed, slower, Vibe(1), emptyMap(), emptyMap())
        val hardHarder = TrackScorer.score(seed, faster, Vibe(1), emptyMap(), emptyMap())
        assertTrue(hardHarder > calmHarder)
    }

    @Test
    fun sameFolderBeatsAnEmptyMatch() {
        val seed = song(1, "Seed", "", folder = "Music/Party")
        val close = song(2, "Next", "", folder = "Music/Party")
        val far = song(3, "Other", "", folder = "Music/Sleep")
        val ranked = TrackScorer.rank(seed, listOf(far, close), Vibe.Neutral, emptySet(), emptyMap(), emptyMap())
        assertEquals(2L, ranked.first().id)
    }

    @Test
    fun spotifyCalmerPrefersAcousticOverRemix() {
        val seed = spotify("s", "Blue", listOf("Ada"))
        val acoustic = spotify("a", "Blue Acoustic", listOf("Ada"))
        val remix = spotify("r", "Blue Remix", listOf("Ada"))
        val ranked = SpotifyRanker.rank(seed, listOf(remix, acoustic), Vibe(-1), emptySet(), emptySet(), emptyMap())
        assertEquals("a", ranked.first().id)
        val harder = SpotifyRanker.rank(seed, listOf(acoustic, remix), Vibe(1), emptySet(), emptySet(), emptyMap())
        assertEquals("r", harder.first().id)
    }

    @Test
    fun spotifyBanSkipAndSavedLibrary() {
        val seed = spotify("s", "Blue", listOf("Ada"))
        val banned = spotify("b", "Nope", listOf("Bea"))
        val saved = spotify("saved", "Lane", listOf("Ada"))
        val plain = spotify("plain", "Road", listOf("Ada"))
        val skipped = spotify("skip", "Hill", listOf("Ada"))
        val ranked = SpotifyRanker.rank(
            seed,
            listOf(banned, saved, plain, skipped),
            Vibe.Neutral,
            bannedArtists = setOf("bea"),
            savedIds = setOf("saved"),
            skipCounts = mapOf("skip" to 1),
        )
        assertFalse(ranked.any { it.id == "b" || it.id == "s" })
        assertEquals("saved", ranked.first().id)
        assertEquals("skip", ranked.last().id)
    }

    @Test
    fun calmerSearchAddsSofterQueries() {
        val seed = spotify("s", "Blue", listOf("Ada"))
        assertTrue(SpotifyRanker.searchQueries(seed, Vibe(-1)).any { it.contains("acoustic") })
        assertFalse(SpotifyRanker.searchQueries(seed, Vibe.Neutral).any { it.contains("remix") })
        assertTrue(SpotifyRanker.searchQueries(seed, Vibe(1)).any { it.contains("club") })
    }

    @Test
    fun readsId3AndMp4Tempo() {
        assertEquals(96, TagBpm.read(id3Tempo("96")))
        assertEquals(128, TagBpm.read(mp4Tempo(128)))
        assertNull(TagBpm.read(ByteArray(16)))
    }

    @Test
    fun estimatesClickTrackTempo() {
        val sampleRate = 8000
        val bpm = 120
        val period = sampleRate * 60 / bpm
        val pcm = ShortArray(sampleRate * 8)
        var cursor = 0
        while (cursor < pcm.size) {
            var burst = 0
            while (burst < 30 && cursor + burst < pcm.size) {
                pcm[cursor + burst] = 16000
                burst += 1
            }
            cursor += period
        }
        val estimated = BpmEstimate.estimate(pcm, sampleRate)
        assertEquals(120, estimated)
        assertNull(BpmEstimate.estimate(ShortArray(sampleRate * 8), sampleRate))
    }

    @Test
    fun crossfadeCapsAndSkipsTinyTracks() {
        assertEquals(0L, CrossfadeTiming.crossfadeMs(1_000))
        assertEquals(2_500L, CrossfadeTiming.crossfadeMs(10_000))
        assertEquals(4_000L, CrossfadeTiming.crossfadeMs(60_000))
    }

    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String = "",
        genre: String = "",
        folder: String = "",
        bpm: Int? = null,
    ) = LibrarySong(id, title, artist, album, genre, folder, bpm)

    private fun spotify(id: String, title: String, artists: List<String>) = SpotifyTrack(
        id = id,
        uri = "spotify:track:$id",
        title = title,
        artists = artists,
        album = "",
        durationMs = 180_000,
    )

    private fun id3Tempo(bpm: String): ByteArray {
        val body = byteArrayOf(0) + bpm.toByteArray(Charsets.ISO_8859_1)
        val frame = ByteArray(10 + body.size)
        "TBPM".toByteArray(Charsets.ISO_8859_1).copyInto(frame)
        val size = body.size
        frame[4] = ((size shr 24) and 0xFF).toByte()
        frame[5] = ((size shr 16) and 0xFF).toByte()
        frame[6] = ((size shr 8) and 0xFF).toByte()
        frame[7] = (size and 0xFF).toByte()
        body.copyInto(frame, 10)
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 3
        val tagSize = frame.size
        header[6] = ((tagSize shr 21) and 0x7F).toByte()
        header[7] = ((tagSize shr 14) and 0x7F).toByte()
        header[8] = ((tagSize shr 7) and 0x7F).toByte()
        header[9] = (tagSize and 0x7F).toByte()
        return header + frame
    }

    private fun mp4Tempo(bpm: Int): ByteArray {
        val atom = ByteArray(16)
        atom[3] = 16
        "tmpo".toByteArray(Charsets.ISO_8859_1).copyInto(atom, 4)
        atom[12] = ((bpm shr 8) and 0xFF).toByte()
        atom[13] = (bpm and 0xFF).toByte()
        return atom
    }
}
