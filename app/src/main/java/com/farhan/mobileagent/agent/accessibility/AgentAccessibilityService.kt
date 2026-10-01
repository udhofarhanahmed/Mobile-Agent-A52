package com.farhan.mobileagent.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

data class ScreenSnapshot(
    val packageName: String?,
    val className: String?,
    val visibleText: List<String>,
    val updatedAt: Long
)

object ScreenStateStore {
    @Volatile private var current = ScreenSnapshot(null, null, emptyList(), 0L)
    fun get(): ScreenSnapshot = current
    internal fun update(snapshot: ScreenSnapshot) { current = snapshot }
}

class AgentAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
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

    private fun collectSafeText(node: android.view.accessibility.AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        fun walk(current: android.view.accessibility.AccessibilityNodeInfo) {
            if (!current.isVisibleToUser || current.isPassword) return
            current.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { result += it }
            current.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { result += it }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let(::walk)
            }
        }
        walk(node)
        return result.distinct().take(120)
    }

    override fun onInterrupt() {
        // Fail closed.
    }
}
