package com.example.spotlyrics.ui.components

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

    // Hoisted shimmer animation state driven directly by activeLineIndex
    val shimmerAnim = remember { Animatable(1f) }

    LaunchedEffect(lines) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0 && activeLineIndex < lines.size) {
            val scrollIndex = (activeLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(scrollIndex)

            // Trigger one-shot shimmer pass for the newly active line
            shimmerAnim.snapTo(0f)
            shimmerAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650)
            )
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
                    distance = distance,
                    shimmerProgress = if (isActive) shimmerAnim.value else 1f
                )
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    distance: Int,
    shimmerProgress: Float
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

    // Inactive lines fade out smoothly; active line is immediately 1.0f on frame 0
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 200),
        label = "lyricAlpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 1.00f,
        animationSpec = tween(durationMillis = 200),
        label = "lyricScale"
    )

    // Immediate active glow shadow on frame 0 when isActive is true
    val textShadow = if (isActive) {
        Shadow(
            color = Color.White.copy(alpha = 0.50f),
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

    val isShimmering = isActive && shimmerProgress < 1f

    val finalStyle = if (isShimmering) {
        val center = shimmerProgress
        val start = (center - 0.25f).coerceIn(0f, 1f)
        val end = (center + 0.25f).coerceIn(0f, 1f)
        baseStyle.copy(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = 0.80f),
                    start to Color.White.copy(alpha = 0.90f),
                    center to Color.White,
                    end to Color.White.copy(alpha = 0.90f),
                    1.0f to Color.White.copy(alpha = 0.80f)
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
                alpha = if (isActive) 1.0f else animatedAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = line.text.ifEmpty { " " },
            style = finalStyle,
            color = if (isActive) Color.White else Color.White.copy(alpha = targetAlpha),
            textAlign = TextAlign.Center
        )
    }
}
