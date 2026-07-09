package com.nixieclock.model

/**
 * Информация о BLE-устройстве Nixie Clock, полученная при сканировании.
 */
data class NixieDevice(
    /** MAC-адрес устройства */
    val address: String,
    /** Имя устройства (всегда "Nixie Clock") */
    val name: String,
    /** Уровень сигнала в dBm */
    val rssi: Int,
)

/**
 * Состояние подключения к ESP32.
 */
sealed interface ConnectionState {
    /** Не подключены */
    data object Disconnected : ConnectionState
    /** Идёт подключение */
    data object Connecting : ConnectionState
    /** Подключены к устройству */
    data class Connected(val device: NixieDevice) : ConnectionState
}

/**
 * Полный статус часов от команды `get_status`.
 */
data class ClockStatus(
    val wifi: String,
    val ssid: String?,
    val ntp: String,
    val rtc: String,
    val timezone: Int,
    val format: String,
    val lamps: Int,
    val brightness: Int = 0,
    val localTime: String?,
)

/**
 * Событие от ESP32 — либо ответ на команду, либо асинхронное событие.
 */
sealed interface ClockEvent {
    /** Ответ на команду ({"event":"response", ...}) */
    data class CommandResponse(val cmd: String, val status: String, val data: Map<String, Any?>) :
        ClockEvent

    /** Асинхронное событие ({"event":"wifi", ...}) */
    data class AsyncEvent(val type: String, val data: Map<String, Any?>) : ClockEvent

    /** Событие OTA ({"event":"ota", "status":"progress", "percent":45}) */
    data class OtaProgress(val status: String, val percent: Int?) : ClockEvent

    /** Необработанный JSON */
    data class Raw(val json: String) : ClockEvent
}

/**
 * Манифест новой прошивки с мастер-сервера.
 */
data class FirmwareManifest(
    val latestVersion: String,
    val minimumVersion: String,
    val firmwareUrl: String,
    val releaseNotes: String,
    val publishedAt: String,
)

/**
 * Результат проверки обновлений.
 */
sealed interface UpdateCheckResult {
    /** Новая версия найдена */
    data class Available(val manifest: FirmwareManifest) : UpdateCheckResult
    /** Текущая версия актуальна */
    data object UpToDate : UpdateCheckResult
    /** Ошибка при проверке */
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Строка в логе событий приложения.
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val type: LogType = LogType.INFO,
)

enum class LogType { INFO, SUCCESS, WARNING, ERROR }
