package com.farhan.mobileagent.agent.ai

import com.farhan.mobileagent.agent.command.CommandResult
import com.farhan.mobileagent.agent.command.LocalCommandRouter
import com.farhan.mobileagent.agent.device.DeviceState
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.task.AgentTask

data class PlannerProposal(
    val answer: String? = null,
    val action: ActionRequest? = null,
    val task: AgentTask? = null,
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
            is CommandResult.BackgroundTask ->
                PlannerProposal(
                    answer = "Background task planned: " + result.task.title,
                    task = result.task,
                    confidence = 1f
                )
            is CommandResult.AddNote ->
                PlannerProposal(answer = "Local note can be saved.", confidence = 1f)
            CommandResult.OpenMusic ->
                PlannerProposal(answer = "The local music player can be opened.", confidence = 1f)
            is CommandResult.OpenCall ->
                PlannerProposal(answer = "The protected call flow can prepare " + result.target + ".", confidence = 1f)
            CommandResult.OpenFileLab ->
                PlannerProposal(answer = "The local File Lab can analyze supported files on the device.", confidence = 1f)
            is CommandResult.Unknown ->
                PlannerProposal(answer = result.text, confidence = 0f)
        }
    }
}

object OfflineAiPolicy {
    fun sanitize(proposal: PlannerProposal): PlannerProposal {
        val safeAction = proposal.action?.let(::sanitizeAction)
        val safeTask = proposal.task?.copy(
            approvedForBackground = false,
            steps = proposal.task.steps.map { step ->
                step.copy(action = sanitizeAction(step.action))
            }
        )
        return proposal.copy(
            action = safeAction,
            task = safeTask
        )
    }

    private fun sanitizeAction(action: ActionRequest): ActionRequest =
        if (action.type == ActionType.OBSERVE) {
            action
        } else {
            action.copy(
                requiresConfirmation = true,
                confirmed = false
            )
        }
}
