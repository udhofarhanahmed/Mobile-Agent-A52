package com.farhan.mobileagent.agent.audit

import com.farhan.mobileagent.agent.model.ActionRequest
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

data class AuditEvent(
    val timestamp: String = Instant.now().toString(),
    val action: String,
    val decision: String,
    val detail: String = "",
    val success: Boolean? = null
)

class AuditLog {
    private val events = CopyOnWriteArrayList<AuditEvent>()
    fun record(event: AuditEvent) { events += event }
    fun record(request: ActionRequest, decision: String, detail: String, success: Boolean? = null) =
        record(AuditEvent(action = request.type.name, decision = decision, detail = detail, success = success))
    fun snapshot(): List<AuditEvent> = events.toList()
    fun clear() = events.clear()
}
