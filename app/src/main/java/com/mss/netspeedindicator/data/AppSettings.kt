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

    var isMasterEnabled: Boolean
        get() = prefs.getBoolean("master_enabled", true)
        set(value) = prefs.edit().putBoolean("master_enabled", value).apply()
        
    // Usage stats persistence
    var lastResetDate: Long
        get() = prefs.getLong("last_reset_date", 0L)
        set(value) = prefs.edit().putLong("last_reset_date", value).apply()

    var dailyMobileRx: Long
        get() = prefs.getLong("daily_mobile_rx", 0L)
        set(value) = prefs.edit().putLong("daily_mobile_rx", value).apply()
        
    var dailyMobileTx: Long
        get() = prefs.getLong("daily_mobile_tx", 0L)
        set(value) = prefs.edit().putLong("daily_mobile_tx", value).apply()

    var dailyWifiRx: Long
        get() = prefs.getLong("daily_wifi_rx", 0L)
        set(value) = prefs.edit().putLong("daily_wifi_rx", value).apply()

    var dailyWifiTx: Long
        get() = prefs.getLong("daily_wifi_tx", 0L)
        set(value) = prefs.edit().putLong("daily_wifi_tx", value).apply()

    var thresholdValue: Float
        get() = prefs.getFloat("threshold_value", 0f)
        set(value) = prefs.edit().putFloat("threshold_value", value).apply()

    var thresholdUnit: String
        get() = prefs.getString("threshold_unit", "KB/s") ?: "KB/s"
        set(value) = prefs.edit().putString("threshold_unit", value).apply()

    var updateInterval: Long
        get() = prefs.getLong("update_interval", 1000L)
        set(value) = prefs.edit().putLong("update_interval", value).apply()

    var isStatsEnabled: Boolean
        get() = prefs.getBoolean("stats_enabled", false)
        set(value) = prefs.edit().putBoolean("stats_enabled", value).apply()
}
