package com.rajk2007.kino.ui.profile

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lagradost.cloudstream3.R

private val ProfileBackground = Color(0xFF080808)
private val ProfileSurface = Color(0xFF151517)
private val ProfileMuted = Color(0xFF96969B)
private val ProfileAccent = Color(0xFFE50914)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onExtensionsClick: () -> Unit = {}
) {
    var showAbout by remember { mutableStateOf(false) }
    var autoplay by remember { mutableStateOf(true) }
    var skipIntro by remember { mutableStateOf(true) }
    var selectedTheme by remember { mutableStateOf("AMOLED") }

    Box(modifier = Modifier.fillMaxSize().background(ProfileBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ProfileHeader() }
            item {
                ProfileSection(title = "PLAYBACK SETTINGS", icon = Icons.Default.PlayArrow) {
                    ProfileRow("Quality", "Auto")
                    ProfileRow("Audio", "Hindi")
                    ProfileSwitchRow("Autoplay next episode", autoplay) { autoplay = it }
                    ProfileSwitchRow("Skip intros automatically", skipIntro) { skipIntro = it }
                }
            }
            item {
                ProfileSection(title = "LANGUAGE SETTINGS", icon = Icons.Default.Settings) {
                    ProfileRow("App language", "English")
                    ProfileRow("Audio language", "Hindi")
                    ProfileRow("Subtitle language", "English")
                }
            }
            item {
                ProfileSection(title = "APPEARANCE", icon = Icons.Default.Settings) {
                    Text("Choose your viewing atmosphere", color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("AMOLED" to Color(0xFF000000), "RED" to Color(0xFF3A0B10), "PURPLE" to Color(0xFF241332)).forEach { (theme, color) ->
                            ThemeTile(theme, color, theme == selectedTheme) { selectedTheme = theme }
                        }
                    }
                }
            }
            item {
                ProfileActionRow(
                    icon = Icons.Default.Settings,
                    title = "Extensions Manager",
                    subtitle = "Manage CloudStream extensions",
                    onClick = onExtensionsClick
                )
            }
            item {
                ProfileActionRow(
                    icon = Icons.Default.Info,
                    title = "About Kino",
                    subtitle = "Version 1.0.0",
                    onClick = { showAbout = true }
                )
            }
        }

        if (showAbout) {
            ModalBottomSheet(
                onDismissRequest = { showAbout = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF171719),
                dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted) }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_intro_logo),
                        contentDescription = "Kino logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(120.dp).clip(RoundedCornerShape(20.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("KINO", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("by Raj Karmakar", color = ProfileMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("Version 1.0.0", color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 28.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader() {
    Box(
        modifier = Modifier.fillMaxWidth().height(285.dp).background(
            Brush.verticalGradient(listOf(Color(0xFFB30D17), Color(0xFF3A080D), ProfileBackground))
        ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 24.dp)) {
            Box(
                modifier = Modifier.size(92.dp).clip(CircleShape).background(Color(0xFF242426)),
                contentAlignment = Alignment.Center
            ) { Text("RK", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold) }
            Text("Raj Karmakar", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            AssistChip(
                onClick = {},
                label = { Text("Premium Member", color = Color(0xFFFFD86B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ProfileSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(ProfileSurface).padding(17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Icon(icon, contentDescription = null, tint = ProfileAccent, modifier = Modifier.size(19.dp))
            Text(title, color = Color(0xFFB5B5BA), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
        }
        content()
    }
}

@Composable
private fun ProfileRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { }.padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color(0xFFF1F1F1), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("$value  ›", color = ProfileMuted, fontSize = 14.sp)
    }
}

@Composable
private fun ProfileSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFFF1F1F1), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ProfileAccent)
        )
    }
}

@Composable
private fun ThemeTile(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(0.32f).clip(RoundedCornerShape(12.dp)).background(color).clickable(onClick = onClick).padding(vertical = 17.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(25.dp).clip(CircleShape).background(if (selected) ProfileAccent else Color(0xFF55555A)))
        Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp))
    }
}

@Composable
private fun ProfileActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(ProfileSurface).clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ProfileAccent, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.Default.ArrowForward, contentDescription = "Open", tint = ProfileMuted, modifier = Modifier.size(15.dp))
    }
}
