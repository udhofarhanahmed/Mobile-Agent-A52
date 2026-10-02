package com.farhan.mobileagent

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.farhan.mobileagent.agent.accessibility.AgentAccessibilityService
import com.farhan.mobileagent.agent.accessibility.ScreenStateStore
import com.farhan.mobileagent.agent.ai.OfflineAiPolicy
import com.farhan.mobileagent.agent.ai.RuleBasedOfflinePlanner
import com.farhan.mobileagent.agent.attention.AttentionItem
import com.farhan.mobileagent.agent.attention.AttentionKind
import com.farhan.mobileagent.agent.attention.ReminderScheduler
import com.farhan.mobileagent.agent.command.CommandResult
import com.farhan.mobileagent.agent.command.LocalCommandRouter
import com.farhan.mobileagent.agent.core.AgentContainer
import com.farhan.mobileagent.agent.core.AgentRuntime
import com.farhan.mobileagent.agent.core.CapabilityCenter
import com.farhan.mobileagent.agent.core.CapabilityStatus
import com.farhan.mobileagent.agent.core.PendingAction
import com.farhan.mobileagent.agent.device.DeviceStateProvider
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.notification.NotificationInbox
import com.farhan.mobileagent.agent.storage.NoteStore
import com.farhan.mobileagent.agent.task.AgentTask
import com.farhan.mobileagent.agent.task.BackgroundAgentService
import com.farhan.mobileagent.agent.task.TaskRunner
import com.farhan.mobileagent.agent.task.TaskState
import com.farhan.mobileagent.agent.task.TaskStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class AgentTab(val label: String, val marker: String) {
    AGENT("Agent", "A"),
    TASKS("Tasks", "T"),
    TOOLS("Tools", "✦"),
    SETTINGS("Settings", "⚙")
}

private data class PendingTaskApproval(
    val taskId: String,
    val stepId: String,
    val pending: PendingAction
)

class MainActivity : ComponentActivity() {
    private var voiceTranscript by mutableStateOf<String?>(null)
    private var refresh by mutableIntStateOf(0)

