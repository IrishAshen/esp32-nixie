#include "ble_manager.h"
#include "config.h"

// ── statics for the BLE stack ──────────────────────────────────
// UUIDs are defined in config.h; the service is instantiated here.
static BLEUUID serviceUUID(BLE_SERVICE_UUID);
static BLEUUID txCharUUID(BLE_CHAR_TX_UUID);
static BLEUUID rxCharUUID(BLE_CHAR_RX_UUID);

// ── begin ───────────────────────────────────────────────────────

void BLEManager::begin() {
    // Initialise BLE device
    BLEDevice::init(BLE_DEVICE_NAME);
    // Optional: set device power to max for better range
    BLEDevice::setPower(ESP_PWR_LVL_P9);

    // Create server
    _server = BLEDevice::createServer();
    _server->setCallbacks(this);

    // Create service
    BLEService *service = _server->createService(serviceUUID);

    // ── TX characteristic (device → phone, with Notify) ─────
    _txChar = service->createCharacteristic(
        txCharUUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    _txChar->addDescriptor(new BLE2902()); // required for notifications

    // ── RX characteristic (phone → device, with Write) ─────
    _rxChar = service->createCharacteristic(
        rxCharUUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    _rxChar->setCallbacks(this);

    // ── Start service & advertising ─────────────────────────
    service->start();

    BLEAdvertising *adv = BLEDevice::getAdvertising();
    adv->addServiceUUID(serviceUUID);
    adv->setScanResponse(true);
    // Apple-friendly advertising interval
    adv->setMinPreferred(0x06);
    adv->setMinPreferred(0x12);
    BLEDevice::startAdvertising();
    _advertising = true;

    Serial.printf("[ble] advertising as \"%s\"\n", BLE_DEVICE_NAME);
}

// ── update (call from loop) ─────────────────────────────────────

void BLEManager::update() {
    // BLE stack runs autonomously; nothing extra needed here.
    // Future: handle pending commands (currently processed in onWrite
    // callback, which is fine for low-frequency control commands).
    //
    // If commands ever come faster than they are processed, add a
    // queue + flag here to defer processing to the main loop.
}

// ── send notification ──────────────────────────────────────────

void BLEManager::send(const String &json) {
    if (!_txChar || _connectedCount == 0) return;

    _txChar->setValue(json.c_str());
    _txChar->notify();
    // notify() is asynchronous; ESP32 BLE stack handles queuing.
}

void BLEManager::restartAdvertising() {
    if (!_advertising) {
        BLEDevice::startAdvertising();
        _advertising = true;
    }
}

// ── BLEServerCallbacks ─────────────────────────────────────────

void BLEManager::onConnect(BLEServer *server) {
    _connectedCount++;
    _advertising = false;
    Serial.printf("[ble] client connected (total: %d)\n", _connectedCount);
}

void BLEManager::onDisconnect(BLEServer *server) {
    if (_connectedCount > 0) _connectedCount--;
    Serial.printf("[ble] client disconnected (remaining: %d)\n",
                  _connectedCount);

    // Restart advertising so new clients can find us.
    server->startAdvertising();
    _advertising = true;
}

// ── BLECharacteristicCallbacks ─────────────────────────────────

void BLEManager::onWrite(BLECharacteristic *pChar) {
    if (!_cmdCb) return;

    std::string raw = pChar->getValue();
    if (raw.empty()) return;

    String cmd = String(raw.c_str());

    // Trim whitespace and check length
    cmd.trim();
    if (cmd.isEmpty() || cmd.length() > CMD_DOC_SIZE) {
        Serial.println(F("[ble] ignoring empty or oversized command"));
        return;
    }

    Serial.printf("[ble] rx: %s\n", cmd.c_str());
    _cmdCb(cmd);
}
