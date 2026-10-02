package com.farhan.mobileagent.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.farhan.mobileagent.agent.audit.AuditLog
import com.farhan.mobileagent.agent.model.*
import com.farhan.mobileagent.agent.policy.ApprovedAction
import com.farhan.mobileagent.agent.security.ActionGuard
import com.farhan.mobileagent.agent.security.SecurityKernel

class ActionExecutor(
    private val service: AccessibilityService,
    private val kernel: SecurityKernel,
    private val guard: ActionGuard,
    private val audit: AuditLog
) {
    fun execute(request: ActionRequest): ActionResult =
        executeAuthorized(request, kernel.evaluate(request))

    fun executeUserApproved(approved: ApprovedAction): ActionResult =
        executeAuthorized(approved.request, kernel.evaluateApproved(approved.request))

    fun executeBackground(request: ActionRequest): ActionResult =
        executeAuthorized(request, kernel.evaluateBackground(request))

    private fun executeAuthorized(
        request: ActionRequest,
        decision: Decision
    ): ActionResult {
        val start = SystemClock.elapsedRealtime()
        if (decision != Decision.ALLOW) {
            audit.record(request, decision.name, "Blocked by security policy", false)
            return ActionResult(false, "Action blocked: " + decision.name, decision, elapsed(start))
        }
        if (!guard.check()) {
            audit.record(request, Decision.DENY.name, guard.classifyLoop(request), false)
            return ActionResult(false, "Action rate-limited", Decision.DENY, elapsed(start))
        }

        val ok = runCatching {
            when (request.type) {
                ActionType.OBSERVE -> true
                ActionType.BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                ActionType.HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                ActionType.RECENTS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                ActionType.TAP -> tap(request.target)
                ActionType.TYPE_TEXT -> typeText(request.target, request.text.orEmpty())
                ActionType.SWIPE -> swipe(request.gesture)
                ActionType.OPEN_APP -> openApp(request.packageName)
                ActionType.OPEN_SETTINGS -> openSettings()
                ActionType.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
                ActionType.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
                ActionType.MUTE -> adjustVolume(AudioManager.ADJUST_MUTE)
            }
        }.getOrDefault(false)

        val message = if (ok) "Action completed" else "Action failed"
        audit.record(request, decision.name, message, ok)
        return ActionResult(ok, message, decision, elapsed(start))
    }

    private fun tap(target: TargetSpec?): Boolean {
        if (target == null) return false
        val node = NodeFinder.find(service.rootInActiveWindow, target) ?: return false
        val clickable = if (node.isClickable) node else findClickableAncestor(node)
        return clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        repeat(8) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    private fun typeText(target: TargetSpec?, text: String): Boolean {
        if (target == null || text.isEmpty()) return false
        val node = NodeFinder.find(service.rootInActiveWindow, target) ?: return false

        // Never inject text into fields Android identifies as password fields.
        // This remains blocked even after the user approves the action.
        if (node.isPassword) return false

        return node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
        )
    }

    private fun swipe(spec: GestureSpec?): Boolean {
        if (spec == null || spec.durationMs !in 100L..3000L) return false
        val path = Path().apply {
            moveTo(spec.startX, spec.startY)
            lineTo(spec.endX, spec.endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, spec.durationMs))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    private fun openApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val launchIntent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(launchIntent)
        return true
    }

    private fun openSettings(): Boolean {
        service.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun adjustVolume(direction: Int): Boolean {
        val audio = service.getSystemService(AudioManager::class.java) ?: return false
        audio.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
        return true
    }

    private fun elapsed(start: Long) = SystemClock.elapsedRealtime() - start
}
