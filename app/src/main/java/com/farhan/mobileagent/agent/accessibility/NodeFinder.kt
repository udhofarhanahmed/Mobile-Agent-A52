package com.farhan.mobileagent.agent.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.farhan.mobileagent.agent.model.TargetSpec

object NodeFinder {
    fun find(root: AccessibilityNodeInfo?, target: TargetSpec): AccessibilityNodeInfo? {
        if (root == null) return null

        target.text?.let { text ->
            root.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { node ->
                return clickableSelfOrAncestor(node) ?: node
            }
        }
        target.resourceId?.let { resourceId ->
            root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull()?.let { node ->
                return clickableSelfOrAncestor(node) ?: node
            }
        }
        target.contentDescription?.let { desc ->
            return depthFirst(root)
                .firstOrNull { it.contentDescription?.toString() == desc }
                ?.let { clickableSelfOrAncestor(it) ?: it }
        }
        return null
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(MAX_ANCESTORS) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    private fun depthFirst(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(root)
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { yieldAll(depthFirst(it)) }
        }
    }

    private const val MAX_ANCESTORS = 8
}
