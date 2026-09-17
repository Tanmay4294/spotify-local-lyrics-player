package com.example.spotlyrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotlyrics.spotify.SpotifyAuthState
import com.example.spotlyrics.spotify.SpotifyPkceAuthManager
import com.example.spotlyrics.ui.theme.SpotifyLocalLyricsPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val authManager = SpotifyPkceAuthManager.getInstance(applicationContext)

        setContent {
            SpotifyLocalLyricsPlayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AuthScreen(
                        authManager = authManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(
    authManager: SpotifyPkceAuthManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by authManager.authState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Spotify Local Lyrics Player",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        val statusText = when (val state = authState) {
            is SpotifyAuthState.SignedOut -> "Spotify not connected"
            is SpotifyAuthState.Authorizing -> "Opening Spotify authorization..."
            is SpotifyAuthState.ExchangingCode -> "Exchanging authorization code..."
            is SpotifyAuthState.Authorized -> "Spotify authorized"
            is SpotifyAuthState.Error -> "Error: ${state.message}"
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (authState) {
            is SpotifyAuthState.Authorized -> {
                OutlinedButton(
                    onClick = { authManager.signOut() }
                ) {
                    Text("Sign Out")
                }
            }
            else -> {
                Button(
                    onClick = { authManager.startAuthorization(context) }
                ) {
                    Text("Connect Spotify")
                }
            }
        }
    }
}
