package com.farhan.mobileagent.agent.audit

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLogTest {
    @Test fun recordsActionEventsInOrder() {
        val log = AuditLog()
        log.record(
            ActionRequest(ActionType.HOME),
            decision = "PENDING",
            detail = "Waiting for user",
            success = null
        )
        log.record(
            ActionRequest(ActionType.HOME, confirmed = true),
            decision = "ALLOW",
            detail = "Completed",
            success = true
        )

        assertEquals(2, log.snapshot().size)
        assertEquals("PENDING", log.snapshot()[0].decision)
        assertTrue(log.snapshot()[1].success == true)
    }

    @Test fun clearRemovesAllEvents() {
        val log = AuditLog()
        log.record(
            ActionRequest(ActionType.OBSERVE, requiresConfirmation = false),
            decision = "ALLOW",
            detail = "Observed",
            success = true
        )
        log.clear()
        assertTrue(log.snapshot().isEmpty())
    }
}
