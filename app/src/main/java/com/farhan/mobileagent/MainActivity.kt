package com.farhan.mobileagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.mobileagent.agent.accessibility.AgentAccessibilityService
import com.farhan.mobileagent.agent.accessibility.ScreenStateStore
import com.farhan.mobileagent.agent.assistant.RuleBasedLocalModel
import com.farhan.mobileagent.agent.attention.AttentionItem
import com.farhan.mobileagent.agent.attention.ReminderScheduler
import com.farhan.mobileagent.agent.command.CommandResult
import com.farhan.mobileagent.agent.command.LocalCommandRouter
import com.farhan.mobileagent.agent.core.AgentContainer
import com.farhan.mobileagent.agent.core.AgentRuntime
import com.farhan.mobileagent.agent.device.DeviceStateProvider

class MainActivity : ComponentActivity() {
    private lateinit var runtime: AgentRuntime
    private lateinit var attention: com.farhan.mobileagent.agent.attention.AttentionManager
    private lateinit var assistant: RuleBasedLocalModel
    private val router = LocalCommandRouter()
    private lateinit var deviceProvider: DeviceStateProvider
    private lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentContainer.initialize(applicationContext)

        attention = AgentContainer.attentionManager
        deviceProvider = DeviceStateProvider(this)
        reminderScheduler = ReminderScheduler(applicationContext)
        assistant = RuleBasedLocalModel(attention, deviceProvider)
        runtime = AgentRuntime(
            AgentContainer.securityKernel,
            AgentContainer.confirmationManager,
            AgentContainer.auditLog
        )

        setContent {
            var input by remember { mutableStateOf("") }
            var messages by remember {
                mutableStateOf(listOf("Agent: Local-first and ready."))
            }
            var stopped by remember { mutableStateOf(AgentContainer.securityKernel.isStopped()) }
            var pending by remember { mutableStateOf<com.farhan.mobileagent.agent.core.PendingAction?>(null) }
            var attentionVersion by remember { mutableIntStateOf(0) }

            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Mobile Agent A52", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (stopped) "EMERGENCY STOP ACTIVE"
                        else "Protected • Local • Policy gated"
                    )

                    Text(
                        "Battery " + deviceProvider.snapshot().batteryPercent + "% • " +
                            if (AgentAccessibilityService.instance() != null) "Accessibility connected" else "Accessibility not connected"
                    )

                    Text(
                        "Pending attention: " + attention.pending().size,
                        style = MaterialTheme.typography.labelLarge
                    )

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
                        label = { Text("Ask or command the agent") },
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (input.isBlank()) return@Button
                            val command = input
                            when (val result = router.route(command, deviceProvider.snapshot())) {
                                is CommandResult.Answer ->
                                    messages = messages + "You: " + command + "\nAgent: " + result.text

                                is CommandResult.AddAttention -> {
                                    val item = AttentionItem(
                                        title = result.title,
                                        priority = result.priority,
                                        dueAt = result.dueAt
                                    )
                                    attention.add(item)
                                    result.dueAt?.let { reminderScheduler.schedule(item) }

                                    if (result.dueAt != null &&
                                        Build.VERSION.SDK_INT >= 33 &&
                                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        ActivityCompat.requestPermissions(
                                            this@MainActivity,
                                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                            501
                                        )
                                    }

                                    attentionVersion++
                                    messages = messages + "You: " + command + "\nAgent: " +
                                        if (result.dueAt != null) "Reminder scheduled locally." else "Added to attention."
                                }

                                is CommandResult.Action -> {
                                    val prepared = runtime.prepare(result.request)
                                    if (prepared == null) {
                                        messages = messages + "Agent: Action blocked by the security state."
                                    } else {
                                        pending = prepared
                                        messages = messages + "Agent: Approval required for " + result.request.type.name.lowercase() + "."
                                    }
                                }

                                is CommandResult.Unknown -> {
                                    val answer = assistant.answer(command).text
                                    messages = messages + "You: " + command + "\nAgent: " + answer
                                }
                            }
                            input = ""
                        }) { Text("Send") }

                        OutlinedButton(onClick = {
                            val screen = ScreenStateStore.get()
                            val text = if (screen.packageName == null) {
                                "No screen observation is available. Enable Accessibility Service."
                            } else {
                                "Current app: " + screen.packageName +
                                    "\nVisible text: " + screen.visibleText.take(12).joinToString(" • ")
                            }
                            messages = messages + "Agent: " + text
                        }) { Text("Screen") }

                        Button(onClick = {
                            AgentContainer.securityKernel.emergencyStop()
                            stopped = true
                        }) { Text("STOP") }
                    }

                    if (stopped) {
                        OutlinedButton(onClick = {
                            AgentContainer.securityKernel.resetAfterExplicitUserAction()
                            stopped = false
                            messages = messages + "Agent: Emergency stop reset by explicit user action."
                        }) { Text("Reset STOP") }
                    }

                    if (attention.pending().isNotEmpty()) {
                        Text("Attention", style = MaterialTheme.typography.titleMedium)
                        attention.pending().take(3).forEach { item ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.title, Modifier.weight(1f))
                                TextButton(onClick = {
                                    if (item.dueAt != null) reminderScheduler.cancel(item)
                                    attention.complete(item.id)
                                    attentionVersion++
                                }) { Text("Done") }
                            }
                        }
                    }
                    if (attentionVersion < 0) Text("")
                }
            }

            pending?.let { action ->
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Approve device action?") },
                    text = {
                        Text(
                            "Action: " + action.request.type.name.lowercase() +
                                "\nReason: " + action.request.reason +
                                (action.request.target?.text?.let { "\nTarget: " + it } ?: "")
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val service = AgentAccessibilityService.instance()
                            if (service == null) {
                                messages = messages + "Agent: Accessibility Service is not connected, so nothing was executed."
                            } else {
                                val result = runtime.approve(action.id) { request ->
                                    service.executeApproved(request)
                                }
                                messages = messages + "Agent: " + (result?.message ?: "Approval expired.")
                            }
                            pending = null
                        }) { Text("Approve") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            runtime.reject(action.id)
                            messages = messages + "Agent: Action rejected."
                            pending = null
                        }) { Text("Reject") }
                    }
                )
            }
        }
    }
}
