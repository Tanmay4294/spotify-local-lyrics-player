package com.example.spotlyrics.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.spotlyrics.data.db.AppDatabase
import com.example.spotlyrics.data.repository.LyricsCacheRepository
import com.example.spotlyrics.lyrics.LyricsManager
import com.example.spotlyrics.lyrics.LyricsProvider
import com.example.spotlyrics.lyrics.LyricsStatus
import com.example.spotlyrics.lyrics.providers.LrcLibProvider
import com.example.spotlyrics.preferences.AppearanceMode
import com.example.spotlyrics.preferences.AppearancePreferences
import com.example.spotlyrics.spotify.SpotifyConnectionState
import com.example.spotlyrics.spotify.SpotifyManager
import com.example.spotlyrics.spotify.SpotifyPlayerState
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val context: Context,
    private val lyricsProvider: LyricsProvider = LrcLibProvider()
) : ViewModel() {

    private val spotifyManager = SpotifyManager.getInstance()
    private val cacheRepository = LyricsCacheRepository(AppDatabase.getInstance(context).lyricsCacheDao())
    private val lyricsManager = LyricsManager(lyricsProvider, cacheRepository)

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(SpotifyConnectionState.Disconnected)
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState

    private val _playerState = MutableStateFlow<SpotifyPlayerState?>(null)
    private var previousTrackId: String? = null

    // Playback anchor for local timer
    private data class PlaybackAnchor(
        val positionMs: Long,
        val anchorRealtimeMs: Long,
        val speed: Float = 1f,
        val isPaused: Boolean,
        val trackId: String?,
        val durationMs: Long
    )

    private var playbackAnchor: PlaybackAnchor? = null
    private val _currentPositionMs = MutableStateFlow<Long>(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()
    val playerState: StateFlow<SpotifyPlayerState?> = _playerState

    // Expose artwork bitmap flow from SpotifyManager
    val artworkBitmap = spotifyManager.artworkBitmap

    // Expose appearance mode flow
    val appearanceMode: StateFlow<AppearanceMode> = AppearancePreferences.getModeFlow(context)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceMode.AlbumColor)

    // Update appearance mode
    fun setAppearanceMode(mode: AppearanceMode) {
        viewModelScope.launch {
            AppearancePreferences.setMode(context, mode)
        }
    }

    val lyricsStatus: StateFlow<LyricsStatus> = lyricsManager.lyricsStatus

    init {
        observeSpotify()
        startPositionTicker()
    }


    // Start a ticker that updates synthetic playback position based on the anchor
    private fun startPositionTicker() {
        viewModelScope.launch {
            while (true) {
                delay(200L)
                val anchor = playbackAnchor ?: continue
                val pos = if (anchor.isPaused) {
                    anchor.positionMs
                } else {
                    anchor.positionMs + ((SystemClock.elapsedRealtime() - anchor.anchorRealtimeMs) * anchor.speed).toLong()
                }
                // Clamp position to [0, duration]
                _currentPositionMs.value = pos.coerceIn(0L, anchor.durationMs)
            }
        }
    }

    // Expose seek operation to UI
    fun seekTo(positionMs: Long) {
        spotifyManager.seekTo(positionMs)
        // Reset anchor after seeking
        playbackAnchor = playbackAnchor?.copy(
            positionMs = positionMs,
            anchorRealtimeMs = SystemClock.elapsedRealtime()
        )
    }

    // Update anchor based on incoming player state
    private fun updateAnchorFromState(state: com.example.spotlyrics.spotify.SpotifyPlayerState?) {
        val newTrackId = state?.track?.id
        val isPlaying = state?.isPlaying ?: false
        val speed = 1f // Assume normal speed
        val position = state?.playbackPositionMs ?: 0L
        val duration = state?.durationMs ?: 0L
        
        if (newTrackId != previousTrackId) {
            // Track changed – reset anchor (including null track)
            if (newTrackId != null) {
                playbackAnchor = PlaybackAnchor(
                    positionMs = position,
                    anchorRealtimeMs = SystemClock.elapsedRealtime(),
                    speed = speed,
                    isPaused = !isPlaying,
                    trackId = newTrackId,
                    durationMs = duration
                )
            } else {
                // No track - clear anchor and reset position
                playbackAnchor = null
                _currentPositionMs.value = 0L
            }
        } else {
            // Same track – possibly pause/play or seek happened
            playbackAnchor?.let { anchor ->
                val expected = if (!anchor.isPaused) {
                    anchor.positionMs + ((SystemClock.elapsedRealtime() - anchor.anchorRealtimeMs) * anchor.speed).toLong()
                } else {
                    anchor.positionMs
                }
                if (kotlin.math.abs(position - expected) > 2000L || anchor.isPaused != !isPlaying) {
                    playbackAnchor = anchor.copy(
                        positionMs = position,
                        anchorRealtimeMs = SystemClock.elapsedRealtime(),
                        isPaused = !isPlaying,
                        durationMs = duration
                    )
                }
            }
        }
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
                // Update anchor based on new player state
                updateAnchorFromState(state)
                val newTrackId = state?.track?.id
                if (newTrackId != previousTrackId) {
                    previousTrackId = newTrackId
                    // Trigger lyrics loading only on track change (including null)
                    lyricsManager.onTrackChanged(state?.track)
                }
                // If same track, UI updates via playerState flow
            }
        }
    }

    val authState: StateFlow<com.example.spotlyrics.spotify.SpotifyAuthState> = spotifyManager.authState

    private val _selfTestResult = MutableStateFlow<com.example.spotlyrics.spotify.SpotifySelfTestResult?>(null)
    val selfTestResult: StateFlow<com.example.spotlyrics.spotify.SpotifySelfTestResult?> = _selfTestResult

    fun connect() {
        spotifyManager.connect(context)
    }

    fun disconnect() {
        spotifyManager.disconnect()
    }

    fun reconnect() {
        spotifyManager.reconnect(context)
    }

    fun runSpotifySelfTest() {
        _selfTestResult.value = spotifyManager.runSelfTest()
    }

    @Deprecated("Use connect() instead. Kept for backward compatibility.")
    fun connectSpotify(context: android.content.Context) {
        spotifyManager.connect(context)
    }

    @Deprecated("Use disconnect() instead. Kept for backward compatibility.")
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

    fun retryLyrics() {
        val track = _playerState.value?.track
        lyricsManager.retry(track)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(context) as T
        }
    }
}