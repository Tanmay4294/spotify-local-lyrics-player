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

    private val _authState = MutableStateFlow<SpotifyAuthState>(SpotifyAuthState.REAUTH_REQUIRED)
    val authState: StateFlow<SpotifyAuthState> = _authState.asStateFlow()

    private val _playerState = MutableStateFlow<SpotifyPlayerState?>(null)
    val playerState: StateFlow<SpotifyPlayerState?> = _playerState.asStateFlow()

    private val _artworkBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val artworkBitmap: StateFlow<android.graphics.Bitmap?> = _artworkBitmap.asStateFlow()

    private var retryCount = 0
    private val MAX_RETRIES = 3

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

    fun runSelfTest(): SpotifySelfTestResult {
        val isConn = isConnected()
        val currentState = _playerState.value
        val playerOk = currentState != null

        if (!isConn) {
            com.example.spotlyrics.diagnostics.HealthMonitor.recordFailure("SpotifySelfTest", "Connection unavailable")
            return SpotifySelfTestResult(
                success = false,
                connectionOk = false,
                playerStateOk = playerOk,
                message = "Spotify App Remote is disconnected."
            )
        }

        val msg = if (playerOk) {
            "Connected to Spotify and player state is active."
        } else {
            "Connected to Spotify but player state is currently null (no active track)."
        }

        com.example.spotlyrics.diagnostics.HealthMonitor.recordSuccess("SpotifySelfTest", msg)
        return SpotifySelfTestResult(
            success = true,
            connectionOk = true,
            playerStateOk = playerOk,
            message = msg
        )
    }

    fun reconnect(context: Context) {
        retryCount = 0
        disconnect()
        connect(context)
    }

    fun connect(context: Context) {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        if (clientId.isBlank()) {
            _connectionState.value = SpotifyConnectionState.Error("Spotify Client ID missing in local.properties")
            _authState.value = SpotifyAuthState.REAUTH_REQUIRED
            return
        }

        if (retryCount >= MAX_RETRIES) {
            Log.w(TAG, "Max connection retry limit reached. User action required to reconnect.")
            _authState.value = SpotifyAuthState.REAUTH_REQUIRED
            _connectionState.value = SpotifyConnectionState.Error("Max reconnect retries reached. Tap Reconnect Spotify.")
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
                    retryCount = 0
                    _connectionState.value = SpotifyConnectionState.Connected
                    _authState.value = SpotifyAuthState.VALID
                    Log.d(TAG, "Successfully connected to Spotify App Remote")

                    // Record successful Spotify auth and App Remote connection
                    com.example.spotlyrics.diagnostics.HealthMonitor.recordSuccess("SpotifyAuth", "Connected")
                    com.example.spotlyrics.diagnostics.HealthMonitor.recordSuccess("AppRemote", "Connected")

                    subscribeToPlayerState()
                }

                override fun onFailure(throwable: Throwable) {
                    spotifyAppRemote = null
                    retryCount++
                    val errorMsg = parseConnectionError(throwable)
                    _connectionState.value = SpotifyConnectionState.Error(errorMsg)
                    val classifiedAuth = classifyAuthFailure(throwable)
                    _authState.value = classifiedAuth

                    // Record failure for Spotify authentication/App Remote connection
                    com.example.spotlyrics.diagnostics.HealthMonitor.recordFailure("SpotifyAuth", "Connection failed: $errorMsg ($classifiedAuth)")
                    com.example.spotlyrics.diagnostics.HealthMonitor.recordFailure("AppRemote", "Connection failed: $errorMsg")
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

                // Record successful reception of PlayerState (even if track is null)
                com.example.spotlyrics.diagnostics.HealthMonitor.recordSuccess("PlayerState", "Received")
                _playerState.value = newMappedState

                val imageUri = sdkState.track?.imageUri
                if (imageUri != null) {
                    remote.imagesApi.getImage(imageUri).setResultCallback { bitmap ->
                        _artworkBitmap.value = bitmap
                    }
                } else {
                    _artworkBitmap.value = null
                }
            }
            .setErrorCallback { throwable ->
                Log.e(TAG, "PlayerState subscription error: ${throwable.localizedMessage}", throwable)
                com.example.spotlyrics.diagnostics.HealthMonitor.recordFailure("PlayerState", "Subscription error: ${throwable.localizedMessage}")
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
        _artworkBitmap.value = null
        _connectionState.value = SpotifyConnectionState.Disconnected
        Log.d(TAG, "Disconnected from Spotify App Remote")
    }

    fun play() {
        val remote = spotifyAppRemote
        if (remote == null) {
            Log.w(TAG, "Cannot play: Spotify App Remote not connected")
            return
        }
        remote.playerApi.resume().setResultCallback { result ->
            if (result != null) {
                Log.d(TAG, "Resume command sent")
            } else {
                Log.e(TAG, "Resume command failed: null result")
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

    fun classifyAuthFailure(throwable: Throwable): SpotifyAuthState {
        return when (throwable) {
            is UserNotAuthorizedException -> SpotifyAuthState.AUTH_REVOKED
            is NotLoggedInException -> SpotifyAuthState.REAUTH_REQUIRED
            else -> {
                val msg = throwable.localizedMessage ?: ""
                if (msg.contains("expired", ignoreCase = true)) {
                    SpotifyAuthState.ACCESS_TOKEN_EXPIRED
                } else if (msg.contains("refresh", ignoreCase = true)) {
                    SpotifyAuthState.REFRESH_FAILED
                } else {
                    SpotifyAuthState.REAUTH_REQUIRED
                }
            }
        }
    }
}