package com.farhan.mobileagent.agent.security

enum class ActionType { OBSERVE, TAP, SWIPE, BACK, HOME, RECENTS, TYPE_TEXT }
enum class Decision { ALLOW, REQUIRE_CONFIRMATION, DENY }

data class ActionRequest(val type: ActionType, val target: String? = null, val reason: String = "")

class SecurityKernel {
    @Volatile private var stopped = false
    fun evaluate(request: ActionRequest): Decision {
        if (stopped) return Decision.DENY
        return when (request.type) {
            ActionType.OBSERVE -> Decision.ALLOW
            else -> Decision.REQUIRE_CONFIRMATION
        }
    }
    fun emergencyStop() { stopped = true }
    fun resetAfterExplicitUserAction() { stopped = false }
    fun isStopped(): Boolean = stopped
}
