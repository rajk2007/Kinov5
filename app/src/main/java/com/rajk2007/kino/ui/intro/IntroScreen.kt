package com.rajk2007.kino.ui.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import kotlinx.coroutines.delay

private const val INTRO_FADE_IN_MS = 1_000
private const val INTRO_HOLD_MS = 1_000
private const val INTRO_FADE_OUT_MS = 500

@Composable
fun IntroScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay((INTRO_FADE_IN_MS + INTRO_HOLD_MS).toLong())
        visible = false
        delay(INTRO_FADE_OUT_MS.toLong())
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(420.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x80E50914),
                            Color(0x30000000),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(INTRO_FADE_IN_MS)) + scaleIn(
                initialScale = 0.8f,
                animationSpec = tween(INTRO_FADE_IN_MS)
            ),
            exit = fadeOut(tween(INTRO_FADE_OUT_MS))
        ) {
            Image(
                painter = painterResource(R.drawable.ic_intro_logo),
                contentDescription = "Kino",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(2f / 3f)
                    .shadow(
                        elevation = 32.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFFE50914),
                        spotColor = Color(0xFFE50914)
                    )
            )
        }
    }
}
