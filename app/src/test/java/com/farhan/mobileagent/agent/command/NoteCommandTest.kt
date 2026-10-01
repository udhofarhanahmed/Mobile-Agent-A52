package com.farhan.mobileagent.agent.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCommandTest {
    private val router = LocalCommandRouter()

    @Test fun noteCommandCreatesNoteResult() {
        val result = router.route("note remember to review the financial engine")
        assertTrue(result is CommandResult.AddNote)
        assertEquals(
            "remember to review the financial engine",
            (result as CommandResult.AddNote).content
        )
    }

    @Test fun naturalNoteCommandCreatesNoteResult() {
        val result = router.route("make a note call Ali tomorrow")
        assertTrue(result is CommandResult.AddNote)
        assertEquals("call Ali tomorrow", (result as CommandResult.AddNote).content)
    }
}
