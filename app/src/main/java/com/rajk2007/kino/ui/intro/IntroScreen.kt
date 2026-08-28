package com.rajk2007.kino.ui.intro

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import kotlinx.coroutines.delay

private const val INTRO_FADE_IN_MS = 1_500
private const val INTRO_HOLD_MS = 500
private const val INTRO_FADE_OUT_MS = 500

@Composable
fun IntroScreen(onFinished: () -> Unit) {
    var targetAlpha by remember { mutableFloatStateOf(0f) }
    var targetScale by remember { mutableFloatStateOf(0.8f) }

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (targetAlpha == 0f) INTRO_FADE_OUT_MS else INTRO_FADE_IN_MS,
            easing = FastOutSlowInEasing
        ),
        label = "intro_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(INTRO_FADE_IN_MS, easing = FastOutSlowInEasing),
        label = "intro_scale"
    )

    LaunchedEffect(Unit) {
        targetAlpha = 1f
        targetScale = 1f
        delay(INTRO_FADE_IN_MS.toLong() + INTRO_HOLD_MS)
        targetAlpha = 0f
        delay(INTRO_FADE_OUT_MS.toLong())
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x66E50914),
                            Color(0x22000000),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Image(
            painter = painterResource(R.drawable.ic_intro_logo),
            contentDescription = "Kino",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(300.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = CircleShape,
                    ambientColor = Color(0xFFE50914),
                    spotColor = Color(0xFFE50914)
                )
                .graphicsLayer { this.alpha = alpha }
                .scale(scale)
        )
    }
}
