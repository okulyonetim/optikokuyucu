package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.okulyonetim.optikokuyucu.R
import kotlinx.coroutines.delay

/**
 * Static, aspect-ratio-safe startup artwork.
 *
 * Portrait phones/tablets fill the screen without stretching; only decorative outer edges may be
 * cropped. Landscape/wide displays keep the whole artwork visible and use the matching cream
 * background for any remaining space.
 */
@Composable
fun StaticStartupSplash(content: @Composable () -> Unit) {
    var splashVisible by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (splashVisible) {
            LaunchedEffect(Unit) {
                delay(SPLASH_DURATION_MS)
                splashVisible = false
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SplashBackground),
                contentAlignment = Alignment.Center
            ) {
                val portrait = maxHeight >= maxWidth
                Image(
                    painter = painterResource(R.drawable.startup_splash),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (portrait) ContentScale.Crop else ContentScale.Fit,
                    alignment = Alignment.Center
                )
            }
        }
    }
}

private const val SPLASH_DURATION_MS = 4_000L
private val SplashBackground = Color(0xFFFDFCF7)
