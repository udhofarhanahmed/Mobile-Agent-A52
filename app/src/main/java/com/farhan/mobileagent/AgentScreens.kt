package com.farhan.mobileagent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.farhan.mobileagent.agent.attention.AttentionItem
import com.farhan.mobileagent.agent.core.CapabilityDescriptor
import com.farhan.mobileagent.agent.core.CapabilityStatus
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.task.AgentTask
import com.farhan.mobileagent.agent.task.TaskState
import com.farhan.mobileagent.agent.task.TaskStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AgentHome(
    modifier: Modifier,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onCall: () -> Unit,
    onMusic: () -> Unit,
    onFiles: () -> Unit,
    messages: List<String>,
    capabilities: List<CapabilityDescriptor>,
    battery: Int,
    stopped: Boolean,
    attention: List<AttentionItem>,
    notesCount: Int,
    onScreen: () -> Unit,
    onRun: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(com.farhan.mobileagent.R.drawable.agent_logo),
                null,
                Modifier.size(48.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mobile Agent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Your private, on-device assistant", style = MaterialTheme.typography.bodySmall)
            }
            StatusPill(if (stopped) "STOPPED" else "ONLINE", stopped)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("YOUR DAY, IN ONE PLACE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (stopped) "Emergency stop is on" else "What can I help with?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (stopped) {
                        "Actions are paused. Open Settings when you are ready to reset it."
                    } else {
                        "I work offline with supported commands. Phone actions always wait for your approval."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    input,
                    onInput,
                    Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask a question or give me a command") },
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (!stopped && input.isNotBlank()) onSend() }
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSend, enabled = !stopped && input.isNotBlank()) { Text("Send") }
                    OutlinedButton(onClick = onVoice, enabled = !stopped) { Text("Voice") }
                }
                Text("TRY A COMMAND", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = { onInput("what is 25% of 80") }, label = { Text("Calculate") })
                    AssistChip(
                        onClick = { onInput("remind me to call Ali in 20 minutes") },
                        label = { Text("Set reminder") }
                    )
                    AssistChip(onClick = { onInput("battery") }, label = { Text("Battery") })
                }
                Text("QUICK ACCESS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = onCall, label = { Text("Call") })
                    AssistChip(onClick = onMusic, label = { Text("Music") })
                    AssistChip(onClick = onFiles, label = { Text("Files") })
                    AssistChip(onClick = onSettings, label = { Text("Settings") })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Battery", battery.toString() + "%", Modifier.weight(1f))
                MetricCard(
                    "Screen access",
                    if (capabilities.firstOrNull { it.capability == "Screen observation" }?.status == CapabilityStatus.READY) "Ready" else "Needed",
                    Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("To do", attention.size.toString(), Modifier.weight(1f))
                MetricCard("Saved notes", notesCount.toString(), Modifier.weight(1f))
            }
        }

        SectionCard("Up next") {
            if (attention.isEmpty()) {
                Text("You’re all caught up. Reminders and tasks will appear here.")
            } else {
                attention.take(5).forEach {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(it.title, fontWeight = FontWeight.SemiBold)
                        it.dueAt?.let { due -> Text("Due " + agentTime(due), style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider()
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onRun, enabled = !stopped, modifier = Modifier.weight(1f)) {
                    Text("Run authorized work")
                }
                OutlinedButton(onClick = onScreen, modifier = Modifier.weight(1f)) {
                    Text("Inspect screen")
                }
            }
        }

        SectionCard("Recent activity") {
            val recent = messages.takeLast(5)
            if (recent.isEmpty()) {
                Text("Your recent commands and results will appear here.")
            } else {
                recent.forEachIndexed { index, message ->
                    ActivityBubble(message, isLatest = index == recent.lastIndex)
                }
            }
        }

    }
}

