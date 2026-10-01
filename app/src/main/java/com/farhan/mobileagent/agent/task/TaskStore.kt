package com.farhan.mobileagent.agent.task

import android.content.Context
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.TargetSpec
import com.farhan.mobileagent.agent.storage.LocalMemory
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TaskStore(context: Context) {
    private val memory = LocalMemory(context.applicationContext)
    private val key = "agent_tasks"

    @Synchronized
    fun upsert(task: AgentTask) {
        require(task.title.length <= MAX_TITLE) { "Task title is too long." }
        require(task.steps.size <= MAX_STEPS) { "Task has too many steps." }
        require(task.maxActions in 1..MAX_ACTIONS) { "Invalid action budget." }

        val all = all().associateBy { it.id }.toMutableMap()
        all[task.id] = task
        val bounded = all.values
            .sortedBy { it.createdAt }
            .takeLast(MAX_TASKS)
        memory.put(key, JSONArray().apply {
            bounded.forEach { put(encode(it)) }
        }.toString())
    }

    @Synchronized
    fun get(id: String): AgentTask? = all().firstOrNull { it.id == id }

    fun all(): List<AgentTask> = runCatching {
        val raw = memory.get(key) ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) add(decode(array.getJSONObject(i)))
        }.sortedBy { it.createdAt }
    }.getOrElse { emptyList() }

    fun active(): List<AgentTask> =
        all().filter { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING || it.state == TaskState.PAUSED }

    @Synchronized
    fun cancel(id: String) {
        get(id)?.let { upsert(it.copy(state = TaskState.CANCELLED)) }
    }

    @Synchronized
    fun authorizeBackground(id: String): AgentTask? =
        get(id)?.takeIf {
            it.state == TaskState.QUEUED ||
                it.state == TaskState.WAITING_FOR_USER ||
                it.state == TaskState.PAUSED
        }?.copy(
            approvedForBackground = true,
            state = if (it.state == TaskState.WAITING_FOR_USER) TaskState.WAITING_FOR_USER else TaskState.RUNNING
        )?.also(::upsert)

    private fun encode(t: AgentTask) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("createdAt", t.createdAt)
        put("deadlineAt", t.deadlineAt ?: JSONObject.NULL); put("state", t.state.name)
        put("currentStep", t.currentStep); put("attempts", t.attempts)
        put("actionsExecuted", t.actionsExecuted); put("maxActions", t.maxActions)
        put("approvedForBackground", t.approvedForBackground)
        put("lastError", t.lastError ?: JSONObject.NULL)
        put("steps", JSONArray().apply { t.steps.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id); put("package", s.action.packageName ?: JSONObject.NULL)
                put("type", s.action.type.name); put("text", s.action.text ?: JSONObject.NULL)
                put("targetText", s.action.target?.text ?: JSONObject.NULL)
                put("targetId", s.action.target?.resourceId ?: JSONObject.NULL)
                put("targetDesc", s.action.target?.contentDescription ?: JSONObject.NULL)
                put("reason", s.action.reason)
                put("verifyPackage", s.verifyPackage ?: JSONObject.NULL)
                put("verifyText", s.verifyText ?: JSONObject.NULL)
                put("maxAttempts", s.maxAttempts)
            })
        }})
    }

    private fun decode(o: JSONObject): AgentTask {
        val steps = buildList {
            val a = o.getJSONArray("steps")
            for (i in 0 until a.length()) {
                val s = a.getJSONObject(i)
                val type = runCatching { ActionType.valueOf(s.getString("type")) }
                    .getOrDefault(ActionType.OBSERVE)

                val targetText = nullableString(s, "targetText")
                val targetId = nullableString(s, "targetId")
                val targetDesc = nullableString(s, "targetDesc")
                val target = if (targetText != null || targetId != null || targetDesc != null) {
                    TargetSpec(text = targetText, resourceId = targetId, contentDescription = targetDesc)
                } else null

                add(TaskStep(
                    id = nullableString(s, "id") ?: UUID.randomUUID().toString(),
                    action = ActionRequest(
                        type = type,
                        packageName = nullableString(s, "package"),
                        text = nullableString(s, "text"),
                        target = target,
                        reason = s.optString("reason"),
                        requiresConfirmation = true
                    ),
                    verifyPackage = nullableString(s, "verifyPackage"),
                    verifyText = nullableString(s, "verifyText"),
                    maxAttempts = s.optInt("maxAttempts", 2).coerceIn(1, 5)
                ))
            }
        }
        return AgentTask(
            id = nullableString(o, "id") ?: UUID.randomUUID().toString(),
            title = o.optString("title"),
            steps = steps,
            createdAt = o.optLong("createdAt"),
            deadlineAt = if (o.isNull("deadlineAt")) null else o.optLong("deadlineAt"),
            state = runCatching { TaskState.valueOf(o.optString("state")) }.getOrDefault(TaskState.QUEUED),
            currentStep = o.optInt("currentStep").coerceIn(0, steps.size),
            attempts = o.optInt("attempts").coerceAtLeast(0),
            actionsExecuted = o.optInt("actionsExecuted", 0).coerceAtLeast(0),
            maxActions = o.optInt("maxActions", 30).coerceIn(1, 100),
            approvedForBackground = o.optBoolean("approvedForBackground", false),
            lastError = nullableString(o, "lastError")
        )
    }

    private companion object {
        const val MAX_TASKS = 100
        const val MAX_STEPS = 50
        const val MAX_ACTIONS = 100
        const val MAX_TITLE = 200
    }

    private fun nullableString(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null
        else o.optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}
