package com.friendsvibe.autopilot.spotify

import com.friendsvibe.autopilot.rank.SpotifyTrack
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Catalog metadata only. This client never asks Spotify for audio,
 * audio features, audio analysis, or recommendations.
 */
class SpotifyWebApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    fun profile(token: String): SpotifyProfile {
        val json = JSONObject(get("https://api.spotify.com/v1/me", token))
        val name = json.optString("display_name").ifBlank { "Spotify listener" }
        return SpotifyProfile(name)
    }

    fun search(token: String, query: String): List<SpotifyTrack> {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.spotify.com")
            .addPathSegments("v1/search")
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "10")
            .build()
        val json = JSONObject(get(url.toString(), token))
        val items = json.optJSONObject("tracks")?.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val track = items.optJSONObject(index) ?: continue
                parseTrack(track)?.let { add(it) }
            }
        }
    }

    fun savedTracks(token: String): List<SpotifyTrack> {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.spotify.com")
            .addPathSegments("v1/me/tracks")
            .addQueryParameter("limit", "50")
            .build()
        val json = JSONObject(get(url.toString(), token))
        val items = json.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val wrapper = items.optJSONObject(index) ?: continue
                val track = wrapper.optJSONObject("track") ?: continue
                parseTrack(track)?.let { add(it) }
            }
        }
    }

    private fun get(url: String, token: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "Spotify returned ${response.code}." }
                throw SpotifyApiException(response.code, message)
            }
            return body
        }
    }

    private fun parseTrack(json: JSONObject): SpotifyTrack? {
        val id = json.optString("id")
        if (id.isBlank()) return null
        val artistsJson = json.optJSONArray("artists")
        val artists = buildList {
            if (artistsJson != null) {
                for (index in 0 until artistsJson.length()) {
                    val name = artistsJson.optJSONObject(index)?.optString("name").orEmpty()
                    if (name.isNotBlank()) add(name)
                }
            }
        }
        return SpotifyTrack(
            id = id,
            uri = json.optString("uri").ifBlank { "spotify:track:$id" },
            title = json.optString("name").ifBlank { "Untitled" },
            artists = artists,
            album = json.optJSONObject("album")?.optString("name").orEmpty(),
            durationMs = json.optLong("duration_ms"),
        )
    }
}

data class SpotifyProfile(val name: String)

class SpotifyApiException(val status: Int, message: String) : Exception(message)
