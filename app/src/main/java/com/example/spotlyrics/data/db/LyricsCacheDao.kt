package com.example.spotlyrics.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsCacheDao {

    @Query("SELECT * FROM lyrics_cache WHERE cacheKey = :cacheKey")
    fun getByCacheKey(cacheKey: String): Flow<LyricsCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: LyricsCacheEntity)
}