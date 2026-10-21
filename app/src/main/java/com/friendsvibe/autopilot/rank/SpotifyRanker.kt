package com.friendsvibe.autopilot.rank

data class SpotifyTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
) {
    val artist: String
        get() = artists.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Unknown artist" }

    fun sharesBannedArtist(banned: Set<String>): Boolean {
        val keys = banned.map { Names.key(it) }.filter { it.isNotEmpty() }.toSet()
        return artists.any { Names.key(it) in keys }
    }
}

object SpotifyRanker {
    private val calmWords = listOf(
        "acoustic", "chill", "piano", "soft", "unplugged", "ballad", "stripped", "ambient", "lo-fi", "lofi",
    )
    private val hardWords = listOf(
        "remix", "club", "dance", "hard", "extended", "vip", "bass",
    )

    fun searchQueries(seed: SpotifyTrack, vibe: Vibe): List<String> {
        val artist = seed.artists.firstOrNull().orEmpty().replace("\"", "").trim()
        val title = seed.title.replace("\"", "").trim()
        val queries = mutableListOf<String>()
        if (artist.isNotEmpty()) queries += "artist:\"$artist\""
        if (title.isNotEmpty()) queries += "track:\"$title\""
        if (artist.isNotEmpty() && vibe.clamped < 0) {
            queries += "artist:\"$artist\" acoustic"
            queries += "artist:\"$artist\" chill"
        }
        if (artist.isNotEmpty() && vibe.clamped > 0) {
            queries += "artist:\"$artist\" remix"
            queries += "artist:\"$artist\" club"
        }
        return queries.distinct()
    }

    fun rank(
        seed: SpotifyTrack,
        pool: List<SpotifyTrack>,
        vibe: Vibe,
        bannedArtists: Set<String>,
        savedIds: Set<String>,
        skipCounts: Map<String, Int>,
        excluding: Set<String> = setOf(seed.id),
        limit: Int = 8,
    ): List<SpotifyTrack> {
        return pool
            .asSequence()
            .filter { it.id !in excluding && it.uri != seed.uri }
            .filterNot { it.sharesBannedArtist(bannedArtists) }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<SpotifyTrack> { score(seed, it, vibe, savedIds, skipCounts) }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id },
            )
            .take(limit)
            .toList()
    }

    fun score(
        seed: SpotifyTrack,
        candidate: SpotifyTrack,
        vibe: Vibe,
        savedIds: Set<String>,
        skipCounts: Map<String, Int>,
    ): Int {
        var points = 0
        val seedArtists = seed.artists.map { Names.key(it) }.filter { it.isNotEmpty() }.toSet()
        val candidateArtists = candidate.artists.map { Names.key(it) }.filter { it.isNotEmpty() }.toSet()
        if (seedArtists.isNotEmpty() && seedArtists.intersect(candidateArtists).isNotEmpty()) points += 40

        val seedAlbum = Names.key(seed.album)
        val album = Names.key(candidate.album)
        if (seedAlbum.isNotEmpty() && seedAlbum == album) points += 12

        val overlap = Names.tokens(seed.title).intersect(Names.tokens(candidate.title)).size
        points += (overlap * 8).coerceAtMost(24)

        if (candidate.id in savedIds) points += 15

        val lean = vibe.clamped
        val title = candidate.title.lowercase()
        val calm = calmWords.any { title.contains(it) }
        val hard = hardWords.any { title.contains(it) }
        if (lean < 0) {
            if (calm) points += 16 * -lean
            if (hard) points -= 12 * -lean
        } else if (lean > 0) {
            if (hard) points += 16 * lean
            if (calm) points -= 12 * lean
        }

        points -= (skipCounts[candidate.id] ?: 0) * 40
        return points
    }
}
