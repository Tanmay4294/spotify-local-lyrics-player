package com.example.spotlyrics.spotify

sealed interface SpotifyAuthState {
    data object SignedOut : SpotifyAuthState
    data object Authorizing : SpotifyAuthState
    data object ExchangingCode : SpotifyAuthState
    data class Authorized(
        val expiresAtEpochSeconds: Long
    ) : SpotifyAuthState
    data class Error(
        val message: String
    ) : SpotifyAuthState
}
