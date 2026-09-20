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
    val alphaAnim = remember { Animatable(1f) }

    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            previousBlurredBitmap = currentBlurredBitmap
            val newBlurred = withContext(Dispatchers.IO) {
                createBlurredBackgroundBitmap(context, bitmap)
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

        // Current blurred background (fading in)
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

        // Dark overlay for contrast and readability (75% black overlay)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
        )
    }
}

/**
 * Creates a blurred background bitmap scaled to fill the entire screen.
 * Runs on IO thread to avoid blocking UI.
 */
private fun createBlurredBackgroundBitmap(context: Context, bitmap: Bitmap): ImageBitmap {
    // Get screen dimensions
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    windowManager.defaultDisplay.getRealMetrics(metrics)
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels

    // Scale bitmap to fill screen (crop to aspect ratio)
    val sourceAspect = bitmap.width.toFloat() / bitmap.height
    val screenAspect = screenWidth.toFloat() / screenHeight

    val (scaledWidth, scaledHeight) = if (sourceAspect > screenAspect) {
        // Source is wider - scale by height
        val h = screenHeight
        val w = (h * sourceAspect).toInt()
        w to h
    } else {
        // Source is taller - scale by width
        val w = screenWidth
        val h = (w / sourceAspect).toInt()
        w to h
    }

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

    // Scale back up to screen dimensions
    val result = Bitmap.createScaledBitmap(blurred, scaledWidth, scaledHeight, true)

    if (scaled != bitmap && !scaled.isRecycled) scaled.recycle()
    if (!blurred.isRecycled) blurred.recycle()

    return result.asImageBitmap()
}