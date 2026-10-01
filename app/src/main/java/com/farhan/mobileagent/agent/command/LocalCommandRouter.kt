package com.farhan.mobileagent.agent.command

import com.farhan.mobileagent.agent.device.DeviceState
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.TargetSpec
import com.farhan.mobileagent.agent.task.AgentTask
import com.farhan.mobileagent.agent.task.TaskStep

sealed interface CommandResult {
    data class Answer(val text: String) : CommandResult
    data class Action(val request: ActionRequest) : CommandResult
    data class AddAttention(val title: String, val priority: Int = 60, val dueAt: Long? = null) : CommandResult
    data class BackgroundTask(val task: AgentTask) : CommandResult
    data class Unknown(val text: String) : CommandResult
}

class LocalCommandRouter {
    fun route(input: String, device: DeviceState? = null): CommandResult {
        val q = input.trim().lowercase()
        val reminder = Regex("^remind me to (.+) in (\\d+) (minute|minutes|hour|hours)$").find(q)
        val background = q.removePrefix("background ").trim()
        val appPackage = appPackageFor(q.removePrefix("open app ").removePrefix("open ").trim())

        if (q.startsWith("background ")) {
            val commands = background
                .split(Regex("\\s+(?:and then|then)\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (commands.isEmpty()) {
                return CommandResult.Unknown("I need at least one concrete background action.")
            }

            val results = commands.map { route(it, device) }
            if (results.all { it is CommandResult.Action }) {
                val steps = results.map { result ->
                    taskStepFor((result as CommandResult.Action).request)
                }
                return CommandResult.BackgroundTask(
                    AgentTask(
                        title = background,
                        steps = steps,
                        approvedForBackground = false
                    )
                )
            }
            return CommandResult.Unknown(
                "I can background supported device actions, but every step must be a concrete supported action."
            )
        }

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
            q.startsWith("open app ") && q.removePrefix("open app ").trim().contains(".") ->
                CommandResult.Action(ActionRequest(
                    type = ActionType.OPEN_APP,
                    packageName = q.removePrefix("open app ").trim(),
                    reason = "User requested a specific app package"
                ))
            q.startsWith("open ") && appPackage != null ->
                CommandResult.Action(ActionRequest(
                    type = ActionType.OPEN_APP,
                    packageName = appPackage,
                    reason = "User requested an app"
                ))
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
                CommandResult.Action(ActionRequest(
                    type = ActionType.TAP,
                    target = TargetSpec(text = input.substringAfter("tap ").trim()),
                    reason = "User requested a visible target"
                ))
            q.startsWith("remember ") ->
                CommandResult.AddAttention(input.substringAfter("remember ").trim(), 60)
            q.contains("battery") && device != null ->
                CommandResult.Answer(
                    "Battery " + device.batteryPercent + "%. " +
                        if (device.charging) "The phone is charging." else "The phone is not charging."
                )
            q == "what are you doing" || q == "status" ->
                CommandResult.Answer(
                    "I am local-first. I can manage attention, inspect supported screens, and run authorized low-risk background tasks."
                )
            else ->
                CommandResult.Unknown("I do not have a safe built-in command for that yet.")
        }
    }

    private fun taskStepFor(request: ActionRequest): TaskStep =
        TaskStep(
            action = request,
            verifyPackage = request.packageName.takeIf { request.type == ActionType.OPEN_APP }
        )

    private fun appPackageFor(name: String): String? = when (name) {
        "chrome", "google chrome" -> "com.android.chrome"
        "youtube" -> "com.google.android.youtube"
        "whatsapp" -> "com.whatsapp"
        "telegram" -> "org.telegram.messenger"
        else -> null
    }
}
