package com.farhan.mobileagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.mobileagent.agent.accessibility.AgentAccessibilityService
import com.farhan.mobileagent.agent.accessibility.ScreenStateStore
import com.farhan.mobileagent.agent.attention.AttentionItem
import com.farhan.mobileagent.agent.attention.ReminderScheduler
import com.farhan.mobileagent.agent.command.CommandResult
import com.farhan.mobileagent.agent.command.LocalCommandRouter
import com.farhan.mobileagent.agent.core.AgentContainer
import com.farhan.mobileagent.agent.core.AgentRuntime
import com.farhan.mobileagent.agent.core.PendingAction
import com.farhan.mobileagent.agent.device.DeviceStateProvider
import com.farhan.mobileagent.agent.notification.NotificationInbox
import com.farhan.mobileagent.agent.notification.NotificationListenerState
import com.farhan.mobileagent.agent.task.BackgroundAgentService
import com.farhan.mobileagent.agent.task.TaskRunner
import com.farhan.mobileagent.agent.task.TaskState
import com.farhan.mobileagent.agent.task.TaskStore

private data class PendingTaskApproval(
    val taskId: String,
    val stepId: String,
    val pending: PendingAction
)

class MainActivity : ComponentActivity() {
    private lateinit var runtime: AgentRuntime
    private lateinit var attention: com.farhan.mobileagent.agent.attention.AttentionManager
    private val router = LocalCommandRouter()
    private lateinit var deviceProvider: DeviceStateProvider
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var taskStore: TaskStore
    private lateinit var taskRunner: TaskRunner
    private lateinit var notificationInbox: NotificationInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentContainer.initialize(applicationContext)

        attention = AgentContainer.attentionManager
        deviceProvider = DeviceStateProvider(this)
        reminderScheduler = ReminderScheduler(applicationContext)
        taskStore = TaskStore(applicationContext)
        runtime = AgentRuntime(
            AgentContainer.securityKernel,
            AgentContainer.confirmationManager,
            AgentContainer.auditLog
        )
        taskRunner = TaskRunner(
            taskStore,
            runtime,
            AgentContainer.securityKernel
        )
        notificationInbox = NotificationInbox(applicationContext)

