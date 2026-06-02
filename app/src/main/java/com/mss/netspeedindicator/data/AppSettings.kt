package com.mss.netspeedindicator.data

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var isRealTimeEnabled: Boolean
        get() = prefs.getBoolean("real_time_enabled", false)
        set(value) = prefs.edit().putBoolean("real_time_enabled", value).apply()

    var isDailyUsageEnabled: Boolean
        get() = prefs.getBoolean("daily_usage_enabled", false)
        set(value) = prefs.edit().putBoolean("daily_usage_enabled", value).apply()
        
    // Usage stats persistence (simplified for today)
    var lastResetDate: Long
        get() = prefs.getLong("last_reset_date", 0L)
        set(value) = prefs.edit().putLong("last_reset_date", value).apply()

    var baseMobileRx: Long
        get() = prefs.getLong("base_mobile_rx", 0L)
        set(value) = prefs.edit().putLong("base_mobile_rx", value).apply()
        
    var baseMobileTx: Long
        get() = prefs.getLong("base_mobile_tx", 0L)
        set(value) = prefs.edit().putLong("base_mobile_tx", value).apply()

    var baseWifiRx: Long
        get() = prefs.getLong("base_wifi_rx", 0L)
        set(value) = prefs.edit().putLong("base_wifi_rx", value).apply()

    var baseWifiTx: Long
        get() = prefs.getLong("base_wifi_tx", 0L)
        set(value) = prefs.edit().putLong("base_wifi_tx", value).apply()

    var thresholdValue: Float
        get() = prefs.getFloat("threshold_value", 0f)
        set(value) = prefs.edit().putFloat("threshold_value", value).apply()

    var thresholdUnit: String
        get() = prefs.getString("threshold_unit", "KB/s") ?: "KB/s"
        set(value) = prefs.edit().putString("threshold_unit", value).apply()
}
