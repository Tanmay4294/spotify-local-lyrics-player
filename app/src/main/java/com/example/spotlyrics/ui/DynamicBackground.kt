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
            val color = saturateAndEnrichColor(swatch.rgb)
            val weight = (swatch.population / totalPopulation).coerceIn(0.15f, 0.65f)

            // Increased fluid motion speed parameters for clearly noticeable live animation
            val speedX = 0.55f + (index * 0.12f)
            val speedY = 0.45f + (index * 0.14f)
            val phaseX = index * 1.5f
            val phaseY = index * 2.3f

            // Strategic distribution across quadrants
            val baseX = when (index % 4) {
                0 -> 0.30f
                1 -> 0.70f
                2 -> 0.25f
                else -> 0.75f
            }
            val baseY = when (index % 4) {
                0 -> 0.30f
                1 -> 0.30f
                2 -> 0.70f
                else -> 0.70f
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

    private fun saturateAndEnrichColor(rgb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)

        // Boost saturation for vibrant, rich album-derived colors while maintaining soft glass tone
        hsv[1] = (hsv[1] * 1.45f + 0.20f).coerceIn(0.50f, 0.95f)
        // Ensure healthy brightness value
        hsv[2] = (hsv[2] * 1.20f + 0.15f).coerceIn(0.45f, 0.90f)

        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    fun defaultFallbackPalette(): List<WeightedColor> {
        return listOf(
            WeightedColor(Color(0xFF34495E), 0.40f, 0.55f, 0.45f, 0.0f, 0.0f, 0.30f, 0.30f),
            WeightedColor(Color(0xFF9B59B6), 0.30f, 0.65f, 0.55f, 1.5f, 1.8f, 0.70f, 0.30f),
            WeightedColor(Color(0xFF2980B9), 0.20f, 0.75f, 0.65f, 3.0f, 3.2f, 0.25f, 0.70f),
            WeightedColor(Color(0xFF1ABC9C), 0.15f, 0.85f, 0.75f, 4.5f, 4.7f, 0.75f, 0.70f)
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

    // Continuous smooth animation clock driving fluid organic liquid glass motion
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
        // Deep ambient base canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C10))
        )

        // Previous palette field (fading out during track transition)
        previousPalette?.let { prevList ->
            PaletteColorCanvas(
                palette = prevList,
                timeSeconds = timeSeconds,
                alpha = 1f - crossfadeAnim.value,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Current palette field (fading in during track transition)
        PaletteColorCanvas(
            palette = currentPalette,
            timeSeconds = timeSeconds,
            alpha = crossfadeAnim.value,
            modifier = Modifier.fillMaxSize()
        )

        // Soft liquid glass overlay (vignette & contrast filter)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle radial ambient vignette to accentuate glassy depth
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.25f),
                        Color.Black.copy(alpha = 0.45f)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = maxOf(size.width, size.height) * 0.75f
                )
            )
        }
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

        palette.forEachIndexed { index, item ->
            // Compound multi-wave harmonic motion for continuous fluid drift and morphing
            val driftX = (sin(timeSeconds * item.speedX + item.phaseX) * 0.32f +
                    cos(timeSeconds * 0.35f + item.phaseY) * 0.12f) * width
            val driftY = (cos(timeSeconds * item.speedY + item.phaseY) * 0.32f +
                    sin(timeSeconds * 0.40f + item.phaseX) * 0.12f) * height

            val centerX = (item.baseXRatio * width) + driftX
            val centerY = (item.baseYRatio * height) + driftY

            // Organic liquid expansion/contraction pulsating
            val pulse = 1f + 0.20f * sin(timeSeconds * 0.85f + index * 1.3f)
            val radius = maxDim * (0.50f + item.weight * 0.45f) * pulse

            // Rich multi-stop radial gradient for soft liquid glass color fields
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        item.color.copy(alpha = 0.85f),
                        item.color.copy(alpha = 0.55f),
                        item.color.copy(alpha = 0.20f),
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
