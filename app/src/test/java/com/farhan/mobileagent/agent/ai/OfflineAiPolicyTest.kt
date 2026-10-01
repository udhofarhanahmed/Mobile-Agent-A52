package com.farhan.mobileagent.agent.ai

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAiPolicyTest {
    @Test fun proposedDeviceActionIsAlwaysUnconfirmed() {
        val proposal = PlannerProposal(
            action = ActionRequest(ActionType.HOME, confirmed = true, requiresConfirmation = false),
            confidence = 1f
        )
        val sanitized = OfflineAiPolicy.sanitize(proposal)
        assertFalse(sanitized.action!!.confirmed)
        assertTrue(sanitized.action!!.requiresConfirmation)
    }

    @Test fun observeRemainsObservationOnly() {
        val proposal = PlannerProposal(
            action = ActionRequest(ActionType.OBSERVE, confirmed = true, requiresConfirmation = false)
        )
        val sanitized = OfflineAiPolicy.sanitize(proposal)
        assertTrue(sanitized.action!!.type == ActionType.OBSERVE)
    }
}
