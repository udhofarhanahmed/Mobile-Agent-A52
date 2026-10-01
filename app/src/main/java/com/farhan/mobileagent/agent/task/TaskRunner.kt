package com.farhan.mobileagent.agent.task

import android.os.SystemClock
import com.farhan.mobileagent.agent.accessibility.AgentAccessibilityService
import com.farhan.mobileagent.agent.accessibility.ScreenStateStore
import com.farhan.mobileagent.agent.core.AgentRuntime
import com.farhan.mobileagent.agent.model.ActionResult
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.security.SecurityKernel

object BackgroundActionPolicy {
    fun mayRunUnattended(type: ActionType): Boolean = when (type) {
        ActionType.OBSERVE,
        ActionType.OPEN_APP,
        ActionType.OPEN_SETTINGS,
        ActionType.BACK,
        ActionType.HOME,
        ActionType.RECENTS,
        ActionType.VOLUME_UP,
        ActionType.VOLUME_DOWN,
        ActionType.MUTE -> true
        ActionType.TAP,
        ActionType.SWIPE,
        ActionType.TYPE_TEXT -> false
    }
}

object TaskBudgetPolicy {
    const val MAX_LIFETIME_MS = 24L * 60L * 60L * 1000L

    fun exceeded(task: AgentTask, now: Long = System.currentTimeMillis()): String? =
        when {
            now < task.createdAt -> "Invalid task clock state"
            now - task.createdAt > MAX_LIFETIME_MS -> "Maximum task lifetime exceeded"
            task.actionsExecuted >= task.maxActions -> "Maximum action budget exceeded"
            else -> null
        }
}

class TaskRunner(
    private val store: TaskStore,
    private val runtime: AgentRuntime,
    private val kernel: SecurityKernel
) {
    fun tick(): String {
        if (kernel.isStopped()) return "STOPPED"

        val approvedTasks = store.active().filter { it.approvedForBackground }
        if (approvedTasks.isEmpty()) return "IDLE"

        val service = AgentAccessibilityService.instance()
        val task = approvedTasks.firstOrNull {
            it.state == TaskState.QUEUED || it.state == TaskState.RUNNING
        } ?: approvedTasks.firstOrNull()

        if (service == null) {
            if (task != null && task.state != TaskState.PAUSED) {
                store.upsert(
                    task.copy(
                        state = TaskState.PAUSED,
                        lastError = "Accessibility service unavailable"
                    )
                )
            }
            return "PAUSED"
        }

        val runnableTask = if (task?.state == TaskState.PAUSED) {
            task.copy(state = TaskState.RUNNING, lastError = null).also(store::upsert)
        } else {
            task
        } ?: return "IDLE"

        TaskBudgetPolicy.exceeded(runnableTask)?.let { reason ->
            store.upsert(runnableTask.copy(state = TaskState.FAILED, lastError = reason))
            return "FAILED"
        }

        if (runnableTask.deadlineAt != null && System.currentTimeMillis() > runnableTask.deadlineAt) {
            store.upsert(runnableTask.copy(state = TaskState.FAILED, lastError = "Deadline exceeded"))
            return "FAILED"
        }
        if (runnableTask.currentStep >= runnableTask.steps.size) {
            store.upsert(runnableTask.copy(state = TaskState.COMPLETED))
            return "COMPLETED"
        }

        val step = runnableTask.steps[runnableTask.currentStep]
        if (!BackgroundActionPolicy.mayRunUnattended(step.action.type)) {
            store.upsert(runnableTask.copy(
                state = TaskState.WAITING_FOR_USER,
                lastError = "Sensitive step requires approval"
            ))
            return "WAITING_FOR_USER"
        }

        val nextActionCount = runnableTask.actionsExecuted + 1
        val autonomousRequest = step.action.copy(requiresConfirmation = false, confirmed = true)
        val result = service.executeApproved(autonomousRequest)
        if (!result.success) {
            return handleFailure(runnableTask, nextActionCount, result.message)
        }

        if (!verifyStep(step)) {
            return handleFailure(runnableTask, nextActionCount, "Post-action verification failed")
        }

        val next = runnableTask.currentStep + 1
        store.upsert(
            runnableTask.copy(
                state = if (next >= runnableTask.steps.size) TaskState.COMPLETED else TaskState.RUNNING,
                currentStep = next,
                attempts = 0,
                actionsExecuted = nextActionCount,
                lastError = null
            )
        )
        return if (next >= runnableTask.steps.size) "COMPLETED" else "STEP_OK"
    }

    private fun handleFailure(task: AgentTask, actionCount: Int, message: String): String {
        val attempts = task.attempts + 1
        val max = task.steps.getOrNull(task.currentStep)?.maxAttempts ?: 1
        return if (attempts >= max) {
            store.upsert(task.copy(
                state = TaskState.FAILED,
                attempts = attempts,
                actionsExecuted = actionCount,
                lastError = message
            ))
            "FAILED"
        } else {
            store.upsert(task.copy(
                state = TaskState.RUNNING,
                attempts = attempts,
                actionsExecuted = actionCount,
                lastError = message
            ))
            "RETRYING"
        }
    }

    private fun verifyStep(step: TaskStep): Boolean {
        if (step.verifyPackage == null && step.verifyText == null) return true
        val deadline = SystemClock.elapsedRealtime() + 1_500L

        while (SystemClock.elapsedRealtime() < deadline) {
            val screen = ScreenStateStore.get()
            val packageOk = step.verifyPackage == null || screen.packageName == step.verifyPackage
            val textOk = step.verifyText == null ||
                screen.visibleText.any { it.contains(step.verifyText, ignoreCase = true) }
            if (packageOk && textOk) return true
            try {
                Thread.sleep(100L)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return false
    }

    fun recordUserApprovedStep(
        taskId: String,
        stepId: String,
        result: ActionResult
    ): Boolean {
        val task = store.get(taskId) ?: return false
        if (task.state != TaskState.WAITING_FOR_USER) return false
        val step = task.steps.getOrNull(task.currentStep) ?: return false
        if (step.id != stepId) return false

        val actionCount = task.actionsExecuted + 1
        if (actionCount > task.maxActions) {
            store.upsert(task.copy(
                state = TaskState.FAILED,
                actionsExecuted = actionCount,
                lastError = "Maximum action budget exceeded"
            ))
            return true
        }

        if (result.success) {
            val next = task.currentStep + 1
            store.upsert(
                task.copy(
                    state = if (next >= task.steps.size) TaskState.COMPLETED else TaskState.RUNNING,
                    currentStep = next,
                    attempts = 0,
                    actionsExecuted = actionCount,
                    lastError = null
                )
            )
        } else {
            val attempts = task.attempts + 1
            if (attempts >= step.maxAttempts) {
                store.upsert(task.copy(
                    state = TaskState.FAILED,
                    attempts = attempts,
                    actionsExecuted = actionCount,
                    lastError = result.message
                ))
            } else {
                store.upsert(task.copy(
                    state = TaskState.WAITING_FOR_USER,
                    attempts = attempts,
                    actionsExecuted = actionCount,
                    lastError = result.message
                ))
            }
        }
        return true
    }
}