@Composable
private fun ActivityBubble(message: String, isLatest: Boolean) {
    val isUser = message.startsWith("You")
    val content = message.substringAfter(": ", message)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(18.dp),
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isLatest -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    if (isUser) "YOU" else "AGENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun TaskScreen(
    modifier: Modifier,
    version: Int,
    attention: List<AttentionItem>,
    store: TaskStore,
    autonomy: Boolean,
    onDone: (String) -> Unit,
    onAuthorize: (String) -> Unit,
    onRevoke: (String) -> Unit,
    onApproveStep: (AgentTask) -> Unit
) {
    val taskList = remember(version) { store.all().reversed() }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Reminders, attention and autonomous work in one place.")

        SectionCard("Things worth your attention") {
            if (attention.isEmpty()) {
                Text("Nothing pending.")
            } else {
                attention.take(20).forEach {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(it.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                it.kind.name.lowercase() + (it.dueAt?.let { due -> " • " + agentTime(due) } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { onDone(it.id) }) { Text("Done") }
                    }
                    HorizontalDivider()
                }
            }
        }

        SectionCard("Background agent queue") {
            val active = taskList.filter {
                it.state != TaskState.COMPLETED && it.state != TaskState.CANCELLED
            }
            if (active.isEmpty()) {
                Text("No active background tasks.")
            } else {
                active.take(15).forEach { task ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(task.title, fontWeight = FontWeight.Bold)
                            Text(
                                "State: " + task.state.name.lowercase() +
                                    " • Step " + ((task.currentStep + 1).coerceAtMost(task.steps.size)) +
                                    "/" + task.steps.size,
                                style = MaterialTheme.typography.bodySmall
                            )
                            task.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!task.approvedForBackground) {
                                    Button(onClick = { onAuthorize(task.id) }, enabled = autonomy) { Text("Authorize") }
                                } else {
                                    OutlinedButton(onClick = { onRevoke(task.id) }) { Text("Revoke") }
                                }
                                if (task.state == TaskState.WAITING_FOR_USER && task.approvedForBackground) {
                                    OutlinedButton(onClick = { onApproveStep(task) }) { Text("Approve step") }
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionCard("Agent policy") {
            Text("Low-risk supported actions may run only after you authorize the task.")
            Text("Taps, swipes and text entry pause for you.")
            Text("STOP, time limits and action budgets remain above the planner.")
        }
    }
}

@Composable
internal fun ToolScreen(
    modifier: Modifier,
    onCommand: (String) -> Unit,
    onCall: () -> Unit,
    onMusic: () -> Unit,
    onFiles: () -> Unit,
    onScreen: () -> Unit,
    onAction: (ActionRequest) -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Direct entry points for the things this A52 agent can already do.")

        ToolCard("Protected calls", "Resolve a saved contact locally and prepare the Phone app.", "Call", onCall)
        ToolCard("Reminders & alarms", "Examples: set an alarm for 7:30 pm; remind me to call Ali in 20 minutes.", "Test alarm", { onCommand("set an alarm for 7:30") })
        ToolCard("Local music", "Play local audio without cloud streaming.", "Open music", onMusic)
        ToolCard("File Lab", "Analyze supported files locally.", "Open File Lab", onFiles)
        ToolCard("Screen awareness", "Inspect the supported screen tree.", "Inspect", onScreen)
        ToolCard("Volume control", "Run a protected volume action.", "Volume up", { onAction(ActionRequest(ActionType.VOLUME_UP, reason = "User requested volume up")) })
        ToolCard("Navigation", "Run a protected Home action.", "Home", { onAction(ActionRequest(ActionType.HOME, reason = "User requested Home")) })

        SectionCard("Fast commands") {
            listOf(
                "Set an alarm for 7:30 pm",
                "Remind me to call Ali in 20 minutes",
                "Call Ali",
                "Play music",
                "Open file lab",
                "Background open Chrome then go home"
            ).forEach { text ->
                TextButton(onClick = { onCommand(text) }) { Text(text) }
            }
        }
    }
}

@Composable
internal fun ProSettings(
    modifier: Modifier,
    stopped: Boolean,
    autonomy: Boolean,
    startOnBoot: Boolean,
    theme: AgentThemeMode,
    contacts: Boolean,
    microphone: Boolean,
    notifications: Boolean,
    notificationAccess: Boolean,
    accessibility: Boolean,
    exactAlarms: Boolean,
    batteryOptimized: Boolean,
    onAutonomy: (Boolean) -> Unit,
    onBoot: (Boolean) -> Unit,
    onTheme: (AgentThemeMode) -> Unit,
    onAccessibility: () -> Unit,
    onNotificationAccess: () -> Unit,
    onPostNotifications: () -> Unit,
    onContacts: () -> Unit,
    onMicrophone: () -> Unit,
    onExactAlarms: () -> Unit,
    onBattery: () -> Unit,
    onResetStop: () -> Unit,
    onClearAudit: () -> Unit,
    onClearNotifications: () -> Unit,
    onCancelTasks: () -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pro Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Tune autonomy, appearance, permissions and local data without hiding what the agent can access.")

        SectionCard("Security & autonomy") {
            SettingSwitch(
                "Background autonomy",
                if (autonomy) "ON • authorized tasks may run within policy limits." else "OFF • autonomous execution is disabled.",
                autonomy,
                !stopped,
                onAutonomy
            )
            SettingSwitch(
                "Start after reboot",
                "Resume only already-authorized background work.",
                startOnBoot,
                true,
                onBoot
            )
            Text("Protected rule: device/UI actions keep explicit approval. The planner cannot turn that off.")
            if (stopped) Button(onClick = onResetStop, Modifier.fillMaxWidth()) { Text("Reset Emergency STOP") }
        }

        SectionCard("Access center") {
            AccessLine("Accessibility", if (accessibility) "Connected" else "Needed for screen/UI control", accessibility, onAccessibility)
            AccessLine("Notification awareness", if (notificationAccess) "Enabled" else "Optional metadata awareness", notificationAccess, onNotificationAccess)
            AccessLine("Notifications", if (notifications) "Allowed" else "Needed for reminder alerts", notifications, onPostNotifications)
            AccessLine("Contacts", if (contacts) "Allowed" else "Needed to resolve “Call Ali”", contacts, onContacts)
            AccessLine("Microphone", if (microphone) "Allowed" else "Needed for voice", microphone, onMicrophone)
            AccessLine("Exact alarms", if (exactAlarms) "Allowed" else "Recommended for precise alarms", exactAlarms, onExactAlarms)
            AccessLine("Battery optimization", if (!batteryOptimized) "Not optimized" else "May limit long background work", !batteryOptimized, onBattery)
        }

        SectionCard("Appearance") {
            Text("Theme", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentThemeMode.entries.forEach {
                    FilterChip(
                        selected = theme == it,
                        onClick = { onTheme(it) },
                        label = { Text(it.label) }
                    )
                }
            }
        }

        SectionCard("Local data") {
            Text("Encrypted local memory holds agent state. Notification awareness stores metadata only.")
            OutlinedButton(onClick = onClearAudit, Modifier.fillMaxWidth()) { Text("Clear audit history") }
            OutlinedButton(onClick = onClearNotifications, Modifier.fillMaxWidth()) { Text("Clear notification metadata") }
            OutlinedButton(onClick = onCancelTasks, Modifier.fillMaxWidth()) { Text("Cancel all local tasks") }
        }

        SectionCard("Capabilities") {
            CapabilityLine("Local commands & planning", true)
            CapabilityLine("Reminders and alarms", true)
            CapabilityLine("Protected calls by contact name", contacts)
            CapabilityLine("Screen observation and UI automation", accessibility)
            CapabilityLine("Notification metadata awareness", notificationAccess)
            CapabilityLine("Background work", autonomy)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun AccessLine(title: String, detail: String, ready: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onClick) { Text(if (ready) "Manage" else "Enable") }
    }
}

@Composable
private fun CapabilityLine(title: String, ready: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Text(
            if (ready) "READY" else "NEEDS ACCESS",
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ToolCard(title: String, detail: String, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatusPill(label: String, error: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
    }
}

private fun agentTime(value: Long): String =
    DateTimeFormatter.ofPattern("d MMM • h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(value))

@Composable
internal fun MobileAgentTheme(mode: AgentThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        AgentThemeMode.SYSTEM -> isSystemInDarkTheme()
        AgentThemeMode.LIGHT -> false
        AgentThemeMode.DARK -> true
    }
    val light = lightColorScheme(
        primary = Color(0xFF7655D9),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF241047),
        secondaryContainer = Color(0xFFE9DFFF),
        onSecondaryContainer = Color(0xFF231B30),
        background = Color(0xFFF7F5FA),
        surface = Color.White,
        surfaceVariant = Color(0xFFEEEAF4)
    )
    val darkScheme = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF352052),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFF6EDFF),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFEFE7F7),
        background = Color(0xFF0F0D13),
        surface = Color(0xFF1A1820),
        surfaceVariant = Color(0xFF302C37)
    )
    MaterialTheme(colorScheme = if (dark) darkScheme else light, content = content)
}
