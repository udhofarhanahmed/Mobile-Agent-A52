package com.farhan.mobileagent.agent.notification

import android.content.Context
import com.farhan.mobileagent.agent.storage.LocalMemory
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NotificationObservation(
    val key: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appLabel: String,
    val channelId: String? = null,
    val category: String? = null,
    val postedAt: Long = System.currentTimeMillis()
)

object NotificationPolicy {
    fun shouldObserve(sourcePackage: String, ownPackage: String, isOngoing: Boolean): Boolean =
        sourcePackage != ownPackage && !isOngoing
}

object NotificationAttentionPolicy {
    const val PRIORITY = 70
    fun attentionId(notificationKey: String): String = "notification:$notificationKey"
    fun title(appLabel: String): String = "Notification from $appLabel"
}

class NotificationInbox(context: Context) {
    private val memory = LocalMemory(context.applicationContext)
    private val key = "notification_inbox"

    @Synchronized
    fun record(observation: NotificationObservation) {
        val current = latest().filterNot { it.key == observation.key }.toMutableList()
        current.add(0, observation)
        memory.put(key, JSONArray().apply {
            current.take(MAX_ENTRIES).forEach { put(encode(it)) }
        }.toString())
    }

    fun latest(): List<NotificationObservation> = runCatching {
        val raw = memory.get(key) ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) add(decode(array.getJSONObject(i)))
        }
    }.getOrElse { emptyList() }

    @Synchronized
    fun clear() {
        memory.put(key, "[]")
    }

    private fun encode(item: NotificationObservation) = JSONObject().apply {
        put("key", item.key)
        put("package", item.packageName)
        put("appLabel", item.appLabel)
        put("channelId", item.channelId ?: JSONObject.NULL)
        put("category", item.category ?: JSONObject.NULL)
        put("postedAt", item.postedAt)
    }

    private fun decode(o: JSONObject) = NotificationObservation(
        key = o.optString("key").ifBlank { UUID.randomUUID().toString() },
        packageName = o.optString("package"),
        appLabel = o.optString("appLabel"),
        channelId = o.optString("channelId").takeIf { it.isNotBlank() && it != "null" },
        category = o.optString("category").takeIf { it.isNotBlank() && it != "null" },
        postedAt = o.optLong("postedAt", System.currentTimeMillis())
    )

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
