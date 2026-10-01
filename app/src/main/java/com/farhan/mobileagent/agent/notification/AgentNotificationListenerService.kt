package com.farhan.mobileagent.agent.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.farhan.mobileagent.agent.core.AgentContainer

class AgentNotificationListenerService : NotificationListenerService() {
    private lateinit var inbox: NotificationInbox

    override fun onCreate() {
        super.onCreate()
        AgentContainer.initialize(applicationContext)
        inbox = NotificationInbox(applicationContext)
    }

    override fun onListenerConnected() {
        NotificationListenerState.connected = true
    }

    override fun onListenerDisconnected() {
        NotificationListenerState.connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val sourcePackage = sbn.packageName
        if (!NotificationPolicy.shouldObserve(sourcePackage, packageName, sbn.isOngoing)) return

        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sourcePackage, 0)
            ).toString()
        }.getOrDefault(sourcePackage)

        val notification = sbn.notification
        inbox.record(
            NotificationObservation(
                key = sbn.key,
                packageName = sourcePackage,
                appLabel = label,
                channelId = notification.channelId,
                category = notification.category
            )
        )
    }
}

object NotificationListenerState {
    @Volatile var connected: Boolean = false
}
