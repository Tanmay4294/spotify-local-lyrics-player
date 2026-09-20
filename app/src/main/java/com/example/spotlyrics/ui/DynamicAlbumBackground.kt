package com.example.spotlyrics.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DynamicAlbumBackground(
    context: Context,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var currentProcessedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previousProcessedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val alphaAnim = remember { Animatable(1f) }

    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            previousProcessedBitmap = currentProcessedBitmap
            val newProcessed = withContext(Dispatchers.IO) {
                createAtmosphericBackgroundBitmap(bitmap)
            }
            currentProcessedBitmap = newProcessed
            if (previousProcessedBitmap != null) {
                alphaAnim.snapTo(0f)
                alphaAnim.animateTo(1f, animationSpec = tween(durationMillis = 400))
                previousProcessedBitmap = null
            } else {
                alphaAnim.snapTo(1f)
            }
        } else {
            currentProcessedBitmap = null
            previousProcessedBitmap = null
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

        // Previous processed background (fading out)
        previousProcessedBitmap?.let { prevBmp ->
            Image(
                bitmap = prevBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 1f - alphaAnim.value
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = RenderEffect
                                .createBlurEffect(70f, 70f, Shader.TileMode.MIRROR)
                                .asComposeRenderEffect()
                        }
                    }
                    .then(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            Modifier.blur(35.dp)
                        } else Modifier
                    )
            )
        }

        // Current processed background (fading in)
        currentProcessedBitmap?.let { currBmp ->
            Image(
                bitmap = currBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = alphaAnim.value
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = RenderEffect
                                .createBlurEffect(70f, 70f, Shader.TileMode.MIRROR)
                                .asComposeRenderEffect()
                        }
                    }
                    .then(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            Modifier.blur(35.dp)
                        } else Modifier
                    )
            )
        }

        // Glassmorphic atmospheric overlays
        // Base dark overlay (subtle 22% black for vibrant glass depth)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
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

/**
 * Determines if an album artwork is predominantly dark/black.
 * Samples pixels at reduced resolution to calculate dark pixel proportion.
 */
private fun isAlbumDarkDominant(bitmap: Bitmap): Boolean {
    val sampleSize = max(bitmap.width, bitmap.height) / 100
    val step = max(sampleSize, 1)

    var darkPixels = 0
    var totalPixels = 0
    var luminanceSum = 0f

    val luminanceThreshold = 0.28f
    val proportionThreshold = 0.60f

    for (y in 0 until bitmap.height step step) {
        for (x in 0 until bitmap.width step step) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

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

    return darkProportion >= proportionThreshold || avgLuminance < 0.20f
}

/**
 * Creates an atmospheric background bitmap from high-resolution album artwork.
 * Applies color matrix adjustments (saturation ~1.35x, contrast ~1.08x, brightness ~0.95x)
 * and software pre-blur for pre-Android 12 devices while keeping full native resolution.
 * Runs on IO thread.
 */
private fun createAtmosphericBackgroundBitmap(bitmap: Bitmap): ImageBitmap {
    val outWidth = bitmap.width
    val outHeight = bitmap.height

    val processed = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val cm = android.graphics.ColorMatrix()
    cm.setSaturation(1.35f)

    val contrast = 1.08f
    val brightness = 0.95f
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

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        paint.maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
    }

    val canvas = Canvas(processed)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)

    return processed.asImageBitmap()
}