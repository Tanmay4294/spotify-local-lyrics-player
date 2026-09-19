package com.example.spotlyrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.spotlyrics.ui.PlayerViewModel
import com.example.spotlyrics.ui.screens.PlayerScreen
import com.example.spotlyrics.ui.theme.SpotifyLocalLyricsPlayerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register ViewModel as lifecycle observer for automatic Spotify connection management
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.connect()
                Lifecycle.Event.ON_STOP -> viewModel.disconnect()
                else -> {}
            }
        })

        setContent {
            SpotifyLocalLyricsPlayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PlayerScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
