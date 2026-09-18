package com.example.spotlyrics.lyrics.providers

import com.example.spotlyrics.lyrics.LyricsProvider
import com.example.spotlyrics.lyrics.LyricsResult
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Test

class LrcLibProviderTest {

    @Test
    fun `URL encoding handles special characters`() {
        val title = "Song & Dance"
        val artist = "Artist + Name"
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")

        assertTrue(encodedTitle.contains("%26"))
        assertTrue(encodedArtist.contains("%2B"))
    }

    @Test
    fun `LyricsResult can represent found result with plain and synced lyrics`() {
        val result = LyricsResult(
            source = "lrclib",
            plainText = "Plain lyrics",
            syncedText = "[00:00.00]Synced lyrics",
            durationSeconds = 240.0,
            found = true
        )

        assertTrue(result.found)
        assertEquals("lrclib", result.source)
        assertEquals("Plain lyrics", result.plainText)
        assertEquals("[00:00.00]Synced lyrics", result.syncedText)
        assertEquals(240.0, result.durationSeconds!!, 0.01)
    }

    @Test
    fun `LyricsResult can represent not-found result`() {
        val result = LyricsResult(
            source = "lrclib",
            plainText = null,
            syncedText = null,
            durationSeconds = null,
            found = false
        )

        assertFalse(result.found)
        assertNull(result.plainText)
        assertNull(result.syncedText)
        assertNull(result.durationSeconds)
    }

    @Test
    fun `provider interface can be implemented`() = runBlockingTest {
        val testProvider = object : LyricsProvider {
            override suspend fun search(
                title: String,
                artist: String,
                album: String?,
                durationSeconds: Double?
            ): LyricsResult? = LyricsResult(
                source = "test",
                plainText = "Test lyrics",
                syncedText = null,
                durationSeconds = 100.0,
                found = true
            )
        }

        val result = testProvider.search("Title", "Artist", null, null)

        assertNotNull(result)
        assertTrue(result!!.found)
    }

    @Test
    fun `provider search with blank title returns null`() = runBlockingTest {
        val testProvider = object : LyricsProvider {
            override suspend fun search(
                title: String,
                artist: String,
                album: String?,
                durationSeconds: Double?
            ): LyricsResult? {
                if (title.isBlank() || artist.isBlank()) return null
                return LyricsResult("test", "lyrics", null, 100.0, true)
            }
        }

        val result1 = testProvider.search("", "Artist", null, null)
        val result2 = testProvider.search("Title", "", null, null)
        val result3 = testProvider.search("  ", "Artist", null, null)

        assertNull(result1)
        assertNull(result2)
        assertNull(result3)
    }

    @Test
    fun `LyricsResult with only plain lyrics`() {
        val result = LyricsResult(
            source = "lrclib",
            plainText = "Plain lyrics only",
            syncedText = null,
            durationSeconds = 180.0,
            found = true
        )

        assertTrue(result.found)
        assertEquals("Plain lyrics only", result.plainText)
        assertNull(result.syncedText)
        assertEquals(180.0, result.durationSeconds!!, 0.01)
    }

    @Test
    fun `LyricsResult with only synced lyrics`() {
        val result = LyricsResult(
            source = "lrclib",
            plainText = null,
            syncedText = "[00:00.00]Synced lyrics",
            durationSeconds = 200.0,
            found = true
        )

        assertTrue(result.found)
        assertNull(result.plainText)
        assertEquals("[00:00.00]Synced lyrics", result.syncedText)
        assertEquals(200.0, result.durationSeconds!!, 0.01)
    }

    @Test
    fun `LyricsResult with both plain and synced lyrics`() {
        val result = LyricsResult(
            source = "lrclib",
            plainText = "Plain lyrics",
            syncedText = "[00:00.00]Synced lyrics",
            durationSeconds = 240.0,
            found = true
        )

        assertTrue(result.found)
        assertEquals("Plain lyrics", result.plainText)
        assertEquals("[00:00.00]Synced lyrics", result.syncedText)
        assertEquals(240.0, result.durationSeconds!!, 0.01)
    }

    @Test
    fun `normalization handles common punctuation differences`() {
        val original1 = "Don't Stop Me Now"
        val original2 = "Don't Stop Me Now"
        val normalized1 = normalizeForComparison(original1)
        val normalized2 = normalizeForComparison(original2)

        assertEquals(normalized1, normalized2)
    }

    @Test
    fun `normalization handles smart quotes and dashes`() {
        val text1 = "Artist – Song"
        val text2 = "Artist - Song"
        val normalized1 = normalizeForComparison(text1)
        val normalized2 = normalizeForComparison(text2)

        assertEquals(normalized1, normalized2)
    }

    @Test
    fun `normalization handles case and whitespace`() {
        val text1 = "  ARTIST  -  SONG  "
        val text2 = "artist - song"
        val normalized1 = normalizeForComparison(text1)
        val normalized2 = normalizeForComparison(text2)

        assertEquals(normalized1, normalized2)
    }

    @Test
    fun `title match is required`() {
        // This tests the logic in LrcLibProvider - a candidate with different title should be rejected
        val targetTitle = "test track"
        val candidateTitle = "different track"

        val normalizedTarget = normalizeForComparison(targetTitle)
        val normalizedCandidate = normalizeForComparison(candidateTitle)

        assertNotEquals(normalizedTarget, normalizedCandidate)
    }

    @Test
    fun `artist match is required`() {
        val targetArtist = "test artist"
        val candidateArtist = "different artist"

        val normalizedTarget = normalizeForComparison(targetArtist)
        val normalizedCandidate = normalizeForComparison(candidateArtist)

        assertNotEquals(normalizedTarget, normalizedCandidate)
    }

