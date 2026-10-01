package com.farhan.mobileagent.agent.device

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager

data class DeviceState(
    val batteryPercent: Int,
    val charging: Boolean,
    val interactive: Boolean
)

class DeviceStateProvider(context: Context) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun snapshot(): DeviceState {
        val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return DeviceState(
            batteryPercent = capacity.coerceIn(0, 100),
            charging = batteryManager.isCharging,
            interactive = powerManager?.isInteractive == true
        )
    }
}
