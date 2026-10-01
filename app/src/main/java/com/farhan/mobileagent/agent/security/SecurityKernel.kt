package com.farhan.mobileagent.agent.security

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.Decision

class SecurityKernel {
    @Volatile private var stopped = false

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

    fun emergencyStop() { stopped = true }
    fun resetAfterExplicitUserAction() { stopped = false }
    fun isStopped(): Boolean = stopped
}
