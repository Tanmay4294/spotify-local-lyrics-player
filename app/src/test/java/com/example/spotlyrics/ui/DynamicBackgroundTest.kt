package com.example.spotlyrics.ui

import android.graphics.Bitmap
import com.example.spotlyrics.preferences.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicBackgroundTest {

    @Test
    fun testNullBitmapReturnsFallbackWeightedPalette() {
        val palette = PaletteExtractor.extractWeightedPalette(null)
        assertNotNull(palette)
        assertTrue(palette.isNotEmpty())
        assertTrue(palette.size >= 3)
    }

    @Test
    fun testWeightedPaletteColorWeightsAreValidProportions() {
        val palette = PaletteExtractor.extractWeightedPalette(null)
        palette.forEach { weightedColor ->
            assertTrue(weightedColor.weight in 0.05f..1.0f)
            assertTrue(weightedColor.speedX > 0f)
            assertTrue(weightedColor.speedY > 0f)
        }
    }

    @Test
    fun testAppearanceModeEnumContainsDynamicBackground() {
        val mode = AppearanceMode.valueOf("DynamicBackground")
        assertEquals(AppearanceMode.DynamicBackground, mode)
        assertEquals(3, AppearanceMode.values().size)
    }
}
