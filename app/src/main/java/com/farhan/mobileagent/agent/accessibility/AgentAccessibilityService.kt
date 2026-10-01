package com.farhan.mobileagent.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AgentAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observation only in the initial baseline.
    }
    override fun onInterrupt() {
        // Fail closed. No automatic fallback action.
    }
}
