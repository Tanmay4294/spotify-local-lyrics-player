package com.example.spotlyrics.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.util.DisplayMetrics
import android.view.WindowManager
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
                    createBlurredBackgroundBitmap(context, bitmap)
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
                contentScale = ContentScale.Fit,
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
                contentScale = ContentScale.Fit,
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

        // Dark overlay for contrast and readability (75% black overlay) - only for non-dark albums
        if (!isDarkAlbum) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
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

    // Luminance threshold (0.0-1.0): pixels below this are considered "dark"
    val luminanceThreshold = 0.22f
    // Proportion threshold: if dark pixels exceed this, album is dark-dominant
    val proportionThreshold = 0.75f

    for (y in 0 until bitmap.height step step) {
        for (x in 0 until bitmap.width step step) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // Calculate perceived luminance (sRGB)
            val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b

            if (luminance < luminanceThreshold) {
                darkPixels++
            }
            totalPixels++
        }
    }

    return if (totalPixels > 0) {
        darkPixels.toFloat() / totalPixels >= proportionThreshold
    } else {
        false
    }
}

/**
 * Creates a blurred background bitmap with conservative scaling (less zoomed).
 * Runs on IO thread to avoid blocking UI.
 */
private fun createBlurredBackgroundBitmap(context: Context, bitmap: Bitmap): ImageBitmap {
    // Get screen dimensions
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    windowManager.defaultDisplay.getRealMetrics(metrics)
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels

    // Calculate fit scale (entire artwork visible) and cover scale (screen filled)
    val fitScale = min(screenWidth.toFloat() / bitmap.width, screenHeight.toFloat() / bitmap.height)
    val coverScale = max(screenWidth.toFloat() / bitmap.width, screenHeight.toFloat() / bitmap.height)

    // Use a scale between fit and cover: ~1.20x fit scale for less zoom
    val targetScale = fitScale * 1.20f

    // Clamp to cover scale as maximum
    val finalScale = min(targetScale, coverScale)

    val scaledWidth = (bitmap.width * finalScale).toInt()
    val scaledHeight = (bitmap.height * finalScale).toInt()

    // Scale down for faster blur processing (target ~400px on shorter side for quality/performance)
    val blurScale = 400f / min(scaledWidth, scaledHeight)
    val blurWidth = (scaledWidth * blurScale).toInt().coerceAtLeast(1)
    val blurHeight = (scaledHeight * blurScale).toInt().coerceAtLeast(1)

    val scaled = Bitmap.createScaledBitmap(bitmap, blurWidth, blurHeight, true)
    val blurred = Bitmap.createBitmap(blurWidth, blurHeight, Bitmap.Config.ARGB_8888)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Strong blur radius for atmospheric effect
    paint.maskFilter = BlurMaskFilter(25f * blurScale, BlurMaskFilter.Blur.NORMAL)

    val canvas = Canvas(blurred)
    canvas.drawBitmap(scaled, 0f, 0f, paint)

    // Scale back up to target dimensions
    val result = Bitmap.createScaledBitmap(blurred, scaledWidth, scaledHeight, true)

    if (scaled != bitmap && !scaled.isRecycled) scaled.recycle()
    if (!blurred.isRecycled) blurred.recycle()

    return result.asImageBitmap()
}