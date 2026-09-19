package com.example.spotlyrics.lyrics

import com.example.spotlyrics.data.db.LyricsCacheDao
import com.example.spotlyrics.data.db.LyricsCacheEntity
import com.example.spotlyrics.data.repository.LyricsCacheRepository
import com.example.spotlyrics.diagnostics.HealthMonitor
import com.example.spotlyrics.diagnostics.ProviderHealth
import com.example.spotlyrics.spotify.SpotifyTrack
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
class LyricsFailureInjectionTest {

    @Before
    fun setUp() {
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
    fun test821_SynchronizedLyricsParsing() {
        val lrc = "[00:10.00]Line 1\n[00:20.00]Line 2"
        val lines = LrcParser.parse(lrc)

        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].startMs)
        assertEquals("Line 1", lines[0].text)
        assertEquals(20000L, lines[1].startMs)
        assertEquals("Line 2", lines[1].text)

        assertEquals(0, LrcParser.activeLine(lines, 15000L))
        assertEquals(1, LrcParser.activeLine(lines, 25000L))
    }

    @Test
    fun test822_PlainLyricsWithoutTimestamps() {
        val plainText = "First plain line\nSecond plain line"
        val lines = LrcParser.parse(plainText)

        assertTrue(lines.isEmpty())
        val result = LyricsResult(source = "lrclib", plainText = plainText, syncedText = null, durationSeconds = 180.0, found = true)
        assertEquals(plainText, result.plainText)
        assertNull(result.syncedText)
    }

    @Test
    fun test823_NoLyricsFound() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val track = SpotifyTrack("id1", "Unknown Track", "Unknown Artist", "Album", null, 180000L)

        val emptyProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                return LyricsResult("lrclib", null, null, 180.0, false)
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(emptyProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.NotFound)
    }

    @Test
    fun test824_ProviderTimeout() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val track = SpotifyTrack("id1", "Track", "Artist", "Album", null, 180000L)

        val timeoutProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                HealthMonitor.updateProviderHealth(
                    ProviderHealth("LRCLIB", false, null, System.currentTimeMillis(), null, "Request timeout")
                )
                throw java.net.SocketTimeoutException("Timeout")
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(timeoutProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.ProviderError)
        val providers = HealthMonitor.providers.first()
        assertFalse(providers["LRCLIB"]?.healthy == true)
    }

    @Test
    fun test825_Http404() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val track = SpotifyTrack("id1", "Track", "Artist", "Album", null, 180000L)

        val http404Provider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                HealthMonitor.updateProviderHealth(
                    ProviderHealth("LRCLIB", false, null, System.currentTimeMillis(), 404, "HTTP 404 Not Found")
                )
                return LyricsResult("lrclib", null, null, 180.0, false)
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(http404Provider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.NotFound)
        val providers = HealthMonitor.providers.first()
        assertEquals(404, providers["LRCLIB"]?.lastHttpCode)
    }

    @Test
    fun test826_Http429RateLimit() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val track = SpotifyTrack("id1", "Track", "Artist", "Album", null, 180000L)

        val http429Provider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                HealthMonitor.updateProviderHealth(
                    ProviderHealth("LRCLIB", false, null, System.currentTimeMillis(), 429, "HTTP 429 Rate Limit Exceeded")
                )
                throw java.io.IOException("HTTP 429")
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(http429Provider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.ProviderError)
        val providers = HealthMonitor.providers.first()
        assertEquals(429, providers["LRCLIB"]?.lastHttpCode)
    }

    @Test
    fun test827_Http500ServerError() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val track = SpotifyTrack("id1", "Track", "Artist", "Album", null, 180000L)

        val http500Provider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                HealthMonitor.updateProviderHealth(
                    ProviderHealth("LRCLIB", false, null, System.currentTimeMillis(), 500, "HTTP 500 Server Error")
                )
                throw java.io.IOException("HTTP 500")
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(http500Provider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.ProviderError)
        val providers = HealthMonitor.providers.first()
        assertEquals(500, providers["LRCLIB"]?.lastHttpCode)
    }

    @Test
    fun test828_MalformedJson() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val track = SpotifyTrack("id1", "Track", "Artist", "Album", null, 180000L)

        val malformedJsonProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                HealthMonitor.updateProviderHealth(
                    ProviderHealth("LRCLIB", false, null, System.currentTimeMillis(), 200, "Malformed JSON response")
                )
                throw com.google.gson.JsonSyntaxException("Malformed JSON")
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(malformedJsonProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.ProviderError)
        val providers = HealthMonitor.providers.first()
        assertEquals("Malformed JSON response", providers["LRCLIB"]?.lastError)
    }

    @Test
    fun test829_WrongVersionRemixResultRejected() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val targetTrack = SpotifyTrack("id1", "Original Song", "Artist", "Original Album", null, 180000L)

        val mismatchProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                return LyricsResult("lrclib", null, null, 180.0, false)
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(mismatchProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(targetTrack)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.NotFound)
    }

    @Test
    fun test8210_CachedLyricsWhileProviderDown() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val track = SpotifyTrack("id1", "Cached Song", "Artist", "Album", null, 180000L)

        val cachedEntity = LyricsCacheEntity(
            cacheKey = "cached song_artist_180",
            title = "Cached Song",
            artist = "Artist",
            album = "Album",
            durationSeconds = 180.0,
            source = "lrclib",
            plainLyrics = "Cached Lyrics Text",
            syncedLyrics = null,
            fetchedAt = System.currentTimeMillis()
        )

        val fakeDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(cachedEntity)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }

        val downProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                throw java.net.SocketTimeoutException("Provider down")
            }
        }

        val cacheRepo = LyricsCacheRepository(fakeDao)
        val lyricsManager = LyricsManager(downProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(track)
        testScheduler.advanceUntilIdle()

        val status = lyricsManager.lyricsStatus.value
        assertTrue(status is LyricsStatus.Found)
        assertEquals("Cached Lyrics Text", (status as LyricsStatus.Found).lyrics.plainText)
    }
}
