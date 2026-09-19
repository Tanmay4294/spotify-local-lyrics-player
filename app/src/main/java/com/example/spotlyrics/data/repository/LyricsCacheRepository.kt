package com.example.spotlyrics.data.repository

import com.example.spotlyrics.data.db.LyricsCacheDao
import com.example.spotlyrics.data.db.LyricsCacheEntity
import com.example.spotlyrics.lyrics.LyricsResult
import com.example.spotlyrics.lyrics.util.LyricsCacheKey
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.flow.first

class LyricsCacheRepository(private val dao: LyricsCacheDao) {

    suspend fun getCachedLyrics(track: SpotifyTrack): LyricsResult? {
        val cacheKey = LyricsCacheKey.generate(track)
        return try {
            val entity = dao.getByCacheKey(cacheKey).first()
            com.example.spotlyrics.diagnostics.HealthMonitor.recordSuccess("CacheDB", "Read success")
            entity?.toLyricsResult()
        } catch (e: Exception) {
            com.example.spotlyrics.diagnostics.HealthMonitor.recordFailure("CacheDB", "Read failure: ${e.message}")
            null
        }
    }

    suspend fun saveLyrics(track: SpotifyTrack, result: LyricsResult) {
        val cacheKey = LyricsCacheKey.generate(track)
        val entity = LyricsCacheEntity(
            cacheKey = cacheKey,
            title = track.name,
            artist = track.artistName,
            album = track.albumName,
            durationSeconds = track.durationMs?.let { it / 1000.0 },
            source = result.source,
            plainLyrics = result.plainText,
            syncedLyrics = result.syncedText,
            fetchedAt = System.currentTimeMillis()
        )
        try {
            dao.insertOrReplace(entity)
            com.example.spotlyrics.diagnostics.HealthMonitor.recordSuccess("CacheDB", "Write success")
        } catch (e: Exception) {
            com.example.spotlyrics.diagnostics.HealthMonitor.recordFailure("CacheDB", "Write failure: ${e.message}")
        }
    }
}

private fun LyricsCacheEntity.toLyricsResult(): LyricsResult? {
    if (plainLyrics.isNullOrBlank() && syncedLyrics.isNullOrBlank()) {
        return null
    }
    return LyricsResult(
        source = source,
        plainText = plainLyrics?.takeIf { it.isNotBlank() },
        syncedText = syncedLyrics?.takeIf { it.isNotBlank() },
        durationSeconds = durationSeconds,
        found = true
    )
}