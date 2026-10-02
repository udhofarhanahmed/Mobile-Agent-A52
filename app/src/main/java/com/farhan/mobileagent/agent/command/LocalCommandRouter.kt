package com.farhan.mobileagent.agent.command

import com.farhan.mobileagent.agent.attention.AttentionKind
import com.farhan.mobileagent.agent.device.DeviceState
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.TargetSpec
import com.farhan.mobileagent.agent.task.AgentTask
import com.farhan.mobileagent.agent.task.TaskStep
import com.farhan.mobileagent.agent.tools.LocalCalculator
import java.util.Calendar

sealed interface CommandResult {
    data class Answer(val text: String) : CommandResult
    data class Action(val request: ActionRequest) : CommandResult
    data class AddAttention(
        val title: String,
        val priority: Int = 60,
        val dueAt: Long? = null,
        val kind: AttentionKind = AttentionKind.TASK
    ) : CommandResult
    data class AddNote(val content: String) : CommandResult
    data object OpenMusic : CommandResult
    data object OpenFileLab : CommandResult
    data class OpenCall(val target: String) : CommandResult
    data class BackgroundTask(val task: AgentTask) : CommandResult
    data class Unknown(val text: String) : CommandResult
}

class LocalCommandRouter {
    fun route(input: String, device: DeviceState? = null): CommandResult {
        val raw = input.trim()
        val q = raw.lowercase()
        val reminder = Regex("^remind me to (.+) in (\\d+) (minute|minutes|hour|hours)$").find(q)
        val alarmRelative = Regex("^(?:set\\s+)?(?:an\\s+)?alarm\\s+in\\s+(\\d+)\\s+(minute|minutes|hour|hours)$").find(q)
        val alarmAbsolute = Regex("^(?:set\\s+)?(?:an\\s+)?alarm(?:\\s+for|\\s+at)?\\s+(\\d{1,2}:\\d{2}(?:\\s*(?:am|pm))?)$").find(q)
        val wakeAbsolute = Regex("^wake me (?:at|for)\\s+(\\d{1,2}:\\d{2}(?:\\s*(?:am|pm))?)$").find(q)
        val remindAbsolute = Regex("^remind me at\\s+(\\d{1,2}:\\d{2}(?:\\s*(?:am|pm))?)\\s+to\\s+(.+)$").find(q)
        val percentOf = Regex("^(?:calculate\\s+|calc\\s+|what is\\s+)?([0-9.]+)%\\s+of\\s+([0-9.]+)$").find(q)
        val background = q.removePrefix("background ").trim()
        val appPackage = appPackageFor(q.removePrefix("open app ").removePrefix("open ").trim())

        if (q.startsWith("background ")) {
            val commands = background
                .split(Regex("\\s+(?:and then|then)\\s+"))
                .map(String::trim)
                .filter(String::isNotEmpty)
            if (commands.isEmpty()) return CommandResult.Unknown("I need at least one concrete background action.")
            val results = commands.map { route(it, device) }
            if (results.all { it is CommandResult.Action }) {
                return CommandResult.BackgroundTask(
                    AgentTask(
                        title = background,
                        steps = results.map {
                            val request = (it as CommandResult.Action).request
                            TaskStep(
                                action = request,
                                verifyPackage = request.packageName.takeIf { request.type == ActionType.OPEN_APP }
                            )
                        },
                        approvedForBackground = false
                    )
                )
            }
            return CommandResult.Unknown(
                "I can background supported device actions, but every step must be a concrete supported action."
            )
        }

        fun attention(title: String, dueAt: Long?, kind: AttentionKind, priority: Int = 90) =
            CommandResult.AddAttention(title, priority, dueAt, kind)

        return when {
            reminder != null -> {
                val amount = reminder.groupValues[2].toLongOrNull()
                    ?: return CommandResult.Unknown("I could not read that reminder time.")
                val unit = reminder.groupValues[3]
                val delay = if (unit.startsWith("hour")) amount * 3600000L else amount * 60000L
                attention(reminder.groupValues[1].trim(), System.currentTimeMillis() + delay, AttentionKind.REMINDER)
            }
            alarmRelative != null -> {
                val amount = alarmRelative.groupValues[1].toLongOrNull()
                    ?: return CommandResult.Unknown("I could not read that alarm time.")
                val unit = alarmRelative.groupValues[2]
                val delay = if (unit.startsWith("hour")) amount * 3600000L else amount * 60000L
                attention("Alarm", System.currentTimeMillis() + delay, AttentionKind.REMINDER, 100)
            }
            alarmAbsolute != null || wakeAbsolute != null -> {
                val clock = (alarmAbsolute ?: wakeAbsolute)!!.groupValues[1]
                val at = nextClockTime(clock) ?: return CommandResult.Unknown("Use a time like 7:30 or 7:30 pm.")
                attention("Alarm", at, AttentionKind.REMINDER, 100)
            }
            remindAbsolute != null -> {
                val at = nextClockTime(remindAbsolute.groupValues[1]) ?: return CommandResult.Unknown("Use a time like 7:30 or 7:30 pm.")
                attention(remindAbsolute.groupValues[2].trim(), at, AttentionKind.REMINDER)
            }
            percentOf != null -> {
                val result = runCatching {
                    LocalCalculator.percentOf(percentOf.groupValues[1], percentOf.groupValues[2])
                }.getOrElse { return CommandResult.Unknown("I could not calculate that expression.") }
                CommandResult.Answer(
                    percentOf.groupValues[1] + "% of " + percentOf.groupValues[2] + " = " + result.formatted
                )
            }
            q.startsWith("calculate ") || q.startsWith("calc ") -> {
                val expression = raw.substringAfter(" ").trim()
                val result = runCatching { LocalCalculator.calculate(expression) }
                    .getOrElse { return CommandResult.Unknown("I could not safely calculate that expression.") }
                CommandResult.Answer(expression + " = " + result.formatted)
            }
            q.startsWith("open app ") && q.removePrefix("open app ").trim().contains(".") ->
                CommandResult.Action(
                    ActionRequest(
                        type = ActionType.OPEN_APP,
                        packageName = q.removePrefix("open app ").trim(),
                        reason = "User requested a specific app package"
                    )
                )
            q.startsWith("open ") && appPackage != null ->
                CommandResult.Action(
                    ActionRequest(
                        type = ActionType.OPEN_APP,
                        packageName = appPackage,
                        reason = "User requested an app"
                    )
                )
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
            q == "play music" || q == "music" || q == "open music" || q == "play a song" ->
                CommandResult.OpenMusic
            q == "file lab" || q == "open file lab" || q == "analyze file" || q == "open files" ->
                CommandResult.OpenFileLab
            q.startsWith("call ") || q.startsWith("dial ") ->
                CommandResult.OpenCall(raw.substringAfter(" ").trim())
            q == "mute" || q == "mute phone" ->
                CommandResult.Action(ActionRequest(ActionType.MUTE, reason = "User requested mute"))
            q.startsWith("tap ") ->
                CommandResult.Action(
                    ActionRequest(
                        type = ActionType.TAP,
                        target = TargetSpec(text = raw.substringAfter("tap ").trim()),
                        reason = "User requested a visible target"
                    )
                )
            q.startsWith("note ") ->
                CommandResult.AddNote(raw.substringAfter("note ").trim())
            q.startsWith("add note ") ->
                CommandResult.AddNote(raw.substringAfter("add note ").trim())
            q.startsWith("write note ") ->
                CommandResult.AddNote(raw.substringAfter("write note ").trim())
            q.startsWith("make a note ") ->
                CommandResult.AddNote(raw.substringAfter("make a note ").trim())
            q.startsWith("remember ") ->
                CommandResult.AddAttention(raw.substringAfter("remember ").trim(), 60)
            q.contains("battery") && device != null ->
                CommandResult.Answer(
                    "Battery " + device.batteryPercent + "%. " +
                        if (device.charging) "The phone is charging." else "The phone is not charging."
                )
            q == "what are you doing" || q == "status" ->
                CommandResult.Answer(
                    "I am local-first. I plan locally, schedule local attention, prepare protected device actions, and run only explicitly authorized low-risk background work."
                )
            else -> CommandResult.Unknown("I do not have a safe built-in command for that yet.")
        }
    }

    private fun nextClockTime(value: String): Long? {
        val match = Regex("^(\\d{1,2}):(\\d{2})\\s*(am|pm)?$").find(value.trim()) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val suffix = match.groupValues[3].ifBlank { null }
        if (minute !in 0..59) return null
        if (suffix == null) {
            if (hour !in 0..23) return null
        } else {
            if (hour !in 1..12) return null
            if (suffix == "pm" && hour != 12) hour += 12
            if (suffix == "am" && hour == 12) hour = 0
        }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    private fun appPackageFor(name: String): String? = when (name) {
        "chrome", "google chrome" -> "com.android.chrome"
        "youtube" -> "com.google.android.youtube"
        "whatsapp" -> "com.whatsapp"
        "telegram" -> "org.telegram.messenger"
        else -> null
    }
}