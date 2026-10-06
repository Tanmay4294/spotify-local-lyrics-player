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

class QQMusicProvider : LyricsProvider {

    private val gson = Gson()

    private data class QQSinger(val name: String?)
    private data class QQSong(
        val songmid: String?,
        val songname: String?,
        val singer: List<QQSinger>?,
        val albumname: String?,
        val interval: Double?
    )
    private data class QQSongListData(val list: List<QQSong>?)
    private data class QQSearchData(val song: QQSongListData?)
    private data class QQSearchResponse(val data: QQSearchData?, val code: Int?)

    private data class QQLyricResponse(
        val lyric: String?,
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

            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = URL("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$query&format=json&n=5")
            val connection = searchUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.setRequestProperty("Referer", "https://y.qq.com/")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                recordFailure(responseCode, "Non-OK HTTP search response: $responseCode")
                connection.disconnect()
                return@withContext null
            }

            val searchBody = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            connection.disconnect()

            val searchResponse = gson.fromJson(searchBody, QQSearchResponse::class.java)
            val songs = searchResponse?.data?.song?.list
            if (songs.isNullOrEmpty()) {
                recordFailure(responseCode, "Empty search results")
                return@withContext null
            }

            val match = findBestMatch(songs, title, artist, album, durationSeconds)
            if (match == null || match.songmid.isNullOrBlank()) {
                recordFailure(responseCode, "No matching track")
                return@withContext null
            }

            val lyricResult = fetchLyricForSongMid(match.songmid, durationSeconds ?: match.interval)
            if (lyricResult != null) {
                HealthMonitor.updateProviderHealth(
                    ProviderHealth(
                        provider = "QQ_MUSIC",
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

    private fun fetchLyricForSongMid(songMid: String, defaultDurationSec: Double?): LyricsResult? {
        val lyricUrl = URL("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json&nobase64=1")
        val connection = lyricUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        connection.setRequestProperty("Referer", "https://y.qq.com/")

        return try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) return null

            val body = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            val response = gson.fromJson(body, QQLyricResponse::class.java)

            val rawLyric = response?.lyric
            if (rawLyric.isNullOrBlank()) return null

            val decodedLyric = decodeLyricText(rawLyric)
            if (decodedLyric.isBlank()) return null

            val validatedSynced = validateSyncedLyrics(decodedLyric)
            val plainText = if (validatedSynced == null) cleanPlainLyrics(decodedLyric) else null

            if (validatedSynced == null && plainText.isNullOrBlank()) return null

            LyricsResult(
                source = "qq_music",
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

    private fun decodeLyricText(raw: String): String {
        var text = raw
        // Unescape HTML entities if present (e.g. &#10;, &#32;, &#58;)
        text = text.replace("&#10;", "\n")
            .replace("&#32;", " ")
            .replace("&#58;", ":")
            .replace("&#46;", ".")
            .replace("&#45;", "-")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")

        // If it looks like base64 encoded
        if (!text.contains("[") && text.length > 20 && !text.contains(" ")) {
            try {
                val bytes = java.util.Base64.getDecoder().decode(text.trim())
                text = String(bytes, StandardCharsets.UTF_8)
            } catch (ignored: Exception) {
            }
        }
        return text
    }

    private fun findBestMatch(
        candidates: List<QQSong>,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String?,
        targetDuration: Double?
    ): QQSong? {
        val normalizedTargetTitle = normalizeForComparison(targetTitle)
        val normalizedTargetArtist = normalizeForComparison(targetArtist)
        val normalizedTargetAlbum = targetAlbum?.let { normalizeForComparison(it) }

        var bestMatch: QQSong? = null
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
        candidate: QQSong,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String?,
        targetDuration: Double?
    ): Int {
        val candidateTitle = candidate.songname?.let { normalizeForComparison(it) } ?: return -1
        val candidateArtist = candidate.singer?.firstOrNull()?.name?.let { normalizeForComparison(it) } ?: return -1

        if (candidateTitle != targetTitle && !candidateTitle.contains(targetTitle) && !targetTitle.contains(candidateTitle)) return -1
        if (candidateArtist != targetArtist && !candidateArtist.contains(targetArtist) && !targetArtist.contains(candidateArtist)) return -1

        var score = 2

        if (targetAlbum != null && candidate.albumname != null) {
            val candidateAlbum = normalizeForComparison(candidate.albumname)
            if (candidateAlbum == targetAlbum) {
                score += 1
            }
        }

        if (targetDuration != null && candidate.interval != null) {
            val diff = Math.abs(targetDuration - candidate.interval)
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
                provider = "QQ_MUSIC",
                healthy = false,
                lastSuccessAt = null,
                lastFailureAt = System.currentTimeMillis(),
                lastHttpCode = httpCode,
                lastError = errorMsg
            )
        )
    }
}
