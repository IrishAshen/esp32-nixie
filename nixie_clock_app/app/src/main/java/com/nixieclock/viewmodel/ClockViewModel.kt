package com.nixieclock.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.nixieclock.ble.BLEManager
import com.nixieclock.data.SettingsStore
import com.nixieclock.data.UpdateChecker
import com.nixieclock.model.ClockEvent
import com.nixieclock.model.ClockStatus
import com.nixieclock.model.ConnectionState
import com.nixieclock.model.LogEntry
import com.nixieclock.model.LogType
import com.nixieclock.model.NixieDevice
import com.nixieclock.model.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Единый ViewModel для всего приложения Nixie Clock.
 *
 * Управляет BLE-сканированием, подключением, отправкой команд,
 * логом событий и проверкой обновлений прошивки.
 */
class ClockViewModel(
    private val bleManager: BLEManager,
    private val settingsStore: SettingsStore,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val gson = Gson()

    // ── Сканирование ─────────────────────────────────────────────

    private val _scanResults = MutableStateFlow<List<NixieDevice>>(emptyList())
    val scanResults: StateFlow<List<NixieDevice>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // ── Подключение ──────────────────────────────────────────────

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── Лог событий ──────────────────────────────────────────────

    private val _eventLog = MutableStateFlow<List<LogEntry>>(emptyList())
    val eventLog: StateFlow<List<LogEntry>> = _eventLog.asStateFlow()

    // ── Статус часов ─────────────────────────────────────────────

    private val _clockStatus = MutableStateFlow<ClockStatus?>(null)
    val clockStatus: StateFlow<ClockStatus?> = _clockStatus.asStateFlow()

    private val _clockVersion = MutableStateFlow<Map<String, Any?>?>(null)
    val clockVersion: StateFlow<Map<String, Any?>?> = _clockVersion.asStateFlow()

    // ── Проверка обновлений ──────────────────────────────────────

    private val _updateResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateResult: StateFlow<UpdateCheckResult?> = _updateResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    // ── Флаг готовности (найден сервис, можно слать команды) ────

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // ── Вспомогательное ──────────────────────────────────────────

    /** Отфильтрованный набор адресов для дедупликации сканирования */
    private val seenAddresses = mutableSetOf<String>()
    /** Максимальное число устройств в списке сканирования */
    private companion object {
        const val MAX_SCAN_RESULTS = 20
    }

    init {
        // Отлавливаем отключение BLE
        bleManager.onDisconnectedCallback = ::onBleDisconnected
        bleManager.onDataCallback = ::onBleData
        bleManager.onErrorCallback = ::addErrorLog
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLE Scan
    // ═══════════════════════════════════════════════════════════════

    /** Запустить BLE-сканирование (flow собирается в корутине) */
    fun startScan() {
        if (_isScanning.value) return

        seenAddresses.clear()
        _scanResults.value = emptyList()
        _isScanning.value = true
        _connectionState.value = ConnectionState.Disconnected
        addLog("Scanning for Nixie Clock...", LogType.INFO)

        viewModelScope.launch {
            bleManager.scan().collect { device ->
                // Дедупликация
                if (seenAddresses.add(device.address) &&
                    _scanResults.value.size < MAX_SCAN_RESULTS
                ) {
                    _scanResults.update { current -> current + device }
                }
            }
        }
    }

    /** Остановить BLE-сканирование */
    fun stopScan() {
        _isScanning.value = false
        bleManager.stopScan()
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLE Connect / Disconnect
    // ═══════════════════════════════════════════════════════════════

    /** Подключиться к выбранному устройству */
    fun connectTo(device: NixieDevice) {
        if (_connectionState.value is ConnectionState.Connected) return

        _connectionState.value = ConnectionState.Connecting
        _isScanning.value = false
        bleManager.stopScan()
        addLog("Connecting to ${device.name} (${device.address})...", LogType.INFO)

        viewModelScope.launch {
            val result = bleManager.connect(device.address)
            if (result.isSuccess) {
                _connectionState.value = ConnectionState.Connected(device)
                addLog("Connected to ${device.name} ${device.address}", LogType.SUCCESS)
                _isReady.value = true
                // Автоматически запрашиваем статус при подключении
                getStatus()
                getVersion()
            } else {
                _connectionState.value = ConnectionState.Disconnected
                addLog(
                    "Connection failed: ${result.exceptionOrNull()?.message}",
                    LogType.ERROR,
                )
                // Возвращаемся к сканированию
                startScan()
            }
        }
    }

    /** Отключиться от устройства */
    fun disconnect() {
        bleManager.disconnect()
        onBleDisconnected()
    }

    private fun onBleDisconnected() {
        _connectionState.value = ConnectionState.Disconnected
        _clockStatus.value = null
        _clockVersion.value = null
        _isReady.value = false
        addLog("Device disconnected", LogType.WARNING)
        startScan()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Commands
    // ═══════════════════════════════════════════════════════════════

    /** Отправить сырую команду */
    fun sendCommand(cmd: String, params: Map<String, Any?> = emptyMap()) {
        if (!_isReady.value) {
            addLog("Not connected — cannot send command", LogType.ERROR)
            return
        }

        val jsonObj = JsonObject().apply {
            addProperty("cmd", cmd)
            params.forEach { (key, value) ->
                when (value) {
                    is String -> addProperty(key, value)
                    is Number -> addProperty(key, value)
                    is Boolean -> addProperty(key, value)
                    else -> addProperty(key, value?.toString() ?: "")
                }
            }
        }
        val json = gson.toJson(jsonObj)
        addLog("> $json", LogType.INFO)
        bleManager.send(json)
    }

    // ── Shortcut-команды ─────────────────────────────────────────

    fun setWifi(ssid: String, password: String) {
        sendCommand("set_wifi", mapOf("ssid" to ssid, "password" to password))
    }

    fun forgetWifi() {
        sendCommand("forget_wifi")
    }

    /** Установить время (Unix timestamp в секундах) */
    fun setTime(timestamp: Long) {
        sendCommand("set_time", mapOf("timestamp" to timestamp))
    }

    fun setTimezone(offset: Int) {
        settingsStore.lastTimezone = offset
        sendCommand("set_timezone", mapOf("offset" to offset))
    }

    fun setFormat(is12h: Boolean) {
        settingsStore.lastFormat12h = is12h
        sendCommand("set_format", mapOf("value" to if (is12h) "12h" else "24h"))
    }

    fun getStatus() {
        sendCommand("get_status")
    }

    fun getVersion() {
        sendCommand("get_version")
    }

    fun startOTA(url: String) {
        settingsStore.lastOtaUrl = url
        sendCommand("ota", mapOf("url" to url))
    }

    fun reboot() {
        sendCommand("reboot")
    }

    fun listCommands() {
        sendCommand("list_commands")
    }

    // ═══════════════════════════════════════════════════════════════
    //  Firmware update check
    // ═══════════════════════════════════════════════════════════════

    /** Проверить наличие новой прошивки на мастер-сервере */
    fun checkFirmwareUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateResult.value = null
            addLog("Checking for firmware updates...", LogType.INFO)

            val result = updateChecker.check()
            _updateResult.value = result

            when (result) {
                is UpdateCheckResult.Available -> addLog(
                    "New firmware v${result.manifest.latestVersion} available!",
                    LogType.SUCCESS,
                )
                is UpdateCheckResult.UpToDate -> addLog(
                    "Firmware is up to date",
                    LogType.INFO,
                )
                is UpdateCheckResult.Error -> addLog(
                    "Update check failed: ${result.message}",
                    LogType.ERROR,
                )
            }
            _isCheckingUpdate.value = false
        }
    }

    /** Сбросить результат проверки обновлений */
    fun clearUpdateResult() {
        _updateResult.value = null
    }

    // ═══════════════════════════════════════════════════════════════
    //  Data processing
    // ═══════════════════════════════════════════════════════════════

    /** Обработка входящих данных из TX-характеристики */
    private fun onBleData(json: String) {
        addLog("< $json", LogType.INFO)
        parseEvent(json)
    }

    /** Парсинг JSON-события от ESP32 */
    private fun parseEvent(json: String) {
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val eventType = obj.get("event")?.asString

            when (eventType) {
                "response" -> {
                    val cmd = obj.get("cmd")?.asString ?: ""
                    val status = obj.get("status")?.asString ?: ""
                    when (cmd) {
                        "get_status" -> parseStatus(obj)
                        "get_version" -> parseVersion(obj)
                    }
                }

                "wifi" -> {
                    val status = obj.get("status")?.asString
                    when (status) {
                        "connected" -> {
                            val ip = obj.get("ip")?.asString ?: "?"
                            addLog("WiFi connected: $ip", LogType.SUCCESS)
                        }
                        "disconnected" -> addLog("WiFi disconnected", LogType.WARNING)
                    }
                }

                "ntp" -> {
                    val status = obj.get("status")?.asString
                    when (status) {
                        "synced" -> addLog("NTP synced", LogType.SUCCESS)
                        "failed" -> {
                            val msg = obj.get("message")?.asString ?: "unknown error"
                            addLog("NTP failed: $msg", LogType.ERROR)
                        }
                    }
                }

                "ota" -> {
                    val status = obj.get("status")?.asString ?: ""
                    val percent = obj.get("percent")?.asInt
                    when (status) {
                        "started" -> addLog("OTA started", LogType.INFO)
                        "progress" -> addLog("OTA progress: $percent%", LogType.INFO)
                        "success" -> addLog("OTA completed — rebooting", LogType.SUCCESS)
                        "failed" -> {
                            val msg = obj.get("message")?.asString ?: "unknown error"
                            addLog("OTA failed: $msg", LogType.ERROR)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            addLog("Parse error: ${e.message}", LogType.ERROR)
        }
    }

    /** Парсинг ответа get_status */
    private fun parseStatus(obj: JsonObject) {
        val state = obj.getAsJsonObject("state") ?: return
        val status = ClockStatus(
            wifi = state.get("wifi")?.asString ?: "?",
            ssid = state.get("ssid")?.asString,
            ntp = state.get("ntp")?.asString ?: "?",
            rtc = state.get("rtc")?.asString ?: "?",
            timezone = state.get("timezone")?.asInt ?: 0,
            format = state.get("format")?.asString ?: "24h",
            lamps = state.get("lamps")?.asInt ?: 0,
            localTime = state.get("local_time")?.asString,
        )
        _clockStatus.value = status
    }

    /** Парсинг ответа get_version */
    private fun parseVersion(obj: JsonObject) {
        val map = mutableMapOf<String, Any?>()
        for (key in obj.keySet()) {
            val el = obj.get(key)
            map[key] = when {
                el.isJsonPrimitive -> (el as JsonElement).asString
                else -> el.toString()
            }
        }
        _clockVersion.value = map
    }

    // ═══════════════════════════════════════════════════════════════
    //  Log
    // ═══════════════════════════════════════════════════════════════

    private fun addLog(text: String, type: LogType = LogType.INFO) {
        _eventLog.update { current ->
            (current + LogEntry(text = text, type = type)).takeLast(200)
        }
    }

    private fun addErrorLog(message: String) {
        addLog(message, LogType.ERROR)
    }

    /** Очистить лог событий */
    fun clearLog() {
        _eventLog.value = emptyList()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        bleManager.close()
    }
}
