package com.rajk2007.kino.ui.profile

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import kotlin.random.Random

private const val PROFILE_PREFS = "kino_profile"
private const val USER_NAME_KEY = "user_name"
private const val USER_EMAIL_KEY = "user_email"
private const val IS_PREMIUM_KEY = "is_premium"
private const val GUEST_NUMBER_KEY = "guest_number"
private const val USE_LOGO_KEY = "use_logo_avatar"

private val KinoBlack = Color(0xFF080808)
private val KinoSurface = Color(0xFF141414)
private val KinoSurfaceRaised = Color(0xFF1A1A1C)
private val KinoRed = Color(0xFFE50914)
private val KinoMuted = Color(0xFF9A9A9F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(PROFILE_PREFS, 0) }
    val guestNumber = remember {
        prefs.getInt(GUEST_NUMBER_KEY, 0).takeIf { it != 0 } ?: Random.nextInt(1000, 10_000).also {
            prefs.edit().putInt(GUEST_NUMBER_KEY, it).apply()
        }
    }
    var name by remember { mutableStateOf(prefs.getString(USER_NAME_KEY, "").orEmpty()) }
    var email by remember { mutableStateOf(prefs.getString(USER_EMAIL_KEY, "").orEmpty()) }
    var isPremium by remember { mutableStateOf(prefs.getBoolean(IS_PREMIUM_KEY, false)) }
    var useLogo by remember { mutableStateOf(prefs.getBoolean(USE_LOGO_KEY, true)) }
    var autoplay by remember { mutableStateOf(true) }
    var skipIntros by remember { mutableStateOf(true) }
    var wifiOnly by remember { mutableStateOf(false) }
    var closedCaptions by remember { mutableStateOf(true) }
    var showEdit by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var showSubscription by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val displayName = name.ifBlank { "Guest-$guestNumber" }
    val isGuest = email.isBlank()

    fun saveProfile(newName: String, logoAvatar: Boolean) {
        name = newName.trim()
        useLogo = logoAvatar
        prefs.edit().putString(USER_NAME_KEY, name).putBoolean(USE_LOGO_KEY, useLogo).apply()
    }

    Box(Modifier.fillMaxSize().background(KinoBlack)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                ProfileHeader(
                    name = displayName,
                    isGuest = isGuest,
                    useLogo = useLogo,
                    isPremium = isPremium,
                    onEdit = { showEdit = true },
                    onSwitchAccount = { showAccounts = true },
                    onSubscription = { showSubscription = true }
                )
            }
            item {
                ProfileSection("PLAYBACK", Icons.Default.PlayArrow) {
                    ProfileSwitchRow("Autoplay next episode", autoplay) { autoplay = it }
                    ProfileSwitchRow("Skip intros automatically", skipIntros) { skipIntros = it }
                }
            }
            item {
                ProfileSection("DOWNLOADS", Icons.Default.ArrowForward) {
                    ProfileSwitchRow("Download over Wi-Fi only", wifiOnly) { wifiOnly = it }
                }
            }
            item {
                ProfileSection("PREFERENCES", Icons.Default.Settings) {
                    ProfileRow("Subtitles/CC Language", "English") {}
                    ProfileSwitchRow("Closed Captions", closedCaptions) { closedCaptions = it }
                }
            }
            item {
                ProfileSection("ACCOUNT", Icons.Default.Info) {
                    ProfileRow("Email", email) {}
                    ProfileRow("Password", "") {}
                    ProfileRow("Subscription", if (isPremium) "Premium" else "Free") { showSubscription = true }
                    ProfileRow("Notifications", "") {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                    ProfileRow("Log Out", "") {
                        name = ""
                        email = ""
                        isPremium = false
                        useLogo = true
                        prefs.edit().remove(USER_NAME_KEY).remove(USER_EMAIL_KEY).putBoolean(IS_PREMIUM_KEY, false).putBoolean(USE_LOGO_KEY, true).apply()
                    }
                    ProfileRow("Delete My Account", "", danger = true) { showDelete = true }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Text("Terms & Conditions", color = KinoMuted, fontSize = 12.sp, modifier = Modifier.clickable { })
                        Text("  •  ", color = KinoMuted, fontSize = 12.sp)
                        Text("Privacy Policy", color = KinoMuted, fontSize = 12.sp, modifier = Modifier.clickable { })
                    }
                    Text("Version 1.2.0", color = Color(0xFF68686C), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (showEdit) {
            EditProfileDialog(
                initialName = name,
                useLogoInitially = useLogo,
                onDismiss = { showEdit = false },
                onSave = { newName, logoAvatar ->
                    saveProfile(newName, logoAvatar)
                    showEdit = false
                }
            )
        }
        if (showAccounts) {
            AccountSwitcher(
                currentName = displayName,
                currentEmail = email,
                onDismiss = { showAccounts = false },
                onAddAccount = {
                    showAccounts = false
                    showEdit = true
                }
            )
        }
        if (showSubscription) {
            SubscriptionSheet(
                isPremium = isPremium,
                onDismiss = { showSubscription = false },
                onBuy = {
                    isPremium = true
                    prefs.edit().putBoolean(IS_PREMIUM_KEY, true).apply()
                    showSubscription = false
                }
            )
        }
        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                containerColor = KinoSurfaceRaised,
                title = { Text("Delete account?", color = Color.White) },
                text = { Text("This will remove the local Kino profile from this device.", color = KinoMuted) },
                confirmButton = {
                    TextButton(onClick = {
                        name = ""
                        email = ""
                        isPremium = false
                        prefs.edit().clear().apply()
                        showDelete = false
                    }) { Text("Delete", color = KinoRed) }
                },
                dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel", color = Color.White) } }
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    isGuest: Boolean,
    useLogo: Boolean,
    isPremium: Boolean,
    onEdit: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSubscription: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF3B060A), Color(0xFF170609), KinoBlack)))
            .padding(start = 24.dp, end = 18.dp, top = 22.dp, bottom = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (isGuest && useLogo) {
                Image(
                    painter = painterResource(R.drawable.ic_intro_logo),
                    contentDescription = "Kino guest avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(108.dp).clip(CircleShape)
                )
            } else {
                LetterAvatar(name = name, size = 108.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                        Text("✎", color = Color.White, fontSize = 22.sp)
                    }
                }
                Text(
                    "Welcome to Kino",
                    color = KinoMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSwitchAccount)
                        .padding(top = 11.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Switch Account", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, "Switch account", tint = KinoMuted, modifier = Modifier.size(17.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSubscription)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subscription", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(if (isPremium) "Premium" else "Free", color = if (isPremium) Color(0xFFFFD86B) else KinoMuted, fontSize = 14.sp)
                    Icon(Icons.Default.ArrowForward, "Subscription", tint = KinoMuted, modifier = Modifier.padding(start = 7.dp).size(17.dp))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 17.dp)
                .height(1.dp)
                .background(Color(0xFF292929))
        )
    }
}

