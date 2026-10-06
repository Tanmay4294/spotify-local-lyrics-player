package com.example.spotlyrics.lyrics.providers

import com.example.spotlyrics.diagnostics.HealthMonitor
import com.example.spotlyrics.diagnostics.NetworkMonitor
import com.example.spotlyrics.diagnostics.ProviderHealth
import com.example.spotlyrics.lyrics.LyricsProvider
import com.example.spotlyrics.lyrics.LyricsResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class NetEaseProvider : LyricsProvider {

    private val gson = Gson()

    private data class NetEaseArtist(val name: String?)
    private data class NetEaseAlbum(val name: String?)
    private data class NetEaseSong(
        val id: Long,
        val name: String?,
        val artists: List<NetEaseArtist>?,
        val album: NetEaseAlbum?,
        val duration: Long?
    )
    private data class NetEaseSearchResult(val songs: List<NetEaseSong>?)
    private data class NetEaseSearchResponse(val result: NetEaseSearchResult?, val code: Int?)

    private data class NetEaseLrc(val lyric: String?)
    private data class NetEaseLyricResponse(
        val lrc: NetEaseLrc?,
        val uncollected: Boolean?,
        val nolyric: Boolean?,
        val code: Int?
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

        try {
            NetworkMonitor.recordNetworkStatus()

            val searchQuery = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = URL("https://music.163.com/api/search/get/web?s=$searchQuery&type=1&offset=0&limit=10")
            val connection = searchUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.setRequestProperty("Referer", "https://music.163.com/")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                recordFailure(responseCode, "Non-OK HTTP search response: $responseCode")
                connection.disconnect()
                return@withContext null
            }

            val searchBody = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            connection.disconnect()

            val searchResponse = gson.fromJson(searchBody, NetEaseSearchResponse::class.java)
            val songs = searchResponse?.result?.songs
            if (songs.isNullOrEmpty()) {
                recordFailure(responseCode, "Empty search results")
                return@withContext null
            }

            val match = findBestMatch(songs, title, artist, album, durationSeconds)
            if (match == null) {
                recordFailure(responseCode, "No matching track")
                return@withContext null
            }

            // Fetch lyrics for the matched song ID
            val lyricResult = fetchLyricForSongId(match.id, durationSeconds ?: (match.duration?.div(1000.0)))
            if (lyricResult != null) {
                HealthMonitor.updateProviderHealth(
                    ProviderHealth(
                        provider = "NETEASE",
                        healthy = true,
                        lastSuccessAt = System.currentTimeMillis(),
                        lastFailureAt = null,
                        lastHttpCode = HttpURLConnection.HTTP_OK,
                        lastError = null
                    )
                )
            } else {
                recordFailure(responseCode, "Missing lyrics content")
            }
            return@withContext lyricResult

        } catch (e: Exception) {
            recordFailure(null, "Error: ${e.message}")
            return@withContext null
        }
    }

    private fun fetchLyricForSongId(songId: Long, defaultDurationSec: Double?): LyricsResult? {
        val lyricUrl = URL("https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1")
        val connection = lyricUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        connection.setRequestProperty("Referer", "https://music.163.com/")

        return try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) return null

            val body = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            val response = gson.fromJson(body, NetEaseLyricResponse::class.java)

            if (response?.nolyric == true || response?.uncollected == true) {
                return null
            }

            val rawLyric = response?.lrc?.lyric
            if (rawLyric.isNullOrBlank()) return null

            val validatedSynced = validateSyncedLyrics(rawLyric)
            val plainText = if (validatedSynced == null) cleanPlainLyrics(rawLyric) else null

            if (validatedSynced == null && plainText.isNullOrBlank()) return null

            LyricsResult(
                source = "netease",
                plainText = plainText,
                syncedText = validatedSynced,
                durationSeconds = defaultDurationSec,
                found = true
            )
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun findBestMatch(
        candidates: List<NetEaseSong>,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String?,
        targetDuration: Double?
    ): NetEaseSong? {
        val normalizedTargetTitle = normalizeForComparison(targetTitle)
        val normalizedTargetArtist = normalizeForComparison(targetArtist)
        val normalizedTargetAlbum = targetAlbum?.let { normalizeForComparison(it) }

        var bestMatch: NetEaseSong? = null
        var bestScore = -1

        for (candidate in candidates) {
            val score = scoreCandidate(
                candidate,
                normalizedTargetTitle,
                normalizedTargetArtist,
                normalizedTargetAlbum,
                targetDuration
            )
            if (score > bestScore) {
                bestScore = score
                bestMatch = candidate
            }
        }

        return if (bestScore >= 2) bestMatch else null
    }

    private fun scoreCandidate(
        candidate: NetEaseSong,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String?,
        targetDuration: Double?
    ): Int {
        val candidateTitle = candidate.name?.let { normalizeForComparison(it) } ?: return -1
        val candidateArtist = candidate.artists?.firstOrNull()?.name?.let { normalizeForComparison(it) } ?: return -1

        if (candidateTitle != targetTitle && !candidateTitle.contains(targetTitle) && !targetTitle.contains(candidateTitle)) return -1
        if (candidateArtist != targetArtist && !candidateArtist.contains(targetArtist) && !targetArtist.contains(candidateArtist)) return -1

        var score = 2

        if (targetAlbum != null && candidate.album?.name != null) {
            val candidateAlbum = normalizeForComparison(candidate.album.name)
            if (candidateAlbum == targetAlbum) {
                score += 1
            }
        }

        if (targetDuration != null && candidate.duration != null) {
            val candidateDurationSec = candidate.duration / 1000.0
            val diff = Math.abs(targetDuration - candidateDurationSec)
            if (diff <= 8.0) {
                score += 1
            }
        }

        return score
    }

    private fun validateSyncedLyrics(syncedLyrics: String?): String? {
        val trimmed = syncedLyrics?.trim() ?: return null
        val timestampPattern = Pattern.compile("\\[\\d{2}:\\d{2}(?:\\.\\d{2,3})?\\]")
        val hasTimestamps = timestampPattern.matcher(trimmed).find()
        return if (hasTimestamps) trimmed else null
    }

    private fun cleanPlainLyrics(raw: String): String? {
        val lines = raw.lines()
            .map { it.replace("\\[\\d{2}:\\d{2}(?:\\.\\d{2,3})?\\]".toRegex(), "").trim() }
            .filter { it.isNotBlank() }
        return if (lines.isNotEmpty()) lines.joinToString("\n") else null
    }

    private fun normalizeForComparison(text: String): String {
        return text.trim()
            .lowercase()
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("–", "-")
            .replace("—", "-")
            .replace("[^a-z0-9'\\-\\s]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun recordFailure(httpCode: Int?, errorMsg: String) {
        HealthMonitor.updateProviderHealth(
            ProviderHealth(
                provider = "NETEASE",
                healthy = false,
                lastSuccessAt = null,
                lastFailureAt = System.currentTimeMillis(),
                lastHttpCode = httpCode,
                lastError = errorMsg
            )
        )
    }
}
