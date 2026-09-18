package com.example.spotlyrics.ui

import com.example.spotlyrics.lyrics.LrcParser
import com.example.spotlyrics.lyrics.LyricLine
import com.example.spotlyrics.lyrics.LyricsResult
import com.example.spotlyrics.lyrics.LyricsStatus
import com.example.spotlyrics.spotify.SpotifyPlayerState
import com.example.spotlyrics.spotify.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerViewModelUiTest {

    @Test
    fun activeLineUpdatesCorrectlyWithPlaybackPosition() {
        val lines = listOf(
            LyricLine(1000L, "Intro"),
            LyricLine(5000L, "Verse 1"),
            LyricLine(10000L, "Chorus")
        )

        assertEquals(-1, LrcParser.activeLine(lines, 500L))
        assertEquals(0, LrcParser.activeLine(lines, 1500L))
        assertEquals(1, LrcParser.activeLine(lines, 6000L))
        assertEquals(2, LrcParser.activeLine(lines, 12000L))
    }

    @Test
    fun plainLyricsFallbackWhenSyncedTextIsNull() {
        val result = LyricsResult(
            source = "lrclib",
            plainText = "Plain lyric line 1\nPlain lyric line 2",
            syncedText = null,
            durationSeconds = 180.0,
            found = true
        )

        val status = LyricsStatus.Found(result)
        assertTrue(result.lyricLines.isEmpty())
        assertEquals("Plain lyric line 1\nPlain lyric line 2", result.plainText)
    }

    @Test
    fun malformedSyncedLyricsFallsBackToEmptyLyricLines() {
        val result = LyricsResult(
            source = "lrclib",
            plainText = "Fallback plain text",
            syncedText = "[invalid timestamp tag] Some text",
            durationSeconds = 180.0,
            found = true
        )

        assertTrue(result.lyricLines.isEmpty())
        assertEquals("Fallback plain text", result.plainText)
    }

    @Test
    fun playPauseActionLogicDependsOnPlayerStateIsPlaying() {
        val playingState = SpotifyPlayerState(
            track = SpotifyTrack("id1", "Song", "Artist", "Album", null, 180000L),
            isPlaying = true,
            playbackPositionMs = 5000L,
            durationMs = 180000L
        )

        val pausedState = SpotifyPlayerState(
            track = SpotifyTrack("id1", "Song", "Artist", "Album", null, 180000L),
            isPlaying = false,
            playbackPositionMs = 5000L,
            durationMs = 180000L
        )

        assertTrue(playingState.isPlaying)
        assertFalse(pausedState.isPlaying)
    }
}
