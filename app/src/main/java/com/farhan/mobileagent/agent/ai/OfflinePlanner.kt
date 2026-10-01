package com.farhan.mobileagent.agent.ai

import com.farhan.mobileagent.agent.command.CommandResult
import com.farhan.mobileagent.agent.command.LocalCommandRouter
import com.farhan.mobileagent.agent.device.DeviceState
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType

data class PlannerProposal(
    val answer: String? = null,
    val action: ActionRequest? = null,
    val confidence: Float = 0f
)

interface OfflinePlanner {
    fun propose(input: String, device: DeviceState? = null): PlannerProposal
}

class RuleBasedOfflinePlanner(
    private val router: LocalCommandRouter = LocalCommandRouter()
) : OfflinePlanner {
    override fun propose(input: String, device: DeviceState?): PlannerProposal {
        return when (val result = router.route(input, device)) {
            is CommandResult.Answer ->
                PlannerProposal(answer = result.text, confidence = 1f)
            is CommandResult.Action ->
                PlannerProposal(action = result.request, confidence = 1f)
            is CommandResult.AddAttention ->
                PlannerProposal(
                    answer = "Attention item can be created locally.",
                    confidence = 1f
                )
            is CommandResult.Unknown ->
                PlannerProposal(answer = result.text, confidence = 0f)
        }
    }
}

object OfflineAiPolicy {
    fun sanitize(proposal: PlannerProposal): PlannerProposal {
        val action = proposal.action ?: return proposal
        return if (action.type == ActionType.OBSERVE) {
            proposal
        } else {
            proposal.copy(
                action = action.copy(
                    requiresConfirmation = true,
                    confirmed = false
                )
            )
        }
    }
}
