package com.example.spotlyrics.lyrics.util

import com.example.spotlyrics.spotify.SpotifyTrack
import org.junit.Assert.*
import org.junit.Test

class LyricsCacheKeyTest {

    @Test
    fun `spotify ID available - same ID produces same key`() {
        val track1 = SpotifyTrack(id = "spotify:track:123", name = "Test", artistName = "Artist", albumName = null, imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = "spotify:track:123", name = "Different Title", artistName = "Different Artist", albumName = "Different Album", imageUri = null, durationMs = 300000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertEquals(key1, key2)
        assertTrue(key1.startsWith("spotify:"))
    }

    @Test
    fun `spotify ID available - different IDs produce different keys`() {
        val track1 = SpotifyTrack(id = "spotify:track:123", name = "Test", artistName = "Artist", albumName = null, imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = "spotify:track:456", name = "Test", artistName = "Artist", albumName = null, imageUri = null, durationMs = 240000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `no spotify ID - same normalized title artist album duration produces same key`() {
        val track1 = SpotifyTrack(id = null, name = "  Test Track  ", artistName = "  Test Artist  ", albumName = "  Test Album  ", imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "test track", artistName = "test artist", albumName = "test album", imageUri = null, durationMs = 240000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertEquals(key1, key2)
        assertTrue(key1.startsWith("composite:"))
    }

    @Test
    fun `normalization - surrounding whitespace does not change fallback key`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "  Test Track  ", artistName = "  Test Artist  ", albumName = "  Test Album  ", imageUri = null, durationMs = 240000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertEquals(key1, key2)
    }

    @Test
    fun `normalization case differences do not change fallback key`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "TEST TRACK", artistName = "TEST ARTIST", albumName = "TEST ALBUM", imageUri = null, durationMs = 240000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertEquals(key1, key2)
    }

    @Test
    fun `normalization - repeated whitespace does not change fallback key`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "Test   Track", artistName = "Test   Artist", albumName = "Test   Album", imageUri = null, durationMs = 240000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertEquals(key1, key2)
    }

    @Test
    fun `album - different albums produce different fallback keys when available`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Album One", imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Album Two", imageUri = null, durationMs = 240000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `duration - rounded deterministically`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 240500)
        val track3 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 241000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)
        val key3 = LyricsCacheKey.generate(track3)

        // 240000 and 240500 both round to 240s
        assertEquals(key1, key2)
        // 241000 rounds to 241s
        assertNotEquals(key1, key3)
    }

    @Test
    fun `duration - materially different rounded durations produce different keys`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 300000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `title only protection - same title but different artist album duration must NOT produce same fallback key`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Artist One", albumName = "Album One", imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "Test Track", artistName = "Artist Two", albumName = "Album Two", imageUri = null, durationMs = 300000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `nullable album handled deterministically`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = null, imageUri = null, durationMs = 240000)
        val track2 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = null, imageUri = null, durationMs = 240000)
        val track3 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Some Album", imageUri = null, durationMs = 240000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)
        val key3 = LyricsCacheKey.generate(track3)

        assertEquals(key1, key2)
        assertNotEquals(key1, key3)
    }

    @Test
    fun `nullable duration handled deterministically`() {
        val track1 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = null)
        val track2 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = null)
        val track3 = SpotifyTrack(id = null, name = "Test Track", artistName = "Test Artist", albumName = "Test Album", imageUri = null, durationMs = 240000)

        val key1 = LyricsCacheKey.generate(track1)
        val key2 = LyricsCacheKey.generate(track2)
        val key3 = LyricsCacheKey.generate(track3)

        assertEquals(key1, key2)
        assertNotEquals(key1, key3)
    }
}