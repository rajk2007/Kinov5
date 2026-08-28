package com.rajk2007.kino.ui.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var phase by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    val logoScale by animateFloatAsState(
        targetValue = if (phase == 0) 1.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "kino_logo_scale"
    )

    LaunchedEffect(Unit) {
        delay(2000)
        phase = 1
        delay(500)
        phase = 2
        delay(500)
        phase = 3
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (phase < 3) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_kino_logo),
                    contentDescription = "Kino logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(180.dp).scale(logoScale)
                )
                AnimatedVisibility(visible = phase >= 1, enter = fadeIn()) {
                    Text("By", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp))
                }
                AnimatedVisibility(visible = phase >= 2, enter = fadeIn()) {
                    Text(
                        "R Karmakar",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_kino_logo),
                    contentDescription = "Kino logo",
                    modifier = Modifier.size(120.dp)
                )
                Spacer(modifier = Modifier.height(36.dp))
                Text(
                    "Welcome to Kino",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "What should we call you?",
                    color = Color.LightGray,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Your name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF171717),
                        unfocusedContainerColor = Color(0xFF171717),
                        focusedIndicatorColor = Color(0xFFE50914),
                        unfocusedIndicatorColor = Color.DarkGray,
                        focusedLabelColor = Color(0xFFE50914),
                        unfocusedLabelColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            context.getSharedPreferences("kino_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("user_name", trimmed)
                                .apply()
                            onComplete()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Let's Go", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
