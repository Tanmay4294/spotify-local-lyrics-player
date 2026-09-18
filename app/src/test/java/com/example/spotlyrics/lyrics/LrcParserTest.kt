package com.example.spotlyrics.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun test1_singleTimestamp() {
        val lrc = "[00:12.34]First line"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(12340L, lines[0].startMs)
        assertEquals("First line", lines[0].text)
    }

    @Test
    fun test2_multipleLyricLines() {
        val lrc = """
            [00:10.00]Line 1
            [00:20.00]Line 2
            [00:30.00]Line 3
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals(10000L, lines[0].startMs)
        assertEquals("Line 1", lines[0].text)
        assertEquals(20000L, lines[1].startMs)
        assertEquals("Line 2", lines[1].text)
        assertEquals(30000L, lines[2].startMs)
        assertEquals("Line 3", lines[2].text)
    }

    @Test
    fun test3_minuteValuesGreaterThanZero() {
        val lrc = "[01:02.50]Minute line"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(62500L, lines[0].startMs)
        assertEquals("Minute line", lines[0].text)
    }

    @Test
    fun test4_fractionalSecondsPrecision() {
        val lrc1 = "[00:05.4]One digit"
        val lrc2 = "[00:05.45]Two digits"
        val lrc3 = "[00:05.456]Three digits"

        val lines1 = LrcParser.parse(lrc1)
        val lines2 = LrcParser.parse(lrc2)
        val lines3 = LrcParser.parse(lrc3)

        assertEquals(5400L, lines1[0].startMs)
        assertEquals(5450L, lines2[0].startMs)
        assertEquals(5456L, lines3[0].startMs)
    }

    @Test
    fun test5_metadataLinesIgnored() {
        val lrc = """
            [ar:Artist Name]
            [ti:Song Title]
            [al:Album Name]
            [by:Author]
            [offset:100]
            [00:15.00]Actual lyric line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(15000L, lines[0].startMs)
        assertEquals("Actual lyric line", lines[0].text)
    }

    @Test
    fun test6_emptyLinesHandled() {
        val lrc = "\n  \n[00:05.00]Line after blank\n  \n"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(5000L, lines[0].startMs)
    }

    @Test
    fun test7_malformedTimestampsHandledSafely() {
        val lrc = """
            [invalid:timestamp]Not a lyric
            [99999999999999999999999:99]Overflow timestamp
            [00:10.00]Valid line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(10000L, lines[0].startMs)
        assertEquals("Valid line", lines[0].text)
    }

    @Test
    fun test8_emptyInputReturnsEmptyList() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse(null).isEmpty())
        assertTrue(LrcParser.parse("   ").isEmpty())
    }

    @Test
    fun test9_linesOutOfChronologicalOrderSorted() {
        val lrc = """
            [00:30.00]Third line
            [00:10.00]First line
            [00:20.00]Second line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals(10000L, lines[0].startMs)
        assertEquals(20000L, lines[1].startMs)
        assertEquals(30000L, lines[2].startMs)
    }

    @Test
    fun test10_identicalTimestampsPreserveOrder() {
        val lrc = """
            [00:10.00]Line A
            [00:10.00]Line B
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals("Line A", lines[0].text)
        assertEquals("Line B", lines[1].text)
    }

    @Test
    fun test11_activeLinePositionBeforeFirstLine() {
        val lines = listOf(LyricLine(5000L, "First line"))
        assertEquals(-1, LrcParser.activeLine(lines, 3000L))
    }

    @Test
    fun test12_activeLinePositionExactlyAtFirstLine() {
        val lines = listOf(LyricLine(5000L, "First line"), LyricLine(10000L, "Second line"))
        assertEquals(0, LrcParser.activeLine(lines, 5000L))
    }

    @Test
    fun test13_activeLinePositionBetweenTwoLines() {
        val lines = listOf(LyricLine(5000L, "First line"), LyricLine(10000L, "Second line"))
        assertEquals(0, LrcParser.activeLine(lines, 7500L))
    }

    @Test
    fun test14_activeLinePositionExactlyAtLaterLine() {
        val lines = listOf(LyricLine(5000L, "First line"), LyricLine(10000L, "Second line"))
        assertEquals(1, LrcParser.activeLine(lines, 10000L))
    }

    @Test
    fun test15_activeLinePositionAfterFinalLine() {
        val lines = listOf(LyricLine(5000L, "First line"), LyricLine(10000L, "Second line"))
        assertEquals(1, LrcParser.activeLine(lines, 15000L))
    }

    @Test
    fun test16_activeLineEmptyList() {
        assertEquals(-1, LrcParser.activeLine(emptyList(), 5000L))
    }

    @Test
    fun test17_activeLineNegativePosition() {
        val lines = listOf(LyricLine(5000L, "First line"))
        assertEquals(-1, LrcParser.activeLine(lines, -100L))
    }

    @Test
    fun test18_multipleTimestampsOnOneLine() {
        val lrc = "[00:10.00][00:20.00]Repeated Chorus"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].startMs)
        assertEquals("Repeated Chorus", lines[0].text)
        assertEquals(20000L, lines[1].startMs)
        assertEquals("Repeated Chorus", lines[1].text)
    }

    @Test
    fun test19_unicodeLyricsSupported() {
        val lrc = "[00:05.00]こんにちは世界 🎵 😊"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals("こんにちは世界 🎵 😊", lines[0].text)
    }

    @Test
    fun test20_lyricsWithPunctuationSupported() {
        val lrc = "[00:08.50]Hello, world! (Is anyone there?)"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals("Hello, world! (Is anyone there?)", lines[0].text)
    }
}
