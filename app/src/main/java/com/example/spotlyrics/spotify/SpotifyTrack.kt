package com.example.spotlyrics.spotify

data class SpotifyTrack(
    val id: String?,
    val name: String,
    val artistName: String,
    val albumName: String?,
    val imageUri: String?,
    val durationMs: Long?
)
