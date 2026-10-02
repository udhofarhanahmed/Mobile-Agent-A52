package com.farhan.mobileagent.agent.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NoteStore(context: Context) {
    private val memory = LocalMemory(context.applicationContext)
    private val legacyPrefs =
        context.applicationContext.getSharedPreferences("agent_notes", Context.MODE_PRIVATE)
    private val key = "notes"

    init {
        migrateLegacyNotes()
    }

    @Synchronized
    fun add(content: String): AgentNote? {
        val clean = content.trim()
        if (clean.isEmpty()) return null

        val note = AgentNote(
            id = UUID.randomUUID().toString(),
            content = clean,
            createdAt = System.currentTimeMillis()
        )
        val array = read()
        array.put(note.toJson())
        while (array.length() > MAX_NOTES) array.remove(0)
        write(array)
        return note
    }

    fun all(): List<AgentNote> =
        read().toListOfNotes().sortedByDescending { it.createdAt }

    fun search(query: String): List<AgentNote> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all()
        return all().filter { it.content.lowercase().contains(q) }
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val array = read()
        var removed = false
        val kept = JSONArray()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("id") == id) {
                removed = true
            } else {
                kept.put(item)
            }
        }

        if (removed) write(kept)
        return removed
    }

    @Synchronized
    fun clear() {
        memory.put(key, "[]")
    }

    private fun read(): JSONArray = runCatching {
        JSONArray(memory.get(key) ?: "[]")
    }.getOrElse { JSONArray() }

    private fun write(array: JSONArray) {
        memory.put(key, array.toString())
    }

    private fun migrateLegacyNotes() {
        if (memory.get(key) != null) {
            legacyPrefs.edit().clear().apply()
            return
        }

        val legacy = legacyPrefs.getString(KEY_NOTES, null) ?: return
        runCatching {
            val array = JSONArray(legacy)
            val bounded = JSONArray()
            val start = (array.length() - MAX_NOTES).coerceAtLeast(0)
            for (i in start until array.length()) {
                array.optJSONObject(i)?.let(bounded::put)
            }
            memory.put(key, bounded.toString())
            legacyPrefs.edit().clear().commit()
        }
    }

    private fun AgentNote.toJson() = JSONObject().apply {
        put("id", id)
        put("content", content)
        put("createdAt", createdAt)
    }

    private fun JSONArray.toListOfNotes(): List<AgentNote> =
        buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { obj ->
                    add(
                        AgentNote(
                            id = obj.optString("id"),
                            content = obj.optString("content"),
                            createdAt = obj.optLong("createdAt")
                        )
                    )
                }
            }
        }

    private companion object {
        const val KEY_NOTES = "notes"
        const val MAX_NOTES = 500
    }
}
