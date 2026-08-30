package com.rajk2007.kino.ui.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val sections = listOf(
        "Information Stored on Your Device" to "Kino stores profile details you choose to provide, such as a display name, selected avatar, active account, language, and playback preferences. These settings are stored locally using Android preferences.",
        "Information from Providers" to "When you browse or play a title, requests may be sent to the selected third-party provider. Those providers may receive technical information needed to respond to the request and are responsible for their own privacy policies.",
        "How We Use Information" to "Local profile information is used to personalize the Profile screen and remember your settings. We do not sell your personal information or use your profile name for advertising purposes.",
        "Permissions and Links" to "The app may open external providers, email applications, or web pages when you choose an action. External services operate independently and may collect information under their own terms.",
        "Retention and Deletion" to "You can clear locally stored profile and preference information through Android app settings. Clearing application data removes the locally stored profile and settings from the device.",
        "Children’s Privacy" to "Kino is not directed at children under the minimum age required by local law. We do not knowingly request personal information from children.",
        "Security" to "We take reasonable steps to keep locally stored preferences within the application environment. No electronic storage or transmission method can be guaranteed to be completely secure.",
        "Policy Updates" to "We may update this Privacy Policy when the application or applicable requirements change. The updated policy will be made available in the Profile section.",
        "Contact" to "For privacy questions, contact kino.official.in@gmail.com."
    )
    Scaffold(
        containerColor = Color(0xFF080808),
        topBar = { TopAppBar(title = { Text("Privacy Policy") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            item { Text("Kino Privacy", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(bottom = 18.dp)) }
            items(sections) { (heading, body) ->
                Text(heading, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE50914), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD0D0D0), modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}
