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
fun TermsScreen(onBack: () -> Unit) {
    val sections = listOf(
        "Acceptance of Terms" to "By using Kino, you agree to these Terms and Conditions and to use the application in compliance with applicable law. If you do not agree, please stop using the application.",
        "The Kino Service" to "Kino provides tools for discovering, organizing, and watching streaming content made available through third-party providers. Availability, playback quality, subtitles, metadata, and supported devices may change without notice.",
        "Third-Party Content" to "Kino does not host or control third-party content. Each provider is responsible for its own catalog, licensing, advertising, privacy practices, and terms. You are responsible for complying with the rules of the services you access.",
        "Accounts and Preferences" to "You are responsible for keeping profile information accurate and for protecting access to your device and account preferences. You must not impersonate another person, abuse the service, or attempt to bypass technical restrictions.",
        "Premium Features" to "Optional premium features may be offered for convenience. Feature descriptions, availability, and pricing may change. Purchases and billing, where applicable, are handled by the relevant app store or payment provider.",
        "Acceptable Use" to "You may not reverse engineer, scrape, overload, interfere with, or distribute the application or its services. You may not use Kino to infringe copyright, privacy, or other rights.",
        "Disclaimers and Changes" to "The service is provided on an as-available basis. Kino makes no guarantee that every title or provider will always be available. We may update these terms or the application; continued use after an update means you accept the revised terms.",
        "Contact" to "Questions about these terms can be sent to kino.official.in@gmail.com."
    )
    Scaffold(
        containerColor = Color(0xFF080808),
        topBar = { TopAppBar(title = { Text("Terms and Conditions") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            item { Text("Kino Terms", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(bottom = 18.dp)) }
            items(sections) { (heading, body) ->
                Text(heading, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE50914), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD0D0D0), modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}
