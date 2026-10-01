package com.farhan.mobileagent.agent.command

import com.farhan.mobileagent.agent.device.DeviceState
import com.farhan.mobileagent.agent.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test fun timedReminderGetsDueTime() {
        val before = System.currentTimeMillis()
        val result = router.route("remind me to call Ali in 10 minutes")
        assertTrue(result is CommandResult.AddAttention)
        val item = result as CommandResult.AddAttention
        assertEquals("call ali", item.title)
        assertNotNull(item.dueAt)
        assertTrue(item.dueAt!! >= before + 9L * 60L * 1000L)
    }

    @Test fun appAliasBecomesOpenAppAction() {
        val result = router.route("open chrome")
        assertTrue(result is CommandResult.Action)
        val request = (result as CommandResult.Action).request
        assertEquals(ActionType.OPEN_APP, request.type)
        assertEquals("com.android.chrome", request.packageName)
    }

    @Test fun batteryUsesLocalDeviceState() {
        val result = router.route("battery", DeviceState(42, false, true))
        assertTrue(result is CommandResult.Answer)
        assertTrue((result as CommandResult.Answer).text.contains("42%"))
    }

    @Test fun backgroundPlanSupportsMultipleSteps() {
        val result = router.route("background open chrome then go home")
        assertTrue(result is CommandResult.BackgroundTask)
        val task = (result as CommandResult.BackgroundTask).task
        assertEquals(2, task.steps.size)
        assertEquals(ActionType.OPEN_APP, task.steps[0].action.type)
        assertEquals("com.android.chrome", task.steps[0].verifyPackage)
        assertEquals(ActionType.HOME, task.steps[1].action.type)
        assertFalse(task.approvedForBackground)
    }
}
