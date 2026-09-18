package com.example.spotlyrics.lyrics.providers

import com.example.spotlyrics.lyrics.LyricsProvider
import com.example.spotlyrics.lyrics.LyricsResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class LrcLibProvider : LyricsProvider {

    private val gson = Gson()

    private data class LrcLibSearchResponse(
        val id: String,
        val trackName: String,
        val artistName: String,
        val albumName: String?,
        val duration: Double?,
        val instrumental: Boolean?,
        val plainLyrics: String?,
        val syncedLyrics: String?
    )

    override suspend fun search(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Double?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) {
            return@withContext null
        }

        val queryParams = mutableMapOf(
            "track_name" to title,
            "artist_name" to artist
        )
        if (!album.isNullOrBlank()) {
            queryParams["album_name"] = album!!
        }

        val encodedParams = queryParams.entries.map { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }.joinToString("&")

        val url = URL("https://lrclib.net/api/search?$encodedParams")
        val connection = url.openConnection()
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val inputStream = try {
            connection.getInputStream()
        } catch (e: Exception) {
            return@withContext null
        }

        try {
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val responseBody = reader.use { it.readText() }
            val results = gson.fromJson(responseBody, Array<LrcLibSearchResponse>::class.java)

            if (results.isEmpty()) {
                return@withContext null
            }

            val match = findBestMatch(results, title, artist, album, durationSeconds)
                ?: return@withContext null

            if (match.plainLyrics.isNullOrBlank() && match.syncedLyrics.isNullOrBlank()) {
                return@withContext null
            }

            LyricsResult(
                source = "lrclib",
                plainText = match.plainLyrics,
                syncedText = match.syncedLyrics,
                durationSeconds = match.duration,
                found = true
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                connection.getInputStream()?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun findBestMatch(
        candidates: Array<LrcLibSearchResponse>,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String?,
        targetDuration: Double?
    ): LrcLibSearchResponse? {
        val normalizedTargetTitle = normalizeForComparison(targetTitle)
        val normalizedTargetArtist = normalizeForComparison(targetArtist)
        val normalizedTargetAlbum = targetAlbum?.let { normalizeForComparison(it) }

        for (candidate in candidates) {
            val candidateTitle = normalizeForComparison(candidate.trackName)
            val candidateArtist = normalizeForComparison(candidate.artistName)

            if (candidateTitle != normalizedTargetTitle || candidateArtist != normalizedTargetArtist) {
                continue
            }

            if (normalizedTargetAlbum != null) {
                val candidateAlbum = candidate.albumName?.let { normalizeForComparison(it) }
                if (candidateAlbum != null && candidateAlbum != normalizedTargetAlbum) {
                    continue
                }
            }

            if (targetDuration != null && candidate.duration != null) {
                val durationDiff = Math.abs(targetDuration - candidate.duration!!)
                if (durationDiff > 5.0) {
                    continue
                }
            }

            return candidate
        }

        return null
    }

    private fun normalizeForComparison(text: String): String {
        return text.trim().lowercase()
    }
}