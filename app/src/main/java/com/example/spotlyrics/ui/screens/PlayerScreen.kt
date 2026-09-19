package com.example.spotlyrics.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spotlyrics.spotify.SpotifyConnectionState
import com.example.spotlyrics.ui.PlayerViewModel
import com.example.spotlyrics.ui.components.AlbumArtwork
import com.example.spotlyrics.ui.components.LyricsContent
import com.example.spotlyrics.ui.components.PlaybackControls
import com.example.spotlyrics.ui.components.TrackHeader

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
    // Time formatting helper (simple mm:ss)
    fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
    
    // In UI, after LyricsContent (replace positionMs with lyricsPositionMs) add Slider
    // We'll modify the LyricsContent call later.


    val (showDiagnostics, setShowDiagnostics) = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Track Header (shows track metadata or title fallback)
        TrackHeader(
            track = if (isConnected) currentTrack else null
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Center Area: Main Content Box (Handling 8 states)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (connectionState) {
                // State 1: Disconnected
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
                            AlbumArtwork(
                                bitmap = artworkBitmap,
                                modifier = Modifier.size(140.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
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

                // State 2: Connecting
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

                // State 7: Spotify Error
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

                // Connected States (3, 4, 5, 6, 8)
                is SpotifyConnectionState.Connected -> {
                    if (currentTrack == null) {
                        // State 3: Connected / No Track
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AlbumArtwork(
                                bitmap = artworkBitmap,
                                modifier = Modifier.size(160.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
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
                        // States 4, 5, 6, 8: Active Track
                        // Use synthetic playback position for lyrics
                        LyricsContent(
                            lyricsStatus = lyricsStatus,
                            positionMs = lyricsPositionMs,
                            track = currentTrack,
                            artworkBitmap = artworkBitmap,
                            onRetry = { viewModel.retryLyrics() },
                            modifier = Modifier.fillMaxSize()
                        )
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
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = formatMs(dragPositionMs.value ?: currentPositionMs))
                                    Text(text = formatMs(durationMs))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback Controls Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlaybackControls(
                isPlaying = isPlaying,
                enabled = isConnected && currentTrack != null,
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrevious = { viewModel.skipPrevious() }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnected) {
                    OutlinedButton(onClick = { viewModel.disconnect() }) {
                        Text("Disconnect")
                    }
                }
                OutlinedButton(onClick = { setShowDiagnostics(true) }) {
                    Text("Diagnostics")
                }
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            viewModel = viewModel,
            onDismiss = { setShowDiagnostics(false) }
        )
    }
}
