package com.friendsvibe.autopilot.rank

data class LibrarySong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val folder: String,
    val bpm: Int?,
)

object TrackScorer {
    private const val NEUTRAL_BPM = 110
    private val weakGenres = setOf("music", "other", "unknown", "podcast", "audiobook")

    fun rank(
        seed: LibrarySong,
        library: List<LibrarySong>,
        vibe: Vibe,
        bannedArtists: Set<String>,
        skipCounts: Map<Long, Int>,
        artistSkipCounts: Map<String, Int>,
        limit: Int = 40,
    ): List<LibrarySong> {
        val banned = bannedArtists.map { Names.key(it) }.filter { it.isNotEmpty() }.toSet()
        return library
            .asSequence()
            .filter { it.id != seed.id }
            .filter { candidate ->
                val artist = Names.key(candidate.artist)
                artist.isEmpty() || artist !in banned
            }
            .sortedWith(
                compareByDescending<LibrarySong> {
                    score(seed, it, vibe, skipCounts, artistSkipCounts)
                }.thenBy { it.title.lowercase() }.thenBy { it.id },
            )
            .take(limit)
            .toList()
    }

    fun score(
        seed: LibrarySong,
        candidate: LibrarySong,
        vibe: Vibe,
        skipCounts: Map<Long, Int>,
        artistSkipCounts: Map<String, Int>,
    ): Int {
        var points = 0
        val seedArtist = Names.key(seed.artist)
        val artist = Names.key(candidate.artist)
        if (seedArtist.isNotEmpty() && seedArtist == artist) points += 42

        val seedAlbum = Names.key(seed.album)
        val album = Names.key(candidate.album)
        if (seedAlbum.isNotEmpty() && seedAlbum == album) points += 22

        val seedGenre = Names.key(seed.genre)
        val genre = Names.key(candidate.genre)
        if (seedGenre.isNotEmpty() && seedGenre == genre && seedGenre !in weakGenres) points += 18

        val seedFolder = folderKey(seed.folder)
        val folder = folderKey(candidate.folder)
        if (seedFolder.isNotEmpty() && seedFolder == folder) points += 10

        val overlap = Names.tokens(seed.title).intersect(Names.tokens(candidate.title)).size
        points += (overlap * 6).coerceAtMost(18)

        val target = (seed.bpm ?: NEUTRAL_BPM) + vibe.tempoOffset
        val bpm = candidate.bpm
        if (bpm != null) {
            val closeness = 1.0 - (kotlin.math.abs(bpm - target) / 40.0)
            points += (28.0 * closeness.coerceIn(0.0, 1.0)).toInt()
        }

        points -= (skipCounts[candidate.id] ?: 0) * 30
        if (artist.isNotEmpty()) {
            points -= (artistSkipCounts[artist] ?: 0) * 8
        }
        return points
    }

    private fun folderKey(folder: String): String = Names.key(folder).trim('/')
}
