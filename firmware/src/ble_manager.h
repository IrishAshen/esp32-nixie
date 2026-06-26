#pragma once

#include <Arduino.h>
#include <functional>
#include <NimBLEDevice.h>

// ─────────────────────────────────────────────────────────────────
//  BLE GATT server (via NimBLE-Arduino)
//  - Advertises as "Nixie Clock"
//  - Exposes one service with TX (notify) and RX (write) chars
//  - Incoming writes are forwarded to the command callback
// ─────────────────────────────────────────────────────────────────

class BLEManager : public NimBLEServerCallbacks,
                   public NimBLECharacteristicCallbacks {
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

    // ── NimBLEServerCallbacks ─────────────────────────────────
    void onConnect(NimBLEServer *pServer) override;
    void onDisconnect(NimBLEServer *pServer) override;

    // ── NimBLECharacteristicCallbacks ─────────────────────────
    void onWrite(NimBLECharacteristic *pCharacteristic) override;

private:
    CommandCallback      _cmdCb  = nullptr;
    NimBLEServer        *_server = nullptr;
    NimBLECharacteristic *_txChar = nullptr;
    NimBLECharacteristic *_rxChar = nullptr;
    bool                 _advertising = false;
    int                  _connectedCount = 0;
};
