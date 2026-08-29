package com.rajk2007.kino.ui.profile

import android.content.Context
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val ProfileBackground = Color(0xFF080808)
private val ProfileSurface = Color(0xFF151517)
private val ProfileMuted = Color(0xFF96969B)
private val ProfileAccent = Color(0xFFE50914)
private val ProfileGold = Color(0xFFFFD86B)
private const val PROFILE_PREFS = "kino_profile"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onExtensionsClick: () -> Unit = {}) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf(preferences.getString("user_name", null)) }
    var isPremium by remember { mutableStateOf(preferences.getBoolean("is_premium", false)) }
    var avatarIndex by remember { mutableStateOf(preferences.getInt("avatar_index", 0)) }
    val guestNumber = remember {
        preferences.getInt("guest_number", 0).takeIf { it in 1000..9999 } ?: Random.nextInt(1000, 10000).also {
            preferences.edit().putInt("guest_number", it).apply()
        }
    }

    var autoplay by rememberSaveable { mutableStateOf(true) }
    var skipIntro by rememberSaveable { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<ProfileSheet?>(null) }
    var showSignIn by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(ProfileBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PremiumProfileHeader(
                    displayName = name ?: "Guest#$guestNumber",
                    avatarIndex = avatarIndex,
                    isPremium = isPremium,
                    onSignIn = { showSignIn = true },
                    onAvatarClick = {
                        avatarIndex = (avatarIndex + 1) % avatarColors.size
                        preferences.edit().putInt("avatar_index", avatarIndex).apply()
                    }
                )
            }
            item {
                if (isPremium) {
                    PremiumMemberCard()
                } else {
                    ExplorePremiumCard { dialog = ProfileSheet.Premium }
                }
            }
            item {
                ProfileSection(title = "PLAYBACK SETTINGS", icon = Icons.Default.PlayArrow) {
                    ProfileSwitchRow("Autoplay next episode", autoplay) { autoplay = it }
                    ProfileSwitchRow("Skip intros automatically", skipIntro) { skipIntro = it }
                }
            }
            item {
                ProfileSection(title = "LANGUAGE SETTINGS", icon = Icons.Default.Settings) {
                    ProfileActionRow("App language", "English") { dialog = ProfileSheet.Language }
                    ProfileActionRow("Audio language", "Hindi") { dialog = ProfileSheet.Language }
                    ProfileActionRow("Subtitle language", "English") { dialog = ProfileSheet.Language }
                }
            }
            item {
                ProfileSection(title = "APPEARANCE", icon = Icons.Default.Settings) {
                    ProfileActionRow("Viewing atmosphere", "AMOLED") { dialog = ProfileSheet.Appearance }
                }
            }
            item {
                ProfileActionRow(
                    icon = Icons.Default.Info,
                    title = "About Kino",
                    subtitle = "Version 1.0.0",
                    onClick = { dialog = ProfileSheet.About }
                )
            }
        }

        if (showSignIn) {
            SignInDialog(
                initialName = name.orEmpty(),
                onDismiss = { showSignIn = false },
                onSave = { enteredName ->
                    name = enteredName
                    preferences.edit().putString("user_name", enteredName).apply()
                    showSignIn = false
                }
            )
        }

        dialog?.let { sheet ->
            ProfileBottomSheet(sheet = sheet, onDismiss = { dialog = null }, onBuyPremium = {
                isPremium = true
                preferences.edit().putBoolean("is_premium", true).apply()
                dialog = null
            })
        }
    }
}

private enum class ProfileSheet { Premium, Language, Appearance, About }

private val avatarColors = listOf(
    Color(0xFFB71C1C), Color(0xFF0D47A1), Color(0xFF6A1B9A), Color(0xFF00695C), Color(0xFFEF6C00)
)

@Composable
private fun PremiumProfileHeader(
    displayName: String,
    avatarIndex: Int,
    isPremium: Boolean,
    onSignIn: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().height(300.dp).background(
            Brush.verticalGradient(listOf(Color(0xFFE50914), Color(0xFF5A070B), ProfileBackground))
        ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 22.dp)) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(avatarColors[avatarIndex]).clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                Text("${displayName.firstOrNull()?.uppercase() ?: "G"}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Text(displayName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            if (isPremium) {
                AssistChip(onClick = {}, label = { Text("Premium Member", color = ProfileGold, fontWeight = FontWeight.SemiBold) }, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("Tap your avatar to change it", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                OutlinedButton(onClick = onSignIn, modifier = Modifier.padding(top = 10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text("Sign In", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ExplorePremiumCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF4A090D), Color(0xFF211012)))).clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✦", color = ProfileGold, fontSize = 28.sp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Explore Premium", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Unlock the full Kino experience", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.Default.ArrowForward, contentDescription = "Explore Premium", tint = ProfileGold)
    }
}

@Composable
private fun PremiumMemberCard() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF262016)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("★", color = ProfileGold, fontSize = 25.sp)
        Column(Modifier.padding(start = 14.dp)) {
            Text("Premium Member", color = ProfileGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Your premium benefits are active", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBottomSheet(sheet: ProfileSheet, onDismiss: () -> Unit, onBuyPremium: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF171719), dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (sheet) {
                ProfileSheet.Premium -> {
                    Text("Make Kino yours", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Premium gives you more ways to enjoy every story.", color = ProfileMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                    listOf("1080p downloads", "No ads while you watch", "Multi-device access").forEach { benefit ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) { Text("✓", color = ProfileGold, fontWeight = FontWeight.Bold); Text(benefit, color = Color.White, modifier = Modifier.padding(start = 12.dp)) }
                    }
                    Button(onClick = onBuyPremium, modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 20.dp), colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent)) { Text("Buy Premium") }
                }
                ProfileSheet.Language -> ComingSoonSheet("Language Settings")
                ProfileSheet.Appearance -> ComingSoonSheet("Appearance")
                ProfileSheet.About -> {
                    Text("KINO", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("A better way to watch", color = ProfileMuted, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ComingSoonSheet(title: String) {
    Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    Text("Coming Soon", color = ProfileGold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
    Text("We are polishing this experience for a future update.", color = ProfileMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 30.dp))
}

@Composable
private fun SignInDialog(initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var enteredName by remember { mutableStateOf(initialName) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProfileSurface,
        title = { Text("Welcome to Kino", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = enteredName, onValueChange = { enteredName = it }, singleLine = true, label = { Text("Your name") }) },
        confirmButton = { TextButton(onClick = { if (enteredName.trim().isNotEmpty()) onSave(enteredName.trim()) }) { Text("Continue", color = ProfileGold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ProfileMuted) } }
    )
}

@Composable
private fun ProfileSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(ProfileSurface).padding(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Icon(icon, contentDescription = null, tint = ProfileAccent, modifier = Modifier.size(19.dp))
            Text(title, color = Color(0xFFB5B5BA), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
        }
        content()
    }
}

@Composable
private fun ProfileActionRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFFF1F1F1), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("$value  ›", color = ProfileMuted, fontSize = 14.sp)
    }
}

@Composable
private fun ProfileActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(ProfileSurface).clickable(onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ProfileAccent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.Default.ArrowForward, contentDescription = "Open", tint = ProfileMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ProfileSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFFF1F1F1), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ProfileAccent))
    }
}
