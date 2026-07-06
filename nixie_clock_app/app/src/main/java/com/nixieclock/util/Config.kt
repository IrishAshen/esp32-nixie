package com.nixieclock.util

/**
 * Константы приложения.
 *
 * BLE UUIDs должны соответствовать nixie_clock/src/config.h в прошивке ESP32.
 */
object Config {

    // ── BLE ───────────────────────────────────────────────────────
    /** UUID сервиса GATT на ESP32 */
    const val BLE_SERVICE_UUID = "e20a39f4-73f5-4bc4-a12f-17d1ad07a961"
    /** TX характеристика (ESP32 → телефон, Notify) */
    const val BLE_TX_UUID = "e20a39f5-73f5-4bc4-a12f-17d1ad07a961"
    /** RX характеристика (телефон → ESP32, Write) */
    const val BLE_RX_UUID = "e20a39f6-73f5-4bc4-a12f-17d1ad07a961"

    /** Имя BLE-устройства (должно совпадать с прошивкой) */
    const val DEVICE_NAME = "Nixie Clock"

    /** Таймаут подключения BLE (мс) */
    const val BLE_CONNECT_TIMEOUT_MS = 10_000L

    // ── Master-сервер (заглушка) ─────────────────────────────────
    /** URL до JSON-манифеста с информацией о новой прошивке */
    const val FIRMWARE_MANIFEST_URL =
        "https://api.nixie-clock.example.com/firmware/manifest.json"

    // ── SharedPreferences ────────────────────────────────────────
    const val PREFS_NAME = "nixie_clock_prefs"
    const val KEY_LAST_OTA_URL = "last_ota_url"
    const val KEY_LAST_TIMEZONE = "last_timezone"
    const val KEY_LAST_FORMAT_12H = "last_format_12h"
    const val KEY_LAST_BRIGHTNESS = "last_brightness"
}
