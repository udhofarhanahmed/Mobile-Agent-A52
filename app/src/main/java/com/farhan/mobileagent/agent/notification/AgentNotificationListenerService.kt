package com.farhan.mobileagent.agent.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.farhan.mobileagent.agent.attention.AttentionItem
import com.farhan.mobileagent.agent.attention.AttentionKind
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
        if (!NotificationPolicy.shouldObserve(sbn.packageName, packageName, sbn.isOngoing)) return

        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)

        val notification = sbn.notification
        val observation = NotificationObservation(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label,
            channelId = notification.channelId,
            category = notification.category
        )
        inbox.record(observation)

        AgentContainer.attentionManager.upsert(
            AttentionItem(
                id = NotificationAttentionPolicy.attentionId(sbn.key),
                title = NotificationAttentionPolicy.title(label),
                detail = observation.category.orEmpty().ifBlank { "External notification" },
                kind = AttentionKind.ALERT,
                priority = NotificationAttentionPolicy.PRIORITY,
                createdAt = observation.postedAt
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        AgentContainer.attentionManager.complete(
            NotificationAttentionPolicy.attentionId(sbn.key)
        )
    }
}

object NotificationListenerState {
    @Volatile var connected: Boolean = false
}
