package com.farhan.mobileagent.agent.task

import com.farhan.mobileagent.agent.model.ActionRequest
import java.util.UUID

enum class TaskState { QUEUED, RUNNING, WAITING_FOR_USER, PAUSED, COMPLETED, FAILED, CANCELLED }

data class TaskStep(
    val id: String = UUID.randomUUID().toString(),
    val action: ActionRequest,
    val verifyPackage: String? = null,
    val verifyText: String? = null,
    val maxAttempts: Int = 2
)

data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val steps: List<TaskStep>,
    val createdAt: Long = System.currentTimeMillis(),
    val deadlineAt: Long? = null,
    val state: TaskState = TaskState.QUEUED,
    val currentStep: Int = 0,
    val attempts: Int = 0,
    val lastError: String? = null
)
