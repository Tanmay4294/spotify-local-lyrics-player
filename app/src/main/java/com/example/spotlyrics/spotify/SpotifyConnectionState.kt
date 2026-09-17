package com.example.spotlyrics.spotify

sealed interface SpotifyConnectionState {
    data object Disconnected : SpotifyConnectionState
    data object Connecting : SpotifyConnectionState
    data object Connected : SpotifyConnectionState
    data class Error(val message: String) : SpotifyConnectionState
}
