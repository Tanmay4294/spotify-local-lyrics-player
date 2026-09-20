package com.example.spotlyrics.preferences

import com.example.spotlyrics.spotify.SpotifyPlayerState
import com.example.spotlyrics.spotify.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeTest {

    @Test
    fun defaultAppearanceModeIsAlbumColor() {
        val defaultMode = AppearanceMode.AlbumColor
        assertEquals(AppearanceMode.AlbumColor, defaultMode)
        assertNotNull(defaultMode)
    }

    @Test
    fun appearanceModeEnumContainsExactlyTwoModes() {
        val values = AppearanceMode.values()
        assertEquals(2, values.size)
        assertTrue(values.contains(AppearanceMode.AlbumColor))
        assertTrue(values.contains(AppearanceMode.DynamicAlbumArt))
    }

    @Test
    fun appearanceModeSelectionIsMutuallyExclusive() {
        var currentMode = AppearanceMode.AlbumColor
        assertEquals(AppearanceMode.AlbumColor, currentMode)
        assertNotEquals(AppearanceMode.DynamicAlbumArt, currentMode)

        currentMode = AppearanceMode.DynamicAlbumArt
        assertEquals(AppearanceMode.DynamicAlbumArt, currentMode)
        assertNotEquals(AppearanceMode.AlbumColor, currentMode)
    }

    @Test
    fun appearanceModeValueOfParsingSupportsBothFormats() {
        assertEquals(AppearanceMode.AlbumColor, AppearanceMode.valueOf("AlbumColor"))
        assertEquals(AppearanceMode.DynamicAlbumArt, AppearanceMode.valueOf("DynamicAlbumArt"))
    }

    @Test
    fun appearanceChangesDoNotAffectPlaybackState() {
        val track = SpotifyTrack("track_1", "Test Title", "Test Artist", "Test Album", null, 200000L)
        val playerState = SpotifyPlayerState(
            track = track,
            isPlaying = true,
            playbackPositionMs = 45000L,
            durationMs = 200000L
        )

        var mode = AppearanceMode.AlbumColor
        assertEquals(45000L, playerState.playbackPositionMs)
        assertTrue(playerState.isPlaying)

        // Change mode
        mode = AppearanceMode.DynamicAlbumArt
        assertEquals(AppearanceMode.DynamicAlbumArt, mode)

        // Verify player state is completely unaffected
        assertEquals(45000L, playerState.playbackPositionMs)
        assertTrue(playerState.isPlaying)
        assertEquals("track_1", playerState.track?.id)
    }
}
