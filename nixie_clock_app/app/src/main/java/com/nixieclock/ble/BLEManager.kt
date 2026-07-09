package com.nixieclock.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.nixieclock.model.NixieDevice
import com.nixieclock.util.Config
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Менеджер BLE для работы с Nixie Clock (ESP32).
 *
 * Чистый Android BLE API без сторонних библиотек.
 * Все BLE-операции обёрнуты в корутины для удобной работы.
 *
 * Использование:
 * ```
 * val manager = BLEManager(context)
 * manager.scan().collect { device -> ... }
 * manager.connect(device.address)
 * manager.send(json)
 * manager.disconnect()
 * ```
 */
class BLEManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceUuid: UUID = UUID.fromString(Config.BLE_SERVICE_UUID)
    private val txUuid: UUID = UUID.fromString(Config.BLE_TX_UUID)
    private val rxUuid: UUID = UUID.fromString(Config.BLE_RX_UUID)
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    /** Адрес текущего подключённого устройства */
    private var connectedAddress: String? = null

    private val isScanning = AtomicBoolean(false)

    // ── Scan ──────────────────────────────────────────────────────

    /**
     * Сканирование BLE-устройств с фильтром по Service UUID.
     *
     * Flow эмитит [NixieDevice] при каждом обнаружении.
     * Отфильтрованы дубли по MAC-адресу на стороне вызывающего.
     * Завершается при отмене корутины или вызове [stopScan].
     */
    fun scan(): Flow<NixieDevice> = callbackFlow {
        if (bluetoothAdapter?.isEnabled != true) {
            close()
            return@callbackFlow
        }

        val leScanner = bluetoothAdapter.bluetoothLeScanner
        if (leScanner == null) {
            close()
            return@callbackFlow
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = device.name ?: ""
                val address = device.address

                // Фильтруем только устройства с ожидаемым именем (или любые с нашим UUID)
                if (name.isNotBlank() && !name.contains(Config.DEVICE_NAME, ignoreCase = true)) {
                    return
                }

                trySend(
                    NixieDevice(
                        address = address,
                        name = name.ifBlank { Config.DEVICE_NAME },
                        rssi = result.rssi,
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(Exception("BLE scan failed with code $errorCode"))
            }
        }

        isScanning.set(true)
        leScanner.startScan(listOf(filter), settings, scanCallback)

        awaitClose {
            isScanning.set(false)
            try {
                leScanner.stopScan(scanCallback)
            } catch (_: Exception) {
                // Ignore errors during scan stop
            }
        }
    }.also { isScanning.set(false) }

    /** Остановить сканирование (если запущено через scan().launchIn(...)) */
    fun stopScan() {
        if (isScanning.compareAndSet(true, false)) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(null as android.bluetooth.le.ScanCallback?)
            } catch (_: Exception) { }
        }
    }

    // ── Connect ───────────────────────────────────────────────────

    /**
     * Подключиться к BLE-устройству по MAC-адресу.
     * Выполняет: connect → discoverServices → subscribe notify.
     * Блокируется до успеха или таймаута (10 с).
     */
    suspend fun connect(address: String): Result<Unit> = suspendCancellableCoroutine { cont ->
        if (bluetoothAdapter?.isEnabled != true) {
            cont.resume(Result.failure(BLEException("Bluetooth is disabled")))
            return@suspendCancellableCoroutine
        }

        val device = bluetoothAdapter.getRemoteDevice(address)
        if (device == null) {
            cont.resume(Result.failure(BLEException("Device not found: $address")))
            return@suspendCancellableCoroutine
        }

        connectedAddress = address

        // Таймаут подключения
        val timeoutRunnable = Runnable {
            if (cont.isActive) {
                disconnectInternal()
                cont.resume(Result.failure(BLEException("Connection timeout")))
            }
        }
        mainHandler.postDelayed(timeoutRunnable, Config.BLE_CONNECT_TIMEOUT_MS)

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (cont.isActive) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        cont.resume(
                            Result.failure(
                                BLEException("Connection failed: status=$status")
                            )
                        )
                    }
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        // Discovery services
                        mainHandler.post {
                            gatt.discoverServices()
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedAddress = null
                        txCharacteristic = null
                        rxCharacteristic = null
                        onDisconnectedCallback?.invoke()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (cont.isActive) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        cont.resume(
                            Result.failure(
                                BLEException("Service discovery failed: status=$status")
                            )
                        )
                    }
                    return
                }

                val service = gatt.getService(serviceUuid)
                if (service == null) {
                    if (cont.isActive) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        cont.resume(
                            Result.failure(BLEException("Service not found"))
                        )
                    }
                    return
                }

                val txChar = service.getCharacteristic(txUuid)
                val rxChar = service.getCharacteristic(rxUuid)

                if (txChar == null || rxChar == null) {
                    if (cont.isActive) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        cont.resume(
                            Result.failure(BLEException("Characteristics not found"))
                        )
                    }
                    return
                }

                this@BLEManager.txCharacteristic = txChar
                this@BLEManager.rxCharacteristic = rxChar

                // Enable notifications on TX characteristic
                val success = gatt.setCharacteristicNotification(txChar, true)
                if (!success) {
                    if (cont.isActive) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        cont.resume(
                            Result.failure(BLEException("Failed to enable notifications"))
                        )
                    }
                    return
                }

                // Write CCCD descriptor
                val cccd = txChar.getDescriptor(cccdUuid)
                if (cccd != null) {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(cccd)
                }

                this@BLEManager.gatt = gatt

                if (cont.isActive) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    cont.resume(Result.success(Unit))
                }
            }

            @Deprecated("Deprecated in Java", level = DeprecationLevel.HIDDEN)
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (characteristic.uuid == txUuid) {
                    val value = characteristic.getStringValue(0)
                    if (value != null) {
                        onDataCallback?.invoke(value)
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == txUuid) {
                    val str = String(value, Charsets.UTF_8)
                    onDataCallback?.invoke(str)
                }
            }
        }

        // Connect on main thread
        mainHandler.post {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    // ── Send ──────────────────────────────────────────────────────

    /**
     * Отправить JSON-команду на ESP32.
     * Данные пишутся в RX-характеристику.
     */
    fun send(json: String) {
        val char = rxCharacteristic ?: return
        val gatt = this.gatt ?: return

        val bytes = json.toByteArray(Charsets.UTF_8)

        mainHandler.post {
            try {
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                char.setValue(bytes)
                gatt.writeCharacteristic(char)
            } catch (e: Exception) {
                onErrorCallback?.invoke("Send failed: ${e.message}")
            }
        }
    }

    // ── Disconnect ────────────────────────────────────────────────

    /** Отключиться и освободить ресурсы */
    fun disconnect() {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        stopScan()
        connectedAddress = null
        txCharacteristic = null
        rxCharacteristic = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) { }
        gatt = null
    }

    /** Полная очистка */
    fun close() {
        disconnectInternal()
    }

    // ── State ─────────────────────────────────────────────────────

    fun isConnected(): Boolean = gatt != null && connectedAddress != null

    val connectedDeviceAddress: String? get() = connectedAddress

    // ── Callbacks ─────────────────────────────────────────────────

    /** Вызывается при получении данных из TX-характеристики */
    var onDataCallback: ((String) -> Unit)? = null

    /** Вызывается при отключении устройства */
    var onDisconnectedCallback: (() -> Unit)? = null

    /** Вызывается при ошибках */
    var onErrorCallback: ((String) -> Unit)? = null

    // ── Internal ──────────────────────────────────────────────────

    /** Блокировка для предотвращения повторного коннекта */
    private val connectingLock = Any()
}

/** Исключение BLE */
class BLEException(message: String) : Exception(message)
