package com.example.spotlyrics.lyrics.providers

import com.example.spotlyrics.diagnostics.HealthMonitor
import com.example.spotlyrics.diagnostics.ProviderHealth
import com.example.spotlyrics.lyrics.LyricsProvider
import com.example.spotlyrics.lyrics.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusixmatchProvider : LyricsProvider {

    override suspend fun search(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Double?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) {
            return@withContext null
        }

        // Record health status: Musixmatch API authentication is disabled to ensure zero exposed credentials
        HealthMonitor.updateProviderHealth(
            ProviderHealth(
                provider = "MUSIXMATCH",
                healthy = false,
                lastSuccessAt = null,
                lastFailureAt = System.currentTimeMillis(),
                lastHttpCode = null,
                lastError = "Authentication key not provided (security restriction)"
            )
        )

        // Under strict security requirements, no credentials or insecure scraping are used.
        null
    }
}
