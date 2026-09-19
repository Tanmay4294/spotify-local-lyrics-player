package com.example.spotlyrics.diagnostics

import com.example.spotlyrics.data.db.LyricsCacheDao
import com.example.spotlyrics.data.db.LyricsCacheEntity
import com.example.spotlyrics.data.repository.LyricsCacheRepository
import com.example.spotlyrics.lyrics.LyricsResult
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HealthMonitorTest {

    @Before
    fun setUp() {
        // Clear HealthMonitor state before each test
        // HealthMonitor uses mutable maps; we reflectively clear them.
        val componentMapField = HealthMonitor::class.java.getDeclaredField("componentHealthMap")
        componentMapField.isAccessible = true
        (componentMapField.get(null) as java.util.concurrent.ConcurrentHashMap<*, *>).clear()
        val providerMapField = HealthMonitor::class.java.getDeclaredField("providerHealthMap")
        providerMapField.isAccessible = true
        (providerMapField.get(null) as java.util.concurrent.ConcurrentHashMap<*, *>).clear()
    }

    @Test
    fun testRecordSuccessSetsHealthyAndTimestamp() = runTest {
        HealthMonitor.recordSuccess("TestComponent", "All good")
        val comp = HealthMonitor.components.first()["TestComponent"]
        assertNotNull(comp)
        assertTrue(comp!!.healthy)
        assertNotNull(comp.lastSuccessAt)
        assertNull(comp.lastFailureAt)
        assertEquals("All good", comp.errorInfo)
    }

    @Test
    fun testRecordFailureSetsUnhealthyAndErrorInfo() = runTest {
        HealthMonitor.recordFailure("TestComponent", "Something broke")
        val comp = HealthMonitor.components.first()["TestComponent"]
        assertNotNull(comp)
        assertFalse(comp!!.healthy)
        assertNull(comp.lastSuccessAt)
        assertNotNull(comp.lastFailureAt)
        assertEquals("Something broke", comp.errorInfo)
    }

    @Test
    fun testSuccessThenFailureUpdatesFields() = runTest {
        HealthMonitor.recordSuccess("Comp", "Init")
        val first = HealthMonitor.components.first()["Comp"]!!
        val successTime = first.lastSuccessAt!!
        HealthMonitor.recordFailure("Comp", "Now fail")
        val after = HealthMonitor.components.first()["Comp"]!!
        assertFalse(after.healthy)
        assertEquals(successTime, after.lastSuccessAt) // should retain previous success timestamp
        assertNotNull(after.lastFailureAt)
        assertEquals("Now fail", after.errorInfo)
    }

    @Test
    fun testUpdateProviderHealthAddsEntry() = runTest {
        val ph = ProviderHealth(
            provider = "LRCLIB",
            healthy = true,
            lastSuccessAt = System.currentTimeMillis(),
            lastFailureAt = null,
            lastHttpCode = 200,
            lastError = null
        )
        HealthMonitor.updateProviderHealth(ph)
        val stored = HealthMonitor.providers.first()["LRCLIB"]
        assertNotNull(stored)
        assertTrue(stored!!.healthy)
        assertEquals(200, stored.lastHttpCode)
    }

    @Test
    fun testProviderHealthReplacedOnSecondUpdate() = runTest {
        val ph1 = ProviderHealth("LRCLIB", false, null, System.currentTimeMillis(), 404, "Not found")
        HealthMonitor.updateProviderHealth(ph1)
        val ph2 = ProviderHealth("LRCLIB", true, System.currentTimeMillis(), null, 200, null)
        HealthMonitor.updateProviderHealth(ph2)
        val stored = HealthMonitor.providers.first()["LRCLIB"]!!
        assertTrue(stored.healthy)
        assertEquals(200, stored.lastHttpCode)
        assertNull(stored.lastError)
    }

    // ---- LyricsCacheRepository health tests ----
    private class FakeSuccessDao : LyricsCacheDao {
        private val flow = MutableStateFlow<LyricsCacheEntity?>(null)
        override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> = flow
        override suspend fun insertOrReplace(entity: LyricsCacheEntity) {
            // No-op success
        }
    }

    private class FakeFailureDao : LyricsCacheDao {
        override fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?> {
            throw RuntimeException("DB read failure")
        }
        override suspend fun insertOrReplace(entity: LyricsCacheEntity) {
            throw RuntimeException("DB write failure")
        }
    }

    @Test
    fun testCacheRepositoryReadSuccessUpdatesHealth() = runTest {
        val repo = LyricsCacheRepository(FakeSuccessDao())
        // Use a dummy track
        val track = SpotifyTrack("id", "name", "artist", "album", null, 180000L)
        repo.getCachedLyrics(track)
        val comp = HealthMonitor.components.first()["CacheDB"]
        assertNotNull(comp)
        assertTrue(comp!!.healthy)
    }

    @Test
    fun testCacheRepositoryReadFailureUpdatesHealth() = runTest {
        val repo = LyricsCacheRepository(FakeFailureDao())
        val track = SpotifyTrack("id", "name", "artist", "album", null, 180000L)
        repo.getCachedLyrics(track)
        val comp = HealthMonitor.components.first()["CacheDB"]
        assertNotNull(comp)
        assertFalse(comp!!.healthy)
        assertTrue(comp.errorInfo!!.contains("Read failure"))
    }

    @Test
    fun testCacheRepositoryWriteSuccessUpdatesHealth() = runTest {
        val repo = LyricsCacheRepository(FakeSuccessDao())
        val track = SpotifyTrack("id", "name", "artist", "album", null, 180000L)
        val result = LyricsResult(source = "test", plainText = "abc", syncedText = null, durationSeconds = 180.0, found = true)
        repo.saveLyrics(track, result)
        val comp = HealthMonitor.components.first()["CacheDB"]
        assertNotNull(comp)
        assertTrue(comp!!.healthy)
    }

    @Test
    fun testCacheRepositoryWriteFailureUpdatesHealth() = runTest {
        val repo = LyricsCacheRepository(FakeFailureDao())
        val track = SpotifyTrack("id", "name", "artist", "album", null, 180000L)
        val result = LyricsResult(source = "test", plainText = "abc", syncedText = null, durationSeconds = 180.0, found = true)
        repo.saveLyrics(track, result)
        val comp = HealthMonitor.components.first()["CacheDB"]
        assertNotNull(comp)
        assertFalse(comp!!.healthy)
        assertTrue(comp.errorInfo!!.contains("Write failure"))
    }

    // ---- NetworkMonitor tests ----
    @Test
    fun testNetworkMonitorIsNetworkAvailableDoesNotThrow() {
        // This test simply ensures the method can be called without exception.
        // The actual result depends on the environment.
        NetworkMonitor.isNetworkAvailable()
    }

    @Test
    fun testNetworkMonitorRecordNetworkStatusUpdatesHealth() = runTest {
        // Call the method; it will record success or failure.
        NetworkMonitor.recordNetworkStatus()
        val comp = HealthMonitor.components.first()["Network"]
        assertNotNull(comp)
        // The health may be true or false depending on actual connectivity; just ensure a record exists.
    }
}
