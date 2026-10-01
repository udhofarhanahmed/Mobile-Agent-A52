package com.farhan.mobileagent.agent.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("agent_notes", Context.MODE_PRIVATE)

    @Synchronized
    fun add(content: String): AgentNote? {
        val clean = content.trim()
        if (clean.isEmpty()) return null

        val note = AgentNote(
            id = UUID.randomUUID().toString(),
            content = clean,
            createdAt = System.currentTimeMillis()
        )
        val array = JSONArray(prefs.getString(KEY_NOTES, "[]"))
        array.put(note.toJson())
        while (array.length() > MAX_NOTES) array.remove(0)
        prefs.edit().putString(KEY_NOTES, array.toString()).apply()
        return note
    }

    fun all(): List<AgentNote> {
        val array = JSONArray(prefs.getString(KEY_NOTES, "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                add(agentNoteFromJson(array.getJSONObject(i)))
            }
        }.sortedByDescending { it.createdAt }
    }

    fun search(query: String): List<AgentNote> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all()
        return all().filter { it.content.lowercase().contains(q) }
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val array = JSONArray(prefs.getString(KEY_NOTES, "[]"))
        var removed = false
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            if (item.optString("id") == id) {
                removed = true
            } else {
                kept.put(item)
            }
        }
        if (removed) {
            prefs.edit().putString(KEY_NOTES, kept.toString()).apply()
        }
        return removed
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_NOTES).apply()
    }

    private fun AgentNote.toJson() = JSONObject().apply {
        put("id", id)
        put("content", content)
        put("createdAt", createdAt)
    }

    private companion object {
        const val KEY_NOTES = "notes"
        const val MAX_NOTES = 500
    }
}

private fun agentNoteFromJson(obj: JSONObject): AgentNote = AgentNote(
    id = obj.optString("id"),
    content = obj.optString("content"),
    createdAt = obj.optLong("createdAt")
)
 
