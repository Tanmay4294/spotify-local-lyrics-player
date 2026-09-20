package com.example.spotlyrics.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DynamicAlbumBackground(
    context: Context,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var currentBlurredBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previousBlurredBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isDarkAlbum by remember { mutableStateOf(false) }
    val alphaAnim = remember { Animatable(1f) }

    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            val darkDetected = withContext(Dispatchers.IO) {
                isAlbumDarkDominant(bitmap)
            }
            isDarkAlbum = darkDetected

            previousBlurredBitmap = currentBlurredBitmap
            val newBlurred = if (!darkDetected) {
                withContext(Dispatchers.IO) {
                    createBlurredBackgroundBitmap(bitmap)
                }
            } else {
                null
            }
            currentBlurredBitmap = newBlurred
            if (previousBlurredBitmap != null) {
                alphaAnim.snapTo(0f)
                alphaAnim.animateTo(1f, animationSpec = tween(durationMillis = 400))
                previousBlurredBitmap = null
            } else {
                alphaAnim.snapTo(1f)
            }
        } else {
            currentBlurredBitmap = null
            previousBlurredBitmap = null
            isDarkAlbum = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Fallback dark background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        )

        // Previous blurred background (fading out)
        previousBlurredBitmap?.let { prevBmp ->
            Image(
                bitmap = prevBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - alphaAnim.value }
            )
        }

        // Current blurred background (fading in) - only for non-dark albums
        currentBlurredBitmap?.let { currBmp ->
            Image(
                bitmap = currBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = alphaAnim.value }
            )
        }

        // Dark album: pure black background
        if (isDarkAlbum) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = alphaAnim.value }
                    .background(Color.Black)
            )
        }

        // Non-dark album: glassmorphic atmospheric layers
        if (!isDarkAlbum) {
            // Base dark overlay (subtle 20% black for vibrant background)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
            )

            // Subtle glass/frost layer (5% white frost)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.05f))
            )

            // Subtle glass highlight layer - top gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.04f),
                            0.3f to Color.Transparent,
                            1.0f to Color.Transparent
                        )
                    )
            )

            // Subtle glass highlight layer - bottom gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1.0f to Color.White.copy(alpha = 0.03f)
                        )
                    )
            )

            // Subtle radial vignette for depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.15f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(
                                x = 0.5f,
                                y = 0.5f
                            ),
                            radius = 0.8f
                        )
                    )
            )
        }
    }
}

/**
 * Determines if an album artwork is predominantly dark/black.
 * Samples pixels at reduced resolution to calculate dark pixel proportion.
 */
private fun isAlbumDarkDominant(bitmap: Bitmap): Boolean {
    // Sample at reduced resolution for performance
    val sampleSize = max(bitmap.width, bitmap.height) / 100
    val step = max(sampleSize, 1)

    var darkPixels = 0
    var totalPixels = 0
    var luminanceSum = 0f

    // Luminance threshold (0.0-1.0): pixels below this are considered "dark"
    val luminanceThreshold = 0.28f
    // Proportion threshold: if dark pixels exceed this, album is dark-dominant
    val proportionThreshold = 0.60f

    for (y in 0 until bitmap.height step step) {
        for (x in 0 until bitmap.width step step) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // Calculate perceived luminance (sRGB)
            val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
            luminanceSum += luminance

            if (luminance < luminanceThreshold) {
                darkPixels++
            }
            totalPixels++
        }
    }

    val avgLuminance = if (totalPixels > 0) luminanceSum / totalPixels else 1f
    val darkProportion = if (totalPixels > 0) darkPixels.toFloat() / totalPixels else 0f

    // Album is dark-dominant if:
    // - At least 60% of pixels are dark (luminance < 0.28), OR
    // - Average luminance is very low (< 0.20)
    return darkProportion >= proportionThreshold || avgLuminance < 0.20f
}

/**
 * Creates a blurred background bitmap with saturation, contrast, and brightness adjustments.
 * Runs on IO thread to avoid blocking UI.
 */
private fun createBlurredBackgroundBitmap(bitmap: Bitmap): ImageBitmap {
    // Scale down to 20% for blur processing, then scale back up
    val scale = 0.2f
    val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

    val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    val blurred = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Color matrix for saturation ~1.4, brightness ~0.92, contrast ~1.08
    val cm = android.graphics.ColorMatrix()
    cm.setSaturation(1.4f)

    val contrast = 1.08f
    val brightness = 0.92f
    val cScale = contrast * brightness
    val cOffset = (1f - contrast) * 128f * brightness

    val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
        cScale, 0f, 0f, 0f, cOffset,
        0f, cScale, 0f, 0f, cOffset,
        0f, 0f, cScale, 0f, cOffset,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(contrastMatrix)
    
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
    paint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)

    val canvas = Canvas(blurred)
    canvas.drawBitmap(scaled, 0f, 0f, paint)

    // Scale back up to full bitmap dimensions
    val result = Bitmap.createScaledBitmap(blurred, bitmap.width, bitmap.height, true)

    if (scaled != bitmap && !scaled.isRecycled) scaled.recycle()
    if (!blurred.isRecycled) blurred.recycle()

    return result.asImageBitmap()
}