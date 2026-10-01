package com.farhan.mobileagent.agent.assistant

import com.farhan.mobileagent.agent.attention.AttentionManager

data class AssistantAnswer(val text: String, val actions: List<String> = emptyList())

interface LocalModel {
    fun answer(input: String, context: String = ""): AssistantAnswer
}

class RuleBasedLocalModel(
    private val attention: AttentionManager
) : LocalModel {
    override fun answer(input: String, context: String): AssistantAnswer {
        val q = input.trim().lowercase()
        return when {
            q.isBlank() -> AssistantAnswer("Tell me what you need.")
            q.contains("attention") || q.contains("tasks") ->
                AssistantAnswer(
                    attention.pending().take(5).joinToString(
                        prefix = if (attention.pending().isEmpty()) "Nothing urgent is waiting." else "Here is what is waiting:\n",
                        separator = "\n"
                    ) { "• " + it.title }
                )
            q.contains("status") ->
                AssistantAnswer("I am running locally. Core functions do not require the internet.")
            q.contains("help") ->
                AssistantAnswer("I can manage attention items, inspect the current screen, prepare device actions, answer locally, and execute only policy-approved actions.")
            else ->
                AssistantAnswer("I understood: \"" + input + "\". I can answer locally and route device actions through the security policy.")
        }
    }
}
