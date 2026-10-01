package com.farhan.mobileagent.agent.security

import android.os.SystemClock
import com.farhan.mobileagent.agent.model.ActionRequest
import java.util.concurrent.atomic.AtomicInteger

class ActionGuard(
    private val minIntervalMs: Long = 250L,
    private val maxActionsPerMinute: Int = 30
) {
    private var lastActionAt = 0L
    private var windowStartedAt = 0L
    private val actionsInWindow = AtomicInteger(0)

    @Synchronized
    fun check(now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (lastActionAt != 0L && now - lastActionAt < minIntervalMs) return false
        if (windowStartedAt == 0L || now - windowStartedAt >= 60_000L) {
            windowStartedAt = now
            actionsInWindow.set(0)
        }
        if (actionsInWindow.incrementAndGet() > maxActionsPerMinute) {
            actionsInWindow.decrementAndGet()
            return false
        }
        lastActionAt = now
        return true
    }

    fun classifyLoop(request: ActionRequest): String =
        "rate-limit:" + request.type.name.lowercase()
}
