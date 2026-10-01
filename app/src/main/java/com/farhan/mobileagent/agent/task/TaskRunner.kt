package com.farhan.mobileagent.agent.task

import com.farhan.mobileagent.agent.accessibility.AgentAccessibilityService
import com.farhan.mobileagent.agent.accessibility.ScreenStateStore
import com.farhan.mobileagent.agent.core.AgentRuntime
import com.farhan.mobileagent.agent.model.ActionResult
import com.farhan.mobileagent.agent.security.SecurityKernel

class TaskRunner(
    private val store: TaskStore,
    private val runtime: AgentRuntime,
    private val kernel: SecurityKernel
) {
    fun tick(): String {
        if (kernel.isStopped()) return "STOPPED"
        val task = store.active().firstOrNull() ?: return "IDLE"
        if (task.deadlineAt != null && System.currentTimeMillis() > task.deadlineAt) {
            store.upsert(task.copy(state = TaskState.FAILED, lastError = "Deadline exceeded"))
            return "FAILED"
        }
        if (task.currentStep >= task.steps.size) {
            store.upsert(task.copy(state = TaskState.COMPLETED))
            return "COMPLETED"
        }

        val step = task.steps[task.currentStep]
        val service = AgentAccessibilityService.instance()
            ?: run {
                store.upsert(task.copy(state = TaskState.PAUSED, lastError = "Accessibility service unavailable"))
                return "PAUSED"
            }

        val prepared = runtime.prepare(step.action)
            ?: return "BLOCKED"

        store.upsert(task.copy(state = TaskState.WAITING_FOR_USER))
        // Background automation never self-approves a device action.
        // A future approval UI/notification will call resumeApproved().
        return "WAITING_FOR_USER:" + prepared.id
    }

    fun resumeApproved(taskId: String, stepId: String, request: com.farhan.mobileagent.agent.model.ActionRequest): ActionResult? {
        val task = store.get(taskId) ?: return null
        val result = AgentAccessibilityService.instance()?.executeApproved(request) ?: return null
        if (result.success) {
            val next = task.currentStep + 1
            val nextState = if (next >= task.steps.size) TaskState.COMPLETED else TaskState.RUNNING
            store.upsert(task.copy(state = nextState, currentStep = next, attempts = 0, lastError = null))
        } else {
            val attempts = task.attempts + 1
            val step = task.steps[task.currentStep]
            if (attempts >= step.maxAttempts) {
                store.upsert(task.copy(state = TaskState.FAILED, attempts = attempts, lastError = result.message))
            } else {
                store.upsert(task.copy(state = TaskState.RUNNING, attempts = attempts, lastError = result.message))
            }
        }
        return result
    }

    fun verifyCurrent(taskId: String): Boolean {
        val task = store.get(taskId) ?: return false
        val step = task.steps.getOrNull(task.currentStep - 1) ?: return task.state == TaskState.COMPLETED
        val screen = ScreenStateStore.get()
        val packageOk = step.verifyPackage == null || screen.packageName == step.verifyPackage
        val textOk = step.verifyText == null || screen.visibleText.any { it.contains(step.verifyText, ignoreCase = true) }
        return packageOk && textOk
    }
}
