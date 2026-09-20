package com.example.spotlyrics.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.palette.graphics.Palette

object AlbumColorExtractor {

    /**
     * Extracts a prominent color from the album artwork, darkened for background use.
     * Returns null if bitmap is null or color extraction fails.
     */
    fun extractDarkenedColor(context: Context, bitmap: Bitmap?): Int? {
        return bitmap?.let { bmp ->
            Palette.from(bmp).generate().let { palette ->
                val swatch = palette.vibrantSwatch
                    ?: palette.darkVibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.darkMutedSwatch

                swatch?.let { s ->
                    val rgb = s.rgb
                    // Convert to HSV (hue, saturation, value)
                    val hsv = FloatArray(3)
                    android.graphics.Color.RGBToHSV(
                        (rgb shr 16) and 0xFF,
                        (rgb shr 8) and 0xFF,
                        rgb and 0xFF,
                        hsv
                    )
                    // Clamp saturation and value to avoid neon/bright colors
                    hsv[1] = hsv[1].coerceAtMost(0.5f) // max 50% saturation
                    hsv[2] = hsv[2].coerceAtMost(0.4f) // max 40% brightness
                    // Return opaque color with clamped HSV
                    android.graphics.Color.HSVToColor(0xFF, hsv)
                }
            }
        }
    }

    /**
     * Darkens a color by the given factor (0.0 = black, 1.0 = original).
     * For background use, we want a very dark color.
     */
    private fun darkenColor(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt()
        val g = ((color shr 8 and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
}