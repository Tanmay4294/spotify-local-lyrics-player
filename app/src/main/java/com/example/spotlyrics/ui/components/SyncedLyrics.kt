package com.example.spotlyrics.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
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

                LyricLineItem(
                    line = line,
                    isActive = isActive,
                    distance = distance
                )
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    distance: Int
) {
    val targetAlpha = when {
        isActive -> 1.0f
        distance == 1 -> 0.70f
        distance == 2 -> 0.50f
        else -> 0.35f
    }

    val targetSize = when {
        isActive -> 28.sp
        distance == 1 -> 24.sp
        else -> 22.sp
    }

    val targetWeight = when {
        isActive -> FontWeight.Bold
        distance == 1 -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 350),
        label = "lyricAlpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 1.00f,
        animationSpec = tween(durationMillis = 350),
        label = "lyricScale"
    )

    val animatedGlowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.45f else 0.0f,
        animationSpec = tween(durationMillis = 350),
        label = "lyricGlow"
    )

    val animatedColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.75f),
        animationSpec = tween(durationMillis = 350),
        label = "lyricColor"
    )

    val shimmerProgress = remember { Animatable(0f) }

    // Shimmer effect triggers ONCE when active status changes to true
    LaunchedEffect(isActive) {
        if (isActive) {
            shimmerProgress.snapTo(0f)
            shimmerProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750)
            )
        } else {
            shimmerProgress.snapTo(0f)
        }
    }

    val shimmerVal = shimmerProgress.value
    val isShimmering = isActive && shimmerVal > 0f && shimmerVal < 1f

    val textShadow = if (animatedGlowAlpha > 0.01f) {
        Shadow(
            color = Color.White.copy(alpha = animatedGlowAlpha),
            offset = Offset.Zero,
            blurRadius = 14f
        )
    } else null

    val baseStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = targetSize,
        fontWeight = targetWeight,
        lineHeight = (targetSize.value * 1.35f).sp,
        textMotion = TextMotion.Animated,
        shadow = textShadow
    )

    val finalStyle = if (isShimmering) {
        val center = shimmerVal
        val start = (center - 0.25f).coerceIn(0f, 1f)
        val end = (center + 0.25f).coerceIn(0f, 1f)
        baseStyle.copy(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = 0.75f),
                    start to Color.White.copy(alpha = 0.85f),
                    center to Color.White,
                    end to Color.White.copy(alpha = 0.85f),
                    1.0f to Color.White.copy(alpha = 0.75f)
                )
            )
        )
    } else {
        baseStyle
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = line.text.ifEmpty { " " },
            style = finalStyle,
            color = if (isShimmering) Color.Unspecified else animatedColor,
            textAlign = TextAlign.Center
        )
    }
}