@Composable
fun LetterAvatar(name: String, size: Dp = 96.dp) {
    val letter = name.trim().firstOrNull()?.uppercaseChar() ?: '#'
    val backgroundColor = Color.hsv(((letter.code * 37) % 360).toFloat(), 0.68f, 0.72f)
    Box(Modifier.size(size).clip(CircleShape).background(backgroundColor), contentAlignment = Alignment.Center) {
        Text(letter.toString(), color = Color.White, fontSize = if (size.value >= 96f) 36.sp else 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Text(title, color = KinoMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 22.dp, bottom = 7.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KinoSurface)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
                Icon(icon, null, tint = KinoRed, modifier = Modifier.size(19.dp))
                Text(title, color = Color(0xFFBDBDC2), fontSize = 11.sp, modifier = Modifier.padding(start = 9.dp))
            }
            content()
        }
    }
}

@Composable
private fun ProfileRow(title: String, value: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = if (danger) KinoRed else Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) Text(value, color = KinoMuted, fontSize = 14.sp, modifier = Modifier.padding(end = 9.dp))
        Icon(Icons.Default.ArrowForward, null, tint = if (danger) KinoRed else KinoMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ProfileSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KinoRed, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF353539)))
    }
}

@Composable
private fun EditProfileDialog(initialName: String, useLogoInitially: Boolean, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var editedName by remember { mutableStateOf(initialName) }
    var logoAvatar by remember { mutableStateOf(useLogoInitially) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KinoSurfaceRaised,
        title = { Text("Edit Profile", color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (logoAvatar) Image(painterResource(R.drawable.ic_intro_logo), "Kino avatar", modifier = Modifier.size(72.dp).clip(CircleShape)) else LetterAvatar(editedName, 72.dp)
                TextButton(onClick = { logoAvatar = !logoAvatar }) { Text(if (logoAvatar) "Use letter avatar" else "Use KINO avatar", color = KinoRed) }
                TextField(value = editedName, onValueChange = { editedName = it }, singleLine = true, label = { Text("Profile name") }, colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = KinoSurface, unfocusedContainerColor = KinoSurface, focusedIndicatorColor = KinoRed, unfocusedIndicatorColor = Color.Transparent, focusedLabelColor = KinoRed, unfocusedLabelColor = KinoMuted))
            }
        },
        confirmButton = { Button(onClick = { onSave(editedName, logoAvatar) }, colors = ButtonDefaults.buttonColors(containerColor = KinoRed)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = KinoMuted) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSwitcher(currentName: String, currentEmail: String, onDismiss: () -> Unit, onAddAccount: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = KinoSurfaceRaised, dragHandle = { BottomSheetDefaults.DragHandle(color = KinoMuted) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Switch Account", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            AccountChoice(currentName, if (currentEmail.isBlank()) "Guest account" else currentEmail, selected = true) {}
            AccountChoice("Add Account", "Sign in with a Gmail account", selected = false, onClick = onAddAccount)
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun AccountChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0x33E50914) else KinoSurface).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (title == "Add Account") Text("+", color = KinoRed, fontSize = 28.sp, modifier = Modifier.width(42.dp)) else LetterAvatar(title, 44.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = KinoMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (selected) Text("✓", color = KinoRed, fontSize = 20.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionSheet(isPremium: Boolean, onDismiss: () -> Unit, onBuy: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = KinoSurfaceRaised, dragHandle = { BottomSheetDefaults.DragHandle(color = KinoMuted) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
            Text("Subscription", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(if (isPremium) "Premium" else "Free", color = if (isPremium) Color(0xFFFFD86B) else KinoMuted, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
            if (!isPremium) {
                Text("Unlock 1080p downloads, an ad-free experience, and multi-device access.", color = KinoMuted, modifier = Modifier.padding(top = 12.dp))
                Button(onClick = onBuy, modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp), colors = ButtonDefaults.buttonColors(containerColor = KinoRed)) { Text("Buy Premium") }
            } else Spacer(Modifier.height(28.dp))
        }
    }
}
