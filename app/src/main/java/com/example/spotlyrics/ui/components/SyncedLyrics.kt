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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotlyrics.lyrics.LrcParser
import com.example.spotlyrics.lyrics.LyricLine

@Composable
fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val activeLineIndex = LrcParser.activeLine(lines, positionMs)
    val listState = rememberLazyListState()

    // Smooth auto-scroll when active line index changes
    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0 && activeLineIndex < lines.size) {
            val scrollIndex = (activeLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(scrollIndex)
        }
    }

    if (lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
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
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeLineIndex

                val textColor by animateColorAsState(
                    targetValue = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = tween(durationMillis = 300),
                    label = "lyricTextColor"
                )

                val targetAlpha = if (isActive) 1.0f else 0.45f
                val targetSize = if (isActive) 20.sp else 16.sp
                val targetWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

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
