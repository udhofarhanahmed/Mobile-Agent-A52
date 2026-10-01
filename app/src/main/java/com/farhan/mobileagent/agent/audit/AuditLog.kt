package com.farhan.mobileagent.agent.audit

import android.content.Context
import com.farhan.mobileagent.agent.model.ActionRequest
import com.farhan.mobileagent.agent.storage.LocalMemory
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

data class AuditEvent(
    val timestamp: String = Instant.now().toString(),
    val action: String,
    val decision: String,
    val detail: String = "",
    val success: Boolean? = null
)

class AuditLog {
    private val events = CopyOnWriteArrayList<AuditEvent>()
    @Volatile private var memory: LocalMemory? = null
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val m = LocalMemory(context.applicationContext)
            memory = m
            load(m)
            initialized = true
        }
    }

    @Synchronized
    fun record(event: AuditEvent) {
        events += event
        persist()
    }

    fun record(
        request: ActionRequest,
        decision: String,
        detail: String,
        success: Boolean? = null
    ) = record(
        AuditEvent(
            action = request.type.name,
            decision = decision,
            detail = detail,
            success = success
        )
    )

    fun snapshot(): List<AuditEvent> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
        memory?.put(KEY, "[]")
    }

    private fun load(store: LocalMemory) {
        events.clear()
        runCatching {
            val raw = store.get(KEY) ?: return
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                events += AuditEvent(
                    timestamp = o.optString("timestamp", Instant.now().toString()),
                    action = o.optString("action"),
                    decision = o.optString("decision"),
                    detail = o.optString("detail"),
                    success = when {
                        o.isNull("success") -> null
                        else -> o.optBoolean("success")
                    }
                )
            }
        }
    }

    private fun persist() {
        val store = memory ?: return
        val bounded = events.takeLast(MAX_EVENTS)
        if (events.size > MAX_EVENTS) {
            events.clear()
            events.addAll(bounded)
        }
        store.put(KEY, JSONArray().apply {
            bounded.forEach { event ->
                put(JSONObject().apply {
                    put("timestamp", event.timestamp)
                    put("action", event.action)
                    put("decision", event.decision)
                    put("detail", event.detail)
                    put("success", event.success ?: JSONObject.NULL)
                })
            }
        }.toString())
    }

    private companion object {
        const val KEY = "audit_events"
        const val MAX_EVENTS = 500
    }
}
