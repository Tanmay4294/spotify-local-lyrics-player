package com.example.spotlyrics.spotify

enum class SpotifyAuthState {
    VALID,
    ACCESS_TOKEN_EXPIRED,
    REFRESH_FAILED,
    AUTH_REVOKED,
    REAUTH_REQUIRED
}
