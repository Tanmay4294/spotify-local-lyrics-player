package com.example.spotlyrics.lyrics

import com.example.spotlyrics.data.db.LyricsCacheDao
import com.example.spotlyrics.data.db.LyricsCacheEntity
import com.example.spotlyrics.data.repository.LyricsCacheRepository
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class LyricsManagerTest {

    private val provider = TestLyricsProvider()
    private val fakeDao = FakeLyricsCacheDao()
    private val cacheRepository = LyricsCacheRepository(fakeDao)

    @Test
    fun newTrackEmitsLoadingThenFoundWhenLyricsAvailable() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
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
    fun newTrackEmitsLoadingThenNotFoundWhenNoLyrics() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
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
    fun providerExceptionResultsInProviderError() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
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
    fun staleResultProtectionTrackAResultIgnoredWhenTrackBArrivesFirst() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
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
    fun duplicateSameTrackDoesNotCreateDuplicateRequests() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
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
    fun nullTrackCancelsActiveRequestAndShowsNotFound() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
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
    fun noCurrentTrackShowsNotFound() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.NotFound)
    }

    @Test
    fun cacheHitReturnsCachedLyricsWithoutCallingProvider() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
        
        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")
        
        val cacheKey = com.example.spotlyrics.lyrics.util.LyricsCacheKey.generate(track)
        fakeDao.insertOrReplace(LyricsCacheEntity(
            cacheKey = cacheKey,
            title = track.name,
            artist = track.artistName,
            album = track.albumName,
            durationSeconds = 100.0,
            source = "cache",
            plainLyrics = "Cached lyrics",
            syncedLyrics = null,
            fetchedAt = System.currentTimeMillis()
        ))

        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        val found = finalStatus as LyricsStatus.Found
        assertEquals("Cached lyrics", found.lyrics.plainText)
        assertEquals("cache", found.lyrics.source)
        // Provider should NOT have been called
        assertEquals(0, provider.requestCount)
    }

    @Test
    fun cacheMissThenProviderSuccessSavesToCache() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
        provider.setNextResult(LyricsResult(
            source = "lrclib",
            plainText = "Provider lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))

        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")

        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        val found = finalStatus as LyricsStatus.Found
        assertEquals("Provider lyrics", found.lyrics.plainText)
        
        // Verify it was cached
        val cached = cacheRepository.getCachedLyrics(track)
        assertNotNull(cached)
        assertEquals("Provider lyrics", cached?.plainText)
    }

    @Test
    fun cacheMissProviderReturnsNullReturnsNotFound() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
        provider.setNextResult(null)

        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")

        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.NotFound)
        // Nothing should be cached
        val cached = cacheRepository.getCachedLyrics(track)
        assertNull(cached)
    }

    @Test
    fun invalidCachedEntryTriggersProviderLookup() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
        provider.setNextResult(LyricsResult(
            source = "lrclib",
            plainText = "Provider lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))

        val track = createTrack(id = "1", name = "Test Track", artist = "Test Artist")
        
        val cacheKey = com.example.spotlyrics.lyrics.util.LyricsCacheKey.generate(track)
        fakeDao.insertOrReplace(LyricsCacheEntity(
            cacheKey = cacheKey,
            title = track.name,
            artist = track.artistName,
            album = track.albumName,
            durationSeconds = 100.0,
            source = "cache",
            plainLyrics = "",
            syncedLyrics = "",
            fetchedAt = System.currentTimeMillis()
        ))

        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        // Should fall back to provider
        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        val found = finalStatus as LyricsStatus.Found
        assertEquals("Provider lyrics", found.lyrics.plainText)
        assertEquals("lrclib", found.lyrics.source)
        assertEquals(1, provider.requestCount)
    }

    @Test
    fun staleCacheResultProtectionTrackACacheResultIgnoredWhenTrackBArrives() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
        
        val trackA = createTrack(id = "A", name = "Track A", artist = "Artist")
        val trackB = createTrack(id = "B", name = "Track B", artist = "Artist")
        
        val cacheKeyA = com.example.spotlyrics.lyrics.util.LyricsCacheKey.generate(trackA)
        fakeDao.insertOrReplace(LyricsCacheEntity(
            cacheKey = cacheKeyA,
            title = trackA.name,
            artist = trackA.artistName,
            album = trackA.albumName,
            durationSeconds = 100.0,
            source = "cache",
            plainLyrics = "Track A cached lyrics",
            syncedLyrics = null,
            fetchedAt = System.currentTimeMillis()
        ))

        // Start track A
        lyricsManager.onTrackChanged(trackA)

        // Switch to track B before A completes
        lyricsManager.onTrackChanged(trackB)
        advanceUntilIdle()

        // Track B should not get Track A's cached result
        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.NotFound)
    }

    @Test
    fun staleProviderResultProtectionTrackAProviderResultIgnoredWhenTrackBArrives() = runTest {
        val lyricsManager = LyricsManager(provider, cacheRepository)
        provider.setNextResult(LyricsResult(
            source = "lrclib",
            plainText = "Track A provider lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))
        provider.setNextResult(LyricsResult(
            source = "lrclib",
            plainText = "Track B provider lyrics",
            syncedText = null,
            durationSeconds = 100.0,
            found = true
        ))

        val trackA = createTrack(id = "A", name = "Track A", artist = "Artist")
        val trackB = createTrack(id = "B", name = "Track B", artist = "Artist")

        // Start track A provider lookup
        lyricsManager.onTrackChanged(trackA)

        // Switch to track B before A completes
        lyricsManager.onTrackChanged(trackB)
        advanceUntilIdle()

        // Track B should complete first
        advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        val found = finalStatus as LyricsStatus.Found
        assertEquals("Track B provider lyrics", found.lyrics.plainText)
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

    class FakeLyricsCacheDao : LyricsCacheDao {
        private val entities = mutableMapOf<String, LyricsCacheEntity>()

        override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> {
            return flowOf(entities[cacheKey])
        }

        override suspend fun insertOrReplace(entity: LyricsCacheEntity) {
            entities[entity.cacheKey] = entity
        }
    }
}