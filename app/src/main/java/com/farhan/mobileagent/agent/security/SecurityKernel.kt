package com.farhan.mobileagent.agent.security

import android.content.Context
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.Decision
import com.farhan.mobileagent.agent.storage.LocalMemory

class SecurityKernel {
    @Volatile private var stopped = false
    @Volatile private var memory: LocalMemory? = null

    fun initialize(context: Context) {
        synchronized(this) {
            if (memory != null) return
            val m = LocalMemory(context.applicationContext)
            memory = m
            stopped = m.get(KEY_EMERGENCY_STOP) == "true"
        }
    }

    fun evaluate(request: ActionRequest): Decision {
        if (stopped) return Decision.DENY
        return when (request.type) {
            ActionType.OBSERVE -> Decision.ALLOW
            else -> if (request.requiresConfirmation && !request.confirmed) {
                Decision.REQUIRE_CONFIRMATION
            } else {
                Decision.ALLOW
            }
        }
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

    private companion object {
        const val KEY_EMERGENCY_STOP = "security_emergency_stop"
    }
}
