package com.example.spotlyrics.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotlyrics.lyrics.LrcParser
import com.example.spotlyrics.lyrics.LyricLine

fun Modifier.lyricsFadeMask(): Modifier = this
    .clipToBounds()
    .graphicsLayer { alpha = 0.99f }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.12f to Color.Black,
                0.88f to Color.Black,
                1.0f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val activeLineIndex = LrcParser.activeLine(lines, positionMs)
    val listState = rememberLazyListState()

    // Smooth auto-scroll when active line index changes or lines instance changes
    LaunchedEffect(lines) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0 && activeLineIndex < lines.size) {
            val scrollIndex = (activeLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(scrollIndex)
        }
    }

    if (lines.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No synchronized lyrics available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .lyricsFadeMask(),
            contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeLineIndex
                val distance = if (activeLineIndex >= 0) kotlin.math.abs(index - activeLineIndex) else 999

                val textColor by animateColorAsState(
                    targetValue = if (isActive) {
                        Color.White // active line pure white
                    } else {
                        Color.White.copy(alpha = 0.7f) // softer white for other lines
                    },
                    animationSpec = tween(durationMillis = 300),
                    label = "lyricTextColor"
                )

                val targetAlpha = when {
                    isActive -> 1.0f
                    distance == 1 -> 0.65f
                    distance == 2 -> 0.45f
                    else -> 0.25f
                }

                val targetSize = when {
                    isActive -> 23.sp
                    distance == 1 -> 19.sp
                    else -> 17.sp
                }

                val targetWeight = when {
                    isActive -> FontWeight.Bold
                    distance == 1 -> FontWeight.SemiBold
                    else -> FontWeight.Normal
                }

                Text(
                    text = line.text.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = targetSize,
                        fontWeight = targetWeight,
                        lineHeight = 28.sp
                    ),
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .alpha(targetAlpha)
                )
            }
        }
    }
}
