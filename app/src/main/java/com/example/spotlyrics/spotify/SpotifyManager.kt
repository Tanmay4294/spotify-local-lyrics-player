package com.example.spotlyrics.spotify

import android.content.Context
import android.util.Log
import com.example.spotlyrics.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.PendingResult
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

class SpotifyManager private constructor() {

    companion object {
        private const val TAG = "SpotifyManager"
        const val REDIRECT_URI = "spotlyrics://callback"

        @Volatile
        private var instance: SpotifyManager? = null

        fun getInstance(): SpotifyManager {
            return instance ?: synchronized(this) {
                instance ?: SpotifyManager().also { instance = it }
            }
        }
    }

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var playerStateSubscription: PendingResult<PlayerState>? = null

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(SpotifyConnectionState.Disconnected)
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState.asStateFlow()

    private val _playerState = MutableStateFlow<SpotifyPlayerState?>(null)
    val playerState: StateFlow<SpotifyPlayerState?> = _playerState.asStateFlow()

    fun observePlayerState(): StateFlow<SpotifyPlayerState> {
        return _playerState
            .filterNotNull()
            .stateIn(
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
                started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(),
                initialValue = SpotifyPlayerState(
                    track = null,
                    isPlaying = false,
                    playbackPositionMs = 0,
                    durationMs = 0
                )
            )
    }

    fun isConnected(): Boolean = _connectionState.value == SpotifyConnectionState.Connected

    fun connect(context: Context) {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        if (clientId.isBlank()) {
            _connectionState.value = SpotifyConnectionState.Error("Spotify Client ID missing in local.properties")
            return
        }

        if (_connectionState.value == SpotifyConnectionState.Connecting ||
            _connectionState.value == SpotifyConnectionState.Connected
        ) {
            return
        }

        _connectionState.value = SpotifyConnectionState.Connecting

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(
            context.applicationContext,
            connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    spotifyAppRemote = appRemote
                    _connectionState.value = SpotifyConnectionState.Connected
                    Log.d(TAG, "Successfully connected to Spotify App Remote")

                    subscribeToPlayerState()
                }

                override fun onFailure(throwable: Throwable) {
                    spotifyAppRemote = null
                    val errorMsg = parseConnectionError(throwable)
                    _connectionState.value = SpotifyConnectionState.Error(errorMsg)
                    Log.e(TAG, "App Remote connection failed: $errorMsg", throwable)
                }
            }
        )
    }

    private fun subscribeToPlayerState() {
        val remote = spotifyAppRemote ?: return

        playerStateSubscription?.cancel()

        playerStateSubscription = remote.playerApi
            .subscribeToPlayerState()
            .setEventCallback { sdkState ->
                val newMappedState = SpotifyTrackMapper.mapPlayerState(sdkState)
                val oldMappedState = _playerState.value

                if (SpotifyTrackMapper.hasTrackChanged(oldMappedState, newMappedState)) {
                    Log.d(
                        TAG,
                        "Track changed to: ${newMappedState?.track?.name} by ${newMappedState?.track?.artistName}"
                    )
                }

                _playerState.value = newMappedState
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "PlayerState subscription error: ${throwable.localizedMessage}", throwable)
            }
    }

    fun disconnect() {
        playerStateSubscription?.cancel()
        playerStateSubscription = null

        spotifyAppRemote?.let { remote ->
            try {
                SpotifyAppRemote.disconnect(remote)
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting: ${e.message}")
            }
        }
        spotifyAppRemote = null
        _playerState.value = null
        _connectionState.value = SpotifyConnectionState.Disconnected
        Log.d(TAG, "Disconnected from Spotify App Remote")
    }

    fun play() {
        val remote = spotifyAppRemote
        if (remote == null) {
            Log.w(TAG, "Cannot play: Spotify App Remote not connected")
            return
        }
        remote.playerApi.play("spotify:app:player").setResultCallback { result ->
            if (result != null) {
                Log.d(TAG, "Play command sent")
            } else {
                Log.e(TAG, "Play command failed: null result")
            }
        }
    }

    fun pause() {
        val remote = spotifyAppRemote
        if (remote == null) {
            Log.w(TAG, "Cannot pause: Spotify App Remote not connected")
            return
        }
        remote.playerApi.pause().setResultCallback { result ->
            if (result != null) {
                Log.d(TAG, "Pause command sent")
            } else {
                Log.e(TAG, "Pause command failed: null result")
            }
        }
    }

    fun skipNext() {
        val remote = spotifyAppRemote
        if (remote == null) {
            Log.w(TAG, "Cannot skip next: Spotify App Remote not connected")
            return
        }
        remote.playerApi.skipNext().setResultCallback { result ->
            if (result != null) {
                Log.d(TAG, "Skip next command sent")
            } else {
                Log.e(TAG, "Skip next command failed: null result")
            }
        }
    }

    fun skipPrevious() {
        val remote = spotifyAppRemote
        if (remote == null) {
            Log.w(TAG, "Cannot skip previous: Spotify App Remote not connected")
            return
        }
        remote.playerApi.skipPrevious().setResultCallback { result ->
            if (result != null) {
                Log.d(TAG, "Skip previous command sent")
            } else {
                Log.e(TAG, "Skip previous command failed: null result")
            }
        }
    }

    private fun parseConnectionError(throwable: Throwable): String {
        return when (throwable) {
            is NotLoggedInException -> "Please log into the Spotify app"
            is UserNotAuthorizedException -> "User authorization denied or cancelled"
            else -> {
                val msg = throwable.localizedMessage ?: ""
                if (msg.contains("CouldNotFindSpotifyApp") || msg.contains("not installed")) {
                    "Spotify app is not installed on this device"
                } else {
                    "Failed to connect to Spotify app: $msg"
                }
            }
        }
    }
}