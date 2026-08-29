package com.rajk2007.kino.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import kotlin.random.Random

private val ProfileBackground = Color(0xFF080808)
private val ProfileSurface = Color(0xFF151517)
private val ProfileMuted = Color(0xFF96969B)
private val ProfileAccent = Color(0xFFE50914)
private val ProfileGold = Color(0xFFFFD86B)
private const val PROFILE_PREFS = "kino_prefs"
private const val USER_NAME = "user_name"
private const val IS_LOGGED_IN = "is_logged_in"
private const val AVATAR_INDEX = "avatar_index"
private const val AUTOPLAY = "autoplay"
private const val SKIP_INTRO = "skip_intro"
private const val DOWNLOAD_MOBILE_DATA = "download_mobile_data"
private const val IS_PREMIUM = "is_premium"

private val avatarIcons = listOf(Icons.Default.Person, Icons.Default.AccountCircle, Icons.Default.Face, Icons.Default.Info, Icons.Default.Settings)
private val avatarColors = listOf(Color(0xFFB71C1C), Color(0xFF0D47A1), Color(0xFF6A1B9A), Color(0xFF00695C), Color(0xFFEF6C00))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onExtensionsClick: () -> Unit = {}) {
    val context = LocalContext.current
    val profilePrefs = remember { context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE) }
    var isLoggedIn by remember { mutableStateOf(profilePrefs.getBoolean(IS_LOGGED_IN, false)) }
    var name by remember { mutableStateOf(profilePrefs.getString(USER_NAME, null)) }
    var avatarIndex by remember { mutableStateOf(profilePrefs.getInt(AVATAR_INDEX, 0).coerceIn(avatarIcons.indices)) }
    val guestNumber = remember {
        profilePrefs.getInt("guest_number", 0).takeIf { it in 1000..9999 } ?: Random.nextInt(1000, 10000).also {
            profilePrefs.edit().putInt("guest_number", it).apply()
        }
    }
    var autoplay by remember { mutableStateOf(profilePrefs.getBoolean(AUTOPLAY, true)) }
    var skipIntro by remember { mutableStateOf(profilePrefs.getBoolean(SKIP_INTRO, true)) }
    var downloadMobileData by remember { mutableStateOf(profilePrefs.getBoolean(DOWNLOAD_MOBILE_DATA, false)) }
    var isPremium by remember { mutableStateOf(profilePrefs.getBoolean(IS_PREMIUM, false)) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showPremium by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var policy by remember { mutableStateOf<Policy?>(null) }

    fun saveProfile(nameValue: String, avatarValue: Int) {
        name = nameValue
        avatarIndex = avatarValue
        isLoggedIn = true
        profilePrefs.edit().putString(USER_NAME, nameValue).putInt(AVATAR_INDEX, avatarValue).putBoolean(IS_LOGGED_IN, true).apply()
    }

    Box(Modifier.fillMaxSize().background(ProfileBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProfileHeader(
                    displayName = if (isLoggedIn) name.orEmpty() else "Guest#$guestNumber",
                    avatarIndex = avatarIndex,
                    isLoggedIn = isLoggedIn,
                    onSwitchAccount = { if (isLoggedIn) showAccountSheet = true else showProfileEditor = true },
                    onEdit = { showProfileEditor = true }
                )
            }
            item {
                ExplorePremiumCard(isPremium = isPremium, onClick = { showPremium = true })
            }
            item {
                ProfileSection(title = "PLAYBACK SETTINGS", icon = Icons.Default.PlayArrow) {
                    ProfileSwitchRow("Autoplay next episode", autoplay) { enabled ->
                        autoplay = enabled
                        profilePrefs.edit().putBoolean(AUTOPLAY, enabled).apply()
                    }
                    ProfileSwitchRow("Skip intros automatically", skipIntro) { enabled ->
                        skipIntro = enabled
                        profilePrefs.edit().putBoolean(SKIP_INTRO, enabled).apply()
                    }
                }
            }
            item {
                ProfileSection(title = "DOWNLOAD SETTINGS", icon = Icons.Default.Info) {
                    ProfileSwitchRow("Download over mobile data", downloadMobileData, "Use cellular data for downloads") { enabled ->
                        downloadMobileData = enabled
                        profilePrefs.edit().putBoolean(DOWNLOAD_MOBILE_DATA, enabled).apply()
                    }
                }
            }
            item {
                ProfileSection(title = "APP PREFERENCES", icon = Icons.Default.Settings) {
                    SupportRow(Icons.Default.Settings, "App Language") { showToast(context, "App Language") }
                    SupportRow(Icons.Default.Info, "Help Center") { openHelpCenter(context) }
                    SupportRow(Icons.Default.Info, "Terms and Conditions") { policy = Policy.Terms }
                    SupportRow(Icons.Default.Info, "Privacy Policy") { policy = Policy.Privacy }
                }
            }
            item {
                ProfileActionRow(Icons.Default.Info, "About Kino", "Version 1.2.0") { showAbout = true }
            }
        }

        if (showProfileEditor) {
            ProfileEditorDialog(
                isLoggedIn = isLoggedIn,
                initialName = name.orEmpty(),
                initialAvatar = avatarIndex,
                onDismiss = { showProfileEditor = false },
                onSave = { newName, newAvatar ->
                    saveProfile(newName, newAvatar)
                    showProfileEditor = false
                }
            )
        }
        if (showAccountSheet) {
            AccountSwitcherSheet(
                onDismiss = { showAccountSheet = false },
                onSelectAccount = { showAccountSheet = false; showProfileEditor = true }
            )
        }
        if (showPremium) {
            PremiumSheet(onDismiss = { showPremium = false }, onBuyPremium = {
                isPremium = true
                profilePrefs.edit().putBoolean(IS_PREMIUM, true).apply()
                showPremium = false
            })
        }
        if (showAbout) {
            AboutKinoSheet(onDismiss = { showAbout = false })
        }
        policy?.let { selectedPolicy ->
            PolicyDialog(policy = selectedPolicy, onDismiss = { policy = null })
        }
    }
}

