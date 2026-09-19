package com.example.spotlyrics.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Central health monitor for the application.
 *
 * It records health information for different components (Spotify auth, App Remote,
 * PlayerState, Lyrics provider, Cache, Network) and provides a read‑only flow for
 * consumers. The implementation is deliberately lightweight – a singleton object
 * with thread‑safe maps. No external DI framework is required.
 */
object HealthMonitor {
    private val componentHealthMap = ConcurrentHashMap<String, ComponentHealth>()
    private val providerHealthMap = ConcurrentHashMap<String, ProviderHealth>()

    private val _components = MutableStateFlow<Map<String, ComponentHealth>>(emptyMap())
    val components: StateFlow<Map<String, ComponentHealth>> = _components.asStateFlow()

    private val _providers = MutableStateFlow<Map<String, ProviderHealth>>(emptyMap())
    val providers: StateFlow<Map<String, ProviderHealth>> = _providers.asStateFlow()

    /** Record a successful health event for a component. */
    fun recordSuccess(component: String, info: String? = null) {
        val now = System.currentTimeMillis()
        val health = componentHealthMap.compute(component) { _, existing ->
            (existing ?: ComponentHealth(component)).also {
                it.healthy = true
                it.lastSuccessAt = now
                it.lastFailureAt = null
                it.errorInfo = info
            }
        }!!
        _components.value = componentHealthMap.toMap()
    }

    /** Record a failure health event for a component. */
    fun recordFailure(component: String, error: String, timestamp: Long = System.currentTimeMillis()) {
        val health = componentHealthMap.compute(component) { _, existing ->
            (existing ?: ComponentHealth(component)).also {
                it.healthy = false
                it.lastFailureAt = timestamp
                it.errorInfo = error
            }
        }!!
        _components.value = componentHealthMap.toMap()
    }

    /** Update health information for a specific lyrics provider. */
    fun updateProviderHealth(health: ProviderHealth) {
        providerHealthMap[health.provider] = health
        _providers.value = providerHealthMap.toMap()
    }
}

/** Basic per‑component health record. */
data class ComponentHealth(
    val name: String,
    var healthy: Boolean = true,
    var lastSuccessAt: Long? = null,
    var lastFailureAt: Long? = null,
    var errorInfo: String? = null
)
