package com.farhan.mobileagent.agent.security

import android.content.Context
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.Decision
import com.farhan.mobileagent.agent.storage.LocalMemory

class SecurityKernel {
    @Volatile private var stopped = false
    @Volatile private var backgroundAutonomyEnabled = true
    @Volatile private var memory: LocalMemory? = null

    fun initialize(context: Context) {
        synchronized(this) {
            if (memory != null) return
            val m = LocalMemory(context.applicationContext)
            memory = m
            stopped = m.get(KEY_EMERGENCY_STOP) == "true"
            backgroundAutonomyEnabled = m.get(KEY_BACKGROUND_AUTONOMY) != "false"
        }
    }

    fun evaluate(request: ActionRequest): Decision {
        if (stopped) return Decision.DENY

        // Request fields such as "confirmed" are untrusted input. They never
        // grant execution authority by themselves.
        if (request.type == ActionType.OBSERVE) return Decision.ALLOW
        return Decision.REQUIRE_CONFIRMATION
    }

    internal fun evaluateApproved(request: ActionRequest): Decision {
        if (stopped) return Decision.DENY
        return Decision.ALLOW
    }

    internal fun evaluateBackground(request: ActionRequest): Decision {
        if (stopped) return Decision.DENY
        if (!backgroundAutonomyEnabled) return Decision.DENY
        return if (isBackgroundSafe(request.type)) {
            Decision.ALLOW
        } else {
            Decision.REQUIRE_CONFIRMATION
        }
    }

    private fun isBackgroundSafe(type: ActionType): Boolean = when (type) {
        ActionType.OBSERVE,
        ActionType.OPEN_APP,
        ActionType.OPEN_SETTINGS,
        ActionType.BACK,
        ActionType.HOME,
        ActionType.RECENTS,
        ActionType.VOLUME_UP,
        ActionType.VOLUME_DOWN,
        ActionType.MUTE -> true
        ActionType.TAP,
        ActionType.SWIPE,
        ActionType.TYPE_TEXT -> false
    }

    fun emergencyStop() {
        stopped = true
        memory?.put(KEY_EMERGENCY_STOP, "true")
    }

    fun resetAfterExplicitUserAction() {
        stopped = false
        memory?.put(KEY_EMERGENCY_STOP, "false")
    }

    fun isStopped(): Boolean = stopped

    fun isBackgroundAutonomyEnabled(): Boolean = backgroundAutonomyEnabled

    fun setBackgroundAutonomyEnabled(enabled: Boolean) {
        backgroundAutonomyEnabled = enabled
        memory?.put(KEY_BACKGROUND_AUTONOMY, enabled.toString())
    }

    private companion object {
        const val KEY_EMERGENCY_STOP = "security_emergency_stop"
        const val KEY_BACKGROUND_AUTONOMY = "security_background_autonomy"
    }
}
