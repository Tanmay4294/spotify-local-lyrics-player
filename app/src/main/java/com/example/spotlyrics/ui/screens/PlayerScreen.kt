package com.example.spotlyrics.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spotlyrics.preferences.AppearanceMode
import com.example.spotlyrics.preferences.AppearancePreferences
import com.example.spotlyrics.spotify.SpotifyConnectionState
import com.example.spotlyrics.ui.AlbumColorBackground
import com.example.spotlyrics.ui.AlbumColorExtractor
import com.example.spotlyrics.ui.DynamicAlbumBackground
import com.example.spotlyrics.ui.PlayerViewModel
import com.example.spotlyrics.ui.SettingsDialog
import com.example.spotlyrics.ui.components.AlbumArtwork
import com.example.spotlyrics.ui.components.LyricsContent
import com.example.spotlyrics.ui.components.PlaybackControls
import com.example.spotlyrics.ui.components.TrackHeader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val connectionState by viewModel.connectionState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val lyricsStatus by viewModel.lyricsStatus.collectAsState()
    val artworkBitmap by viewModel.artworkBitmap.collectAsState()

    val currentTrack = playerState?.track
    val isConnected = connectionState is SpotifyConnectionState.Connected
    val isPlaying = playerState?.isPlaying ?: false
    // Collect synthetic playback position from ViewModel
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    // Track duration for slider range
    val durationMs = playerState?.durationMs ?: 0L
    // Use position for lyrics content
    val lyricsPositionMs = currentPositionMs
    // Slider drag state
    val dragPositionMs = remember { mutableStateOf<Long?>(null) }
    // Settings dialog state
    var showSettings by remember { mutableStateOf(false) }
    // Appearance preferences
    val appearanceMode by viewModel.appearanceMode.collectAsState()
    // Extracted album color for Album Color mode
    val albumColor by remember(artworkBitmap) {
        mutableStateOf(artworkBitmap?.let { AlbumColorExtractor.extractDarkenedColor(context, it) })
    }

    // Time formatting helper (simple mm:ss)
    fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background layer - fills entire screen
        when (appearanceMode) {
            AppearanceMode.AlbumColor -> {
                AlbumColorBackground(
                    color = albumColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
            AppearanceMode.DynamicAlbumArt -> {
                DynamicAlbumBackground(
                    context = context,
                    bitmap = artworkBitmap,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Foreground content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spotifly",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Album artwork responsive size (45% of screen width)
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            val artworkSize = screenWidth * 0.45f
            AlbumArtwork(
                bitmap = artworkBitmap,
                modifier = Modifier.size(artworkSize)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Track Header
            TrackHeader(
                track = if (isConnected) currentTrack else null
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Main content area handling connection states
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (connectionState) {
                    // Disconnected – show minimal info and connect button
                    SpotifyConnectionState.Disconnected -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Spotify Disconnected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Connect to Spotify App Remote to view live track info and lyrics.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = { viewModel.connect() }) {
                                    Text("Connect to Spotify")
                                }
                            }
                        }
                    }
                    // Connecting
                    SpotifyConnectionState.Connecting -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connecting to Spotify...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    // Error
                    is SpotifyConnectionState.Error -> {
                        val errorMessage = (connectionState as SpotifyConnectionState.Error).message
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Spotify Connection Error",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.connect() }) {
                                    Text("Reconnect")
                                }
                            }
                        }
                    }
                    // Connected
                    is SpotifyConnectionState.Connected -> {
                        if (currentTrack == null) {
                            // No track yet
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Waiting for a song...",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Play music on your Spotify app to display lyrics here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Active track UI
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                // Progress slider
                                if (durationMs > 0L) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Slider(
                                            value = (dragPositionMs.value ?: currentPositionMs).toFloat(),
                                            onValueChange = { v -> dragPositionMs.value = v.toLong() },
                                            onValueChangeFinished = {
                                                viewModel.seekTo(dragPositionMs.value ?: currentPositionMs)
                                                dragPositionMs.value = null
                                            },
                                            valueRange = 0f..durationMs.toFloat(),
                                            colors = androidx.compose.material3.SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                            ),
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp)
                                                .height(4.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatMs(dragPositionMs.value ?: currentPositionMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = formatMs(durationMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                                // Playback controls
                                PlaybackControls(
                                    isPlaying = isPlaying,
                                    enabled = isConnected && currentTrack != null,
                                    onPlay = { viewModel.play() },
                                    onPause = { viewModel.pause() },
                                    onSkipNext = { viewModel.skipNext() },
                                    onSkipPrevious = { viewModel.skipPrevious() }
                                )
                                // Lyrics heading
                                Text(
                                    text = "LYRICS",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                                // Lyrics viewport occupies remaining space
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    LyricsContent(
                                        lyricsStatus = lyricsStatus,
                                        positionMs = lyricsPositionMs,
                                        track = currentTrack,
                                        artworkBitmap = artworkBitmap,
                                        onRetry = { viewModel.retryLyrics() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Settings dialog
        if (showSettings) {
            SettingsDialog(
                viewModel = viewModel,
                context = context,
                onDismiss = { showSettings = false }
            )
        }
    }
}