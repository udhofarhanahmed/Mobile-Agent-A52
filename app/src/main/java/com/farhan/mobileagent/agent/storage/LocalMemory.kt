package com.farhan.mobileagent.agent.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalMemory(context: Context) {
    private val prefs = context.getSharedPreferences("agent_memory", Context.MODE_PRIVATE)

    @Synchronized
    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)

    @Synchronized
    fun appendEvent(type: String, value: String) {
        val array = JSONArray(prefs.getString("events", "[]"))
        array.put(JSONObject().apply {
            put("type", type)
            put("value", value)
            put("at", System.currentTimeMillis())
        })
        while (array.length() > 200) array.remove(0)
        prefs.edit().putString("events", array.toString()).apply()
    }

    fun events(): List<Pair<String, String>> {
        val array = JSONArray(prefs.getString("events", "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(item.optString("type") to item.optString("value"))
            }
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
