package com.example.spotlyrics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotlyrics.spotify.SpotifyConnectionState
import com.example.spotlyrics.spotify.SpotifyManager
import com.example.spotlyrics.spotify.SpotifyPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {

    private val spotifyManager = SpotifyManager.getInstance()

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(SpotifyConnectionState.Disconnected)
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState

    private val _playerState = MutableStateFlow<SpotifyPlayerState?>(null)
    val playerState: StateFlow<SpotifyPlayerState?> = _playerState

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