        setContent {
            var input by remember { mutableStateOf("") }
            var messages by remember { mutableStateOf(listOf("Agent: Local-first and ready.")) }
            var stopped by remember { mutableStateOf(AgentContainer.securityKernel.isStopped()) }
            var autonomyEnabled by remember {
                mutableStateOf(AgentContainer.securityKernel.isBackgroundAutonomyEnabled())
            }
            var pending by remember { mutableStateOf<PendingAction?>(null) }
            var pendingTaskApproval by remember { mutableStateOf<PendingTaskApproval?>(null) }
            var version by remember { mutableIntStateOf(0) }
            var auditVersion by remember { mutableIntStateOf(0) }

            val notificationEnabled = NotificationManagerCompat
                .getEnabledListenerPackages(this@MainActivity)
                .contains(packageName)
            val auditEvents = remember(auditVersion) {
                AgentContainer.auditLog.snapshot().takeLast(8).asReversed()
            }

            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Mobile Agent A52", style = MaterialTheme.typography.headlineMedium)
                    Text(if (stopped) "EMERGENCY STOP ACTIVE" else "Protected • Local • Policy gated")

                    Text(
                        "Battery " + deviceProvider.snapshot().batteryPercent + "% • " +
                            if (AgentAccessibilityService.instance() != null) "Accessibility connected"
                            else "Accessibility not connected"
                    )
                    Text("Background tasks: " + taskStore.active().size)
                    Text(
                        "Notifications • " +
                            if (notificationEnabled && NotificationListenerState.connected) {
                                "awareness connected"
                            } else if (notificationEnabled) {
                                "access enabled, service reconnecting"
                            } else {
                                "awareness disabled"
                            }
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Security controls",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Background autonomy")
                                    Text(
                                        if (autonomyEnabled) {
                                            "Authorized background tasks may run within policy limits."
                                        } else {
                                            "Background execution is globally disabled."
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = autonomyEnabled,
                                    onCheckedChange = { enabled ->
                                        AgentContainer.securityKernel.setBackgroundAutonomyEnabled(enabled)
                                        autonomyEnabled = enabled
                                        if (!enabled) {
                                            stopService(
                                                Intent(
                                                    this@MainActivity,
                                                    BackgroundAgentService::class.java
                                                )
                                            )
                                            messages = messages +
                                                "Agent: Background autonomy disabled and service stopped."
                                        } else {
                                            messages = messages +
                                                "Agent: Background autonomy enabled. Authorized tasks still require their own approval."
                                        }
                                        auditVersion++
                                    }
                                )
                            }

                            if (auditEvents.isEmpty()) {
                                Text(
                                    "No audit events yet.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                auditEvents.forEach { event ->
                                    Text(
                                        event.timestamp + " • " +
                                            event.action + " • " +
                                            event.decision +
                                            (if (event.detail.isBlank()) "" else " • " + event.detail),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(onClick = {
                                    AgentContainer.auditLog.clear()
                                    auditVersion++
                                    messages = messages + "Agent: Local audit history cleared."
                                }) {
                                    Text("Clear audit history")
                                }
                            }
                        }
                    }

                    if (notificationEnabled) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Recent notification awareness",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                notificationInbox.latest().take(5).forEach { item ->
                                    Text(
                                        item.appLabel + " • " +
                                            item.category.orEmpty().ifBlank { "notification" }
                                    )
                                }
                                OutlinedButton(onClick = {
                                    notificationInbox.clear()
                                    version++
                                    messages = messages + "Agent: Notification history cleared locally."
                                }) { Text("Clear notification history") }
                            }
                        }
                    }

                    OutlinedButton(onClick = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Text(
                            if (notificationEnabled) {
                                "Notification access settings"
                            } else {
                                "Enable notification awareness"
                            }
                        )
                    }

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
                                    version++
                                    messages = messages + "You: " + command +
                                        "\nAgent: " + if (result.dueAt != null) "Reminder scheduled locally." else "Added to attention."
                                    auditVersion++
                                }

                                is CommandResult.BackgroundTask -> {
                                    taskStore.upsert(result.task)
                                    messages = messages + "You: " + command +
                                        "\nAgent: Background task prepared. Review and authorize it below."
                                    version++
                                    auditVersion++
                                }

                                is CommandResult.Action -> {
                                    val prepared = runtime.prepare(result.request)
                                    if (prepared == null) {
                                        messages = messages + "Agent: Action blocked by the security state."
                                    } else {
                                        pending = prepared
                                        messages = messages + "Agent: Approval required for " +
                                            result.request.type.name.lowercase() + "."
                                    }
                                    auditVersion++
                                }

                                is CommandResult.Unknown ->
                                    messages = messages + "You: " + command +
                                        "\nAgent: " + result.text
                            }
                            input = ""
                        }) { Text("Send") }

                        OutlinedButton(onClick = {
                            val screen = ScreenStateStore.get()
                            messages = messages + "Agent: " +
                                if (screen.packageName == null) {
                                    "No screen observation is available. Enable Accessibility Service."
                                } else {
                                    "Current app: " + screen.packageName +
                                        "\nVisible text: " + screen.visibleText.take(12).joinToString(" • ")
                                }
                        }) { Text("Screen") }

                        OutlinedButton(onClick = {
                            if (!autonomyEnabled) {
                                messages = messages + "Agent: Enable Background autonomy first."
                            } else if (taskStore.active().any { it.approvedForBackground }) {
                                val serviceIntent = Intent(this@MainActivity, BackgroundAgentService::class.java)
                                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                                messages = messages + "Agent: Background engine started for authorized tasks."
                            } else {
                                messages = messages + "Agent: No authorized active background tasks."
                            }
                        }) { Text("Run background") }

                        Button(onClick = {
                            AgentContainer.securityKernel.emergencyStop()
                            stopService(
                                Intent(this@MainActivity, BackgroundAgentService::class.java)
                            )
                            stopped = true
                            messages = messages + "Agent: Emergency STOP persisted. Background work is blocked until reset."
                            auditVersion++
                        }) { Text("STOP") }
                    }

                    taskStore.all().filter {
                        it.state == TaskState.QUEUED ||
                            it.state == TaskState.WAITING_FOR_USER ||
                            (it.state == TaskState.PAUSED && it.approvedForBackground)
                    }.take(5).forEach { task ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(task.title, style = MaterialTheme.typography.titleMedium)
                                val shownStep = (task.currentStep + 1).coerceAtMost(task.steps.size)
                                Text("State: " + task.state.name.lowercase() +
                                    " • Step " + shownStep + "/" + task.steps.size)
                                task.lastError?.let { Text("Note: " + it) }

                                if (!task.approvedForBackground) {
                                    Button(onClick = {
                                        if (AgentContainer.securityKernel.isStopped()) {
                                            messages = messages + "Agent: Reset STOP before authorizing new background work."
                                        } else if (!autonomyEnabled) {
                                            messages = messages + "Agent: Enable Background autonomy before authorizing background work."
                                        } else {
                                            val authorized = taskStore.authorizeBackground(task.id)
                                            if (authorized != null) {
                                                val serviceIntent = Intent(this@MainActivity, BackgroundAgentService::class.java)
                                                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                                                if (Build.VERSION.SDK_INT >= 33 &&
                                                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                                ) {
                                                    ActivityCompat.requestPermissions(
                                                        this@MainActivity,
                                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                                        502
                                                    )
                                                }
                                                messages = messages + "Agent: Background authorization granted for " + task.title
                                                version++
                                                auditVersion++
                                            }
                                        }
                                    }) { Text("Authorize background") }
                                }

                                if (task.state == TaskState.WAITING_FOR_USER &&
                                    task.approvedForBackground &&
                                    task.currentStep < task.steps.size
                                ) {
                                    Button(onClick = {
                                        val step = task.steps[task.currentStep]
                                        val prepared = runtime.prepare(step.action)
                                        if (prepared == null) {
                                            messages = messages + "Agent: Could not prepare the sensitive step."
                                        } else {
                                            pendingTaskApproval = PendingTaskApproval(
                                                task.id,
                                                step.id,
                                                prepared
                                            )
                                        }
                                        auditVersion++
                                    }) { Text("Approve current step") }
                                }
                            }
                        }
                    }

                    if (stopped) {
                        OutlinedButton(onClick = {
                            AgentContainer.securityKernel.resetAfterExplicitUserAction()
                            stopped = false
                            messages = messages + "Agent: Emergency stop reset by explicit user action."
                            auditVersion++
                        }) { Text("Reset STOP") }
                    }

                    if (version < 0) Text("")
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
                                messages = messages + "Agent: Accessibility Service is not connected."
                            } else {
                                val result = runtime.approve(action.id) { request ->
                                    service.executeApproved(request)
                                }
                                messages = messages + "Agent: " + (result?.message ?: "Approval expired.")
                            }
                            pending = null
                            auditVersion++
                        }) { Text("Approve") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            runtime.reject(action.id)
                            messages = messages + "Agent: Action rejected."
                            pending = null
                            auditVersion++
                        }) { Text("Reject") }
                    }
                )
            }

            pendingTaskApproval?.let { approval ->
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Approve background step?") },
                    text = {
                        val step = taskStore.get(approval.taskId)?.steps?.firstOrNull { it.id == approval.stepId }
                        Text(
                            "Action: " + (step?.action?.type?.name?.lowercase() ?: "unknown") +
                                (step?.action?.target?.text?.let { "\nTarget: " + it } ?: "") +
                                "\nThis step was paused because background automation is not authorized for this action."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val service = AgentAccessibilityService.instance()
                            if (service == null) {
                                messages = messages + "Agent: Accessibility Service is not connected."
                            } else {
                                val result = runtime.approve(approval.pending.id) { request ->
                                    service.executeApproved(request)
                                }
                                if (result != null) {
                                    taskRunner.recordUserApprovedStep(
                                        approval.taskId,
                                        approval.stepId,
                                        result
                                    )
                                    messages = messages + "Agent: Background step " +
                                        if (result.success) {
                                            "approved and completed."
                                        } else {
                                            "failed: " + result.message
                                        }
                                    version++
                                } else {
                                    messages = messages + "Agent: Background-step approval expired."
                                }
                            }
                            pendingTaskApproval = null
                            auditVersion++
                        }) { Text("Approve") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            runtime.reject(approval.pending.id)
                            messages = messages + "Agent: Background step rejected; task remains paused."
                            pendingTaskApproval = null
                            auditVersion++
                        }) { Text("Reject") }
                    }
                )
            }
        }
    }
}
