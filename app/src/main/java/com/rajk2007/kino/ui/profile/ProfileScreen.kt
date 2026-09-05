package com.rajk2007.kino.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.navigation.Navigation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.R
import kotlin.random.Random

private const val PROFILE_PREFS = "kino_profile"
private const val USER_NAME_KEY = "user_name"
private const val USER_EMAIL_KEY = "user_email"
private const val IS_PREMIUM_KEY = "is_premium"
private const val GUEST_NUMBER_KEY = "guest_number"
private const val AVATAR_URI_KEY = "avatar_uri"

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
    var avatarUri by remember { mutableStateOf(prefs.getString(AVATAR_URI_KEY, null) ?: "") }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            avatarUri = uri.toString()
            prefs.edit().putString(AVATAR_URI_KEY, avatarUri).apply()
        }
    }
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

    fun saveProfile(newName: String) {
        name = newName.trim()
        prefs.edit().putString(USER_NAME_KEY, name).apply()
    }

    Box(Modifier.fillMaxSize().background(KinoBlack)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                HorizontalDivider(
                    color = Color(0xFF1A1A1A),
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                ProfileHeader(
                    profileName = displayName,
                    subscription = if (isPremium) "Premium" else "Free",
                    avatarUri = avatarUri,
                    onEdit = { showEdit = true },
                    onSwitchAccount = { showAccounts = true },
                    onSubscription = { showSubscription = true }
                )
            }
            item {
                ProfileSection("PLAYBACK", Icons.Default.PlayArrow) {
                    ProfileSwitchRow("Autoplay next episode", Icons.Default.PlayArrow, autoplay) { autoplay = it }
                    ProfileSwitchRow("Skip intros automatically", Icons.Default.PlayArrow, skipIntros) { skipIntros = it }
                }
            }
            item {
                ProfileSection("DOWNLOADS", Icons.Default.ArrowForward) {
                    ProfileSwitchRow("Download over Wi-Fi only", Icons.Default.ArrowForward, wifiOnly) { wifiOnly = it }
                }
            }
            item {
                ProfileSection("PREFERENCES", Icons.Default.Settings) {
                    ProfileRow("Subtitles/CC Language", "English", Icons.Default.Settings) {}
                    ProfileRow(
                        title = "Extensions Manager",
                        value = "View installed plugins and repos",
                        icon = Icons.Default.Settings,
                        onClick = {
                            val activity = context as? FragmentActivity ?: return@ProfileRow
                            val navController = Navigation.findNavController(activity, R.id.nav_host_fragment)
                            navController.navigate(R.id.action_navigation_global_to_navigation_settings_extensions)
                        }
                    )
                    ProfileSwitchRow("Closed Captions", Icons.Default.Settings, closedCaptions) { closedCaptions = it }
                }
            }
            item {
                ProfileSection("ACCOUNT", Icons.Default.Info) {
                    ProfileRow("Email", email, Icons.Default.Info) {}
                    ProfileRow("Password", "", Icons.Default.Info) {}
                    ProfileRow("Subscription", if (isPremium) "Premium" else "Free", Icons.Default.Info) { showSubscription = true }
                    ProfileRow("Notifications", "", Icons.Default.Info) {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                    ProfileRow("Log Out", "", Icons.Default.ArrowForward) {
                        name = ""
                        email = ""
                        isPremium = false
                        avatarUri = ""
                        prefs.edit().remove(USER_NAME_KEY).remove(USER_EMAIL_KEY).remove(AVATAR_URI_KEY).putBoolean(IS_PREMIUM_KEY, false).apply()
                    }
                    ProfileRow("Delete My Account", "", Icons.Default.ArrowForward, danger = true) { showDelete = true }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Terms & Conditions • Privacy Policy", color = Color.Gray, fontSize = 12.sp)
                    Text("Version 1.2.0", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }

        if (showEdit) {
            EditProfileDialog(
                initialName = name,
                initialAvatarUri = avatarUri,
                onDismiss = { showEdit = false },
                onPickAvatar = { galleryLauncher.launch("image/*") },
                onSave = { newName ->
                    saveProfile(newName)
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
fun ProfileHeader(
    profileName: String,
    subscription: String,
    avatarUri: String,
    onEdit: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSubscription: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(90.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri.isNotBlank()) {
                    AsyncImage(
                        model = Uri.parse(avatarUri),
                        contentDescription = "Profile avatar",
                        modifier = Modifier.size(90.dp).clip(CircleShape)
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_intro_logo),
                        contentDescription = "Kino avatar",
                        modifier = Modifier.size(90.dp).clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = profileName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Welcome to Kino",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1A1A1A))
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Profile",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                .clickable { onSwitchAccount() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Switch Account", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                .clickable { onSubscription() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Subscription", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(subscription, color = if (subscription == "Premium") Color(0xFFE50914) else Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color.Black, thickness = 1.dp)
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
        Text(
            title,
            color = KinoMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KinoSurface)
        ) {
            content()
        }
    }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp)
}

@Composable
private fun ProfileRow(title: String, value: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) KinoRed else KinoRed, modifier = Modifier.size(20.dp))
            Text(title, color = if (danger) KinoRed else Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            if (value.isNotBlank()) Text(value, color = KinoMuted, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
            Icon(Icons.Default.KeyboardArrowRight, null, tint = if (danger) KinoRed else KinoMuted, modifier = Modifier.size(20.dp))
        }
        ProfileDivider()
    }
}

@Composable
private fun ProfileSwitchRow(title: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = KinoRed, modifier = Modifier.size(20.dp))
            Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KinoRed, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF333333)))
        }
        ProfileDivider()
    }
}

@Composable
private fun EditProfileDialog(initialName: String, initialAvatarUri: String, onPickAvatar: () -> Unit, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var editedName by remember { mutableStateOf(initialName) }
    var selectedAvatarUri by remember(initialAvatarUri) { mutableStateOf(initialAvatarUri) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KinoSurfaceRaised,
        title = { Text("Edit Profile", color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (selectedAvatarUri.isNotBlank()) {
                    AsyncImage(Uri.parse(selectedAvatarUri), "Profile avatar", modifier = Modifier.size(72.dp).clip(CircleShape))
                } else {
                    Image(painterResource(R.drawable.ic_intro_logo), "Kino avatar", modifier = Modifier.size(72.dp).clip(CircleShape))
                }
                TextButton(onClick = onPickAvatar) { Text("Change Profile Picture", color = KinoRed) }
                TextField(value = editedName, onValueChange = { editedName = it }, singleLine = true, label = { Text("Profile name") }, colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = KinoSurface, unfocusedContainerColor = KinoSurface, focusedIndicatorColor = KinoRed, unfocusedIndicatorColor = Color.Transparent, focusedLabelColor = KinoRed, unfocusedLabelColor = KinoMuted))
            }
        },
        confirmButton = { Button(onClick = { onSave(editedName) }, colors = ButtonDefaults.buttonColors(containerColor = KinoRed)) { Text("Save") } },
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
