package com.example.spotlyrics.diagnostics

/**
 * Represents the health status of an individual lyrics provider.
 */
data class ProviderHealth(
    val provider: String,
    val healthy: Boolean,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val lastHttpCode: Int?,
    val lastError: String?
)
