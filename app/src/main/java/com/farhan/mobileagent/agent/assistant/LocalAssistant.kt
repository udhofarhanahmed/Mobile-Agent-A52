package com.farhan.mobileagent.agent.assistant

import com.farhan.mobileagent.agent.attention.AttentionManager
import com.farhan.mobileagent.agent.device.DeviceStateProvider

data class AssistantAnswer(val text: String, val actions: List<String> = emptyList())

interface LocalModel {
    fun answer(input: String, context: String = ""): AssistantAnswer
}

class RuleBasedLocalModel(
    private val attention: AttentionManager,
    private val device: DeviceStateProvider? = null
) : LocalModel {
    override fun answer(input: String, context: String): AssistantAnswer {
        val q = input.trim().lowercase()
        return when {
            q.isBlank() -> AssistantAnswer("Tell me what you need.")
            q.contains("attention") || q.contains("tasks") ->
                AssistantAnswer(
                    attention.pending().take(5).joinToString(
                        prefix = if (attention.pending().isEmpty()) "Nothing is waiting." else "Here is what is waiting:\n",
                        separator = "\n"
                    ) { "• " + it.title }
                )
            q.contains("battery") && device != null -> {
                val s = device.snapshot()
                AssistantAnswer(
                    "Battery " + s.batteryPercent + "%. " +
                        if (s.charging) "The phone is charging." else "The phone is not charging."
                )
            }
            q.contains("status") ->
                AssistantAnswer("I am running locally. Core agent functions do not require the internet.")
            q.contains("help") ->
                AssistantAnswer("I can answer locally, manage attention, inspect supported screens, and prepare device actions that pass the security gate.")
            else ->
                AssistantAnswer("I understood: \"" + input + "\". I do not have a safe offline answer for that yet.")
        }
    }
}
