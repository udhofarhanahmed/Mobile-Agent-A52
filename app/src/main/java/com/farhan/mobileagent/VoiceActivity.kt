package com.farhan.mobileagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import java.util.Locale

class VoiceActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var onDeviceAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onDeviceAvailable = Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)

        setContent {
            var transcript by remember { mutableStateOf("") }
            var status by remember {
                mutableStateOf(
                    if (onDeviceAvailable) {
                        "Offline voice is ready."
                    } else {
                        "On-device speech recognition is not available on this phone."
                    }
                )
            }
            var listening by remember { mutableStateOf(false) }

            MobileAgentTheme {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Voice", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Push to talk • on-device only • nothing is uploaded by this feature.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (transcript.isBlank()) "Say a command…" else transcript,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(status, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = onDeviceAvailable && !listening,
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    this@VoiceActivity,
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                ActivityCompat.requestPermissions(
                                    this@VoiceActivity,
                                    arrayOf(Manifest.permission.RECORD_AUDIO),
                                    REQUEST_AUDIO
                                )
                            } else {
                                startRecognition(
                                    onStatus = { status = it },
                                    onTranscript = { text ->
                                        transcript = text
                                        setResult(
                                            RESULT_OK,
                                            Intent().putExtra(EXTRA_TRANSCRIPT, text)
                                        )
                                        status = "Transcript captured. Returning it to the agent."
                                        finish()
                                    },
                                    onListening = { listening = it }
                                )
                            }
                        }
                    ) {
                        Text(if (listening) "Listening…" else "🎙  Talk to Agent")
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { finish() }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    private fun startRecognition(
        onStatus: (String) -> Unit,
        onTranscript: (String) -> Unit,
        onListening: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < 31 || !onDeviceAvailable) {
            onStatus("Offline voice is unavailable.")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListening(true)
                    onStatus("Listening locally…")
                }

                override fun onBeginningOfSpeech() {
                    onStatus("I’m listening…")
                }

                override fun onEndOfSpeech() {
                    onListening(false)
                    onStatus("Processing locally…")
                }

                override fun onResults(results: Bundle?) {
                    onListening(false)
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (text.isBlank()) {
                        onStatus("I didn't catch that. Try again.")
                    } else {
                        onTranscript(text)
                    }
                }

                override fun onError(error: Int) {
                    onListening(false)
                    onStatus("Voice error $error. No cloud fallback was used.")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { onStatus("Hearing: $it") }
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speech.startListening(intent)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TRANSCRIPT = "voice_transcript"
        private const val REQUEST_AUDIO = 7001
    }
}
