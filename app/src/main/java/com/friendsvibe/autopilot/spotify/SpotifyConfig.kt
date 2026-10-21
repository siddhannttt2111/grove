package com.friendsvibe.autopilot.spotify

import com.friendsvibe.autopilot.BuildConfig

object SpotifyConfig {
    const val REDIRECT_URI = BuildConfig.SPOTIFY_REDIRECT_URI

    fun clientId(): String = BuildConfig.SPOTIFY_CLIENT_ID

    fun isConfigured(): Boolean = clientId().isNotBlank()
}
