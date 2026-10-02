package com.farhan.mobileagent.agent.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.farhan.mobileagent.agent.model.TargetSpec

object NodeFinder {
    fun find(root: AccessibilityNodeInfo?, target: TargetSpec): AccessibilityNodeInfo? {
        if (root == null) return null

        target.text?.let { text ->
            root.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { node ->
                return node
            }
        }
        target.resourceId?.let { resourceId ->
            root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull()?.let { node ->
                return clickableSelfOrAncestor(node) ?: node
            }
        }
        target.contentDescription?.let { desc ->
            return findByContentDescription(root, desc)
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

    private fun findByContentDescription(
        root: AccessibilityNodeInfo,
        description: String
    ): AccessibilityNodeInfo? {
        var visited = 0

        fun walk(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (visited++ >= MAX_NODES) return null
            if (node.contentDescription?.toString() == description) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)?.let { return it }
            }
            return null
        }

        return walk(root)
    }

    private const val MAX_ANCESTORS = 8
    private const val MAX_NODES = 1_000
}
