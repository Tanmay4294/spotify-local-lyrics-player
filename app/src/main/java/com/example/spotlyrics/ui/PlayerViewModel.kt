package com.example.spotlyrics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotlyrics.lyrics.LyricsManager
import com.example.spotlyrics.lyrics.LyricsProvider
import com.example.spotlyrics.lyrics.LyricsStatus
import com.example.spotlyrics.lyrics.providers.LrcLibProvider
import com.example.spotlyrics.spotify.SpotifyConnectionState
import com.example.spotlyrics.spotify.SpotifyManager
import com.example.spotlyrics.spotify.SpotifyPlayerState
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val lyricsProvider: LyricsProvider = LrcLibProvider()
) : ViewModel() {

    private val spotifyManager = SpotifyManager.getInstance()
    private val lyricsManager = LyricsManager(lyricsProvider)

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(SpotifyConnectionState.Disconnected)
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState

    private val _playerState = MutableStateFlow<SpotifyPlayerState?>(null)
    val playerState: StateFlow<SpotifyPlayerState?> = _playerState

    val lyricsStatus: StateFlow<LyricsStatus> = lyricsManager.lyricsStatus

    init {
        observeSpotify()
    }

    private fun observeSpotify() {
        viewModelScope.launch {
            spotifyManager.connectionState.collect { state ->
                _connectionState.value = state
            }
        }

        viewModelScope.launch {
            spotifyManager.playerState.collect { state ->
                _playerState.value = state
                lyricsManager.onTrackChanged(state?.track)
            }
        }
    }

    fun connectSpotify(context: android.content.Context) {
        spotifyManager.connect(context)
    }

    fun disconnectSpotify() {
        spotifyManager.disconnect()
    }

    fun play() {
        spotifyManager.play()
    }

    fun pause() {
        spotifyManager.pause()
    }

    fun skipNext() {
        spotifyManager.skipNext()
    }

    fun skipPrevious() {
        spotifyManager.skipPrevious()
    }
}