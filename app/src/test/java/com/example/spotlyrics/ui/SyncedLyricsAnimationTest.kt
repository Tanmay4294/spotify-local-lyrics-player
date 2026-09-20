package com.example.spotlyrics.ui

import com.example.spotlyrics.lyrics.LrcParser
import com.example.spotlyrics.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncedLyricsAnimationTest {

    @Test
    fun testActiveLineIndexCalculationOnTimestampCross() {
        val lines = listOf(
            LyricLine(1000L, "First line"),
            LyricLine(5000L, "Second line"),
            LyricLine(10000L, "Third line")
        )

        // Before first timestamp
        assertEquals(-1, LrcParser.activeLine(lines, 500L))

        // Exactly at first timestamp
        assertEquals(0, LrcParser.activeLine(lines, 1000L))

        // Between first and second
        assertEquals(0, LrcParser.activeLine(lines, 3000L))

        // Crossing to second timestamp
        assertEquals(1, LrcParser.activeLine(lines, 5000L))

        // Between second and third
        assertEquals(1, LrcParser.activeLine(lines, 7500L))

        // Crossing to third timestamp
        assertEquals(2, LrcParser.activeLine(lines, 10000L))

        // After third timestamp
        assertEquals(2, LrcParser.activeLine(lines, 15000L))
    }

    @Test
    fun testActiveLineIndexRemainsSameWhilePositionAdvancesOnSameLine() {
        val lines = listOf(
            LyricLine(1000L, "First line"),
            LyricLine(5000L, "Second line")
        )

        val idx1 = LrcParser.activeLine(lines, 5000L)
        val idx2 = LrcParser.activeLine(lines, 5500L)
        val idx3 = LrcParser.activeLine(lines, 6000L)
        val idx4 = LrcParser.activeLine(lines, 9999L)

        assertEquals(1, idx1)
        assertEquals(1, idx2)
        assertEquals(1, idx3)
        assertEquals(1, idx4)
    }

    @Test
    fun testSeekingPositionUpdatesActiveLineIndexCorrectly() {
        val lines = listOf(
            LyricLine(1000L, "First line"),
            LyricLine(5000L, "Second line"),
            LyricLine(10000L, "Third line")
        )

        // Seek forward
        assertEquals(2, LrcParser.activeLine(lines, 12000L))

        // Seek backward
        assertEquals(0, LrcParser.activeLine(lines, 2000L))
    }
}
