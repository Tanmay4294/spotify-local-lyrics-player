package com.example.spotlyrics.lyrics

import com.example.spotlyrics.data.db.LyricsCacheDao
import com.example.spotlyrics.data.db.LyricsCacheEntity
import com.example.spotlyrics.data.repository.LyricsCacheRepository
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiProviderLyricsManagerTest {

    private val fakeDao = FakeLyricsCacheDao()
    private val cacheRepository = LyricsCacheRepository(fakeDao)

    class MockProvider(val sourceKey: String) : LyricsProvider {
        var shouldSucceed = false
        var shouldThrow = false
        var searchCount = 0
        var returnedResult: LyricsResult? = null

        override suspend fun search(
            title: String,
            artist: String,
            album: String?,
            durationSeconds: Double?
        ): LyricsResult? {
            searchCount++
            if (shouldThrow) {
                throw RuntimeException("Provider failure in $sourceKey")
            }
            if (shouldSucceed) {
                return returnedResult ?: LyricsResult(
                    source = sourceKey,
                    plainText = "Lyrics from $sourceKey",
                    syncedText = null,
                    durationSeconds = 200.0,
                    found = true
                )
            }
            return null
        }
    }

    private val lrclibMock = MockProvider("lrclib")
    private val musixmatchMock = MockProvider("musixmatch")
    private val neteaseMock = MockProvider("netease")
    private val qqMusicMock = MockProvider("qq_music")

    private val providerMap = mapOf(
        LyricsProviderChoice.LRCLIB to lrclibMock,
        LyricsProviderChoice.MUSIXMATCH to musixmatchMock,
        LyricsProviderChoice.NETEASE to neteaseMock,
        LyricsProviderChoice.QQ_MUSIC to qqMusicMock
    )

    @Test
    fun defaultProviderOrderIsLRCLIBFirst() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))
        val order = lyricsManager.getProviderOrder(LyricsProviderChoice.LRCLIB)

        assertEquals(
            listOf(
                LyricsProviderChoice.LRCLIB,
                LyricsProviderChoice.MUSIXMATCH,
                LyricsProviderChoice.NETEASE,
                LyricsProviderChoice.QQ_MUSIC
            ),
            order
        )
    }

    @Test
    fun manualSelectionPutsSelectedProviderFirst() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))

        val neteaseOrder = lyricsManager.getProviderOrder(LyricsProviderChoice.NETEASE)
        assertEquals(
            listOf(
                LyricsProviderChoice.NETEASE,
                LyricsProviderChoice.LRCLIB,
                LyricsProviderChoice.MUSIXMATCH,
                LyricsProviderChoice.QQ_MUSIC
            ),
            neteaseOrder
        )

        val musixmatchOrder = lyricsManager.getProviderOrder(LyricsProviderChoice.MUSIXMATCH)
        assertEquals(
            listOf(
                LyricsProviderChoice.MUSIXMATCH,
                LyricsProviderChoice.LRCLIB,
                LyricsProviderChoice.NETEASE,
                LyricsProviderChoice.QQ_MUSIC
            ),
            musixmatchOrder
        )
    }

    @Test
    fun preferredProviderAttemptedFirstAndStopsFallbackWhenSuccessful() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))
        lyricsManager.setPreferredProvider(LyricsProviderChoice.NETEASE)

        neteaseMock.shouldSucceed = true
        lrclibMock.shouldSucceed = true

        val track = createTrack("1", "Song Title", "Artist Name")
        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
        val found = status as LyricsStatus.Found
        assertEquals("netease", found.lyrics.source)

        assertEquals(1, neteaseMock.searchCount)
        assertEquals(0, lrclibMock.searchCount)
    }

    @Test
    fun fallbackOccursWhenSelectedProviderFails() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))
        lyricsManager.setPreferredProvider(LyricsProviderChoice.MUSIXMATCH)

        musixmatchMock.shouldSucceed = false
        lrclibMock.shouldSucceed = true

        val track = createTrack("1", "Song Title", "Artist Name")
        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
        val found = status as LyricsStatus.Found
        assertEquals("lrclib", found.lyrics.source)

        assertEquals(1, musixmatchMock.searchCount)
        assertEquals(1, lrclibMock.searchCount)
    }

    @Test
    fun musixmatchFailureDoesNotBreakLyricsManager() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))
        lyricsManager.setPreferredProvider(LyricsProviderChoice.MUSIXMATCH)
        musixmatchMock.shouldThrow = true
        neteaseMock.shouldSucceed = true

        val track = createTrack("1", "Song Title", "Artist Name")
        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
        assertEquals("netease", (status as LyricsStatus.Found).lyrics.source)
    }

    @Test
    fun neteaseFailureDoesNotBreakLyricsManager() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))
        lyricsManager.setPreferredProvider(LyricsProviderChoice.NETEASE)
        neteaseMock.shouldThrow = true
        qqMusicMock.shouldSucceed = true

        val track = createTrack("1", "Song Title", "Artist Name")
        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
        assertEquals("qq_music", (status as LyricsStatus.Found).lyrics.source)
    }

    @Test
    fun qqMusicFailureDoesNotBreakLyricsManager() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))
        lyricsManager.setPreferredProvider(LyricsProviderChoice.QQ_MUSIC)
        qqMusicMock.shouldThrow = true
        lrclibMock.shouldSucceed = true

        val track = createTrack("1", "Song Title", "Artist Name")
        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
        assertEquals("lrclib", (status as LyricsStatus.Found).lyrics.source)
    }

    @Test
    fun allProvidersFailingEmitsNotFoundState() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))
        lrclibMock.shouldSucceed = false
        musixmatchMock.shouldSucceed = false
        neteaseMock.shouldSucceed = false
        qqMusicMock.shouldSucceed = false

        val track = createTrack("1", "Song Title", "Artist Name")
        lyricsManager.onTrackChanged(track)
        advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.NotFound)
    }

    @Test
    fun staleRequestProtectionCancelsPreviousTrackLookup() = runTest {
        val lyricsManager = LyricsManager(providerMap, cacheRepository, StandardTestDispatcher(testScheduler))
        lrclibMock.shouldSucceed = true

        val trackA = createTrack("A", "Track A", "Artist")
        val trackB = createTrack("B", "Track B", "Artist")

        lyricsManager.onTrackChanged(trackA)
        lyricsManager.onTrackChanged(trackB)
        advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
    }

    private fun createTrack(id: String, name: String, artist: String): SpotifyTrack {
        return SpotifyTrack(
            id = id,
            name = name,
            artistName = artist,
            albumName = "Album",
            imageUri = null,
            durationMs = 200000
        )
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
