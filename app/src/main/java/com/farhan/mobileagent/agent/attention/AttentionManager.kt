package com.farhan.mobileagent.agent.attention

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

class AttentionManager {
    private val items = CopyOnWriteArrayList<AttentionItem>()
    fun add(item: AttentionItem) { items += item }
    fun all(): List<AttentionItem> = items.sortedByDescending { it.priority }
    fun pending(): List<AttentionItem> = all().filterNot { it.completed }
    fun complete(id: String) {
        items.replaceAll { if (it.id == id) it.copy(completed = true) else it }
    }
}
