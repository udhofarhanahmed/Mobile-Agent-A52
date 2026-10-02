package com.farhan.mobileagent.agent.storage

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Small encrypted local key/value store for agent state.
 *
 * Data stays inside the app's sandbox and is additionally protected with an
 * Android Keystore AES-GCM key. A one-time migration preserves data written by
 * older builds that used plaintext SharedPreferences.
 */
class LocalMemory(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs =
        appContext.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)
    private val legacyPrefs =
        appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    init {
        migrateLegacyIfNeeded()
    }

    @Synchronized
    fun put(key: String, value: String) {
        val encrypted = encrypt(value) ?: return
        securePrefs.edit().putString(key, encrypted).apply()
    }

    @Synchronized
    fun get(key: String): String? {
        val encoded = securePrefs.getString(key, null) ?: return null
        return decrypt(encoded)
    }

    @Synchronized
    fun appendEvent(type: String, value: String) {
        val array = runCatching {
            org.json.JSONArray(get(EVENTS_KEY) ?: "[]")
        }.getOrElse { org.json.JSONArray() }

        array.put(org.json.JSONObject().apply {
            put("type", type)
            put("value", value)
            put("at", System.currentTimeMillis())
        })
        while (array.length() > MAX_EVENTS) array.remove(0)
        put(EVENTS_KEY, array.toString())
    }

    fun events(): List<Pair<String, String>> {
        val array = runCatching {
            org.json.JSONArray(get(EVENTS_KEY) ?: "[]")
        }.getOrElse { org.json.JSONArray() }

        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(item.optString("type") to item.optString("value"))
            }
        }
    }

    @Synchronized
    fun clear() {
        securePrefs.edit().clear().commit()
        legacyPrefs.edit().clear().commit()
    }

    private fun migrateLegacyIfNeeded() {
        if (securePrefs.getBoolean(MIGRATION_KEY, false)) return

        val values = legacyPrefs.all
        val editor = securePrefs.edit()
        for ((key, value) in values) {
            if (key == MIGRATION_KEY) continue
            val text = when (value) {
                is String -> value
                null -> continue
                else -> value.toString()
            }
            val encrypted = encrypt(text) ?: return
            editor.putString(key, encrypted)
        }

        // Commit the migration atomically from the app's point of view:
        // do not erase the legacy copy unless every value was encrypted.
        if (!editor.putBoolean(MIGRATION_KEY, true).commit()) return
        legacyPrefs.edit().clear().commit()
    }

    private fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv, Base64.NO_WRAP) +
            "." +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(encoded: String): String? = runCatching {
        val parts = encoded.split('.', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val SECURE_PREFS = "agent_memory_secure"
        const val LEGACY_PREFS = "agent_memory"
        const val EVENTS_KEY = "events"
        const val MIGRATION_KEY = "__secure_memory_migrated"
        const val KEY_ALIAS = "mobile_agent_local_memory_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val MAX_EVENTS = 200
    }
}
