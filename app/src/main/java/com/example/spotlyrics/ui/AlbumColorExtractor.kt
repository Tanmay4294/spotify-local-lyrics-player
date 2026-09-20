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
                val vibrant = palette.vibrantSwatch
                val darkVibrant = palette.darkVibrantSwatch
                val lightVibrant = palette.lightVibrantSwatch
                val muted = palette.mutedSwatch
                val darkMuted = palette.darkMutedSwatch

                val swatch = vibrant ?: darkVibrant ?: lightVibrant ?: muted ?: darkMuted

                swatch?.let { s ->
                    darkenColor(s.rgb, 0.35f)
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