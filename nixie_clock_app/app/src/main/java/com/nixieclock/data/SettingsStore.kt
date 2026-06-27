package com.nixieclock.data

import android.content.Context
import android.content.SharedPreferences
import com.nixieclock.util.Config

/**
 * Хранилище локальных настроек приложения (SharedPreferences).
 *
 * Сохраняет последние введённые пользователем значения, чтобы
 * не приходилось заполнять их заново при каждом подключении.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)

    // ── OTA URL ──────────────────────────────────────────────────
    var lastOtaUrl: String
        get() = prefs.getString(Config.KEY_LAST_OTA_URL, "") ?: ""
        set(value) = prefs.edit().putString(Config.KEY_LAST_OTA_URL, value).apply()

    // ── Часовой пояс ─────────────────────────────────────────────
    var lastTimezone: Int
        get() = prefs.getInt(Config.KEY_LAST_TIMEZONE, 3)
        set(value) = prefs.edit().putInt(Config.KEY_LAST_TIMEZONE, value).apply()

    // ── Формат 12/24ч ────────────────────────────────────────────
    var lastFormat12h: Boolean
        get() = prefs.getBoolean(Config.KEY_LAST_FORMAT_12H, false)
        set(value) = prefs.edit().putBoolean(Config.KEY_LAST_FORMAT_12H, value).apply()

    /** Очистить все сохранённые настройки */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
