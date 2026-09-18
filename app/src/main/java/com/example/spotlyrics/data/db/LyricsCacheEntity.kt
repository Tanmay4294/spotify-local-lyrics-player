package com.example.spotlyrics.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val cacheKey: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Double?,
    val source: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val fetchedAt: Long
)