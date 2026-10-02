package com.farhan.mobileagent.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.farhan.mobileagent.agent.core.AgentContainer
import com.farhan.mobileagent.agent.model.ActionResult
import com.farhan.mobileagent.agent.model.Decision
import com.farhan.mobileagent.agent.model.ActionRequest

data class ScreenSnapshot(
    val packageName: String?,
    val className: String?,
    val visibleText: List<String>,
    val updatedAt: Long
)

object ScreenStateStore {
    @Volatile private var current = ScreenSnapshot(null, null, emptyList(), 0L)

    fun get(): ScreenSnapshot = current

    fun clear() {
        current = ScreenSnapshot(null, null, emptyList(), 0L)
    }

    internal fun update(snapshot: ScreenSnapshot) {
        current = snapshot
    }
}

class AgentAccessibilityService : AccessibilityService() {
    private lateinit var executor: ActionExecutor

    @Volatile
    private var actionable = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        AgentContainer.initialize(applicationContext)
        executor = ActionExecutor(
            service = this,
            kernel = AgentContainer.securityKernel,
            guard = AgentContainer.actionGuard,
            audit = AgentContainer.auditLog
        )
        actionable = true
        instance = this
    }

    override fun onDestroy() {
        actionable = false
        if (instance === this) instance = null
        ScreenStateStore.clear()
        super.onDestroy()
    }

    fun executeApproved(request: ActionRequest): ActionResult =
        if (::executor.isInitialized && actionable) {
            executor.execute(request)
        } else {
            ActionResult(false, "Accessibility service is not actionable", Decision.DENY)
        }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!actionable) actionable = true

        val root = rootInActiveWindow ?: run {
            ScreenStateStore.clear()
            return
        }

        val texts = collectSafeText(root).take(120)
        ScreenStateStore.update(
            ScreenSnapshot(
                packageName = event?.packageName?.toString(),
                className = event?.className?.toString(),
                visibleText = texts,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun collectSafeText(
        node: android.view.accessibility.AccessibilityNodeInfo
    ): List<String> {
        val result = mutableListOf<String>()

        fun walk(current: android.view.accessibility.AccessibilityNodeInfo) {
            if (!current.isVisibleToUser || current.isPassword) return

            current.text?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { result += it }

            current.contentDescription?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { result += it }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let(::walk)
            }
        }

        walk(node)
        return result.distinct().take(120)
    }

    override fun onInterrupt() {
        // Fail closed while Android reports an interruption. The service may
        // recover later; the next accessibility event re-enables observation.
        actionable = false
        ScreenStateStore.clear()
    }

    companion object {
        @Volatile private var instance: AgentAccessibilityService? = null

        fun instance(): AgentAccessibilityService? = instance
    }
}
