package com.farhan.mobileagent.agent.command

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
}
