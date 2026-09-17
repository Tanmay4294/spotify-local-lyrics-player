package com.example.spotlyrics.spotify

data class SpotifyPlayerState(
    val track: SpotifyTrack?,
    val isPlaying: Boolean,
    val playbackPositionMs: Long,
    val durationMs: Long
)
