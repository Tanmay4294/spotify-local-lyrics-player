package com.example.spotlyrics.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTrackMapperTest {

    @Test
    fun nullTrackMapsToNull() {
        assertNull(SpotifyTrackMapper.mapTrack(null))
    }

    @Test
    fun nullPlayerStateMapsToNull() {
        assertNull(SpotifyTrackMapper.mapPlayerState(null))
    }

    @Test
    fun trackChangeDetectionWithSameTrackIdReturnsFalse() {
        val track1 = SpotifyTrack("track123", "Song A", "Artist A", "Album A", null, 180000L)
        val track2 = SpotifyTrack("track123", "Song A", "Artist A", "Album A", null, 180000L)
        val state1 = SpotifyPlayerState(track1, true, 1000L, 180000L)
        val state2 = SpotifyPlayerState(track2, true, 5000L, 180000L)

        assertFalse(SpotifyTrackMapper.hasTrackChanged(state1, state2))
    }

    @Test
    fun trackChangeDetectionWithDifferentTrackIdReturnsTrue() {
        val track1 = SpotifyTrack("track123", "Song A", "Artist A", "Album A", null, 180000L)
        val track2 = SpotifyTrack("track456", "Song B", "Artist B", "Album B", null, 200000L)
        val state1 = SpotifyPlayerState(track1, true, 1000L, 180000L)
        val state2 = SpotifyPlayerState(track2, true, 0L, 200000L)

        assertTrue(SpotifyTrackMapper.hasTrackChanged(state1, state2))
    }

    @Test
    fun trackChangeDetectionFromNullTrackReturnsTrue() {
        val track2 = SpotifyTrack("track456", "Song B", "Artist B", "Album B", null, 200000L)
        val state1 = SpotifyPlayerState(null, false, 0L, 0L)
        val state2 = SpotifyPlayerState(track2, true, 0L, 200000L)

        assertTrue(SpotifyTrackMapper.hasTrackChanged(state1, state2))
    }
}
