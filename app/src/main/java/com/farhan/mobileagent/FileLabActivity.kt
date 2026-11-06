package com.farhan.mobileagent

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.mobileagent.agent.tools.FileAnalysis
import com.farhan.mobileagent.agent.tools.LocalFileAnalyzer
import java.io.ByteArrayOutputStream

class FileLabActivity : ComponentActivity() {
    private var status by mutableStateOf("Choose a local CSV, TSV or text file.")
    private var analysis by mutableStateOf<FileAnalysis?>(null)

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        analyzeUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MobileAgentTheme(AgentSettings.themeMode(this@FileLabActivity)) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("File Lab", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Files stay on this phone. The first build handles CSV/TSV tables and text locally, with a 5 MB safety limit.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Text(status, Modifier.padding(16.dp))
                    }

                    Button(
                        onClick = {
                            picker.launch(arrayOf("text/csv", "text/tab-separated-values", "text/plain", "application/json"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose local file")
                    }

                    analysis?.let { report -> AnalysisCard(report) }

                    OutlinedButton(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to agent")
                    }
                }
            }
        }
    }

    private fun analyzeUri(uri: Uri) {
        status = "Reading locally…"
        Thread {
            runCatching {
                val name = displayName(uri) ?: uri.lastPathSegment ?: "selected file"
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    val output = ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        require(total <= 5_000_000) { "File is larger than 5 MB." }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                } ?: error("The file could not be opened.")

                LocalFileAnalyzer.analyze(name, bytes.toString(Charsets.UTF_8))
            }.onSuccess { report ->
                runOnUiThread {
                    analysis = report
                    status = "Analyzed locally: " + report.title
                }
            }.onFailure { error ->
                runOnUiThread {
                    analysis = null
                    status = error.message ?: "Could not analyze the file."
                }
            }
        }.start()
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}

@Composable
private fun AnalysisCard(report: FileAnalysis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(report.title, style = MaterialTheme.typography.titleLarge)
            report.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            report.numericColumns.take(12).forEach { column ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(column.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Count ${column.count} • Missing ${column.missing}\n" +
                        "Sum ${column.sum}\n" +
                        "Average ${column.average}\n" +
                        "Min ${column.minimum} • Max ${column.maximum}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
