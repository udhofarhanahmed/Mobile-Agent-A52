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
    private var running = true

    override fun onCreate() {
        super.onCreate()
        AgentContainer.initialize(applicationContext)
        createChannel()
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Mobile Agent")
            .setContentText("Working locally in the background")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
        startForeground(1001, notification)
        Thread {
            val runner = TaskRunner(
                TaskStore(applicationContext),
                AgentRuntime(
                    AgentContainer.securityKernel,
                    AgentContainer.confirmationManager,
                    AgentContainer.auditLog
                ),
                AgentContainer.securityKernel
            )
            while (running) {
                runner.tick()
                Thread.sleep(3000L)
            }
        }.start()
    }

    override fun onDestroy() {
        running = false
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
