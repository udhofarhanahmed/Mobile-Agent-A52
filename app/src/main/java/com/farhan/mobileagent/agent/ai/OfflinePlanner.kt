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
    val attention: CommandResult.AddAttention? = null,
    val note: CommandResult.AddNote? = null,
    val openMusic: Boolean = false,
    val openFileLab: Boolean = false,
    val callTarget: String? = null,
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
            is CommandResult.Answer -> PlannerProposal(answer = result.text, confidence = 1f)
            is CommandResult.Action -> PlannerProposal(action = result.request, confidence = 1f)
            is CommandResult.AddAttention -> PlannerProposal(attention = result, confidence = 1f)
            is CommandResult.BackgroundTask -> PlannerProposal(
                answer = "Background task planned: " + result.task.title,
                task = result.task,
                confidence = 1f
            )
            is CommandResult.AddNote -> PlannerProposal(note = result, confidence = 1f)
            CommandResult.OpenMusic -> PlannerProposal(openMusic = true, confidence = 1f)
            is CommandResult.OpenCall -> PlannerProposal(callTarget = result.target, confidence = 1f)
            CommandResult.OpenFileLab -> PlannerProposal(openFileLab = true, confidence = 1f)
            is CommandResult.Unknown -> PlannerProposal(answer = result.text, confidence = 0f)
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
        return proposal.copy(action = safeAction, task = safeTask)
    }

    private fun sanitizeAction(action: ActionRequest): ActionRequest =
        if (action.type == ActionType.OBSERVE) {
            action
        } else {
            action.copy(requiresConfirmation = true, confirmed = false)
        }
}