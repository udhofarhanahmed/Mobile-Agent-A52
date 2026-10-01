package com.farhan.mobileagent.agent.task

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.farhan.mobileagent.agent.core.AgentContainer

class AgentBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val appContext = context.applicationContext
        AgentContainer.initialize(appContext)

        val shouldResume = !AgentContainer.securityKernel.isStopped() &&
            TaskStore(appContext).active().any { it.approvedForBackground }

        if (shouldResume) {
            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, BackgroundAgentService::class.java)
                )
            }
        }
    }
}
