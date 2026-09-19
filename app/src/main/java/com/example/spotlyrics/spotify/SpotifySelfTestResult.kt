package com.example.spotlyrics.spotify

data class SpotifySelfTestResult(
    val success: Boolean,
    val connectionOk: Boolean,
    val playerStateOk: Boolean,
    val message: String
)
