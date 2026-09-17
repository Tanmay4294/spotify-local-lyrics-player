package com.example.spotlyrics.spotify

data class SpotifyToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
    val tokenType: String = "Bearer",
    val scope: String = ""
) {
    fun isExpired(safetyBufferSeconds: Long = 60): Boolean {
        val currentEpochSeconds = System.currentTimeMillis() / 1000
        return currentEpochSeconds + safetyBufferSeconds >= expiresAtEpochSeconds
    }
}
