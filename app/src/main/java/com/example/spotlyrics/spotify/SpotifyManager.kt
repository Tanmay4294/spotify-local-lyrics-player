package com.example.spotlyrics.spotify

import android.content.Context
import android.util.Log
import com.example.spotlyrics.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyAppException
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private var playerStateSubscription: Subscription<PlayerState>? = null

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(SpotifyConnectionState.Disconnected)
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState.asStateFlow()

    private val _playerState = MutableStateFlow<SpotifyPlayerState?>(null)
    val playerState: StateFlow<SpotifyPlayerState?> = _playerState.asStateFlow()

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
            if (SpotifyAppRemote.isConnected()) {
                SpotifyAppRemote.disconnect(remote)
            }
        }
        spotifyAppRemote = null
        _playerState.value = null
        _connectionState.value = SpotifyConnectionState.Disconnected
        Log.d(TAG, "Disconnected from Spotify App Remote")
    }

    private fun parseConnectionError(throwable: Throwable): String {
        return when (throwable) {
            is CouldNotFindSpotifyAppException -> "Spotify app is not installed on this device"
            is NotLoggedInException -> "Please log into the Spotify app"
            is UserNotAuthorizedException -> "User authorization denied or cancelled"
            else -> throwable.localizedMessage ?: "Failed to connect to Spotify app"
        }
    }
}
