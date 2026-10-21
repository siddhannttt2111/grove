package com.grove.autopilot.vibe

import com.grove.autopilot.model.Energy
import com.grove.autopilot.model.Source
import com.grove.autopilot.model.Track
import kotlin.math.abs

data class Pick(
    val track: Track,
    val reason: String,
    val score: Int,
)

object VibeScorer {
    private val calmWords = listOf(
        "acoustic", "ambient", "jazz", "classical", "piano", "folk",
        "chill", "lofi", "lo-fi", "ballad", "sleep", "soft",
    )
    private val liftWords = listOf(
        "remix", "club", "dance", "edm", "house", "techno",
        "phonk", "workout", "rave", "hardstyle", "banger",
    )
    private val stopWords = setOf(
        "the", "and", "for", "with", "feat", "ft", "version", "edit", "radio", "mix",
    )

    fun plan(
        seed: Track,
        library: List<Track>,
        energy: Energy,
        bannedArtists: Set<String>,
        excludeIds: Set<String>,
        limit: Int = 24,
    ): List<Pick> {
        val banned = bannedArtists.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val total = library.size.coerceAtLeast(1)
        val folderCounts = library.groupingBy { usable(it.folder) ?: "" }.eachCount()
        val seedArtist = usable(seed.artist)
        val seedAlbum = usable(seed.album)
        val seedFolder = usable(seed.folder)
        val seedWords = words(seed.title)

        val ranked = library.mapNotNull { track ->
            if (track.source != Source.Phone) return@mapNotNull null
            if (track.id == seed.id || track.id in excludeIds) return@mapNotNull null
            if (excludesArtist(track.artist, banned)) return@mapNotNull null

            val artist = usable(track.artist)
            val album = usable(track.album)
            val folder = usable(track.folder)
            val sameArtist = seedArtist != null && artist == seedArtist
            val sameAlbum = seedAlbum != null && album == seedAlbum
            val folderShared = seedFolder != null && folder == seedFolder
            val folderUseful = folderShared && ((folderCounts[seedFolder] ?: 0).toFloat() / total) < 0.7f
            val shared = words(track.title).intersect(seedWords).size
            val text = "${track.title} ${track.artist} ${track.album} ${track.folder}".lowercase()
            val calmHit = calmWords.any { it in text }
            val liftHit = liftWords.any { it in text }
            var score = 0
            if (sameArtist) score += 55
            if (sameAlbum) score += 18
            if (folderUseful) score += 22
            score += shared * 14
            val energyHit = when (energy) {
                Energy.Calm -> calmHit && !liftHit
                Energy.Lift -> liftHit && !calmHit
                Energy.Steady -> false
            }
            when (energy) {
                Energy.Calm -> {
                    if (calmHit) score += 24
                    if (liftHit) score -= 18
                }
                Energy.Lift -> {
                    if (liftHit) score += 24
                    if (calmHit) score -= 18
                }
                Energy.Steady -> Unit
            }
            score += abs((track.id.hashCode() * 31) xor seed.id.hashCode()) % 8
            Scored(track, score, sameArtist, sameAlbum, folderUseful, shared, energyHit)
        }.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.track.title.lowercase() })

        val picked = ArrayList<Scored>(limit)
        val held = ArrayList<Scored>()
        var artistCount = 0
        for (item in ranked) {
            if (picked.size == limit) break
            if (item.sameArtist && artistCount >= 8) {
                held += item
                continue
            }
            if (item.sameArtist) artistCount += 1
            picked += item
        }
        for (item in held) {
            if (picked.size == limit) break
            picked += item
        }
        return picked.map { item ->
            Pick(item.track, reason(item, energy), item.score)
        }
    }

    fun excludesArtist(artist: String, bannedArtists: Set<String>): Boolean {
        val banned = bannedArtists.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (banned.isEmpty()) return false
        val full = artist.trim().lowercase()
        if (full in banned) return true
        return artist.split(",").any { part ->
            val key = part.trim().lowercase()
            key.isNotEmpty() && key in banned
        }
    }

    private fun reason(item: Scored, energy: Energy): String = when {
        item.sameArtist -> "Same artist"
        item.sameAlbum -> "Same album"
        item.sameFolder -> "Same playlist"
        item.sharedWords > 0 -> "Close title"
        item.energyHit && energy == Energy.Calm -> "Calmer"
        item.energyHit && energy == Energy.Lift -> "Harder"
        else -> "Keeps the set"
    }

    private fun usable(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("<unknown>", true)) return null
        if (trimmed.equals("unknown", true)) return null
        if (trimmed.equals("unknown artist", true)) return null
        if (trimmed.equals("unknown album", true)) return null
        return trimmed.lowercase()
    }

    private fun words(title: String): Set<String> {
        return title.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    private data class Scored(
        val track: Track,
        val score: Int,
        val sameArtist: Boolean,
        val sameAlbum: Boolean,
        val sameFolder: Boolean,
        val sharedWords: Int,
        val energyHit: Boolean,
    )
}
