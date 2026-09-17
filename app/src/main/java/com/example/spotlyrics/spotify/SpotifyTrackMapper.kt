package com.example.spotlyrics.spotify

import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track

object SpotifyTrackMapper {

    fun mapTrack(sdkTrack: Track?): SpotifyTrack? {
        if (sdkTrack == null) return null
        val trackUriOrUri = sdkTrack.uri
        val trackId = trackUriOrUri?.removePrefix("spotify:track:")
        val imageUriString = sdkTrack.imageUri?.raw

        return SpotifyTrack(
            id = trackId ?: sdkTrack.uri,
            name = sdkTrack.name ?: "Unknown Track",
            artistName = sdkTrack.artist?.name ?: "Unknown Artist",
            albumName = sdkTrack.album?.name,
            imageUri = imageUriString,
            durationMs = sdkTrack.duration
        )
    }

    fun mapPlayerState(sdkState: PlayerState?): SpotifyPlayerState? {
        if (sdkState == null) return null
        val mappedTrack = mapTrack(sdkState.track)
        val isPaused = sdkState.isPaused
        val isPlaying = !isPaused
        val position = sdkState.playbackPosition
        val duration = sdkState.track?.duration ?: 0L

        return SpotifyPlayerState(
            track = mappedTrack,
            isPlaying = isPlaying,
            playbackPositionMs = position,
            durationMs = duration
        )
    }

    fun hasTrackChanged(oldState: SpotifyPlayerState?, newState: SpotifyPlayerState?): Boolean {
        val oldTrackId = oldState?.track?.id
        val newTrackId = newState?.track?.id
        return oldTrackId != newTrackId
    }
}
