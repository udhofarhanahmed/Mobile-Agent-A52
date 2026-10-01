package com.farhan.mobileagent.agent.policy

import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.model.ActionType
import com.farhan.mobileagent.agent.model.ConfirmationLevel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ConfirmationManager {
    private val pending = ConcurrentHashMap<String, ActionRequest>()

    fun request(request: ActionRequest): String {
        val id = UUID.randomUUID().toString()
        pending[id] = request.copy(confirmed = false)
        return id
    }

    fun approve(id: String): ActionRequest? =
        pending.remove(id)?.copy(confirmed = true)

    fun reject(id: String): ActionRequest? = pending.remove(id)
    fun hasPending(): Boolean = pending.isNotEmpty()

    fun levelFor(request: ActionRequest): ConfirmationLevel =
        when (request.type) {
            ActionType.TYPE_TEXT -> ConfirmationLevel.ALWAYS
            ActionType.TAP,
            ActionType.SWIPE,
            ActionType.BACK,
            ActionType.HOME,
            ActionType.RECENTS,
            ActionType.OPEN_SETTINGS,
            ActionType.VOLUME_UP,
            ActionType.VOLUME_DOWN,
            ActionType.MUTE -> ConfirmationLevel.SENSITIVE
            ActionType.OBSERVE -> ConfirmationLevel.NONE
        }
}
