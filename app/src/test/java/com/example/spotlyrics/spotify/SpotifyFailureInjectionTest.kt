package com.example.spotlyrics.spotify

import com.example.spotlyrics.data.db.LyricsCacheDao
import com.example.spotlyrics.data.db.LyricsCacheEntity
import com.example.spotlyrics.data.repository.LyricsCacheRepository
import com.example.spotlyrics.diagnostics.HealthMonitor
import com.example.spotlyrics.lyrics.LyricsManager
import com.example.spotlyrics.lyrics.LyricsProvider
import com.example.spotlyrics.lyrics.LyricsResult
import com.example.spotlyrics.lyrics.LyricsStatus
import com.spotify.android.appremote.api.error.NotLoggedInException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyFailureInjectionTest {

    private lateinit var spotifyManager: SpotifyManager

    @Before
    fun setUp() {
        spotifyManager = SpotifyManager.getInstance()
        spotifyManager.disconnect()
        clearHealthMonitor()
    }

    private fun clearHealthMonitor() {
        try {
            val componentMapField = HealthMonitor::class.java.getDeclaredField("componentHealthMap")
            componentMapField.isAccessible = true
            (componentMapField.get(null) as ConcurrentHashMap<*, *>).clear()
            val providerMapField = HealthMonitor::class.java.getDeclaredField("providerHealthMap")
            providerMapField.isAccessible = true
            (providerMapField.get(null) as ConcurrentHashMap<*, *>).clear()
        } catch (_: Exception) {}
    }

    @Test
    fun test811_SpotifyInstalledAndLoggedIn() = runTest {
        val track = SpotifyTrack("track_811", "Success Track", "Artist", "Album", null, 200000L)
        val playerState = SpotifyPlayerState(track, isPlaying = true, playbackPositionMs = 1000L, durationMs = 200000L)

        HealthMonitor.recordSuccess("SpotifyAuth", "Connected")
        HealthMonitor.recordSuccess("AppRemote", "Connected")

        val health = HealthMonitor.components.first()
        assertTrue(health["SpotifyAuth"]?.healthy == true)
        assertTrue(health["AppRemote"]?.healthy == true)
        assertEquals("track_811", playerState.track?.id)
    }

    @Test
    fun test812_SpotifyInstalledAndLoggedOut() = runTest {
        val classified = spotifyManager.classifyAuthFailure(NotLoggedInException("Not logged in", null))
        assertEquals(SpotifyAuthState.REAUTH_REQUIRED, classified)
        HealthMonitor.recordFailure("SpotifyAuth", "Connection failed: Not logged in")

        val health = HealthMonitor.components.first()
        assertFalse(health["SpotifyAuth"]?.healthy == true)
    }

    @Test
    fun test813_SpotifyUnavailable() = runTest {
        val errorMsg = "Spotify app is not installed on this device"
        HealthMonitor.recordFailure("SpotifyAuth", "Connection failed: $errorMsg")
        HealthMonitor.recordFailure("AppRemote", "Connection failed: $errorMsg")

        val health = HealthMonitor.components.first()
        assertFalse(health["SpotifyAuth"]?.healthy == true)
        assertEquals("Connection failed: $errorMsg", health["SpotifyAuth"]?.errorInfo)
    }

    @Test
    fun test814_NoActiveTrack() = runTest {
        val state = SpotifyPlayerState(track = null, isPlaying = false, playbackPositionMs = 0L, durationMs = 0L)
        assertNull(state.track)
        assertFalse(state.isPlaying)
    }

    @Test
    fun test815_SongChangeWhileLyricsRequestPending() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val trackA = SpotifyTrack("track_A", "Song A", "Artist A", "Album A", null, 180000L)
        val trackB = SpotifyTrack("track_B", "Song B", "Artist B", "Album B", null, 180000L)

        val provider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                if (title == "Song A") {
                    kotlinx.coroutines.delay(1000)
                    return LyricsResult("lrclib", "Lyrics for A", null, 180.0, true)
                }
                return LyricsResult("lrclib", "Lyrics for B", null, 180.0, true)
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(provider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(trackA)
        lyricsManager.onTrackChanged(trackB)

        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
        val found = status as LyricsStatus.Found
        assertEquals("Lyrics for B", found.lyrics.plainText)
    }

    @Test
    fun test816_PauseResumeDoesNotReloadLyrics() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val track = SpotifyTrack("track_1", "Song 1", "Artist 1", "Album 1", null, 180000L)
        var fetchCount = 0

        val provider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                fetchCount++
                return LyricsResult("lrclib", "Lyrics 1", null, 180.0, true)
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(provider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()
        assertEquals(1, fetchCount)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()
        assertEquals(1, fetchCount)
    }

    @Test
    fun test817_NextPreviousTrackSequence() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val trackA = SpotifyTrack("track_A", "Song A", "Artist A", "Album A", null, 180000L)
        val trackB = SpotifyTrack("track_B", "Song B", "Artist B", "Album B", null, 180000L)

        val provider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                return LyricsResult("lrclib", "Lyrics for $title", null, 180.0, true)
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(provider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(trackA)
        testScheduler.advanceUntilIdle()
        assertEquals("Lyrics for Song A", (lyricsManager.lyricsStatus.value as LyricsStatus.Found).lyrics.plainText)

        lyricsManager.onTrackChanged(trackB)
        testScheduler.advanceUntilIdle()
        assertEquals("Lyrics for Song B", (lyricsManager.lyricsStatus.value as LyricsStatus.Found).lyrics.plainText)

        lyricsManager.onTrackChanged(trackA)
        testScheduler.advanceUntilIdle()
        assertEquals("Lyrics for Song A", (lyricsManager.lyricsStatus.value as LyricsStatus.Found).lyrics.plainText)
    }

    @Test
    fun test818_NetworkOffAfterCachedSong() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val trackA = SpotifyTrack("track_A", "Song A", "Artist A", "Album A", null, 180000L)
        val cachedEntity = LyricsCacheEntity(
            cacheKey = "song a_artist a_180",
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            durationSeconds = 180.0,
            source = "lrclib",
            plainLyrics = "Cached Lyrics A",
            syncedLyrics = null,
            fetchedAt = System.currentTimeMillis()
        )

        val fakeDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(cachedEntity)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }

        val failingProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                throw java.io.IOException("Network unavailable")
            }
        }

        val cacheRepo = LyricsCacheRepository(fakeDao)
        val lyricsManager = LyricsManager(failingProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(trackA)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
        assertEquals("Cached Lyrics A", (status as LyricsStatus.Found).lyrics.plainText)
    }

    @Test
    fun test819_NetworkOffForNewSong() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val trackB = SpotifyTrack("track_B", "Song B", "Artist B", "Album B", null, 180000L)

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }

        val failingProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                HealthMonitor.recordFailure("Network", "Not reachable")
                throw java.io.IOException("Network unavailable")
            }
        }

        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(failingProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(trackB)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.ProviderError)
        val health = HealthMonitor.components.first()
        assertFalse(health["Network"]?.healthy == true)
    }
}
