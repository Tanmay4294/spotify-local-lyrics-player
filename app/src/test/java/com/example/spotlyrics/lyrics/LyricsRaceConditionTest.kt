package com.example.spotlyrics.lyrics

import com.example.spotlyrics.data.db.LyricsCacheDao
import com.example.spotlyrics.data.db.LyricsCacheEntity
import com.example.spotlyrics.data.repository.LyricsCacheRepository
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsRaceConditionTest {

    @Test
    fun testRaceConditionOrder_B_then_A_then_C() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val trackA = SpotifyTrack("track_A", "Song A", "Artist A", "Album A", null, 180000L)
        val trackB = SpotifyTrack("track_B", "Song B", "Artist B", "Album B", null, 180000L)
        val trackC = SpotifyTrack("track_C", "Song C", "Artist C", "Album C", null, 180000L)

        val deferredA = CompletableDeferred<LyricsResult>()
        val deferredB = CompletableDeferred<LyricsResult>()
        val deferredC = CompletableDeferred<LyricsResult>()

        val controlledProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                return when (title) {
                    "Song A" -> deferredA.await()
                    "Song B" -> deferredB.await()
                    "Song C" -> deferredC.await()
                    else -> LyricsResult("lrclib", null, null, 180.0, false)
                }
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(controlledProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(trackA)
        lyricsManager.onTrackChanged(trackB)
        lyricsManager.onTrackChanged(trackC)

        deferredB.complete(LyricsResult("lrclib", "Lyrics for B", null, 180.0, true))
        testScheduler.advanceUntilIdle()
        assertFalse((lyricsManager.lyricsStatus.value as? LyricsStatus.Found)?.lyrics?.plainText == "Lyrics for B")

        deferredA.complete(LyricsResult("lrclib", "Lyrics for A", null, 180.0, true))
        testScheduler.advanceUntilIdle()
        assertFalse((lyricsManager.lyricsStatus.value as? LyricsStatus.Found)?.lyrics?.plainText == "Lyrics for A")

        deferredC.complete(LyricsResult("lrclib", "Lyrics for C", null, 180.0, true))
        testScheduler.advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        assertEquals("Lyrics for C", (finalStatus as LyricsStatus.Found).lyrics.plainText)
    }

    @Test
    fun testRaceConditionOrder_C_then_A_then_B() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val trackA = SpotifyTrack("track_A", "Song A", "Artist A", "Album A", null, 180000L)
        val trackB = SpotifyTrack("track_B", "Song B", "Artist B", "Album B", null, 180000L)
        val trackC = SpotifyTrack("track_C", "Song C", "Artist C", "Album C", null, 180000L)

        val deferredA = CompletableDeferred<LyricsResult>()
        val deferredB = CompletableDeferred<LyricsResult>()
        val deferredC = CompletableDeferred<LyricsResult>()

        val controlledProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                return when (title) {
                    "Song A" -> deferredA.await()
                    "Song B" -> deferredB.await()
                    "Song C" -> deferredC.await()
                    else -> LyricsResult("lrclib", null, null, 180.0, false)
                }
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(controlledProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(trackA)
        lyricsManager.onTrackChanged(trackB)
        lyricsManager.onTrackChanged(trackC)

        deferredC.complete(LyricsResult("lrclib", "Lyrics for C", null, 180.0, true))
        testScheduler.advanceUntilIdle()
        assertEquals("Lyrics for C", (lyricsManager.lyricsStatus.value as LyricsStatus.Found).lyrics.plainText)

        deferredA.complete(LyricsResult("lrclib", "Lyrics for A", null, 180.0, true))
        testScheduler.advanceUntilIdle()
        assertEquals("Lyrics for C", (lyricsManager.lyricsStatus.value as LyricsStatus.Found).lyrics.plainText)

        deferredB.complete(LyricsResult("lrclib", "Lyrics for B", null, 180.0, true))
        testScheduler.advanceUntilIdle()
        assertEquals("Lyrics for C", (lyricsManager.lyricsStatus.value as LyricsStatus.Found).lyrics.plainText)
    }

    @Test
    fun testStaleFailuresDoNotOverwriteValidC() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val trackA = SpotifyTrack("track_A", "Song A", "Artist A", "Album A", null, 180000L)
        val trackC = SpotifyTrack("track_C", "Song C", "Artist C", "Album C", null, 180000L)

        val deferredA = CompletableDeferred<LyricsResult>()
        val deferredC = CompletableDeferred<LyricsResult>()

        val controlledProvider = object : LyricsProvider {
            override suspend fun search(title: String, artist: String, album: String?, durationSeconds: Double?): LyricsResult? {
                return when (title) {
                    "Song A" -> deferredA.await()
                    "Song C" -> deferredC.await()
                    else -> LyricsResult("lrclib", null, null, 180.0, false)
                }
            }
        }

        val emptyDao = object : LyricsCacheDao {
            override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = MutableStateFlow(null)
            override suspend fun insertOrReplace(entity: LyricsCacheEntity) {}
        }
        val cacheRepo = LyricsCacheRepository(emptyDao)
        val lyricsManager = LyricsManager(controlledProvider, cacheRepo, testDispatcher)

        lyricsManager.onTrackChanged(trackA)
        lyricsManager.onTrackChanged(trackC)

        deferredC.complete(LyricsResult("lrclib", "Lyrics for C", null, 180.0, true))
        testScheduler.advanceUntilIdle()

        deferredA.completeExceptionally(java.io.IOException("Stale error A"))
        testScheduler.advanceUntilIdle()

        val finalStatus = lyricsManager.lyricsStatus.value
        assertTrue(finalStatus is LyricsStatus.Found)
        assertEquals("Lyrics for C", (finalStatus as LyricsStatus.Found).lyrics.plainText)
    }
}
