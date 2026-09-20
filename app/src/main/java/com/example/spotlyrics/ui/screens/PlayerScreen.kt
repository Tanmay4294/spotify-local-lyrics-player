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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spotlyrics.preferences.AppearanceMode
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings

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
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs = playerState?.durationMs ?: 0L
    val lyricsPositionMs = currentPositionMs
    val dragPositionMs = remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val appearanceMode by viewModel.appearanceMode.collectAsState()
    val albumColor by remember(artworkBitmap) {
        mutableStateOf(artworkBitmap?.let { AlbumColorExtractor.extractDarkenedColor(context, it) })
    }

    fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background layer
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

        // Foreground player layout (7 components in strict vertical order)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // 1. Top bar (Spotifly title + Settings icon)
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

            // 2. Album artwork (responsive, capped at min(45% width, 25% height))
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val screenHeight = configuration.screenHeightDp.dp
            val artworkSize = minOf(screenWidth * 0.45f, screenHeight * 0.25f)
            AlbumArtwork(
                bitmap = artworkBitmap,
                modifier = Modifier.size(artworkSize)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 3. Track information
            TrackHeader(
                track = if (isConnected) currentTrack else null
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 4. Progress / seek bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = (dragPositionMs.value ?: currentPositionMs).toFloat(),
                    onValueChange = { v -> dragPositionMs.value = v.toLong() },
                    onValueChangeFinished = {
                        viewModel.seekTo(dragPositionMs.value ?: currentPositionMs)
                        dragPositionMs.value = null
                    },
                    valueRange = 0f..(durationMs.coerceAtLeast(1L)).toFloat(),
                    enabled = isConnected && currentTrack != null,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.onBackground,
                        activeTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .height(2.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
            Spacer(modifier = Modifier.height(8.dp))

            // 5. Playback controls (Previous, Play/Pause, Next)
            PlaybackControls(
                isPlaying = isPlaying,
                enabled = isConnected && currentTrack != null,
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrevious = { viewModel.skipPrevious() }
            )

            // 6. LYRICS heading
            Text(
                text = "LYRICS",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            // 7. Dedicated bounded lyrics viewport
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
                    connectionState = connectionState,
                    onRetry = { viewModel.retryLyrics() },
                    modifier = Modifier.fillMaxSize()
                )
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