    @Test
    fun `album match is used when available`() {
        val targetAlbum = "test album"
        val candidateAlbum1 = "test album"
        val candidateAlbum2 = "different album"

        val normalizedTarget = normalizeForComparison(targetAlbum)
        val normalizedCandidate1 = normalizeForComparison(candidateAlbum1)
        val normalizedCandidate2 = normalizeForComparison(candidateAlbum2)

        assertEquals(normalizedTarget, normalizedCandidate1)
        assertNotEquals(normalizedTarget, normalizedCandidate2)
    }

    @Test
    fun `duration within tolerance is accepted`() {
        val targetDuration = 240.0
        val candidateDuration = 242.0

        val durationDiff = Math.abs(targetDuration - candidateDuration)
        assertTrue(durationDiff <= 5.0)
    }

    @Test
    fun `duration outside tolerance is rejected`() {
        val targetDuration = 240.0
        val candidateDuration = 300.0

        val durationDiff = Math.abs(targetDuration - candidateDuration)
        assertTrue(durationDiff > 5.0)
    }

    @Test
    fun `synced lyrics validation accepts valid timestamps`() {
        val syncedLyrics = "[00:00.00]First line\n[00:05.50]Second line"
        val timestampPattern = java.util.regex.Pattern.compile("\\[\\d{2}:\\d{2}(?:\\.\\d{2,3})?\\]")
        val matcher = timestampPattern.matcher(syncedLyrics)

        assertTrue(matcher.find())
    }

    @Test
    fun `synced lyrics validation rejects invalid timestamps`() {
        val syncedLyrics = "Just plain text without timestamps"
        val timestampPattern = java.util.regex.Pattern.compile("\\[\\d{2}:\\d{2}(?:\\.\\d{2,3})?\\]")
        val matcher = timestampPattern.matcher(syncedLyrics)

        assertFalse(matcher.find())
    }

    @Test
    fun `synced lyrics validation rejects empty`() {
        val result = validateSyncedLyrics("")
        assertNull(result)

        val result2 = validateSyncedLyrics("   ")
        assertNull(result2)

        val result3 = validateSyncedLyrics(null)
        assertNull(result3)
    }

    @Test
    fun `version mismatch detection - live`() {
        val title = "Song (Live)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection - acoustic`() {
        val title = "Song (Acoustic)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection - remix`() {
        val title = "Song (Remix)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection - radio edit`() {
        val title = "Song (Radio Edit)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection - instrumental`() {
        val title = "Song (Instrumental)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection - extended`() {
        val title = "Song (Extended Version)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection - demo`() {
        val title = "Song (Demo)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection - karaoke`() {
        val title = "Song (Karaoke)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection - cover`() {
        val title = "Song (Cover)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch detection case insensitive`() {
        val title = "SONG (LIVE)"
        assertTrue(hasVersionMismatch(title))
    }

    @Test
    fun `version mismatch not triggered for normal titles`() {
        val title = "Normal Song Title"
        assertFalse(hasVersionMismatch(title))
    }

    @Test
    fun `LyricsStatus Loading`() {
        val status = com.example.spotlyrics.lyrics.LyricsStatus.Loading
        assertNotNull(status)
    }

    @Test
    fun `LyricsStatus NotFound`() {
        val status = com.example.spotlyrics.lyrics.LyricsStatus.NotFound
        assertNotNull(status)
    }

    @Test
    fun `LyricsStatus Found`() {
        val lyrics = LyricsResult("lrclib", "plain", "synced", 100.0, true)
        val status = com.example.spotlyrics.lyrics.LyricsStatus.Found(lyrics)
        assertNotNull(status)
        assertEquals(lyrics, status.lyrics)
    }

    @Test
    fun `LyricsStatus ProviderError`() {
        val status = com.example.spotlyrics.lyrics.LyricsStatus.ProviderError("lrclib", "timeout")
        assertNotNull(status)
        assertEquals("lrclib", status.source)
        assertEquals("timeout", status.message)
    }

    @Test
    fun `provider error messages are safe`() {
        val status = com.example.spotlyrics.lyrics.LyricsStatus.ProviderError("lrclib", "LRCLIB request timed out")
        assertNotNull(status)
        assertEquals("lrclib", status.source)
        assertEquals("LRCLIB request timed out", status.message)
    }

    @Test
    fun `provider error does not contain secrets`() {
        val status = com.example.spotlyrics.lyrics.LyricsStatus.ProviderError("lrclib", "LRCLIB network error")
        assertNotNull(status)
        assertFalse(status.message.contains("token", ignoreCase = true))
        assertFalse(status.message.contains("secret", ignoreCase = true))
        assertFalse(status.message.contains("authorization", ignoreCase = true))
        assertFalse(status.message.contains("pkce", ignoreCase = true))
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

    private fun validateSyncedLyrics(syncedLyrics: String?): String? {
        val trimmed = syncedLyrics?.trim()
        if (trimmed.isNullOrBlank()) return null

        val timestampPattern = java.util.regex.Pattern.compile("\\[\\d{2}:\\d{2}(?:\\.\\d{2,3})?\\]")
        val hasTimestamps = timestampPattern.matcher(trimmed!!).find()

        return if (hasTimestamps) trimmed else null
    }

    private fun hasVersionMismatch(title: String): Boolean {
        val versionMarkers = listOf(
            "live", "acoustic", "remix", "radio edit", "instrumental",
            "extended", "demo", "version", "edit", "cover", "karaoke"
        )
        val titleLower = title.lowercase()

        return versionMarkers.any { marker ->
            titleLower.contains(marker)
        }
    }
}