package com.farhan.mobileagent.agent.command

import com.farhan.mobileagent.agent.device.DeviceState
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.TargetSpec

sealed interface CommandResult {
    data class Answer(val text: String) : CommandResult
    data class Action(val request: ActionRequest) : CommandResult
    data class AddAttention(val title: String, val priority: Int = 60, val dueAt: Long? = null) : CommandResult
    data class Unknown(val text: String) : CommandResult
}

class LocalCommandRouter {
    fun route(input: String, device: DeviceState? = null): CommandResult {
        val q = input.trim().lowercase()
        val reminder = Regex("^remind me to (.+) in (\\d+) (minute|minutes|hour|hours)$").find(q)

        return when {
            reminder != null -> {
                val amount = reminder.groupValues[2].toLong()
                val unit = reminder.groupValues[3]
                val delayMs = if (unit.startsWith("hour")) amount * 60L * 60L * 1000L
                else amount * 60L * 1000L
                CommandResult.AddAttention(
                    title = reminder.groupValues[1].trim(),
                    priority = 90,
                    dueAt = System.currentTimeMillis() + delayMs
                )
            }
            q == "go home" || q == "home" ->
                CommandResult.Action(ActionRequest(ActionType.HOME, reason = "User requested Home"))
            q == "go back" || q == "back" ->
                CommandResult.Action(ActionRequest(ActionType.BACK, reason = "User requested Back"))
            q == "recent apps" || q == "recents" ->
                CommandResult.Action(ActionRequest(ActionType.RECENTS, reason = "User requested Recents"))
            q == "open settings" || q == "settings" ->
                CommandResult.Action(ActionRequest(ActionType.OPEN_SETTINGS, reason = "User requested Android Settings"))
            q == "volume up" || q == "increase volume" ->
                CommandResult.Action(ActionRequest(ActionType.VOLUME_UP, reason = "User requested volume increase"))
            q == "volume down" || q == "decrease volume" ->
                CommandResult.Action(ActionRequest(ActionType.VOLUME_DOWN, reason = "User requested volume decrease"))
            q == "mute" || q == "mute phone" ->
                CommandResult.Action(ActionRequest(ActionType.MUTE, reason = "User requested mute"))
            q.startsWith("tap ") ->
                CommandResult.Action(
                    ActionRequest(
                        type = ActionType.TAP,
                        target = TargetSpec(text = input.substringAfter("tap ").trim()),
                        reason = "User requested a visible target"
                    )
                )
            q.startsWith("remember ") ->
                CommandResult.AddAttention(input.substringAfter("remember ").trim(), 60)
            q.contains("battery") && device != null ->
                CommandResult.Answer(
                    "Battery " + device.batteryPercent + "%. " +
                        if (device.charging) "The phone is charging." else "The phone is not charging."
                )
            q == "what are you doing" || q == "status" ->
                CommandResult.Answer(
                    "I am local-first. I can manage attention, inspect supported screens, and prepare policy-gated device actions."
                )
            else ->
                CommandResult.Unknown("I do not have a safe built-in command for that yet.")
        }
    }
}
