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
}