private fun showToast(context: Context, item: String) {
    Toast.makeText(context, "Loading $item...", Toast.LENGTH_SHORT).show()
}

private fun openHelpCenter(context: Context) {
    val gmailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:kino.official.in@gmail.com"))
        .putExtra(Intent.EXTRA_SUBJECT, "KINO App Support")
        .setPackage("com.google.android.gm")
    val fallbackIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:kino.official.in@gmail.com"))
        .putExtra(Intent.EXTRA_SUBJECT, "KINO App Support")
    runCatching {
        if (gmailIntent.resolveActivity(context.packageManager) != null) context.startActivity(gmailIntent)
        else context.startActivity(fallbackIntent)
    }.onFailure { Toast.makeText(context, "Unable to open email", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun ProfileHeader(displayName: String, avatarIndex: Int, isLoggedIn: Boolean, onSwitchAccount: () -> Unit, onEdit: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color(0xFFE50914), Color(0xFF5A070B), ProfileBackground)))) {
        Row(Modifier.fillMaxWidth().padding(start = 38.dp, end = 28.dp, top = 98.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(avatarColors[avatarIndex]).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
                Icon(avatarIcons[avatarIndex], contentDescription = "Edit profile", tint = Color.White, modifier = Modifier.size(54.dp))
            }
            Column(Modifier.padding(start = 24.dp).weight(1f)) {
                Text(displayName, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Welcome to Kino", color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp, modifier = Modifier.padding(top = 5.dp))
                OutlinedButton(onClick = onSwitchAccount, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ProfileAccent)) { Text("Switch Account", fontWeight = FontWeight.SemiBold) }
            }
            Text("✎", color = Color.White, fontSize = 25.sp, modifier = Modifier.padding(bottom = 42.dp).clickable(onClick = onEdit))
        }
    }
}

@Composable
private fun ProfileSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(ProfileSurface).padding(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Icon(icon, contentDescription = null, tint = ProfileAccent, modifier = Modifier.size(19.dp))
            Text(title, color = Color(0xFFB5B5BA), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
        }
        content()
    }
}

