package com.farhan.mobileagent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.mobileagent.agent.media.PlaybackService

class MusicActivity : ComponentActivity() {
    private var status by mutableStateOf("Choose a local audio file.")

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not offer persistable grants; the current URI
            // still remains valid for the playback request.
        }

        val name = displayName(uri) ?: uri.lastPathSegment ?: "selected audio"
        PlaybackService.play(this, uri)
        status = "Playing: $name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Music", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Play a local audio file. Playback can continue while this app is in the background.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            status,
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            picker.launch(arrayOf("audio/*"))
                        }
                    ) {
                        Text("Choose music from files")
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            PlaybackService.stop(this@MusicActivity)
                            status = "Playback stopped."
                        }
                    ) {
                        Text("Stop playback")
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { finish() }
                    ) {
                        Text("Back to agent")
                    }
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
}
