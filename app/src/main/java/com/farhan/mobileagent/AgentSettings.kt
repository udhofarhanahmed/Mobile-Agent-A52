package com.farhan.mobileagent

import android.content.Context

enum class AgentThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}

object AgentSettings {
    private const val PREFS = "agent_settings"
    private const val KEY_START_ON_BOOT = "start_on_boot"
    private const val KEY_THEME = "theme"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun startOnBoot(context: Context): Boolean =
        prefs(context).getBoolean(KEY_START_ON_BOOT, true)

    fun setStartOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_START_ON_BOOT, enabled).apply()
    }

    fun themeMode(context: Context): AgentThemeMode = when (
        prefs(context).getString(KEY_THEME, AgentThemeMode.SYSTEM.name)
    ) {
        AgentThemeMode.LIGHT.name -> AgentThemeMode.LIGHT
        AgentThemeMode.DARK.name -> AgentThemeMode.DARK
        else -> AgentThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: AgentThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
    }
}