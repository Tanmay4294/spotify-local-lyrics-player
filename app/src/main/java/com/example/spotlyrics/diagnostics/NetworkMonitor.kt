package com.example.spotlyrics.diagnostics

import java.net.InetAddress

object NetworkMonitor {
    /**
     * Checks basic network connectivity by attempting to reach a well‑known DNS server.
     * This is a lightweight check that does not require Android permissions.
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            // Google's public DNS server – ping with a short timeout.
            val address = InetAddress.getByName("8.8.8.8")
            address.isReachable(1500) // timeout in ms
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Records the current network status in the [HealthMonitor].
     * Should be called before any network‑dependent operation.
     */
    fun recordNetworkStatus() {
        if (isNetworkAvailable()) {
            HealthMonitor.recordSuccess("Network", "Reachable")
        } else {
            HealthMonitor.recordFailure("Network", "Not reachable")
        }
    }
}
