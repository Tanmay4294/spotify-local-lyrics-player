package com.example.spotlyrics.lyrics

import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Test

class LyricsManagerTest {

    private val provider = TestLyricsProvider()

    @Test
    fun `new track emits loading then found when lyrics available`() = runTest {
        val testScope = this as CoroutineScope
        val lyricsManager = LyricsManager(provider, testScope)
        provider.setNextResult(LyricsResult(
            source = "test",
            plainText = "Test lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))

        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")

        lyricsManager.onTrackChanged(track)

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Loading)

        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        val found = finalStatus as LyricsStatus.Found
        assertEquals("Test lyrics", found.lyrics.plainText)
    }

    @Test
    fun `new track emits loading then not found when no lyrics`() = runTest {
        val testScope = this as CoroutineScope
        val lyricsManager = LyricsManager(provider, testScope)
        provider.setNextResult(null)

        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")

        lyricsManager.onTrackChanged(track)

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Loading)

        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.NotFound)
    }

    @Test
    fun `provider exception results in ProviderError`() = runTest {
        val testScope = this as CoroutineScope
        val lyricsManager = LyricsManager(provider, testScope)
        provider.setNextException(Exception("Network error"))

        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")

        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.ProviderError)
        val error = finalStatus as LyricsStatus.ProviderError
        assertEquals("lyrics_provider", error.source)
        assertTrue(error.message.contains("Network error"))
    }

    @Test
    fun `stale result protection - track A result ignored when track B arrives first`() = runTest {
        val testScope = this as CoroutineScope
        val lyricsManager = LyricsManager(provider, testScope)
        provider.setNextResult(LyricsResult(
            source = "test",
            plainText = "Track A lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))
        provider.setNextResult(LyricsResult(
            source = "test",
            plainText = "Track B lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))

        val trackA = createTrack(id = "A", name = "Track A", artist = "Artist")
        val trackB = createTrack(id = "B", name = "Track B", artist = "Artist")

        // Start track A lookup
        lyricsManager.onTrackChanged(trackA)

        // Switch to track B before A completes
        lyricsManager.onTrackChanged(trackB)
        advanceUntilIdle()

        // Track B should complete first (its result is set as next)
        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        val found = finalStatus as LyricsStatus.Found
        assertEquals("Track B lyrics", found.lyrics.plainText)
    }

    @Test
    fun `duplicate same track does not create duplicate requests`() = runTest {
        val testScope = this as CoroutineScope
        val lyricsManager = LyricsManager(provider, testScope)
        provider.setNextResult(LyricsResult(
            source = "test",
            plainText = "Test lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))

        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")

        lyricsManager.onTrackChanged(track)

        // Call again with same track
        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        // Should only have made one request
        assertEquals(1, provider.requestCount)
    }

    @Test
    fun `null track cancels active request and shows NotFound`() = runTest {
        val testScope = this as CoroutineScope
        val lyricsManager = LyricsManager(provider, testScope)
        provider.setNextResult(LyricsResult(
            source = "test",
            plainText = "Test lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))

        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")

        lyricsManager.onTrackChanged(track)

        // Then null track
        lyricsManager.onTrackChanged(null)
        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.NotFound)
    }

    @Test
    fun `no current track shows NotFound`() = runTest {
        val testScope = this as CoroutineScope
        val lyricsManager = LyricsManager(provider, testScope)
        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.NotFound)
    }

    private fun createTrack(
        id: String,
        name: String,
        artist: String,
        album: String? = "Test Album",
        durationMs: Long = 240000
    ): SpotifyTrack {
        return SpotifyTrack(
            id = id,
            name = name,
            artistName = artist,
            albumName = album,
            imageUri = null,
            durationMs = durationMs
        )
    }

    class TestLyricsProvider : LyricsProvider {
        private var nextResult: LyricsResult? = null
        private var nextException: Exception? = null
        var requestCount = 0

        fun setNextResult(result: LyricsResult?) {
            nextResult = result
            nextException = null
        }

        fun setNextException(exception: Exception) {
            nextException = exception
            nextResult = null
        }

        override suspend fun search(
            title: String,
            artist: String,
            album: String?,
            durationSeconds: Double?
        ): LyricsResult? {
            requestCount++
            if (nextException != null) {
                throw nextException!!
            }
            return nextResult
        }
    }
}