package com.example.spotlyrics.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LyricsManager(
    private val provider: LyricsProvider,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : ViewModel() {

    private var currentTrackId: String? = null
    private var searchJob: Job? = null

    private val _lyricsStatus = MutableStateFlow<LyricsStatus>(LyricsStatus.NotFound)
    val lyricsStatus: StateFlow<LyricsStatus> = _lyricsStatus

    fun onTrackChanged(track: SpotifyTrack?) {
        val trackId = track?.id
        
        if (trackId == currentTrackId) {
            return
        }

        currentTrackId = trackId
        searchJob?.cancel()
        
        if (track == null) {
            _lyricsStatus.value = LyricsStatus.NotFound
            return
        }

        _lyricsStatus.value = LyricsStatus.Loading
        
        searchJob = scope.launch {
            try {
                val result = provider.search(
                    title = track.name,
                    artist = track.artistName,
                    album = track.albumName,
                    durationSeconds = track.durationMs?.let { it / 1000.0 }
                )

                if (trackId == currentTrackId) {
                    when {
                        result != null && result.found && (result.plainText?.isNotBlank() == true || result.syncedText?.isNotBlank() == true) ->
                            _lyricsStatus.value = LyricsStatus.Found(result)
                        result != null && !result.found ->
                            _lyricsStatus.value = LyricsStatus.NotFound
                        else ->
                            _lyricsStatus.value = LyricsStatus.NotFound
                    }
                }
            } catch (e: Exception) {
                if (trackId == currentTrackId) {
                    _lyricsStatus.value = LyricsStatus.ProviderError(
                        source = "lyrics_provider",
                        message = "Failed to search lyrics: ${e.message}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}