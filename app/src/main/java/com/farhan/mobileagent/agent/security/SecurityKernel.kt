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

        // The kernel is the authority. Callers cannot disable confirmation
        // simply by setting requiresConfirmation=false. Only observation is
        // intrinsically non-mutating; every device action must arrive confirmed.
        if (request.type == ActionType.OBSERVE) return Decision.ALLOW
        return if (request.confirmed) Decision.ALLOW else Decision.REQUIRE_CONFIRMATION
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
