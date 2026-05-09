package com.internetspeed.meterlite.core.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SHOW_IN_BITS = "show_in_bits"
        const val KEY_START_ON_BOOT = "start_on_boot"
        const val KEY_NOTIF_PRIORITY = "notif_priority"
        const val KEY_DATA_PRECISION = "data_precision"
        const val KEY_BG_ACTIVITY = "bg_activity"
        const val KEY_USAGE_ALERT = "usage_alert"
        const val KEY_APP_THEME = "app_theme"

        const val THEME_DARK = 0
        const val THEME_LIGHT = 1
        const val THEME_MATERIAL_YOU = 2
    }

    var showInBits: Boolean
        get() = prefs.getBoolean(KEY_SHOW_IN_BITS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_IN_BITS, value).apply()

    var startOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    var notificationPriority: Int
        get() = prefs.getInt(KEY_NOTIF_PRIORITY, 0) // 0: Low, 1: High
        set(value) = prefs.edit().putInt(KEY_NOTIF_PRIORITY, value).apply()

    /** 1 or 2 decimal places. Stored as an Int. */
    var dataPrecision: Int
        get() = prefs.getInt(KEY_DATA_PRECISION, 2)
        set(value) = prefs.edit().putInt(KEY_DATA_PRECISION, value).apply()

    var backgroundActivity: Boolean
        get() = prefs.getBoolean(KEY_BG_ACTIVITY, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_ACTIVITY, value).apply()

    var dailyUsageAlert: String
        get() = prefs.getString(KEY_USAGE_ALERT, "Off") ?: "Off"
        set(value) = prefs.edit().putString(KEY_USAGE_ALERT, value).apply()

    /** 0=Dark, 1=Light, 2=Material You */
    var appTheme: Int
        get() = prefs.getInt(KEY_APP_THEME, THEME_DARK)
        set(value) = prefs.edit().putInt(KEY_APP_THEME, value).apply()
}
