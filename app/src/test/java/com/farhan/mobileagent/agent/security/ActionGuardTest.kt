package com.farhan.mobileagent.agent.security

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGuardTest {
    @Test fun immediateSecondActionIsBlocked() {
        val guard = ActionGuard(minIntervalMs = 1000L)
        assertTrue(guard.check(now = 10_000L))
        assertFalse(guard.check(now = 10_100L))
    }

    @Test fun guardResetsAfterMinuteWindow() {
        val guard = ActionGuard(minIntervalMs = 0L, maxActionsPerMinute = 1)
        assertTrue(guard.check(now = 10_000L))
        assertFalse(guard.check(now = 10_001L))
        assertTrue(guard.check(now = 70_001L))
    }
}
