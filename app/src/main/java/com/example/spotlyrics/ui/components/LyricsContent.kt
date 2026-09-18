package com.example.spotlyrics.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotlyrics.lyrics.LyricsStatus
import com.example.spotlyrics.spotify.SpotifyTrack

@Composable
fun LyricsContent(
    lyricsStatus: LyricsStatus,
    track: SpotifyTrack?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (lyricsStatus) {
                is LyricsStatus.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Searching for lyrics...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is LyricsStatus.Found -> {
                    val lyrics = lyricsStatus.lyrics
                    val textToDisplay = when {
                        !lyrics.syncedText.isNullOrBlank() -> lyrics.syncedText
                        !lyrics.plainText.isNullOrBlank() -> lyrics.plainText
                        else -> "No lyrics content available"
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "Lyrics • ${lyrics.source}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = textToDisplay ?: "",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    lineHeight = 26.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                LyricsStatus.NotFound -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AlbumArtwork(modifier = Modifier.size(140.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No lyrics found",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "We couldn't find lyrics for this song.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is LyricsStatus.ProviderError -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AlbumArtwork(modifier = Modifier.size(120.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Lyrics Provider Error",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lyricsStatus.message ?: "Failed to fetch lyrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = onRetry) {
                            Text("Retry Lyrics")
                        }
                    }
                }
            }
        }
    }
}