@Composable
private fun ProfileSwitchRow(title: String, checked: Boolean, subtitle: String? = null, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color(0xFFF1F1F1), fontSize = 15.sp)
            subtitle?.let { Text(it, color = ProfileMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ProfileAccent))
    }
}

@Composable
private fun SupportRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ProfileAccent, modifier = Modifier.size(20.dp))
        Text(title, color = Color(0xFFF1F1F1), fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Icon(Icons.Default.ArrowForward, contentDescription = "Open", tint = ProfileMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ProfileActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
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
private fun ProfileEditorDialog(isLoggedIn: Boolean, initialName: String, initialAvatar: Int, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var enteredName by remember { mutableStateOf(initialName) }
    var selectedAvatar by remember { mutableStateOf(initialAvatar) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProfileSurface,
        title = { Text(if (isLoggedIn) "Edit Profile" else "Welcome to Kino", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = enteredName, onValueChange = { enteredName = it }, singleLine = true, label = { Text("Email or Name") })
                Text("Choose an avatar", color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    avatarIcons.forEachIndexed { index, icon ->
                        Box(Modifier.size(42.dp).clip(CircleShape).background(if (index == selectedAvatar) ProfileAccent else avatarColors[index]).clickable { selectedAvatar = index }, contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = "Avatar ${index + 1}", tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (enteredName.trim().isNotEmpty()) onSave(enteredName.trim(), selectedAvatar) }) { Text("Save", color = ProfileGold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ProfileMuted) } }
    )
}

@Composable
private fun ExplorePremiumCard(isPremium: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF4A090D), Color(0xFF211012)))).clickable(onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("★", color = ProfileGold, fontSize = 27.sp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(if (isPremium) "Premium Member" else "Explore Premium", color = ProfileGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(if (isPremium) "Your benefits are active" else "No ads, 1080p downloads, multi-device", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.Default.ArrowForward, contentDescription = "Premium", tint = ProfileGold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSwitcherSheet(onDismiss: () -> Unit, onSelectAccount: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF171719), dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 12.dp)) {
            Text("Switch Account", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Choose how you want to manage your Kino profile.", color = ProfileMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 7.dp, bottom = 20.dp))
            Button(onClick = onSelectAccount, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent)) { Text("Switch Account") }
            OutlinedButton(onClick = onSelectAccount, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Add another account") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumSheet(onDismiss: () -> Unit, onBuyPremium: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF171719), dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 12.dp)) {
            Text("Kino Premium", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("A better way to watch, everywhere.", color = ProfileMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 7.dp, bottom = 18.dp))
            listOf("No Ads", "1080p Downloads", "Multi-device access").forEach { benefit ->
                Row(Modifier.padding(vertical = 7.dp)) { Text("✓", color = ProfileGold, fontWeight = FontWeight.Bold); Text(benefit, color = Color.White, modifier = Modifier.padding(start = 12.dp)) }
            }
            Button(onClick = onBuyPremium, modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent)) { Text("Buy Premium") }
        }
    }
}

private enum class Policy { Terms, Privacy }

@Composable
private fun PolicyDialog(policy: Policy, onDismiss: () -> Unit) {
    val title = if (policy == Policy.Terms) "Terms and Conditions" else "Privacy Policy"
    val body = if (policy == Policy.Terms) {
        "Kino provides a streaming interface for discovering and watching content from available providers. You agree to use the app lawfully, respect copyright and provider terms, and keep your account information accurate. Content availability may change without notice. Kino is not responsible for third-party provider material or interruptions. You must not reverse engineer, abuse, or attempt to disrupt the app or its services."
    } else {
        "Kino stores only the information needed to provide profile features, such as the name or email you choose to save on this device and your app preferences. Provider requests may be processed by third-party services according to their own policies. We do not sell your personal information. You may clear locally stored profile information through the app or device settings."
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = ProfileSurface, title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) }, text = { Text(body, color = Color(0xFFE2E2E2), fontSize = 14.sp, modifier = Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = ProfileGold) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutKinoSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF171719), dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("KINO", color = ProfileAccent, fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text("by Raj Karmakar", color = ProfileMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Text("Version 1.2.0", color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 22.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent)) { Text("Close") }
        }
    }
}
