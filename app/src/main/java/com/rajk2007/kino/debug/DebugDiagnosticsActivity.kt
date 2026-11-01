package com.rajk2007.kino.debug
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
/**
 * Host Activity for the in-app diagnostic screen.
 *
 * ProfileScreen.kt launches this directly via startActivity — it does not
 * use Navigation Compose. Keeping the diagnostic UI behind its own Activity
 * means it has its own back stack entry and window, so the Close button
 * just calls finish() and returns the user to the Profile tab.
 */
class DebugDiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF080808))) {
                Surface(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF080808)),
                    color = Color(0xFF080808),
                ) {
                    DebugDiagnosticsScreen(onClose = { finish() })
                }
            }
        }
    }
}
