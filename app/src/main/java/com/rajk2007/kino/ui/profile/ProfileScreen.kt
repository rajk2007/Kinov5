package com.rajk2007.kino.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import kotlin.random.Random

private const val PROFILE_PREFS = "kino_profile"
private const val USER_NAME_KEY = "user_name"
private const val IS_PREMIUM_KEY = "is_premium"

private val ProfileBackground = Color(0xFF080808)
private val ProfileSurface = Color(0xFF151517)
private val ProfileMuted = Color(0xFF96969B)
private val ProfileAccent = Color(0xFFE50914)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(PROFILE_PREFS, 0) }
    val guestNumber = remember {
        val stored = prefs.getInt("guest_number", 0)
        if (stored != 0) stored else Random.nextInt(1000, 10_000).also {
            prefs.edit().putInt("guest_number", it).apply()
        }
    }
    val initialName = remember { prefs.getString(USER_NAME_KEY, null).orEmpty() }
    var userName by remember { mutableStateOf(initialName) }
    var isPremium by remember { mutableStateOf(prefs.getBoolean(IS_PREMIUM_KEY, false)) }
    var showSignIn by remember { mutableStateOf(false) }
    var showPremium by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var comingSoonFor by remember { mutableStateOf<String?>(null) }
    var autoplay by remember { mutableStateOf(true) }
    var skipIntro by remember { mutableStateOf(true) }

    fun saveProfile(name: String) {
        userName = name.trim()
        prefs.edit()
            .putString(USER_NAME_KEY, userName)
            .putBoolean(IS_PREMIUM_KEY, true)
            .apply()
        isPremium = true
    }

    Box(Modifier.fillMaxSize().background(ProfileBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProfileHeader(
                    name = if (userName.isBlank()) "Guest#$guestNumber" else userName,
                    isPremium = isPremium,
                    isSignedIn = userName.isNotBlank(),
                    onSignIn = { showSignIn = true }
                )
            }
            item {
                ProfileSection("PLAYBACK SETTINGS", Icons.Default.PlayArrow) {
                    ProfileSwitchRow("Autoplay next episode", autoplay) { autoplay = it }
                    ProfileSwitchRow("Skip intros automatically", skipIntro) { skipIntro = it }
                }
            }
            item {
                ProfileSection("LANGUAGE SETTINGS", Icons.Default.Settings) {
                    ProfileRow("App language", "English") { comingSoonFor = "Language Settings" }
                    ProfileRow("Audio language", "Hindi") { comingSoonFor = "Language Settings" }
                    ProfileRow("Subtitle language", "English") { comingSoonFor = "Language Settings" }
                }
            }
            item {
                ProfileSection("APPEARANCE", Icons.Default.Settings) {
                    Text("Choose your viewing atmosphere", color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("AMOLED" to Color.Black, "RED" to Color(0xFF3A0B10), "PURPLE" to Color(0xFF241332)).forEach { (name, color) ->
                            ThemeTile(name, color) { comingSoonFor = "Appearance" }
                        }
                    }
                }
            }
            item {
                if (isPremium) {
                    PremiumBadgeCard()
                } else {
                    ExplorePremiumCard { showPremium = true }
                }
            }
            item {
                ProfileActionRow(Icons.Default.Info, "About Kino", "Version 1.2.0") { showAbout = true }
            }
        }

        if (showSignIn) {
            SignInDialog(
                onDismiss = { showSignIn = false },
                onConfirm = { name ->
                    if (name.isNotBlank()) {
                        saveProfile(name)
                        showSignIn = false
                    }
                }
            )
        }
        if (showPremium) {
            ModalBottomSheet(
                onDismissRequest = { showPremium = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF171719),
                dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted) }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)) {
                    Text("Kino Premium", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Your best viewing experience, unlocked.", color = ProfileMuted, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                    listOf("1080p downloads", "No ads", "Multi-device access").forEach { benefit ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
                            Text("✓", color = ProfileAccent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(benefit, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    Button(
                        onClick = {
                            isPremium = true
                            prefs.edit().putBoolean(IS_PREMIUM_KEY, true).apply()
                            showPremium = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent)
                    ) { Text("Buy Premium") }
                }
            }
        }
        if (showAbout) {
            ModalBottomSheet(
                onDismissRequest = { showAbout = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF171719),
                dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted) }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.ic_intro_logo), "Kino logo", contentScale = ContentScale.Fit, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)))
                    Text("by Raj Karmakar", color = ProfileMuted, modifier = Modifier.padding(top = 4.dp))
                    Text("Version 1.2.0", color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 28.dp))
                }
            }
        }
        comingSoonFor?.let { section ->
            ModalBottomSheet(
                onDismissRequest = { comingSoonFor = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF171719),
                dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted) }
            ) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(section, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Coming Soon", color = ProfileAccent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    Text("This premium setting is being polished for a future Kino update.", color = ProfileMuted, modifier = Modifier.padding(top = 8.dp, bottom = 26.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(name: String, isPremium: Boolean, isSignedIn: Boolean, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color(0xFFB30D17), Color(0xFF3A080D), ProfileBackground))), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 24.dp)) {
            LetterAvatar(name = name)
            Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            if (isPremium) {
                AssistChip(onClick = {}, label = { Text("Premium Member", color = Color(0xFFFFD86B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }, modifier = Modifier.padding(top = 8.dp))
            } else {
                OutlinedButton(onClick = onSignIn, modifier = Modifier.padding(top = 12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text(if (isSignedIn) "Account" else "Sign In")
                }
            }
        }
    }
}

@Composable
fun LetterAvatar(name: String, size: Dp = 96.dp) {
    val letter = name.trim().firstOrNull()?.uppercaseChar() ?: '#'
    val hue = ((letter.code * 37) % 360).toFloat()
    val backgroundColor = Color.hsv(hue, 0.68f, 0.72f)

    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString(),
            color = Color.White,
            fontSize = if (size.value >= 96f) 36.sp else 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProfileSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(ProfileSurface).padding(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Icon(icon, null, tint = ProfileAccent, modifier = Modifier.size(19.dp))
            Text(title, color = Color(0xFFB5B5BA), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
        }
        content()
    }
}

@Composable
private fun ProfileRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFFF1F1F1), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("$value  ›", color = ProfileMuted, fontSize = 14.sp)
    }
}

@Composable
private fun ProfileSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFFF1F1F1), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ProfileAccent))
    }
}

@Composable
private fun ThemeTile(name: String, color: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(0.32f).clip(RoundedCornerShape(12.dp)).background(color).clickable(onClick = onClick).padding(vertical = 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(25.dp).clip(CircleShape).background(ProfileAccent))
        Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp))
    }
}

@Composable
private fun ExplorePremiumCard(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF8E0B14), Color(0xFF331014)))).clickable(onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Explore Premium", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Unlock the full Kino experience", color = Color(0xFFFFC2C5), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(Icons.Default.ArrowForward, "Explore Premium", tint = Color.White)
    }
}

@Composable
private fun PremiumBadgeCard() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(ProfileSurface).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = Color(0xFFFFD86B), fontSize = 24.sp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Premium Member", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("All premium benefits are active", color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ProfileActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(ProfileSurface).clickable(onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ProfileAccent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.Default.ArrowForward, "Open", tint = ProfileMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun SignInDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B1E),
        title = { Text("Sign In to Kino", color = Color.White) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Your name", color = ProfileMuted) },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF29292D), unfocusedContainerColor = Color(0xFF29292D), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = ProfileAccent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent)) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ProfileMuted) } }
    )
}
