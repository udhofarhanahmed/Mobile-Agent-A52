package com.farhan.mobileagent.agent.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskBudgetPolicyTest {
    @Test fun freshTaskIsWithinBudget() {
        val now = 10_000L
        val task = AgentTask(title = "test", steps = emptyList(), createdAt = now)
        assertNull(TaskBudgetPolicy.exceeded(task, now))
    }

    @Test fun actionBudgetStopsRunawayTasks() {
        val task = AgentTask(
            title = "loop",
            steps = emptyList(),
            actionsExecuted = 30,
            maxActions = 30
        )
        assertEquals(
            "Maximum action budget exceeded",
            TaskBudgetPolicy.exceeded(task)
        )
    }

    @Test fun lifetimeBudgetStopsOldTasks() {
        val created = 10_000L
        val task = AgentTask(title = "old", steps = emptyList(), createdAt = created)
        assertEquals(
            "Maximum task lifetime exceeded",
            TaskBudgetPolicy.exceeded(task, created + TaskBudgetPolicy.MAX_LIFETIME_MS + 1)
        )
    }
}
