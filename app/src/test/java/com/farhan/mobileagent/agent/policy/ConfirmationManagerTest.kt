package com.farhan.mobileagent.agent.policy

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationManagerTest {
    @Test
    fun approvalProducesOneTimeReceipt() {
        val manager = ConfirmationManager()
        val request = ActionRequest(
            type = ActionType.HOME,
            reason = "test"
        )

        val id = manager.request(request)
        val approved = manager.approve(id)

        assertNotNull(approved)
        assertEquals(request.copy(confirmed = false), approved?.request)
        assertNull(manager.approve(id))
    }
}
