package com.example.spotlyrics.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.spotlyrics.diagnostics.HealthMonitor
import com.example.spotlyrics.spotify.SpotifyAuthState
import com.example.spotlyrics.ui.PlayerViewModel

@Composable
fun DiagnosticsDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val selfTestResult by viewModel.selfTestResult.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val components by HealthMonitor.components.collectAsState()
    val providers by HealthMonitor.providers.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Diagnostics & Self-Test",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auth State Section
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (authState) {
                            SpotifyAuthState.VALID -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Auth State: $authState",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (authState != SpotifyAuthState.VALID) {
                            Button(onClick = { viewModel.reconnect() }) {
                                Text("Reconnect Spotify")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Self-Test Section
                Text(
                    text = "Spotify Self-Test",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (selfTestResult != null) {
                    val res = selfTestResult!!
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (res.success) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (res.success) "STATUS: PASS" else "STATUS: FAIL",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Connection OK: ${res.connectionOk}")
                            Text("PlayerState OK: ${res.playerStateOk}")
                            Text("Message: ${res.message}")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { viewModel.runSpotifySelfTest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run Spotify Self-Test")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Component Health Summary
                Text(
                    text = "Component Health",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (components.isEmpty()) {
                    Text("No component health recorded yet.")
                } else {
                    components.forEach { (name, health) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$name: ${if (health.healthy) "HEALTHY" else "UNHEALTHY"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
