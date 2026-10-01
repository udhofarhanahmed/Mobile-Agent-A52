package com.farhan.mobileagent.agent.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallCommandTest {
    @Test fun callNumberOpensProtectedCallFlow() {
        val result = LocalCommandRouter().route("call 03001234567")
        assertTrue(result is CommandResult.OpenCall)
        assertEquals("03001234567", (result as CommandResult.OpenCall).target)
    }

    @Test fun dialContactOpensProtectedCallFlow() {
        val result = LocalCommandRouter().route("dial Ali")
        assertTrue(result is CommandResult.OpenCall)
        assertEquals("Ali", (result as CommandResult.OpenCall).target)
    }
}
