package com.farhan.mobileagent.agent.security

import com.farhan.mobileagent.agent.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityKernelTest {
    @Test fun observationIsAllowed() {
        val kernel = SecurityKernel()
        assertEquals(Decision.ALLOW, kernel.evaluate(ActionRequest(ActionType.OBSERVE, requiresConfirmation = false)))
    }

    @Test fun dangerousActionRequiresConfirmation() {
        val kernel = SecurityKernel()
        assertEquals(Decision.REQUIRE_CONFIRMATION, kernel.evaluate(ActionRequest(ActionType.HOME)))
    }

    @Test fun approvedActionIsAllowed() {
        val kernel = SecurityKernel()
        assertEquals(Decision.ALLOW, kernel.evaluate(ActionRequest(ActionType.HOME, confirmed = true)))
    }

    @Test fun emergencyStopDeniesEverything() {
        val kernel = SecurityKernel()
        kernel.emergencyStop()
        assertEquals(Decision.DENY, kernel.evaluate(ActionRequest(ActionType.OBSERVE, requiresConfirmation = false)))
        assertTrue(kernel.isStopped())
    }

    @Test fun explicitResetReopensKernel() {
        val kernel = SecurityKernel()
        kernel.emergencyStop()
        kernel.resetAfterExplicitUserAction()
        assertFalse(kernel.isStopped())
        assertEquals(Decision.REQUIRE_CONFIRMATION, kernel.evaluate(ActionRequest(ActionType.TAP)))
    }

    @Test fun backgroundAutonomyCanBeToggled() {
        val kernel = SecurityKernel()
        assertTrue(kernel.isBackgroundAutonomyEnabled())
        kernel.setBackgroundAutonomyEnabled(false)
        assertFalse(kernel.isBackgroundAutonomyEnabled())
        kernel.setBackgroundAutonomyEnabled(true)
        assertTrue(kernel.isBackgroundAutonomyEnabled())
    }
}
