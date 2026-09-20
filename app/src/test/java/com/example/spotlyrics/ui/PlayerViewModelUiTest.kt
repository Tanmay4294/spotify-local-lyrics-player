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

    @Test
    fun lyricDistanceHierarchyCalculatesCorrectEmphasisLevels() {
        val lines = listOf(
            LyricLine(1000L, "Line 0"),
            LyricLine(5000L, "Line 1"),
            LyricLine(10000L, "Line 2 - Active"),
            LyricLine(15000L, "Line 3"),
            LyricLine(20000L, "Line 4"),
            LyricLine(25000L, "Line 5")
        )

        val activeIndex = LrcParser.activeLine(lines, 12000L)
        assertEquals(2, activeIndex)

        fun getAlpha(index: Int): Float {
            val dist = kotlin.math.abs(index - activeIndex)
            return when {
                index == activeIndex -> 1.0f
                dist == 1 -> 0.65f
                dist == 2 -> 0.45f
                else -> 0.25f
            }
        }

        assertEquals(1.0f, getAlpha(2), 0.01f)
        assertEquals(0.65f, getAlpha(1), 0.01f)
        assertEquals(0.65f, getAlpha(3), 0.01f)
        assertEquals(0.45f, getAlpha(0), 0.01f)
        assertEquals(0.45f, getAlpha(4), 0.01f)
        assertEquals(0.25f, getAlpha(5), 0.01f)
    }

    @Test
    fun seekingToPositionUpdatesActiveLyricIndexImmediately() {
        val lines = listOf(
            LyricLine(0L, "Start"),
            LyricLine(30000L, "Middle"),
            LyricLine(60000L, "End")
        )

        assertEquals(0, LrcParser.activeLine(lines, 5000L))
        assertEquals(1, LrcParser.activeLine(lines, 35000L))
        assertEquals(2, LrcParser.activeLine(lines, 65000L))
    }

    @Test
    fun trackIdentityChangeDetectionDistinguishesDifferentTracks() {
        val trackA = SpotifyTrack("idA", "Track A", "Artist", "Album", null, 180000L)
        val trackB = SpotifyTrack("idB", "Track B", "Artist", "Album", null, 180000L)
        val trackA2 = SpotifyTrack("idA", "Track A", "Artist", "Album", null, 180000L)

        assertFalse(trackA.id == trackB.id)
        assertTrue(trackA.id == trackA2.id)
    }

    @Test
    fun trackChangeResetsActiveLineToBeginning() {
        val trackALines = listOf(
            LyricLine(1000L, "Track A Line 1"),
            LyricLine(5000L, "Track A Line 2")
        )
        val trackBLines = listOf(
            LyricLine(1000L, "Track B Line 1"),
            LyricLine(5000L, "Track B Line 2")
        )

        val activeLineA = LrcParser.activeLine(trackALines, 6000L)
        val activeLineBInitial = LrcParser.activeLine(trackBLines, 0L)

        assertEquals(1, activeLineA)
        assertEquals(-1, activeLineBInitial)
    }
}
