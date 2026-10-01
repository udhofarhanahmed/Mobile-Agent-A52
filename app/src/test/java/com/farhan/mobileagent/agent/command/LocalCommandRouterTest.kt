package com.farhan.mobileagent.agent.command

import com.farhan.mobileagent.agent.device.DeviceState
import com.farhan.mobileagent.agent.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCommandRouterTest {
    private val router = LocalCommandRouter()

    @Test fun homeBecomesAction() {
        val result = router.route("go home")
        assertTrue(result is CommandResult.Action)
        assertEquals(ActionType.HOME, (result as CommandResult.Action).request.type)
    }

    @Test fun tapCommandFindsTextTarget() {
        val result = router.route("tap Settings")
        assertTrue(result is CommandResult.Action)
        assertEquals("Settings", (result as CommandResult.Action).request.target?.text)
    }

    @Test fun rememberBecomesAttention() {
        val result = router.route("remember buy milk")
        assertTrue(result is CommandResult.AddAttention)
        assertEquals("buy milk", (result as CommandResult.AddAttention).title)
    }

    @Test fun batteryUsesLocalDeviceState() {
        val result = router.route("battery", DeviceState(42, false, true))
        assertTrue(result is CommandResult.Answer)
        assertTrue((result as CommandResult.Answer).text.contains("42%"))
    }
}
