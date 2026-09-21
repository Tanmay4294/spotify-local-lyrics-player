package com.example.spotlyrics.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

data class WeightedColor(
    val color: Color,
    val weight: Float,
    val speedX: Float,
    val speedY: Float,
    val phaseX: Float,
    val phaseY: Float,
    val baseXRatio: Float,
    val baseYRatio: Float
)

object PaletteExtractor {
    fun extractWeightedPalette(bitmap: Bitmap?): List<WeightedColor> {
        if (bitmap == null) {
            return defaultFallbackPalette()
        }

        val palette = try {
            Palette.from(bitmap).maximumColorCount(16).generate()
        } catch (e: Exception) {
            null
        } ?: return defaultFallbackPalette()

        val swatches = palette.swatches.filter { it.population > 0 }
        if (swatches.isEmpty()) {
            return defaultFallbackPalette()
        }

        val totalPopulation = swatches.sumOf { it.population }.toFloat()

        // Extract top swatches sorted by population proportion
        val topSwatches = swatches
            .sortedByDescending { it.population }
            .take(6)

        val result = mutableListOf<WeightedColor>()

        topSwatches.forEachIndexed { index, swatch ->
            val color = Color(swatch.rgb)
            val weight = (swatch.population / totalPopulation).coerceIn(0.12f, 0.60f)

            // Low-frequency harmonic motion parameters per swatch
            val speedX = 0.08f + (index * 0.025f)
            val speedY = 0.06f + (index * 0.035f)
            val phaseX = index * 1.4f
            val phaseY = index * 2.2f

            // Quadrant layout distribution ratios
            val baseX = when (index % 4) {
                0 -> 0.35f
                1 -> 0.65f
                2 -> 0.25f
                else -> 0.75f
            }
            val baseY = when (index % 4) {
                0 -> 0.35f
                1 -> 0.35f
                2 -> 0.65f
                else -> 0.65f
            }

            result.add(
                WeightedColor(
                    color = color,
                    weight = weight,
                    speedX = speedX,
                    speedY = speedY,
                    phaseX = phaseX,
                    phaseY = phaseY,
                    baseXRatio = baseX,
                    baseYRatio = baseY
                )
            )
        }

        return if (result.isEmpty()) defaultFallbackPalette() else result
    }

    fun defaultFallbackPalette(): List<WeightedColor> {
        return listOf(
            WeightedColor(Color(0xFF2C3E50), 0.40f, 0.08f, 0.06f, 0.0f, 0.0f, 0.35f, 0.35f),
            WeightedColor(Color(0xFF8E44AD), 0.30f, 0.10f, 0.07f, 1.4f, 1.6f, 0.65f, 0.35f),
            WeightedColor(Color(0xFF2980B9), 0.20f, 0.07f, 0.09f, 2.8f, 3.0f, 0.25f, 0.65f),
            WeightedColor(Color(0xFF16A085), 0.10f, 0.11f, 0.05f, 4.2f, 4.4f, 0.75f, 0.65f)
        )
    }
}

@Composable
fun DynamicBackground(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var currentPalette by remember { mutableStateOf(PaletteExtractor.extractWeightedPalette(bitmap)) }
    var previousPalette by remember { mutableStateOf<List<WeightedColor>?>(null) }
    val crossfadeAnim = remember { Animatable(1f) }

    var timeSeconds by remember { mutableStateOf(0f) }

    // Palette extraction occurs ONCE on bitmap change off main thread
    LaunchedEffect(bitmap) {
        val newPalette = withContext(Dispatchers.IO) {
            PaletteExtractor.extractWeightedPalette(bitmap)
        }
        previousPalette = currentPalette
        currentPalette = newPalette
        crossfadeAnim.snapTo(0f)
        crossfadeAnim.animateTo(1f, animationSpec = tween(durationMillis = 800))
        previousPalette = null
    }

    // Continuous smooth animation clock driving fluid organic drift
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val deltaSeconds = (frameNanos - lastNanos) / 1_000_000_000f
                    timeSeconds += deltaSeconds
                }
                lastNanos = frameNanos
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Base dark ambient canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F12))
        )

        // Previous palette field (fading out during crossfade)
        previousPalette?.let { prevList ->
            PaletteColorCanvas(
                palette = prevList,
                timeSeconds = timeSeconds,
                alpha = 1f - crossfadeAnim.value,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Current palette field (fading in during crossfade)
        PaletteColorCanvas(
            palette = currentPalette,
            timeSeconds = timeSeconds,
            alpha = crossfadeAnim.value,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle dark translucent overlay (30% black) for contrast & readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f))
        )
    }
}

@Composable
private fun PaletteColorCanvas(
    palette: List<WeightedColor>,
    timeSeconds: Float,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
    ) {
        val width = size.width
        val height = size.height
        val maxDim = maxOf(width, height)

        palette.forEach { item ->
            // Continuous harmonic drift path
            val offsetX = (sin(timeSeconds * item.speedX + item.phaseX) * 0.22f * width)
            val offsetY = (cos(timeSeconds * item.speedY + item.phaseY) * 0.22f * height)

            val centerX = (item.baseXRatio * width) + offsetX
            val centerY = (item.baseYRatio * height) + offsetY

            // Scale radius based on color population weight
            val radius = maxDim * (0.45f + item.weight * 0.55f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        item.color.copy(alpha = 0.70f),
                        item.color.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                center = Offset(centerX, centerY),
                radius = radius
            )
        }
    }
}
