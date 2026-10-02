package com.farhan.mobileagent.agent.core

import com.farhan.mobileagent.agent.audit.AuditLog
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionResult
import com.farhan.mobileagent.agent.policy.ApprovedAction
import com.farhan.mobileagent.agent.policy.ConfirmationManager
import com.farhan.mobileagent.agent.security.SecurityKernel

data class PendingAction(
    val id: String,
    val request: ActionRequest
)

data class ApprovedExecution(
    val approved: ApprovedAction,
    val result: ActionResult
)

class AgentRuntime(
    private val kernel: SecurityKernel,
    private val confirmation: ConfirmationManager,
    private val audit: AuditLog
) {
    fun prepare(request: ActionRequest): PendingAction? {
        if (kernel.isStopped()) {
            audit.record(request, "DENY", "Emergency stop active", false)
            return null
        }
        val id = confirmation.request(request)
        audit.record(request, "PENDING", "Awaiting explicit approval", null)
        return PendingAction(id, request)
    }

    fun approve(
        id: String,
        executor: (ApprovedAction) -> ActionResult
    ): ApprovedExecution? {
        val approved = confirmation.approve(id) ?: return null
        return ApprovedExecution(approved, executor(approved))
    }

    fun reject(id: String): ActionRequest? {
        val rejected = confirmation.reject(id)
        rejected?.let { audit.record(it, "DENY", "User rejected action", false) }
        return rejected
    }
}
