package com.example.spotlyrics.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AlbumColorBackground(
    color: Int?,
    modifier: Modifier = Modifier
) {
    val targetColor = if (color != null) Color(color) else Color(0xFF121212)
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "AlbumColorTransition"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedColor)
    )
}