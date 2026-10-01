package com.farhan.mobileagent.agent.core

import com.farhan.mobileagent.agent.audit.AuditLog
import com.farhan.mobileagent.agent.attention.AttentionManager
import com.farhan.mobileagent.agent.policy.ConfirmationManager
import com.farhan.mobileagent.agent.security.ActionGuard
import com.farhan.mobileagent.agent.security.SecurityKernel

object AgentContainer {
    val securityKernel = SecurityKernel()
    val auditLog = AuditLog()
    val actionGuard = ActionGuard()
    val confirmationManager = ConfirmationManager()
    lateinit var attentionManager: AttentionManager
        private set

    fun initialize(context: android.content.Context) {
        if (!::attentionManager.isInitialized) {
            attentionManager = AttentionManager(context.applicationContext)
        }
    }
}
