package com.farhan.mobileagent.agent.model

enum class ActionType {
    OBSERVE,
    TAP,
    SWIPE,
    BACK,
    HOME,
    RECENTS,
    TYPE_TEXT,
    OPEN_APP,
    OPEN_SETTINGS,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE
}

enum class Decision { ALLOW, REQUIRE_CONFIRMATION, DENY }

enum class ConfirmationLevel { NONE, SENSITIVE, ALWAYS }

data class TargetSpec(
    val text: String? = null,
    val resourceId: String? = null,
    val contentDescription: String? = null
)

data class GestureSpec(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long = 500L
)

data class ActionRequest(
    val type: ActionType,
    val target: TargetSpec? = null,
    val text: String? = null,
    val packageName: String? = null,
    val gesture: GestureSpec? = null,
    val reason: String = "",
    val requiresConfirmation: Boolean = true,
    val confirmed: Boolean = false
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val decision: Decision,
    val elapsedMs: Long = 0L
)
