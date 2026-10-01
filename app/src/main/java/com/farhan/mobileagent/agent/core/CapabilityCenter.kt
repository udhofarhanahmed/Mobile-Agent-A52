package com.farhan.mobileagent.agent.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.farhan.mobileagent.agent.accessibility.AgentAccessibilityService
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.notification.NotificationListenerState

enum class CapabilityClass {
    OBSERVE,
    SUGGEST,
    ACT
}

enum class CapabilityStatus {
    READY,
    ENABLED_NOT_CONNECTED,
    USER_APPROVAL_REQUIRED,
    NEEDS_ACCESS,
    DISABLED_BY_STOP
}

data class CapabilityDescriptor(
    val capability: String,
    val group: CapabilityClass,
    val status: CapabilityStatus,
    val detail: String
)

object CapabilityCenter {
    fun snapshot(context: Context): List<CapabilityDescriptor> {
        val appContext = context.applicationContext
        val stopped = AgentContainer.securityKernel.isStopped()
        val accessibilityEnabled = isAccessibilityEnabled(appContext)
        val accessibilityConnected = AgentAccessibilityService.instance() != null
        val notificationEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)
        val notificationConnected = NotificationListenerState.connected
        val autonomyEnabled = AgentContainer.securityKernel.isBackgroundAutonomyEnabled()

        val blocked = { detail: String ->
            CapabilityStatus.DISABLED_BY_STOP to detail
        }

        return listOf(
            CapabilityDescriptor(
                capability = "Screen observation",
                group = CapabilityClass.OBSERVE,
                status = when {
                    stopped -> blocked("Emergency STOP is active.") .first
                    accessibilityConnected -> CapabilityStatus.READY
                    accessibilityEnabled -> CapabilityStatus.ENABLED_NOT_CONNECTED
                    else -> CapabilityStatus.NEEDS_ACCESS
                },
                detail = when {
                    stopped -> blocked("Emergency STOP is active.").second
                    accessibilityConnected -> "Accessibility is enabled and connected."
                    accessibilityEnabled -> "Accessibility is enabled; service is not currently connected."
                    else -> "Requires the user to enable the Accessibility Service."
                }
            ),
            CapabilityDescriptor(
                capability = "Notification metadata awareness",
                group = CapabilityClass.OBSERVE,
                status = when {
                    stopped -> blocked("Emergency STOP is active.").first
                    notificationConnected -> CapabilityStatus.READY
                    notificationEnabled -> CapabilityStatus.ENABLED_NOT_CONNECTED
                    else -> CapabilityStatus.NEEDS_ACCESS
                },
                detail = when {
                    stopped -> blocked("Emergency STOP is active.").second
                    notificationConnected -> "Observes notification metadata only; message bodies are not stored."
                    notificationEnabled -> "Notification access is enabled; listener is not currently connected."
                    else -> "Requires the user to enable Notification access."
                }
            ),
            CapabilityDescriptor(
                capability = "Local answers and planning",
                group = CapabilityClass.SUGGEST,
                status = CapabilityStatus.READY,
                detail = "Runs locally; it cannot authorize a device action."
            ),
            CapabilityDescriptor(
                capability = "Attention and reminders",
                group = CapabilityClass.ACT,
                status = if (stopped) CapabilityStatus.DISABLED_BY_STOP else CapabilityStatus.READY,
                detail = if (stopped) {
                    "Emergency STOP blocks agent-managed actions."
                } else {
                    "Local attention items and reminders are available."
                }
            ),
            CapabilityDescriptor(
                capability = "Device navigation and controls",
                group = CapabilityClass.ACT,
                status = if (stopped) {
                    CapabilityStatus.DISABLED_BY_STOP
                } else {
                    CapabilityStatus.USER_APPROVAL_REQUIRED
                },
                detail = if (stopped) {
                    "Emergency STOP blocks device actions."
                } else {
                    "Back, Home, Recents, app/settings opening, and volume controls require explicit approval unless an already-authorized safe background task is running."
                }
            ),
            CapabilityDescriptor(
                capability = "UI interaction",
                group = CapabilityClass.ACT,
                status = when {
                    stopped -> CapabilityStatus.DISABLED_BY_STOP
                    !accessibilityEnabled -> CapabilityStatus.NEEDS_ACCESS
                    else -> CapabilityStatus.USER_APPROVAL_REQUIRED
                },
                detail = when {
                    stopped -> "Emergency STOP blocks UI actions."
                    !accessibilityEnabled -> "Tap, swipe, and text entry require the user to enable Accessibility."
                    else -> "Tap, swipe, and text entry require explicit approval and are not allowed unattended."
                }
            ),
            CapabilityDescriptor(
                capability = "Background autonomy",
                group = CapabilityClass.ACT,
                status = when {
                    stopped -> CapabilityStatus.DISABLED_BY_STOP
                    autonomyEnabled -> CapabilityStatus.READY
                    else -> CapabilityStatus.NEEDS_ACCESS
                },
                detail = when {
                    stopped -> "Emergency STOP blocks background execution."
                    autonomyEnabled -> "Enabled. Only explicitly authorized tasks can run, within time and action budgets."
                    else -> "Disabled by the user."
                }
            )
        )
    }

    fun actionUsesAccessibility(type: ActionType): Boolean = when (type) {
        ActionType.TAP,
        ActionType.SWIPE,
        ActionType.TYPE_TEXT,
        ActionType.BACK,
        ActionType.HOME,
        ActionType.RECENTS,
        ActionType.OPEN_APP,
        ActionType.OPEN_SETTINGS,
        ActionType.VOLUME_UP,
        ActionType.VOLUME_DOWN,
        ActionType.MUTE,
        ActionType.OBSERVE -> true
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expected = ComponentName(context, AgentAccessibilityService::class.java)
        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == expected.packageName && it.className == expected.className }
    }
}
