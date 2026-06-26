#pragma once

#include <Arduino.h>
#include <functional>
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>

// ─────────────────────────────────────────────────────────────────
//  BLE GATT server
//  - Advertises as "Nixie Clock"
//  - Exposes one service with TX (notify) and RX (write) chars
//  - Incoming writes are forwarded to the command callback
// ─────────────────────────────────────────────────────────────────

class BLEManager : public BLEServerCallbacks,
                   public BLECharacteristicCallbacks {
public:
    using CommandCallback = std::function<void(const String &json)>;

    /// Set the handler for incoming commands (call before begin()).
    void onCommand(CommandCallback cb) { _cmdCb = std::move(cb); }

    /// Initialise BLE, create service + characteristics, start advertising.
    void begin();

    /// Must be called from loop() – handles all BLE housekeeping.
    void update();

    /// Send a JSON string to the connected client via notification.
    void send(const String &json);

    /// True if at least one client is connected.
    bool isConnected() const { return _connectedCount > 0; }

    /// Number of connected clients.
    int clientCount() const { return _connectedCount; }

    /// Restart advertising (e.g. after disconnect).
    void restartAdvertising();

    // ── BLEServerCallbacks ─────────────────────────────────────
    void onConnect(BLEServer *server) override;
    void onDisconnect(BLEServer *server) override;

    // ── BLECharacteristicCallbacks ─────────────────────────────
    void onWrite(BLECharacteristic *pChar) override;

private:
    CommandCallback   _cmdCb     = nullptr;
    BLEServer        *_server    = nullptr;
    BLECharacteristic *_txChar  = nullptr;
    BLECharacteristic *_rxChar  = nullptr;
    bool               _advertising = false;
    int                _connectedCount = 0;
};
