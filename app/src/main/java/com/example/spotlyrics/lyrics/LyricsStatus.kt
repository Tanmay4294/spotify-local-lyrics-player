package com.example.spotlyrics.lyrics

sealed interface LyricsStatus {
    data object Loading : LyricsStatus

    data class Found(
        val lyrics: LyricsResult
    ) : LyricsStatus

    data object NotFound : LyricsStatus

    data class ProviderError(
        val source: String,
        val message: String
    ) : LyricsStatus
}