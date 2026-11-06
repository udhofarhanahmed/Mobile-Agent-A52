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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.farhan.mobileagent.agent.accessibility.AgentAccessibilityService
import com.farhan.mobileagent.agent.accessibility.ScreenStateStore
import com.farhan.mobileagent.agent.ai.OfflineAiPolicy
import com.farhan.mobileagent.agent.ai.RuleBasedOfflinePlanner
import com.farhan.mobileagent.agent.attention.AttentionKind
import com.farhan.mobileagent.agent.attention.ReminderScheduler
import com.farhan.mobileagent.agent.core.AgentContainer
import com.farhan.mobileagent.agent.core.AgentRuntime
import com.farhan.mobileagent.agent.core.CapabilityCenter
import com.farhan.mobileagent.agent.core.PendingAction
import com.farhan.mobileagent.agent.device.DeviceStateProvider
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.notification.NotificationInbox
import com.farhan.mobileagent.agent.storage.NoteStore
import com.farhan.mobileagent.agent.task.BackgroundAgentService
import com.farhan.mobileagent.agent.task.TaskRunner
import com.farhan.mobileagent.agent.task.TaskStore

private enum class AgentTab(val label: String, val icon: Int) {
    AGENT("Agent", R.drawable.nav_home),
    TASKS("Tasks", R.drawable.nav_tasks),
    TOOLS("Tools", R.drawable.nav_tools),
    SETTINGS("Settings", R.drawable.nav_settings)
}

private data class PendingTaskApproval(
    val taskId: String,
    val stepId: String,
    val pending: PendingAction
)

private fun actionApprovalDetails(request: ActionRequest): String = buildList {
    add("Action: " + request.type.name.lowercase().replace('_', ' '))
    request.packageName?.let { add("App: $it") }
    request.target?.text?.let { add("Visible target: “$it”") }
    request.target?.resourceId?.let { add("Target ID: $it") }
    request.target?.contentDescription?.let { add("Target description: $it") }
    request.text?.let { add("Text to enter: “$it”") }
    request.gesture?.let {
        add("Swipe: (${it.startX}, ${it.startY}) to (${it.endX}, ${it.endY})")
    }
    add("Reason: " + request.reason.ifBlank { "Requested by you" })
}.joinToString("\n")

class MainActivity : ComponentActivity() {
    private var voiceTranscript by mutableStateOf<String?>(null)
    private var refresh by mutableIntStateOf(0)
    private val planner = RuleBasedOfflinePlanner()

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

            val notificationAccess = remember(refresh) {
                NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity).contains(packageName)
            }
            val accessibilityReady = remember(refresh) { AgentAccessibilityService.instance() != null }
            val contactsReady = remember(refresh) {
                Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            }
            val micReady = remember(refresh) {
                Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
            val notificationsReady = remember(refresh) {
                Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            val exactAlarmsReady = remember(refresh) {
                Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
            }
            val batteryOptimized = remember(refresh) {
                getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) != true
            }
            val capabilities = remember(refresh) { CapabilityCenter.snapshot(this@MainActivity) }

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

            fun emergencyStop() {
                AgentContainer.securityKernel.emergencyStop()
                stopService(Intent(this@MainActivity, BackgroundAgentService::class.java))
                stopped = true
                autonomy = false
                say("Agent: Emergency STOP is active.")
            }

            fun executeCommand(command: String) {
                val clean = command.trim()
                if (clean.isBlank()) return
                val proposal = OfflineAiPolicy.sanitize(
                    planner.propose(clean, device.snapshot())
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
                    floatingActionButton = {
                        if (!stopped) {
                            ExtendedFloatingActionButton(
                                onClick = ::emergencyStop,
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) { Text("Emergency STOP") }
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            AgentTab.entries.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = {
                                        Icon(
                                            painter = painterResource(item.icon),
                                            contentDescription = null,
                                            tint = if (tab == index) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                    onDismissRequest = {
                        runtime.reject(action.id)
                        pending = null
                        say("Agent: Action rejected.")
                    },
                    title = { Text("Approve protected action") },
                    text = { Text(actionApprovalDetails(action.request)) },
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
                    onDismissRequest = {
                        runtime.reject(approval.pending.id)
                        pendingTask = null
                        say("Agent: Step rejected.")
                    },
                    title = { Text("Approve sensitive task step") },
                    text = {
                        val task = tasks.get(approval.taskId)
                        val step = task?.steps?.firstOrNull { it.id == approval.stepId }
                        Text(
                            (task?.let { "Task: ${it.title}\n" }.orEmpty()) +
                                (step?.action?.let(::actionApprovalDetails) ?: "Action details are unavailable.") +
                                "\n\nSensitive UI actions always pause for you."
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
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }
}
