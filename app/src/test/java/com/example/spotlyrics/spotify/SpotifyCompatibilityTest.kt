package com.example.spotlyrics.spotify

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpotifyCompatibilityTest {

    private lateinit var spotifyManager: SpotifyManager

    @Before
    fun setUp() {
        spotifyManager = SpotifyManager.getInstance()
        spotifyManager.disconnect()
    }

    @Test
    fun testSelfTestFailsWhenDisconnected() = runTest {
        val result = spotifyManager.runSelfTest()
        assertFalse(result.success)
        assertFalse(result.connectionOk)
        assertTrue(result.message.contains("disconnected", ignoreCase = true))
    }

    @Test
    fun testTrackMapperHandlesMalformedStateGracefully() {
        val emptyTrackState = SpotifyTrackMapper.mapPlayerState(null)
        assertNull(emptyTrackState)
    }

    @Test
    fun testTrackMapperWithValidData() {
        val track = SpotifyTrack(
            id = "track_123",
            name = "Test Track",
            artistName = "Test Artist",
            albumName = "Test Album",
            imageUri = "spotify:image:123",
            durationMs = 200000L
        )
        val state = SpotifyPlayerState(
            track = track,
            isPlaying = true,
            playbackPositionMs = 50000L,
            durationMs = 200000L
        )
        assertEquals("track_123", state.track?.id)
        assertTrue(state.isPlaying)
    }

    @Test
    fun testHasTrackChangedDetection() {
        val track1 = SpotifyTrack("id1", "Title", "Artist", "Album", null, 180000L)
        val track2 = SpotifyTrack("id2", "Title 2", "Artist 2", "Album 2", null, 180000L)

        val state1 = SpotifyPlayerState(track1, true, 1000L, 180000L)
        val state2 = SpotifyPlayerState(track2, true, 2000L, 180000L)
        val state1Duplicate = SpotifyPlayerState(track1, false, 5000L, 180000L)

        assertTrue(SpotifyTrackMapper.hasTrackChanged(state1, state2))
        assertFalse(SpotifyTrackMapper.hasTrackChanged(state1, state1Duplicate))
    }

    @Test
    fun testSpotifyFailuresDoNotCrashApplication() {
        try {
            spotifyManager.play()
            spotifyManager.pause()
            spotifyManager.skipNext()
            spotifyManager.skipPrevious()
            // Should execute without throwing any exception even when disconnected
        } catch (e: Exception) {
            fail("Spotify operations should not throw when disconnected: ${e.message}")
        }
    }
}
