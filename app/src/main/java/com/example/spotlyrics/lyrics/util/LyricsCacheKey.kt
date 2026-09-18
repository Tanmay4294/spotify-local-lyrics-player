package com.example.spotlyrics.lyrics.util

import com.example.spotlyrics.spotify.SpotifyTrack

object LyricsCacheKey {

    fun generate(track: SpotifyTrack): String {
        return if (!track.id.isNullOrBlank()) {
            "spotify:${track.id}"
        } else {
            generateCompositeKey(track)
        }
    }

    private fun generateCompositeKey(track: SpotifyTrack): String {
        val normalizedTitle = normalize(track.name)
        val normalizedArtist = normalize(track.artistName)
        val normalizedAlbum = track.albumName?.let { normalize(it) } ?: "noalbum"
        val roundedDuration = roundDuration(track.durationMs)

        return "composite:$normalizedTitle|$normalizedArtist|$normalizedAlbum|$roundedDuration"
    }

    private fun normalize(text: String): String {
        return text.trim()
            .lowercase()
            .replace("\\s+".toRegex(), " ")
    }

    private fun roundDuration(durationMs: Long?): Long {
        return durationMs?.let { (it / 1000).toLong() } ?: 0L
    }
}