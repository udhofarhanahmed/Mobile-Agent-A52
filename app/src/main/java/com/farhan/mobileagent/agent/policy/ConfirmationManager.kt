package com.farhan.mobileagent.agent.policy

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.ConfirmationLevel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ApprovedAction internal constructor(
    val request: ActionRequest
)

class ConfirmationManager {
    private data class PendingEntry(
        val request: ActionRequest,
        val createdAtMs: Long
    )

    private val pending = ConcurrentHashMap<String, PendingEntry>()

    fun request(request: ActionRequest): String {
        val id = UUID.randomUUID().toString()
        pending[id] = PendingEntry(
            request = request.copy(confirmed = false),
            createdAtMs = System.currentTimeMillis()
        )
        return id
    }

    fun approve(id: String): ApprovedAction? {
        val entry = pending.remove(id) ?: return null
        val age = System.currentTimeMillis() - entry.createdAtMs
        if (age < 0L || age > MAX_PENDING_AGE_MS) return null
        return ApprovedAction(entry.request)
    }

    fun reject(id: String): ActionRequest? = pending.remove(id)?.request
    fun hasPending(): Boolean = pending.isNotEmpty()

    private companion object {
        const val MAX_PENDING_AGE_MS = 5L * 60L * 1000L
    }

    fun levelFor(request: ActionRequest): ConfirmationLevel =
        when (request.type) {
            ActionType.TYPE_TEXT -> ConfirmationLevel.ALWAYS
            ActionType.TAP,
            ActionType.SWIPE,
            ActionType.BACK,
            ActionType.HOME,
            ActionType.RECENTS,
            ActionType.OPEN_APP,
            ActionType.OPEN_SETTINGS,
            ActionType.VOLUME_UP,
            ActionType.VOLUME_DOWN,
            ActionType.MUTE -> ConfirmationLevel.SENSITIVE
            ActionType.OBSERVE -> ConfirmationLevel.NONE
        }
}
