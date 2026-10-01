package com.farhan.mobileagent.agent.core

import com.farhan.mobileagent.agent.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityCenterTest {
    @Test
    fun capabilityGroupsAreExplicit() {
        val descriptors = CapabilityCenter.run {
            listOf(
                CapabilityDescriptor("screen", CapabilityClass.OBSERVE, CapabilityStatus.READY, ""),
                CapabilityDescriptor("answer", CapabilityClass.SUGGEST, CapabilityStatus.READY, ""),
                CapabilityDescriptor("tap", CapabilityClass.ACT, CapabilityStatus.USER_APPROVAL_REQUIRED, "")
            )
        }
        assertEquals(CapabilityClass.OBSERVE, descriptors[0].group)
        assertEquals(CapabilityClass.SUGGEST, descriptors[1].group)
        assertEquals(CapabilityClass.ACT, descriptors[2].group)
    }

    @Test
    fun uiInteractionRequiresAccessibility() {
        assertTrue(CapabilityCenter.actionUsesAccessibility(ActionType.TAP))
        assertTrue(CapabilityCenter.actionUsesAccessibility(ActionType.TYPE_TEXT))
        assertTrue(CapabilityCenter.actionUsesAccessibility(ActionType.HOME))
    }

    @Test
    fun everyActionTypeHasAnAccessibilityClassification() {
        ActionType.entries.forEach { type ->
            CapabilityCenter.actionUsesAccessibility(type)
        }
    }
}
