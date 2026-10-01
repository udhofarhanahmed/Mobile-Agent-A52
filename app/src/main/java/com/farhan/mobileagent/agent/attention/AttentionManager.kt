package com.farhan.mobileagent.agent.attention

import android.content.Context
import com.farhan.mobileagent.agent.storage.LocalMemory
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

enum class AttentionKind { TASK, REMINDER, FOLLOW_UP, NOTE, ALERT }

data class AttentionItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val detail: String = "",
    val kind: AttentionKind = AttentionKind.TASK,
    val priority: Int = 50,
    val dueAt: Long? = null,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val completed: Boolean = false
)

class AttentionManager(context: Context? = null) {
    private val items = CopyOnWriteArrayList<AttentionItem>()
    private val memory = context?.let(::LocalMemory)

    init { load() }

    @Synchronized
    fun add(item: AttentionItem) {
        items += item
        trimToLimit()
        save()
    }

    @Synchronized
    fun upsert(item: AttentionItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item else items += item
        trimToLimit()
        save()
    }

    fun all(): List<AttentionItem> = items.sortedWith(
        compareByDescending<AttentionItem> { it.priority }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }
    )

    fun pending(): List<AttentionItem> = all().filterNot { it.completed }

    @Synchronized
    fun complete(id: String) {
        items.replaceAll { if (it.id == id) it.copy(completed = true) else it }
        save()
    }

    @Synchronized
    fun delete(id: String) {
        items.removeIf { it.id == id }
        save()
    }

    @Synchronized
    private fun save() {
        memory?.put("attention_items", JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title.take(MAX_TEXT))
                    put("detail", item.detail.take(MAX_TEXT))
                    put("kind", item.kind.name)
                    put("priority", item.priority.coerceIn(0, 100))
                    put("dueAt", item.dueAt ?: JSONObject.NULL)
                    put("createdAt", item.createdAt)
                    put("completed", item.completed)
                })
            }
        }.toString())
    }

    private fun trimToLimit() {
        if (items.size <= MAX_ITEMS) return
        val keep = items.sortedWith(
            compareByDescending<AttentionItem> { !it.completed }
                .thenByDescending { it.priority }
                .thenBy { it.createdAt }
        ).take(MAX_ITEMS)
        items.clear()
        items.addAll(keep)
    }

    private fun load() {
        val raw = memory?.get("attention_items") ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                items += AttentionItem(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = o.optString("title").take(MAX_TEXT),
                    detail = o.optString("detail").take(MAX_TEXT),
                    kind = runCatching { AttentionKind.valueOf(o.optString("kind")) }.getOrDefault(AttentionKind.TASK),
                    priority = o.optInt("priority", 50).coerceIn(0, 100),
                    dueAt = if (o.isNull("dueAt")) null else o.optLong("dueAt"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    completed = o.optBoolean("completed", false)
                )
            }
            trimToLimit()
        }
    }

    private companion object {
        const val MAX_ITEMS = 500
        const val MAX_TEXT = 500
    }
}
