package com.farhan.mobileagent.agent.task

import android.content.Context
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.GestureSpec
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
        val safeTask = sanitize(task) ?: return
        val existing = all().firstOrNull { it.id == safeTask.id }
        val keepExistingApproval =
            existing?.approvedForBackground == true &&
                safeTask.approvedForBackground &&
                executionDefinitionMatches(existing, safeTask)

        write(
            safeTask.copy(
                approvedForBackground = keepExistingApproval
            )
        )
    }

    private fun write(task: AgentTask) {
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
            for (i in 0 until array.length()) {
                decode(array.optJSONObject(i) ?: continue)?.let(::add)
            }
        }.sortedBy { it.createdAt }
    }.getOrElse { emptyList() }

    fun active(): List<AgentTask> =
        all().filter { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING || it.state == TaskState.PAUSED }

    @Synchronized
    fun cancel(id: String) {
        get(id)?.let { upsert(it.copy(state = TaskState.CANCELLED)) }
    }

    @Synchronized
    fun authorizeBackground(id: String): AgentTask? {
        val task = get(id) ?: return null
        if (task.state != TaskState.QUEUED &&
            task.state != TaskState.WAITING_FOR_USER &&
            task.state != TaskState.PAUSED
        ) return null

        if (TaskBudgetPolicy.exceeded(task) != null) return null
        if (task.deadlineAt != null && System.currentTimeMillis() > task.deadlineAt) return null

        val authorized = task.copy(
            approvedForBackground = true,
            state = if (task.state == TaskState.WAITING_FOR_USER) {
                TaskState.WAITING_FOR_USER
            } else {
                TaskState.RUNNING
            }
        )
        write(authorized)
        return authorized
    }

    private fun encode(t: AgentTask) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("createdAt", t.createdAt)
        put("deadlineAt", t.deadlineAt ?: JSONObject.NULL); put("state", t.state.name)
        put("currentStep", t.currentStep); put("attempts", t.attempts)
        put("actionsExecuted", t.actionsExecuted); put("maxActions", t.maxActions)
        put("approvedForBackground", t.approvedForBackground)
        put("lastError", t.lastError ?: JSONObject.NULL)
        put("steps", JSONArray().apply { t.steps.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id)
                put("package", s.action.packageName ?: JSONObject.NULL)
                put("type", s.action.type.name)
                put("text", s.action.text ?: JSONObject.NULL)
                put("targetText", s.action.target?.text ?: JSONObject.NULL)
                put("targetId", s.action.target?.resourceId ?: JSONObject.NULL)
                put("targetDesc", s.action.target?.contentDescription ?: JSONObject.NULL)
                put("gestureStartX", s.action.gesture?.startX ?: JSONObject.NULL)
                put("gestureStartY", s.action.gesture?.startY ?: JSONObject.NULL)
                put("gestureEndX", s.action.gesture?.endX ?: JSONObject.NULL)
                put("gestureEndY", s.action.gesture?.endY ?: JSONObject.NULL)
                put("gestureDurationMs", s.action.gesture?.durationMs ?: JSONObject.NULL)
                put("reason", s.action.reason)
                put("verifyPackage", s.verifyPackage ?: JSONObject.NULL)
                put("verifyText", s.verifyText ?: JSONObject.NULL)
                put("maxAttempts", s.maxAttempts)
            })
        }})
    }

    private fun decode(o: JSONObject): AgentTask? {
        val id = nullableString(o, "id")?.takeIf { it.length <= MAX_ID } ?: return null
        val createdAt = o.optLong("createdAt", -1L)
        if (createdAt <= 0L) return null

        val deadlineAt = if (o.isNull("deadlineAt")) {
            null
        } else {
            o.optLong("deadlineAt", -1L).takeIf { it >= createdAt }
        }
        if (!o.isNull("deadlineAt") && deadlineAt == null) return null
        if (deadlineAt != null && deadlineAt - createdAt > TaskBudgetPolicy.MAX_LIFETIME_MS) return null

        val state = runCatching { TaskState.valueOf(o.optString("state")) }.getOrNull() ?: return null
        val array = o.optJSONArray("steps") ?: return null
        if (array.length() > MAX_STEPS) return null

        val steps = buildList {
            val seenIds = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val s = array.optJSONObject(i) ?: return null
                val stepId = nullableString(s, "id")?.takeIf {
                    it.length <= MAX_ID && seenIds.add(it)
                } ?: return null

                val type = runCatching { ActionType.valueOf(s.getString("type")) }.getOrNull()
                    ?: return null

                val packageName = nullableString(s, "package")?.takeIf { it.length <= MAX_PACKAGE }
                val text = nullableString(s, "text")?.takeIf { it.length <= MAX_TEXT }
                val targetText = nullableString(s, "targetText")?.takeIf { it.length <= MAX_TARGET }
                val targetId = nullableString(s, "targetId")?.takeIf { it.length <= MAX_TARGET }
                val targetDesc = nullableString(s, "targetDesc")?.takeIf { it.length <= MAX_TARGET }
                val target = if (targetText != null || targetId != null || targetDesc != null) {
                    TargetSpec(text = targetText, resourceId = targetId, contentDescription = targetDesc)
                } else null

                val gesture = listOf(
                    s.isNull("gestureStartX"),
                    s.isNull("gestureStartY"),
                    s.isNull("gestureEndX"),
                    s.isNull("gestureEndY"),
                    s.isNull("gestureDurationMs")
                ).let { nulls ->
                    if (nulls.all { it }) {
                        null
                    } else if (nulls.any { it }) {
                        return null
                    } else {
                        val startX = s.optDouble("gestureStartX", Double.NaN)
                        val startY = s.optDouble("gestureStartY", Double.NaN)
                        val endX = s.optDouble("gestureEndX", Double.NaN)
                        val endY = s.optDouble("gestureEndY", Double.NaN)
                        val duration = s.optLong("gestureDurationMs", -1L)
                        if (!startX.isFinite() || !startY.isFinite() ||
                            !endX.isFinite() || !endY.isFinite() ||
                            kotlin.math.abs(startX) > MAX_COORDINATE ||
                            kotlin.math.abs(startY) > MAX_COORDINATE ||
                            kotlin.math.abs(endX) > MAX_COORDINATE ||
                            kotlin.math.abs(endY) > MAX_COORDINATE ||
                            duration !in 100L..3000L
                        ) return null
                        GestureSpec(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), duration)
                    }
                }

                if (type == ActionType.OPEN_APP &&
                    (packageName.isNullOrBlank() || !PACKAGE_PATTERN.matches(packageName))
                ) return null
                if (type == ActionType.TAP && target == null) return null
                if (type == ActionType.TYPE_TEXT &&
                    (target == null || text.isNullOrEmpty())
                ) return null
                if (type == ActionType.SWIPE && gesture == null) return null

                val reason = s.optString("reason").take(MAX_REASON)
                val verifyPackage = nullableString(s, "verifyPackage")?.takeIf { it.length <= MAX_PACKAGE }
                val verifyText = nullableString(s, "verifyText")?.takeIf { it.length <= MAX_VERIFY }
                val maxAttempts = s.optInt("maxAttempts", 2)
                if (maxAttempts !in 1..5) return null

                add(TaskStep(
                    id = stepId,
                    action = ActionRequest(
                        type = type,
                        packageName = packageName,
                        text = text,
                        target = target,
                        gesture = gesture,
                        reason = reason,
                        requiresConfirmation = true
                    ),
                    verifyPackage = verifyPackage,
                    verifyText = verifyText,
                    maxAttempts = maxAttempts
                ))
            }
        }

        val maxActions = o.optInt("maxActions", 30)
        if (maxActions !in 1..MAX_ACTIONS) return null
        val currentStep = o.optInt("currentStep", -1)
        if (currentStep !in 0..steps.size) return null
        val attempts = o.optInt("attempts", -1)
        if (attempts !in 0..MAX_ATTEMPTS) return null
        val actionsExecuted = o.optInt("actionsExecuted", -1)
        if (actionsExecuted !in 0..maxActions) return null

        val task = AgentTask(
            id = id,
            title = o.optString("title").take(MAX_TITLE),
            steps = steps,
            createdAt = createdAt,
            deadlineAt = deadlineAt,
            state = state,
            currentStep = currentStep,
            attempts = attempts,
            actionsExecuted = actionsExecuted,
            maxActions = maxActions,
            approvedForBackground = o.optBoolean("approvedForBackground", false),
            lastError = nullableString(o, "lastError")?.take(MAX_ERROR)
        )
        return sanitize(task)
    }

    private fun sanitize(task: AgentTask): AgentTask? {
        val id = task.id.trim().takeIf { it.isNotEmpty() && it.length <= MAX_ID } ?: return null
        if (task.steps.size > MAX_STEPS) return null
        if (task.createdAt <= 0L) return null
        if (task.deadlineAt != null &&
            (task.deadlineAt < task.createdAt ||
                task.deadlineAt - task.createdAt > TaskBudgetPolicy.MAX_LIFETIME_MS)
        ) return null
        if (task.maxActions !in 1..MAX_ACTIONS) return null
        if (task.currentStep !in 0..task.steps.size) return null
        if (task.attempts !in 0..MAX_ATTEMPTS) return null
        if (task.actionsExecuted !in 0..task.maxActions) return null

        val seenIds = mutableSetOf<String>()
        val safeSteps = task.steps.map { step ->
            val stepId = step.id.trim().takeIf {
                it.isNotEmpty() && it.length <= MAX_ID && seenIds.add(it)
            } ?: return null
            val action = step.action
            val packageName = action.packageName?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_PACKAGE }
            val text = action.text?.takeIf { it.length <= MAX_TEXT }
            val target = action.target?.let {
                TargetSpec(
                    text = it.text?.takeIf { value -> value.length <= MAX_TARGET },
                    resourceId = it.resourceId?.takeIf { value -> value.length <= MAX_TARGET },
                    contentDescription = it.contentDescription?.takeIf { value -> value.length <= MAX_TARGET }
                )
            }
            val gesture = action.gesture?.takeIf {
                it.durationMs in 100L..3000L &&
                    it.startX.isFinite() && it.startY.isFinite() &&
                    it.endX.isFinite() && it.endY.isFinite() &&
                    kotlin.math.abs(it.startX) <= MAX_COORDINATE &&
                    kotlin.math.abs(it.startY) <= MAX_COORDINATE &&
                    kotlin.math.abs(it.endX) <= MAX_COORDINATE &&
                    kotlin.math.abs(it.endY) <= MAX_COORDINATE
            }
            if (action.type == ActionType.OPEN_APP &&
                (packageName == null || !PACKAGE_PATTERN.matches(packageName))
            ) return null
            if (action.type == ActionType.TAP && target == null) return null
            if (action.type == ActionType.TYPE_TEXT && (target == null || text.isNullOrEmpty())) return null
            if (action.type == ActionType.SWIPE && gesture == null) return null
            if (step.maxAttempts !in 1..5) return null

            TaskStep(
                id = stepId,
                action = action.copy(
                    packageName = packageName,
                    text = text,
                    target = target,
                    gesture = gesture,
                    reason = action.reason.take(MAX_REASON),
                    requiresConfirmation = true,
                    confirmed = false
                ),
                verifyPackage = step.verifyPackage?.takeIf { it.length <= MAX_PACKAGE },
                verifyText = step.verifyText?.takeIf { it.length <= MAX_VERIFY },
                maxAttempts = step.maxAttempts
            )
        }

        return task.copy(
            id = id,
            title = task.title.take(MAX_TITLE),
            steps = safeSteps,
            lastError = task.lastError?.take(MAX_ERROR)
        )
    }

    private fun executionDefinitionMatches(a: AgentTask, b: AgentTask): Boolean =
        a.steps == b.steps &&
            a.deadlineAt == b.deadlineAt &&
            a.maxActions == b.maxActions

    private companion object {
        const val MAX_TASKS = 100
        const val MAX_STEPS = 50
        const val MAX_ACTIONS = 100
        const val MAX_ATTEMPTS = 5
        const val MAX_TITLE = 200
        const val MAX_ID = 128
        const val MAX_PACKAGE = 200
        const val MAX_TEXT = 2_000
        const val MAX_TARGET = 500
        const val MAX_REASON = 500
        const val MAX_VERIFY = 500
        const val MAX_ERROR = 500
        const val MAX_COORDINATE = 10_000f
        val PACKAGE_PATTERN = Regex("^[A-Za-z0-9_.]+$")
    }

    private fun nullableString(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null
        else o.optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}