    private lateinit var runtime: AgentRuntime
    private lateinit var attention: com.farhan.mobileagent.agent.attention.AttentionManager
    private lateinit var device: DeviceStateProvider
    private lateinit var reminders: ReminderScheduler
    private lateinit var tasks: TaskStore
    private lateinit var taskRunner: TaskRunner
    private lateinit var notifications: NotificationInbox
    private lateinit var notes: NoteStore

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            voiceTranscript = result.data?.getStringExtra(VoiceActivity.EXTRA_TRANSCRIPT)
        }
    }

    private val contactsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    private val microphoneLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    private val postNotificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentContainer.initialize(applicationContext)
        attention = AgentContainer.attentionManager
        device = DeviceStateProvider(this)
        reminders = ReminderScheduler(applicationContext)
        tasks = TaskStore(applicationContext)
        runtime = AgentRuntime(
            AgentContainer.securityKernel,
            AgentContainer.confirmationManager,
            AgentContainer.auditLog
        )
        taskRunner = TaskRunner(tasks, runtime, AgentContainer.securityKernel)
        notifications = NotificationInbox(applicationContext)
        notes = NoteStore(applicationContext)

        setContent {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var input by remember { mutableStateOf("") }
            var messages by remember {
                mutableStateOf(listOf("Agent: Local-first core online. What would you like me to do?"))
            }
            var stopped by remember { mutableStateOf(AgentContainer.securityKernel.isStopped()) }
            var autonomy by remember { mutableStateOf(AgentContainer.securityKernel.isBackgroundAutonomyEnabled()) }
            var startOnBoot by remember { mutableStateOf(AgentSettings.startOnBoot(this@MainActivity)) }
            var theme by remember { mutableStateOf(AgentSettings.themeMode(this@MainActivity)) }
            var pending by remember { mutableStateOf<PendingAction?>(null) }
            var pendingTask by remember { mutableStateOf<PendingTaskApproval?>(null) }
            var dataVersion by remember { mutableIntStateOf(0) }

            LaunchedEffect(voiceTranscript) {
                val text = voiceTranscript ?: return@LaunchedEffect
                input = text
                messages = (messages + ("You (voice): " + text)).takeLast(30)
                voiceTranscript = null
            }

            val notificationAccess = NotificationManagerCompat
                .getEnabledListenerPackages(this@MainActivity)
                .contains(packageName)
            val accessibilityReady = AgentAccessibilityService.instance() != null
            val contactsReady = Build.VERSION.SDK_INT < 23 ||
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            val micReady = Build.VERSION.SDK_INT < 23 ||
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            val notificationsReady = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            val exactAlarmsReady = Build.VERSION.SDK_INT < 31 ||
                getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
            val batteryOptimized = getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(packageName) != true
            val capabilities = CapabilityCenter.snapshot(this@MainActivity)

            fun say(message: String) {
                messages = (messages + message).takeLast(30)
            }

            fun openAndroid(action: String) {
                runCatching { startActivity(Intent(action)) }
                    .onFailure { say("Agent: Android could not open that settings page.") }
            }

            fun queueAction(request: ActionRequest) {
                val prepared = runtime.prepare(request)
                if (prepared == null) {
                    say("Agent: Action blocked by the current security state.")
                } else {
                    pending = prepared
                    say("Agent: Waiting for approval for " + request.type.name.lowercase() + ".")
                }
            }

            fun runAuthorizedTasks() {
                if (!autonomy) {
                    say("Agent: Background autonomy is OFF.")
                    return
                }
                if (tasks.active().none { it.approvedForBackground }) {
                    say("Agent: There are no authorized background tasks.")
                    return
                }
                runCatching {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, BackgroundAgentService::class.java)
                    )
                }.onSuccess {
                    say("Agent: Background engine started.")
                }.onFailure {
                    say("Agent: Android blocked the background service start.")
                }
            }

            fun executeCommand(command: String) {
                val clean = command.trim()
                if (clean.isBlank()) return
                val proposal = OfflineAiPolicy.sanitize(
                    RuleBasedOfflinePlanner(LocalCommandRouter()).propose(clean, device.snapshot())
                )
                say("You: " + clean)

                when {
                    proposal.attention != null -> {
                        val x = proposal.attention
                        val item = AttentionItem(
                            title = x.title,
                            priority = x.priority,
                            dueAt = x.dueAt,
                            kind = x.kind
                        )
                        attention.add(item)
                        if (x.dueAt != null) reminders.schedule(item)
                        if (x.dueAt != null && Build.VERSION.SDK_INT >= 33 && !notificationsReady) {
                            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        say(
                            "Agent: " +
                                if (x.kind == AttentionKind.REMINDER) {
                                    "Reminder/alarm scheduled locally."
                                } else {
                                    "Added to your attention list."
                                }
                        )
                        dataVersion++
                    }
                    proposal.note != null -> {
                        say(
                            if (notes.add(proposal.note.content) != null) {
                                "Agent: Saved locally to encrypted memory."
                            } else {
                                "Agent: The note was empty."
                            }
                        )
                        dataVersion++
                    }
                    proposal.callTarget != null -> {
                        startActivity(
                            Intent(this@MainActivity, CallActivity::class.java)
                                .putExtra(CallActivity.EXTRA_TARGET, proposal.callTarget)
                        )
                        say("Agent: Preparing a protected call for " + proposal.callTarget + ".")
                    }
                    proposal.openMusic -> {
                        startActivity(Intent(this@MainActivity, MusicActivity::class.java))
                        say("Agent: Opening local music.")
                    }
                    proposal.openFileLab -> {
                        startActivity(Intent(this@MainActivity, FileLabActivity::class.java))
                        say("Agent: Opening File Lab.")
                    }
                    proposal.task != null -> {
                        tasks.upsert(proposal.task)
                        say("Agent: Task planned. Review it in Tasks before authorizing background execution.")
                        dataVersion++
                    }
                    proposal.action != null -> queueAction(proposal.action)
                    proposal.answer != null -> say("Agent: " + proposal.answer)
                    else -> say("Agent: I could not form a safe local plan for that request.")
                }
                input = ""
            }

            MobileAgentTheme(theme) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            AgentTab.entries.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (tab == index) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(item.marker, Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                        }
                                    },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (AgentTab.entries[tab]) {
                        AgentTab.AGENT -> AgentHome(
                            modifier = Modifier.padding(padding),
                            input = input,
                            onInput = { input = it },
                            onSend = { executeCommand(input) },
                            onVoice = { voiceLauncher.launch(Intent(this@MainActivity, VoiceActivity::class.java)) },
                            onCall = {
                                startActivity(
                                    Intent(this@MainActivity, CallActivity::class.java)
                                        .putExtra(CallActivity.EXTRA_TARGET, "")
                                )
                            },
                            onMusic = { startActivity(Intent(this@MainActivity, MusicActivity::class.java)) },
                            onFiles = { startActivity(Intent(this@MainActivity, FileLabActivity::class.java)) },
                            messages = messages,
                            capabilities = capabilities,
                            battery = device.snapshot().batteryPercent,
                            stopped = stopped,
                            attention = attention.pending(),
                            notesCount = notes.all().size,
                            onScreen = {
                                val screen = ScreenStateStore.get()
                                if (screen.packageName == null) {
                                    say("Agent: Accessibility is not connected.")
                                } else {
                                    say(
                                        "Agent: Current app " + screen.packageName +
                                            "\nVisible: " + screen.visibleText.take(12).joinToString(" • ")
                                    )
                                }
                            },
                            onRun = ::runAuthorizedTasks,
                            onStop = {
                                AgentContainer.securityKernel.emergencyStop()
                                stopService(Intent(this@MainActivity, BackgroundAgentService::class.java))
                                stopped = true
                                autonomy = false
                                say("Agent: Emergency STOP is active.")
                            },
                            onSettings = { tab = AgentTab.SETTINGS.ordinal }
                        )

                        AgentTab.TASKS -> TaskScreen(
                            modifier = Modifier.padding(padding),
                            version = dataVersion,
                            attention = attention.pending(),
                            store = tasks,
                            autonomy = autonomy,
                            onDone = {
                                attention.complete(it)
                                dataVersion++
                            },
                            onAuthorize = {
                                if (stopped) say("Agent: Reset Emergency STOP first.")
                                else if (!autonomy) say("Agent: Turn on Background autonomy first.")
                                else if (tasks.authorizeBackground(it) != null) {
                                    say("Agent: Background authorization granted.")
                                    dataVersion++
                                    runAuthorizedTasks()
                                } else say("Agent: That task is no longer eligible.")
                            },
                            onRevoke = {
                                tasks.revokeBackground(it)
                                if (tasks.active().none { it.approvedForBackground }) {
                                    stopService(Intent(this@MainActivity, BackgroundAgentService::class.java))
                                }
                                say("Agent: Background authorization revoked.")
                                dataVersion++
                            },
                            onApproveStep = { task ->
                                val step = task.steps.getOrNull(task.currentStep) ?: return@TaskScreen
                                val prepared = runtime.prepare(step.action)
                                if (prepared == null) {
                                    say("Agent: Could not prepare that sensitive step.")
                                } else {
                                    pendingTask = PendingTaskApproval(task.id, step.id, prepared)
                                }
                            }
                        )

                        AgentTab.TOOLS -> ToolScreen(
                            modifier = Modifier.padding(padding),
                            onCommand = ::executeCommand,
                            onCall = {
                                startActivity(
                                    Intent(this@MainActivity, CallActivity::class.java)
                                        .putExtra(CallActivity.EXTRA_TARGET, "")
                                )
                            },
                            onMusic = { startActivity(Intent(this@MainActivity, MusicActivity::class.java)) },
                            onFiles = { startActivity(Intent(this@MainActivity, FileLabActivity::class.java)) },
                            onScreen = {
                                val screen = ScreenStateStore.get()
                                if (screen.packageName == null) say("Agent: Accessibility is not connected.")
                                else say("Agent: " + screen.packageName + "\n" + screen.visibleText.take(15).joinToString(" • "))
                            },
                            onAction = ::queueAction
                        )

                        AgentTab.SETTINGS -> ProSettings(
                            modifier = Modifier.padding(padding),
                            stopped = stopped,
                            autonomy = autonomy,
                            startOnBoot = startOnBoot,
                            theme = theme,
                            contacts = contactsReady,
                            microphone = micReady,
                            notifications = notificationsReady,
                            notificationAccess = notificationAccess,
                            accessibility = accessibilityReady,
                            exactAlarms = exactAlarmsReady,
                            batteryOptimized = batteryOptimized,
                            onAutonomy = {
                                autonomy = it
                                AgentContainer.securityKernel.setBackgroundAutonomyEnabled(it)
                                if (!it) stopService(Intent(this@MainActivity, BackgroundAgentService::class.java))
                                say("Agent: Background autonomy " + if (it) "enabled." else "disabled.")
                            },
                            onBoot = {
                                startOnBoot = it
                                AgentSettings.setStartOnBoot(this@MainActivity, it)
                            },
                            onTheme = {
                                theme = it
                                AgentSettings.setThemeMode(this@MainActivity, it)
                            },
                            onAccessibility = { openAndroid(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                            onNotificationAccess = { openAndroid(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                            onPostNotifications = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onContacts = {
                                if (Build.VERSION.SDK_INT >= 23) contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            onMicrophone = {
                                if (Build.VERSION.SDK_INT >= 23) microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onExactAlarms = {
                                if (Build.VERSION.SDK_INT >= 31) {
                                    runCatching {
                                        startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .setData(Uri.parse("package:" + packageName))
                                        )
                                    }
                                }
                            },
                            onBattery = { openAndroid(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
                            onResetStop = {
                                AgentContainer.securityKernel.resetAfterExplicitUserAction()
                                stopped = false
                                say("Agent: Emergency STOP reset.")
                            },
                            onClearAudit = {
                                AgentContainer.auditLog.clear()
                                say("Agent: Audit history cleared.")
                            },
                            onClearNotifications = {
                                notifications.clear()
                                say("Agent: Notification metadata cleared.")
                                dataVersion++
                            },
                            onCancelTasks = {
                                tasks.all().forEach { tasks.cancel(it.id) }
                                stopService(Intent(this@MainActivity, BackgroundAgentService::class.java))
                                say("Agent: All local tasks cancelled.")
                                dataVersion++
                            }
                        )
                    }
                }
            }

            pending?.let { action ->
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Approve protected action") },
                    text = {
                        Text(
                            "Action: " + action.request.type.name.lowercase() +
                                "\nReason: " + action.request.reason.ifBlank { "Requested by you" }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val service = AgentAccessibilityService.instance()
                            val execution = if (service == null) null else runtime.approve(action.id) { approved ->
                                service.executeApproved(approved)
                            }
                            say("Agent: " + (execution?.result?.message ?: "Approval expired or Accessibility is unavailable."))
                            pending = null
                        }) { Text("Approve") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            runtime.reject(action.id)
                            pending = null
                            say("Agent: Action rejected.")
                        }) { Text("Reject") }
                    }
                )
            }

            pendingTask?.let { approval ->
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Approve sensitive task step") },
                    text = {
                        val task = tasks.get(approval.taskId)
                        val step = task?.steps?.firstOrNull { it.id == approval.stepId }
                        Text(
                            "Action: " + (step?.action?.type?.name?.lowercase() ?: "unknown") +
                                "\nSensitive UI actions always pause for you."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val service = AgentAccessibilityService.instance()
                            if (service == null) {
                                say("Agent: Accessibility is unavailable.")
                            } else {
                                val execution = runtime.approve(approval.pending.id) { approved ->
                                    service.executeApproved(approved)
                                }
                                if (execution != null) {
                                    taskRunner.recordUserApprovedStep(
                                        approval.taskId,
                                        approval.stepId,
                                        execution.approved,
                                        execution.result
                                    )
                                    say(
                                        "Agent: " + if (execution.result.success) {
                                            "Sensitive step completed."
                                        } else {
                                            "Step failed: " + execution.result.message
                                        }
                                    )
                                    dataVersion++
                                } else say("Agent: Step approval expired.")
                            }
                            pendingTask = null
                        }) { Text("Approve") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            runtime.reject(approval.pending.id)
                            pendingTask = null
                            say("Agent: Step rejected.")
                        }) { Text("Reject") }
                    }
                )
            }

            if (refresh < 0) Text("")
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }
}

