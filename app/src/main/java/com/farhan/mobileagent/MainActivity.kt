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
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farhan.mobileagent.agent.accessibility.AgentAccessibilityService
import com.farhan.mobileagent.agent.accessibility.ScreenStateStore
import com.farhan.mobileagent.agent.attention.AttentionItem
import com.farhan.mobileagent.agent.attention.ReminderScheduler
import com.farhan.mobileagent.agent.command.CommandResult
import com.farhan.mobileagent.agent.command.LocalCommandRouter
import com.farhan.mobileagent.agent.core.AgentContainer
import com.farhan.mobileagent.agent.core.AgentRuntime
import com.farhan.mobileagent.agent.core.CapabilityCenter
import com.farhan.mobileagent.agent.core.CapabilityClass
import com.farhan.mobileagent.agent.core.CapabilityStatus
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
            val capabilities = CapabilityCenter.snapshot(this@MainActivity)

            MobileAgentTheme {
                Column(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (stopped) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Mobile Agent A52",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (stopped) {
                                            "Emergency STOP is active"
                                        } else {
                                            "Local-first • policy gated • user controlled"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (stopped) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        }
                                    )
                                }
                                StatusPill(
                                    if (stopped) "STOPPED" else "PROTECTED",
                                    isError = stopped
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { },
                                    enabled = false,
                                    label = { Text("Battery " + deviceProvider.snapshot().batteryPercent + "%") }
                                )
                                AssistChip(
                                    onClick = { },
                                    enabled = false,
                                    label = {
                                        Text(
                                            if (AgentAccessibilityService.instance() != null) {
                                                "Screen connected"
                                            } else {
                                                "Screen access needed"
                                            }
                                        )
                                    }
                                )
                            }

                            AssistChip(
                                onClick = { },
                                enabled = false,
                                label = {
                                    Text(
                                        when {
                                            stopped -> "Notifications blocked by STOP"
                                            notificationEnabled && NotificationListenerState.connected -> "Notifications connected"
                                            notificationEnabled -> "Notification access enabled"
                                            else -> "Notifications off"
                                        }
                                    )
                                }
                            )

                            if (stopped) {
                                Button(
                                    onClick = {
                                        AgentContainer.securityKernel.resetAfterExplicitUserAction()
                                        stopped = false
                                        messages = messages + "Agent: Emergency STOP reset by explicit user action."
                                        auditVersion++
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Resume agent")
                                }
                            }
                        }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Capability center",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Observe • Suggest • Act",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    "LOCAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            CapabilityClass.entries.forEach { group ->
                                Text(
                                    group.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                capabilities.filter { it.group == group }.forEach { capability ->
                                    CapabilityRow(companion = capability)
                                }
                            }
                        }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Security controls",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Background autonomy", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        when {
                                            stopped -> "Policy is " + if (autonomyEnabled) "ON" else "OFF" +
                                                " • Emergency STOP overrides execution."
                                            autonomyEnabled -> "ON • Authorized tasks may run within policy limits."
                                            else -> "OFF • Autonomous background execution is disabled."
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = autonomyEnabled,
                                    enabled = !stopped,
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
                                    "Audit trail is empty.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    "Recent audit • " + auditEvents.size + " event(s)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                auditEvents.take(4).forEach { event ->
                                    Text(
                                        event.timestamp + " • " +
                                            event.action + " • " +
                                            event.decision,
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

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Things worth your attention",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (attention.pending().isEmpty()) {
                                            "You're clear."
                                        } else {
                                            attention.pending().size.toString() + " pending item(s)"
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    "LOCAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            attention.pending().take(4).forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, fontWeight = FontWeight.Medium)
                                        if (item.dueAt != null) {
                                            Text(
                                                "Due " + formatAttentionTime(item.dueAt),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    TextButton(onClick = {
                                        attention.complete(item.id)
                                        version++
                                    }) {
                                        Text("Done")
                                    }
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

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            messages.takeLast(8).reversed().forEachIndexed { index, message ->
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal
                                )
                                if (index < messages.takeLast(8).lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }
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

@Composable
private fun CapabilityRow(companion: com.farhan.mobileagent.agent.core.CapabilityDescriptor) {
    val label = when (companion.status) {
        CapabilityStatus.READY -> "READY"
        CapabilityStatus.ENABLED_NOT_CONNECTED -> "RECONNECTING"
        CapabilityStatus.USER_APPROVAL_REQUIRED -> "APPROVAL"
        CapabilityStatus.NEEDS_ACCESS -> "NEEDS ACCESS"
        CapabilityStatus.DISABLED_BY_USER -> "OFF"
        CapabilityStatus.DISABLED_BY_STOP -> "STOPPED"
    }

    val color = when (companion.status) {
        CapabilityStatus.READY -> MaterialTheme.colorScheme.primary
        CapabilityStatus.ENABLED_NOT_CONNECTED -> MaterialTheme.colorScheme.tertiary
        CapabilityStatus.USER_APPROVAL_REQUIRED -> MaterialTheme.colorScheme.secondary
        CapabilityStatus.NEEDS_ACCESS,
        CapabilityStatus.DISABLED_BY_STOP -> MaterialTheme.colorScheme.error
        CapabilityStatus.DISABLED_BY_USER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(companion.capability, fontWeight = FontWeight.Medium)
                Text(companion.detail, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatAttentionTime(timestamp: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("d MMM • h:mm a")
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(timestamp))

@Composable
private fun StatusPill(label: String, isError: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private val AgentLightColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF6650A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFF8F6FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDE7F6),
    error = Color(0xFFBA1A1A)
)

private val AgentDarkColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFD0BCFF),
    primaryContainer = Color(0xFF4F378B),
    secondaryContainer = Color(0xFF4A4458),
    background = Color(0xFF141218),
    surface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFF49454F)
)

@Composable
private fun MobileAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) AgentDarkColors else AgentLightColors,
        content = content
    )
}
