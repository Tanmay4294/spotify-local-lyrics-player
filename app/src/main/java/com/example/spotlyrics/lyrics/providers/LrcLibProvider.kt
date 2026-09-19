package com.example.spotlyrics.lyrics.providers

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
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"

                try {
                    // Record network availability before making request
                com.example.spotlyrics.diagnostics.NetworkMonitor.recordNetworkStatus()
                val responseCode = connection.responseCode

                    // Record HTTP response code
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        // Record failure for provider
                        com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                            com.example.spotlyrics.diagnostics.ProviderHealth(
                                provider = "LRCLIB",
                                healthy = false,
                                lastSuccessAt = null,
                                lastFailureAt = System.currentTimeMillis(),
                                lastHttpCode = responseCode,
                                lastError = "Non-OK HTTP response"
                            )
                        )
                        return@withContext handleErrorResponse(responseCode)
                    }

                    val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    val responseBody = reader.use { it.readText() }

                    val results = gson.fromJson(responseBody, Array<LrcLibSearchResponse>::class.java)

                    if (results.isEmpty()) {
                        // Record failure - empty results
                        com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                            com.example.spotlyrics.diagnostics.ProviderHealth(
                                provider = "LRCLIB",
                                healthy = false,
                                lastSuccessAt = null,
                                lastFailureAt = System.currentTimeMillis(),
                                lastHttpCode = HttpURLConnection.HTTP_OK,
                                lastError = "Empty results"
                            )
                        )
                        return@withContext null
                    }

                    val match = findBestMatch(results, title, artist, album, durationSeconds)
                        ?: run {
                            com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                                com.example.spotlyrics.diagnostics.ProviderHealth(
                                    provider = "LRCLIB",
                                    healthy = false,
                                    lastSuccessAt = null,
                                    lastFailureAt = System.currentTimeMillis(),
                                    lastHttpCode = HttpURLConnection.HTTP_OK,
                                    lastError = "No matching track"
                                )
                            )
                            return@withContext null
                        }

                    val validatedSynced = validateSyncedLyrics(match.syncedLyrics)

                    if (match.plainLyrics.isNullOrBlank() && validatedSynced == null) {
                        com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                            com.example.spotlyrics.diagnostics.ProviderHealth(
                                provider = "LRCLIB",
                                healthy = false,
                                lastSuccessAt = null,
                                lastFailureAt = System.currentTimeMillis(),
                                lastHttpCode = HttpURLConnection.HTTP_OK,
                                lastError = "Missing lyrics content"
                            )
                        )
                        return@withContext null
                    }

                    // Successful result
                    com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                        com.example.spotlyrics.diagnostics.ProviderHealth(
                            provider = "LRCLIB",
                            healthy = true,
                            lastSuccessAt = System.currentTimeMillis(),
                            lastFailureAt = null,
                            lastHttpCode = HttpURLConnection.HTTP_OK,
                            lastError = null
                        )
                    )

                    LyricsResult(
                        source = "lrclib",
                        plainText = match.plainLyrics?.takeIf { it.isNotBlank() },
                        syncedText = validatedSynced,
                        durationSeconds = match.duration,
                        found = true
                    )
                } catch (e: java.net.SocketTimeoutException) {
                    com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                        com.example.spotlyrics.diagnostics.ProviderHealth(
                            provider = "LRCLIB",
                            healthy = false,
                            lastSuccessAt = null,
                            lastFailureAt = System.currentTimeMillis(),
                            lastHttpCode = null,
                            lastError = "Timeout: ${e.message}"
                        )
                    )
                    null
                } catch (e: java.io.IOException) {
                    com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                        com.example.spotlyrics.diagnostics.ProviderHealth(
                            provider = "LRCLIB",
                            healthy = false,
                            lastSuccessAt = null,
                            lastFailureAt = System.currentTimeMillis(),
                            lastHttpCode = null,
                            lastError = "IO error: ${e.message}"
                        )
                    )
                    null
                } catch (e: com.google.gson.JsonSyntaxException) {
                    com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                        com.example.spotlyrics.diagnostics.ProviderHealth(
                            provider = "LRCLIB",
                            healthy = false,
                            lastSuccessAt = null,
                            lastFailureAt = System.currentTimeMillis(),
                            lastHttpCode = null,
                            lastError = "JSON parse error: ${e.message}"
                        )
                    )
                    null
                } catch (e: Exception) {
                    com.example.spotlyrics.diagnostics.HealthMonitor.updateProviderHealth(
                        com.example.spotlyrics.diagnostics.ProviderHealth(
                            provider = "LRCLIB",
                            healthy = false,
                            lastSuccessAt = null,
                            lastFailureAt = System.currentTimeMillis(),
                            lastHttpCode = null,
                            lastError = "Unexpected error: ${e.message}"
                        )
                    )
                    null
                } finally {
                    connection.disconnect()
                }
    }

    private fun handleErrorResponse(responseCode: Int): LyricsResult? {
        return when (responseCode) {
            HttpURLConnection.HTTP_NOT_FOUND -> null
            HttpURLConnection.HTTP_CLIENT_TIMEOUT -> null
            429 -> null
            in 500..599 -> null
            else -> null
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

        var bestMatch: LrcLibSearchResponse? = null
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
        candidate: LrcLibSearchResponse,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String?,
        targetDuration: Double?
    ): Int {
        var score = 0

        val candidateTitle = normalizeForComparison(candidate.trackName)
        val candidateArtist = normalizeForComparison(candidate.artistName)

        if (candidateTitle != targetTitle) return -1
        if (candidateArtist != targetArtist) return -1

        score += 2

        if (targetAlbum != null) {
            val candidateAlbum = candidate.albumName?.let { normalizeForComparison(it) }
            if (candidateAlbum != null && candidateAlbum == targetAlbum) {
                score += 1
            }
        }

        if (targetDuration != null && candidate.duration != null) {
            val durationDiff = Math.abs(targetDuration - candidate.duration!!)
            if (durationDiff <= 5.0) {
                score += 1
            }
        }

        if (hasVersionMismatch(candidate)) {
            score -= 2
        }

        return score
    }

    private fun hasVersionMismatch(candidate: LrcLibSearchResponse): Boolean {
        val versionMarkers = listOf(
            "live", "acoustic", "remix", "radio edit", "instrumental",
            "extended", "demo", "version", "edit", "cover", "karaoke"
        )
        val titleLower = candidate.trackName.lowercase()
        val artistLower = candidate.artistName.lowercase()

        return versionMarkers.any { marker ->
            titleLower.contains(marker) || artistLower.contains(marker)
        }
    }

    private fun validateSyncedLyrics(syncedLyrics: String?): String? {
        val trimmed = syncedLyrics?.trim()
        if (trimmed.isNullOrBlank()) return null

        val timestampPattern = Pattern.compile("\\[\\d{2}:\\d{2}(?:\\.\\d{2,3})?\\]")
        val hasTimestamps = timestampPattern.matcher(trimmed!!).find()

        return if (hasTimestamps) trimmed else null
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
}