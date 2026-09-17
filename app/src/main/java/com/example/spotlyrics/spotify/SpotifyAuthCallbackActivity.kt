package com.example.spotlyrics.spotify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope

class SpotifyAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent?) {
        if (intent != null && intent.data != null) {
            val authManager = SpotifyPkceAuthManager.getInstance(applicationContext)
            authManager.handleCallback(intent, lifecycleScope)
        }
        finish()
    }
}
