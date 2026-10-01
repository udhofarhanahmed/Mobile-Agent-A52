package com.farhan.mobileagent.agent.ai

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.task.AgentTask
import com.farhan.mobileagent.agent.task.TaskStep
import org.junit.Assert.assertEquals
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

    @Test fun backgroundTaskCannotSelfAuthorize() {
        val proposal = PlannerProposal(
            task = AgentTask(
                title = "demo",
                steps = listOf(
                    TaskStep(
                        action = ActionRequest(
                            ActionType.HOME,
                            requiresConfirmation = false,
                            confirmed = true
                        )
                    )
                ),
                approvedForBackground = true
            )
        )
        val sanitized = OfflineAiPolicy.sanitize(proposal)
        assertFalse(sanitized.task!!.approvedForBackground)
        assertEquals(ActionType.HOME, sanitized.task.steps.single().action.type)
        assertFalse(sanitized.task.steps.single().action.confirmed)
        assertTrue(sanitized.task.steps.single().action.requiresConfirmation)
    }
}
