package com.farhan.mobileagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.mobileagent.agent.assistant.RuleBasedLocalModel
import com.farhan.mobileagent.agent.attention.AttentionItem
import com.farhan.mobileagent.agent.attention.AttentionManager
import com.farhan.mobileagent.agent.security.SecurityKernel

class MainActivity : ComponentActivity() {
    private val kernel = SecurityKernel()
    private val attention = AttentionManager()
    private val assistant = RuleBasedLocalModel(attention)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var input by remember { mutableStateOf("") }
            var messages by remember {
                mutableStateOf(listOf("Agent: Local-first and ready. Ask me about your attention or what I can do."))
            }
            var stopped by remember { mutableStateOf(kernel.isStopped()) }

            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Mobile Agent A52", style = MaterialTheme.typography.headlineMedium)
                    Text(if (stopped) "EMERGENCY STOP ACTIVE" else "Protected • Local")

                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { message -> Text(message) }
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ask the agent") },
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (input.isNotBlank()) {
                                val answer = assistant.answer(input).text
                                messages = messages + "You: " + input + " | Agent: " + answer
                                input = ""
                            }
                        }) { Text("Ask") }

                        OutlinedButton(onClick = {
                            attention.add(AttentionItem(title = "New attention item", priority = 70))
                        }) { Text("Add attention") }

                        Button(onClick = {
                            kernel.emergencyStop()
                            stopped = true
                        }) { Text("STOP") }
                    }

                    if (stopped) {
                        OutlinedButton(onClick = {
                            kernel.resetAfterExplicitUserAction()
                            stopped = false
                        }) { Text("Explicitly reset") }
                    }
                }
            }
        }
    }
}
