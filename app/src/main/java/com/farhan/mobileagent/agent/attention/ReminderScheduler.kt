package com.farhan.mobileagent.agent.attention

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderScheduler(private val context: Context) {
    fun schedule(item: AttentionItem) {
        val dueAt = item.dueAt ?: return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AttentionReminderReceiver::class.java)
            .putExtra("title", item.title)
            .putExtra("detail", item.detail)
            .putExtra("item_id", item.id)
        val pending = PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= 31 && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
        }
    }

    fun cancel(item: AttentionItem) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            Intent(context, AttentionReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pending?.let {
            alarm.cancel(it)
            it.cancel()
        }
    }
}

class AttentionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channelId = "attention_reminders"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Attention & alarms",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.farhan.mobileagent.R.drawable.agent_logo)
            .setContentTitle(intent.getStringExtra("title") ?: "Reminder")
            .setContentText(intent.getStringExtra("detail") ?: "Your agent reminder is due.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
            .also {
                manager.notify(System.currentTimeMillis().toInt(), it)
            }
    }
}