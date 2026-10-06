package com.example.spotlyrics.lyrics

import androidx.lifecycle.ViewModel
import com.example.spotlyrics.data.repository.LyricsCacheRepository
import com.example.spotlyrics.lyrics.providers.LrcLibProvider
import com.example.spotlyrics.lyrics.providers.MusixmatchProvider
import com.example.spotlyrics.lyrics.providers.NetEaseProvider
import com.example.spotlyrics.lyrics.providers.QQMusicProvider
import com.example.spotlyrics.spotify.SpotifyTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LyricsManager(
    private val providers: Map<LyricsProviderChoice, LyricsProvider>,
    private val cacheRepository: LyricsCacheRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
) : ViewModel() {

    // Overloaded constructor for single provider (backwards compatibility with existing tests & code)
    constructor(
        provider: LyricsProvider,
        cacheRepository: LyricsCacheRepository,
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    ) : this(
        providers = mapOf(LyricsProviderChoice.LRCLIB to provider),
        cacheRepository = cacheRepository,
        dispatcher = dispatcher,
        scope = scope
    )

    // Default multi-provider constructor
    constructor(
        cacheRepository: LyricsCacheRepository,
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    ) : this(
        providers = mapOf(
            LyricsProviderChoice.LRCLIB to LrcLibProvider(),
            LyricsProviderChoice.MUSIXMATCH to MusixmatchProvider(),
            LyricsProviderChoice.NETEASE to NetEaseProvider(),
            LyricsProviderChoice.QQ_MUSIC to QQMusicProvider()
        ),
        cacheRepository = cacheRepository,
        dispatcher = dispatcher,
        scope = scope
    )

    private var currentTrackId: String? = null
    private var searchJob: Job? = null
    private var preferredProviderChoice: LyricsProviderChoice = LyricsProviderChoice.LRCLIB

    private val _lyricsStatus = MutableStateFlow<LyricsStatus>(LyricsStatus.NotFound)
    val lyricsStatus: StateFlow<LyricsStatus> = _lyricsStatus

    fun setPreferredProvider(choice: LyricsProviderChoice) {
        preferredProviderChoice = choice
    }

    fun retry(track: SpotifyTrack?) {
        currentTrackId = null
        onTrackChanged(track)
    }

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
            // 1. Check existing cache first
            val cachedResult = cacheRepository.getCachedLyrics(track)

            if (trackId == currentTrackId && cachedResult != null) {
                _lyricsStatus.value = LyricsStatus.Found(cachedResult)
                return@launch
            }

            // 2. Cache miss -> determine provider fallback order
            val order = getProviderOrder(preferredProviderChoice)

            var lastException: Exception? = null

            for (choice in order) {
                if (trackId != currentTrackId) return@launch

                val provider = providers[choice] ?: continue
                try {
                    val result = provider.search(
                        title = track.name,
                        artist = track.artistName,
                        album = track.albumName,
                        durationSeconds = track.durationMs?.let { it / 1000.0 }
                    )

                    if (trackId != currentTrackId) return@launch

                    if (result != null && result.found && (result.plainText?.isNotBlank() == true || result.syncedText?.isNotBlank() == true)) {
                        cacheRepository.saveLyrics(track, result)
                        _lyricsStatus.value = LyricsStatus.Found(result)
                        return@launch
                    }
                } catch (e: Exception) {
                    lastException = e
                    // Provider failure: isolated so fallback chain continues to next provider
                    if (trackId != currentTrackId) return@launch
                }
            }

            // 3. All providers failed or returned no lyrics
            if (trackId == currentTrackId) {
                if (providers.size == 1 && lastException != null) {
                    _lyricsStatus.value = LyricsStatus.ProviderError(
                        source = "lyrics_provider",
                        message = "Failed to search lyrics: ${lastException.message}"
                    )
                } else {
                    _lyricsStatus.value = LyricsStatus.NotFound
                }
            }
        }
    }

    fun getProviderOrder(preferred: LyricsProviderChoice): List<LyricsProviderChoice> {
        val allChoices = listOf(
            LyricsProviderChoice.LRCLIB,
            LyricsProviderChoice.MUSIXMATCH,
            LyricsProviderChoice.NETEASE,
            LyricsProviderChoice.QQ_MUSIC
        )
        return listOf(preferred) + allChoices.filter { it != preferred }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}