@Composable
private fun AgentHome(
    modifier: Modifier,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onCall: () -> Unit,
    onMusic: () -> Unit,
    onFiles: () -> Unit,
    messages: List<String>,
    capabilities: List<com.farhan.mobileagent.agent.core.CapabilityDescriptor>,
    battery: Int,
    stopped: Boolean,
    attention: List<AttentionItem>,
    notesCount: Int,
    onScreen: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(com.farhan.mobileagent.R.drawable.agent_logo),
                "Mobile Agent",
                Modifier.size(56.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mobile Agent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("A52 • local-first personal control", style = MaterialTheme.typography.bodySmall)
            }
            StatusPill(if (stopped) "STOPPED" else "ONLINE", stopped)
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (stopped) "Agent execution is stopped." else "What should I do?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (stopped) {
                        "Reset Emergency STOP from Settings before sending actions."
                    } else {
                        "I plan locally. Protected device actions stay behind the security kernel."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    input,
                    onInput,
                    Modifier.fillMaxWidth(),
                    placeholder = { Text("Try: set an alarm for 7:30 pm") },
                    minLines = 2,
                    maxLines = 4
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSend, enabled = !stopped && input.isNotBlank()) { Text("Send") }
                    OutlinedButton(onClick = onVoice, enabled = !stopped) { Text("Voice") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onCall, label = { Text("Call") })
                    AssistChip(onClick = onMusic, label = { Text("Music") })
                    AssistChip(onClick = onFiles, label = { Text("Files") })
                    AssistChip(onClick = onSettings, label = { Text("Settings") })
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Battery", battery.toString() + "%", Modifier.weight(1f))
            MetricCard(
                "Screen",
                if (capabilities.firstOrNull { it.capability == "Screen observation" }?.status == CapabilityStatus.READY) "Ready" else "Access",
                Modifier.weight(1f)
            )
            MetricCard("Attention", attention.size.toString(), Modifier.weight(1f))
            MetricCard("Memory", notesCount.toString(), Modifier.weight(1f))
        }

        SectionCard("Things worth your attention") {
            if (attention.isEmpty()) {
                Text("You're clear. I will surface reminders, alerts and tasks here.")
            } else {
                attention.take(5).forEach {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(it.title, fontWeight = FontWeight.SemiBold)
                        it.dueAt?.let { due -> Text("Due " + agentTime(due), style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRun, enabled = !stopped) { Text("Run authorized work") }
                OutlinedButton(onClick = onScreen) { Text("Inspect screen") }
            }
        }

        SectionCard("Recent agent activity") {
            messages.takeLast(6).reversed().forEachIndexed { index, message ->
                Text(
                    message,
                    style = if (index == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                )
                if (index < messages.takeLast(6).lastIndex) HorizontalDivider()
            }
        }

        if (!stopped) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Emergency STOP") }
        }
    }
}

@Composable
private fun TaskScreen(
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
private fun ToolScreen(
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
private fun ProSettings(
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
private fun MobileAgentTheme(mode: AgentThemeMode, content: @Composable () -> Unit) {
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