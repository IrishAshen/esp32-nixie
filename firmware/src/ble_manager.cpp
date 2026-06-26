#include "ble_manager.h"
#include "config.h"

// ── begin ───────────────────────────────────────────────────────

void BLEManager::begin() {
    // Initialise BLE device
    NimBLEDevice::init(BLE_DEVICE_NAME);
    // Set power level for better range (+9 dBm)
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);

    // Create server
    _server = NimBLEDevice::createServer();
    _server->setCallbacks(this);

    // Create service (UUID is provided directly as a string)
    NimBLEService *service = _server->createService(BLE_SERVICE_UUID);

    // ── TX characteristic (device → phone, with Notify) ─────
    // NimBLE automatically adds the CCCD (BLE2902) descriptor
    // when NIMBLE_PROPERTY::NOTIFY is set.
    _txChar = service->createCharacteristic(
        BLE_CHAR_TX_UUID,
        NIMBLE_PROPERTY::NOTIFY
    );

    // ── RX characteristic (phone → device, with Write) ─────
    _rxChar = service->createCharacteristic(
        BLE_CHAR_RX_UUID,
        NIMBLE_PROPERTY::WRITE
    );
    _rxChar->setCallbacks(this);

    // ── Start service & advertising ─────────────────────────
    service->start();

    NimBLEAdvertising *adv = NimBLEDevice::getAdvertising();
    adv->addServiceUUID(BLE_SERVICE_UUID);
    adv->setScanResponse(true);
    // Apple-friendly advertising interval
    adv->setMinPreferred(0x06);
    adv->setMinPreferred(0x12);
    adv->start();
    _advertising = true;

    Serial.printf("[ble] advertising as \"%s\"\n", BLE_DEVICE_NAME);
}

// ── update (call from loop) ─────────────────────────────────────

void BLEManager::update() {
    // NimBLE stack runs autonomously; housekeeping is handled
    // by its own threads.  Nothing extra needed here.
}

// ── send notification ──────────────────────────────────────────

void BLEManager::send(const String &json) {
    if (!_txChar || _connectedCount == 0) return;

    _txChar->setValue(json.c_str());
    _txChar->notify();
}

void BLEManager::restartAdvertising() {
    if (!_advertising) {
        NimBLEDevice::getAdvertising()->start();
        _advertising = true;
    }
}

// ── NimBLEServerCallbacks ──────────────────────────────────────

void BLEManager::onConnect(NimBLEServer *pServer) {
    _connectedCount++;
    _advertising = false;
    Serial.printf("[ble] client connected (total: %d)\n", _connectedCount);
}

void BLEManager::onDisconnect(NimBLEServer *pServer) {
    if (_connectedCount > 0) _connectedCount--;
    Serial.printf("[ble] client disconnected (remaining: %d)\n",
                  _connectedCount);

    // Restart advertising so new clients can find us.
    NimBLEDevice::getAdvertising()->start();
    _advertising = true;
}

// ── NimBLECharacteristicCallbacks ──────────────────────────────

void BLEManager::onWrite(NimBLECharacteristic *pCharacteristic) {
    if (!_cmdCb) return;

    std::string raw = pCharacteristic->getValue();
    if (raw.empty()) return;

    String cmd = String(raw.c_str());
    cmd.trim();

    if (cmd.isEmpty() || cmd.length() > CMD_DOC_SIZE) {
        Serial.println(F("[ble] ignoring empty or oversized command"));
        return;
    }

    Serial.printf("[ble] rx: %s\n", cmd.c_str());
    _cmdCb(cmd);
}
