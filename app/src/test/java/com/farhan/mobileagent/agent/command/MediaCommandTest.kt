package com.farhan.mobileagent.agent.command

import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCommandTest {
    @Test fun playMusicOpensLocalMusic() {
        assertTrue(LocalCommandRouter().route("play music") is CommandResult.OpenMusic)
    }
}
