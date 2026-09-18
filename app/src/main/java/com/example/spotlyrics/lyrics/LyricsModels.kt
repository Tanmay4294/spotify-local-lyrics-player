package com.example.spotlyrics.lyrics

data class LyricsResult(
    val source: String,
    val plainText: String?,
    val syncedText: String?,
    val durationSeconds: Double?,
    val found: Boolean
)