package com.example.spotlyrics.lyrics

object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d+):(\d{2})(?:[\.:](\d+))?\]""")
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*\]$""")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) {
            return emptyList()
        }

        val parsedLines = mutableListOf<LyricLine>()

        lrcContent.lines().forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty()) return@forEach

            // Check if entire line is a metadata tag like [ar:Artist]
            if (METADATA_TAG_REGEX.matches(trimmedLine)) {
                return@forEach
            }

            val timestampMatches = TIMESTAMP_REGEX.findAll(trimmedLine).toList()
            if (timestampMatches.isEmpty()) {
                return@forEach
            }

            // Remove all timestamp tags from line to extract actual lyric text
            val lyricText = TIMESTAMP_REGEX.replace(trimmedLine, "").trim()

            for (match in timestampMatches) {
                try {
                    val minutesStr = match.groupValues[1]
                    val secondsStr = match.groupValues[2]
                    val fractionStr = match.groupValues.getOrNull(3)

                    val minutes = minutesStr.toLong()
                    val seconds = secondsStr.toLong()

                    val fracMs = when {
                        fractionStr.isNullOrEmpty() -> 0L
                        fractionStr.length == 1 -> fractionStr.toLong() * 100L
                        fractionStr.length == 2 -> fractionStr.toLong() * 10L
                        fractionStr.length == 3 -> fractionStr.toLong()
                        else -> fractionStr.substring(0, 3).toLong()
                    }

                    val totalMs = minutes * 60_000L + seconds * 1_000L + fracMs
                    parsedLines.add(LyricLine(startMs = totalMs, text = lyricText))
                } catch (e: Exception) {
                    // Ignore malformed timestamp numbers safely
                }
            }
        }

        // Sort chronologically using a stable sort
        return parsedLines.sortedWith(compareBy { it.startMs })
    }

    fun activeLine(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < 0) {
            return -1
        }
        var activeIndex = -1
        for (i in lines.indices) {
            if (lines[i].startMs <= positionMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }
}
