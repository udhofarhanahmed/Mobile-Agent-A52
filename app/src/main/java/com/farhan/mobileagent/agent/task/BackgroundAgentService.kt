package com.farhan.mobileagent.agent.task

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.farhan.mobileagent.agent.core.AgentContainer
import com.farhan.mobileagent.agent.core.AgentRuntime

class BackgroundAgentService : Service() {
    private val channelId = "agent_background"
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        AgentContainer.initialize(applicationContext)
        createChannel()

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Mobile Agent")
            .setContentText("Working locally in the background")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(1001, notification)

        running = true
        worker = Thread {
            val runner = TaskRunner(
                TaskStore(applicationContext),
                AgentRuntime(
                    AgentContainer.securityKernel,
                    AgentContainer.confirmationManager,
                    AgentContainer.auditLog
                ),
                AgentContainer.securityKernel
            )

            while (running && !Thread.currentThread().isInterrupted) {
                val result = runCatching { runner.tick() }.getOrDefault("FAILED")
                if (result == "AUTONOMY_DISABLED" || result == "STOPPED" || result == "IDLE") {
                    stopSelf()
                    break
                }
                try {
                    Thread.sleep(3000L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                channelId,
                "Mobile Agent background work",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
