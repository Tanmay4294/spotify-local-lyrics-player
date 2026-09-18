package com.example.spotlyrics.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_events")
data class HealthEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val component: String,
    val severity: String,
    val message: String,
    val timestamp: Long
)