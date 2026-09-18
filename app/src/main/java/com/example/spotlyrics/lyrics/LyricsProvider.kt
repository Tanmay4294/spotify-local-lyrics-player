package com.example.spotlyrics.lyrics

interface LyricsProvider {
    suspend fun search(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Double?
    ): LyricsResult?
}