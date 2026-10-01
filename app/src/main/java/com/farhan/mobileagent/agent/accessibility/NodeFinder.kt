package com.farhan.mobileagent.agent.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.farhan.mobileagent.agent.model.TargetSpec

object NodeFinder {
    fun find(root: AccessibilityNodeInfo?, target: TargetSpec): AccessibilityNodeInfo? {
        if (root == null) return null
        target.text?.let {
            root.findAccessibilityNodeInfosByText(it).firstOrNull()?.let { node -> return node }
        }
        target.resourceId?.let {
            root.findAccessibilityNodeInfosByViewId(it).firstOrNull()?.let { node -> return node }
        }
        target.contentDescription?.let { desc ->
            return depthFirst(root).firstOrNull { it.contentDescription?.toString() == desc }
        }
        return null
    }

    private fun depthFirst(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(root)
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { yieldAll(depthFirst(it)) }
        }
    }
}
