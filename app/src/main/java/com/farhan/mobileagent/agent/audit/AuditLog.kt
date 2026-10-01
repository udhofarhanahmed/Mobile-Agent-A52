package com.farhan.mobileagent.agent.audit

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

data class AuditEvent(
    val timestamp: String = Instant.now().toString(),
    val action: String,
    val decision: String,
    val detail: String = ""
)

class AuditLog {
    private val events = CopyOnWriteArrayList<AuditEvent>()
    fun record(event: AuditEvent) { events += event }
    fun snapshot(): List<AuditEvent> = events.toList()
}
