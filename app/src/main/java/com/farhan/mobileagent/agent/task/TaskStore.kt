package com.farhan.mobileagent.agent.task

import android.content.Context
import com.farhan.mobileagent.agent.storage.LocalMemory
import org.json.JSONArray
import org.json.JSONObject

class TaskStore(context: Context) {
    private val memory = LocalMemory(context.applicationContext)
    private val key = "agent_tasks"

    @Synchronized
    fun upsert(task: AgentTask) {
        val all = all().associateBy { it.id }.toMutableMap()
        all[task.id] = task
        memory.put(key, JSONArray().apply { all.values.forEach { put(encode(it)) } }.toString())
    }

    @Synchronized
    fun get(id: String): AgentTask? = all().firstOrNull { it.id == id }

    fun all(): List<AgentTask> {
        val raw = memory.get(key) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) add(decode(array.getJSONObject(i)))
        }.sortedBy { it.createdAt }
    }

    fun active(): List<AgentTask> =
        all().filter { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING || it.state == TaskState.PAUSED }

    @Synchronized
    fun cancel(id: String) {
        get(id)?.let { upsert(it.copy(state = TaskState.CANCELLED)) }
    }

    private fun encode(t: AgentTask) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("createdAt", t.createdAt)
        put("deadlineAt", t.deadlineAt ?: JSONObject.NULL); put("state", t.state.name)
        put("currentStep", t.currentStep); put("attempts", t.attempts)
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
                val type = runCatching { com.farhan.mobileagent.agent.model.ActionType.valueOf(s.getString("type")) }
                    .getOrDefault(com.farhan.mobileagent.agent.model.ActionType.OBSERVE)
                add(TaskStep(
                    id = s.optString("id"),
                    action = com.farhan.mobileagent.agent.model.ActionRequest(
                        type = type,
                        packageName = s.optString("package").takeIf { it.isNotBlank() },
                        text = s.optString("text").takeIf { it.isNotBlank() },
                        target = com.farhan.mobileagent.agent.model.TargetSpec(
                            text = s.optString("targetText").takeIf { it.isNotBlank() },
                            resourceId = s.optString("targetId").takeIf { it.isNotBlank() },
                            contentDescription = s.optString("targetDesc").takeIf { it.isNotBlank() }
                        ),
                        reason = s.optString("reason"),
                        requiresConfirmation = true
                    ),
                    verifyPackage = s.optString("verifyPackage").takeIf { it.isNotBlank() },
                    verifyText = s.optString("verifyText").takeIf { it.isNotBlank() },
                    maxAttempts = s.optInt("maxAttempts", 2)
                ))
            }
        }
        return AgentTask(
            id = o.optString("id"),
            title = o.optString("title"),
            steps = steps,
            createdAt = o.optLong("createdAt"),
            deadlineAt = if (o.isNull("deadlineAt")) null else o.optLong("deadlineAt"),
            state = runCatching { TaskState.valueOf(o.optString("state")) }.getOrDefault(TaskState.QUEUED),
            currentStep = o.optInt("currentStep"),
            attempts = o.optInt("attempts"),
            lastError = o.optString("lastError").takeIf { it.isNotBlank() }
        )
    }
}
