package com.farhan.mobileagent.agent.attention

import org.junit.Assert.assertEquals
import org.junit.Test

class AttentionManagerTest {
    @Test fun pendingItemsArePrioritySorted() {
        val manager = AttentionManager()
        manager.add(AttentionItem(title = "low", priority = 10))
        manager.add(AttentionItem(title = "high", priority = 90))
        assertEquals("high", manager.pending().first().title)
    }
}
