package com.farhan.mobileagent.agent.command

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.TargetSpec

sealed interface CommandResult {
    data class Answer(val text: String) : CommandResult
    data class Action(val request: ActionRequest) : CommandResult
    data class Unknown(val text: String) : CommandResult
}

class LocalCommandRouter {
    fun route(input: String): CommandResult {
        val q = input.trim().lowercase()
        return when {
            q == "go home" || q == "home" ->
                CommandResult.Action(ActionRequest(ActionType.HOME, reason = "User requested Home"))
            q == "go back" || q == "back" ->
                CommandResult.Action(ActionRequest(ActionType.BACK, reason = "User requested Back"))
            q == "recent apps" || q == "recents" ->
                CommandResult.Action(ActionRequest(ActionType.RECENTS, reason = "User requested Recents"))
            q.startsWith("tap ") ->
                CommandResult.Action(
                    ActionRequest(
                        type = ActionType.TAP,
                        target = TargetSpec(text = input.substringAfter("tap ").trim()),
                        reason = "User requested a visible target"
                    )
                )
            q == "what are you doing" || q == "status" ->
                CommandResult.Answer("I am local-first. I can observe supported screens, manage attention, and prepare policy-gated device actions.")
            else ->
                CommandResult.Unknown("No safe built-in command matched yet.")
        }
    }
}
