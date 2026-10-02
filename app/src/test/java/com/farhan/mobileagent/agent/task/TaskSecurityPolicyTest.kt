package com.farhan.mobileagent.agent.task

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSecurityPolicyTest {
    @Test
    fun unattendedPolicyAllowsOnlyLowRiskActions() {
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.OBSERVE))
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.OPEN_APP))
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.OPEN_SETTINGS))
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.BACK))
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.HOME))
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.RECENTS))
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.VOLUME_UP))
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.VOLUME_DOWN))
        assertTrue(BackgroundActionPolicy.mayRunUnattended(ActionType.MUTE))

        assertFalse(BackgroundActionPolicy.mayRunUnattended(ActionType.TAP))
        assertFalse(BackgroundActionPolicy.mayRunUnattended(ActionType.SWIPE))
        assertFalse(BackgroundActionPolicy.mayRunUnattended(ActionType.TYPE_TEXT))
    }

    @Test
    fun taskBudgetAcceptsNormalTask() {
        val task = AgentTask(
            title = "test",
            steps = listOf(TaskStep(action = ActionRequest(ActionType.HOME))),
            createdAt = 1_000L,
            actionsExecuted = 1,
            maxActions = 5
        )

        assertNull(TaskBudgetPolicy.exceeded(task, now = 1_001L))
    }

    @Test
    fun taskBudgetRejectsOverLimit() {
        val task = AgentTask(
            title = "test",
            steps = listOf(TaskStep(action = ActionRequest(ActionType.HOME))),
            createdAt = 1_000L,
            actionsExecuted = 5,
            maxActions = 5
        )

        assertTrue(TaskBudgetPolicy.exceeded(task, now = 1_001L)!!.contains("action budget"))
    }

    @Test
    fun taskBudgetRejectsStaleTask() {
        val task = AgentTask(
            title = "test",
            steps = listOf(TaskStep(action = ActionRequest(ActionType.HOME))),
            createdAt = 1_000L,
            actionsExecuted = 0,
            maxActions = 5
        )

        val reason = TaskBudgetPolicy.exceeded(
            task,
            now = 1_000L + TaskBudgetPolicy.MAX_LIFETIME_MS + 1L
        )

        assertTrue(reason!!.contains("lifetime"))
    }
}
