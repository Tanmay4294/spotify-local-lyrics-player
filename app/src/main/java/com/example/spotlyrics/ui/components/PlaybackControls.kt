package com.example.spotlyrics.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous - simple white icon
        IconButton(
            onClick = onSkipPrevious,
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                modifier = Modifier.size(28.dp),
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f)
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Play/Pause - glass circular button
        IconButton(
            onClick = { if (isPlaying) onPause() else onPlay() },
            enabled = enabled
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                        shape = CircleShape
                    )
                    .drawWithContent {
                        drawContent()
                        if (enabled) {
                            // Radial gradient base
                            drawRect(
                                brush = Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.05f),
                                        Color.Black.copy(alpha = 0.4f)
                                    ),
                                    center = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    radius = 0.6f
                                ),
                                blendMode = BlendMode.SrcOver
                            )
                            // Subtle top highlight
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.White.copy(alpha = 0.12f),
                                    0.5f to Color.Transparent,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.SrcOver
                            )
                        }
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center),
                    tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f)
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Next - simple white icon
        IconButton(
            onClick = onSkipNext,
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next track",
                modifier = Modifier.size(28.dp),
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f)
            )
        }
    }
}