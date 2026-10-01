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
    DISABLED_BY_USER,
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

        return listOf(
            CapabilityDescriptor(
                capability = "Screen observation",
                group = CapabilityClass.OBSERVE,
                status = when {
                    stopped -> CapabilityStatus.DISABLED_BY_STOP
                    accessibilityConnected -> CapabilityStatus.READY
                    accessibilityEnabled -> CapabilityStatus.ENABLED_NOT_CONNECTED
                    else -> CapabilityStatus.NEEDS_ACCESS
                },
                detail = when {
                    stopped -> "Emergency STOP is active."
                    accessibilityConnected -> "Accessibility is enabled and connected."
                    accessibilityEnabled -> "Accessibility is enabled; the service is not currently connected."
                    else -> "Requires user-enabled Accessibility access."
                }
            ),
            CapabilityDescriptor(
                capability = "Notification metadata",
                group = CapabilityClass.OBSERVE,
                status = when {
                    stopped -> CapabilityStatus.DISABLED_BY_STOP
                    notificationConnected -> CapabilityStatus.READY
                    notificationEnabled -> CapabilityStatus.ENABLED_NOT_CONNECTED
                    else -> CapabilityStatus.NEEDS_ACCESS
                },
                detail = when {
                    stopped -> "Emergency STOP is active."
                    notificationConnected -> "Metadata only; notification message bodies are not stored."
                    notificationEnabled -> "Access is enabled; listener is reconnecting."
                    else -> "Requires user-enabled Notification access."
                }
            ),
            CapabilityDescriptor(
                capability = "Local answers & planning",
                group = CapabilityClass.SUGGEST,
                status = CapabilityStatus.READY,
                detail = "Local reasoning can propose; the security kernel decides."
            ),
            CapabilityDescriptor(
                capability = "Attention & reminders",
                group = CapabilityClass.ACT,
                status = if (stopped) CapabilityStatus.DISABLED_BY_STOP else CapabilityStatus.READY,
                detail = if (stopped) {
                    "Agent-managed actions are blocked."
                } else {
                    "Local tasks, reminders, and attention items are available."
                }
            ),
            CapabilityDescriptor(
                capability = "Device controls",
                group = CapabilityClass.ACT,
                status = if (stopped) CapabilityStatus.DISABLED_BY_STOP else CapabilityStatus.USER_APPROVAL_REQUIRED,
                detail = if (stopped) {
                    "Emergency STOP blocks device actions."
                } else {
                    "Navigation, supported app/settings launch, and volume controls are policy-gated."
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
                    !accessibilityEnabled -> "Tap, swipe, and text entry require Accessibility access."
                    else -> "Tap, swipe, and text entry require explicit approval and stay out of unattended execution."
                }
            ),
            CapabilityDescriptor(
                capability = "Background autonomy",
                group = CapabilityClass.ACT,
                status = when {
                    stopped -> CapabilityStatus.DISABLED_BY_STOP
                    autonomyEnabled -> CapabilityStatus.READY
                    else -> CapabilityStatus.DISABLED_BY_USER
                },
                detail = when {
                    stopped -> "Emergency STOP has priority over autonomy."
                    autonomyEnabled -> "Enabled. Only explicitly authorized tasks run within time and action budgets."
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
