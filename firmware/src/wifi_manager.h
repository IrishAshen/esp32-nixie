#pragma once

#include <Arduino.h>
#include <functional>

// ─────────────────────────────────────────────────────────────────
//  WiFi + NTP manager
//  - Connects to saved WiFi credentials (non-blocking)
//  - Performs SNTP sync when WiFi connects
//  - Periodic re-sync every 24 hours
//  - Notifies caller via callbacks
// ─────────────────────────────────────────────────────────────────

class WiFiManager {
public:
    using StatusCallback = std::function<void(bool connected)>;
    using NTPSyncCallback = std::function<void(bool ok)>;

    void onWiFiStatus(StatusCallback cb)   { _wifiCb = std::move(cb); }
    void onNTPSync(NTPSyncCallback cb)     { _ntpCb  = std::move(cb); }

    void begin();
    void connect();
    void setCredentials(const String &ssid, const String &pass);
    void forgetCredentials();
    void update();

    // ── queries ────────────────────────────────────────────
    bool isConnected()     const { return _wifiConnected; }
    bool isTimeSynced()    const { return _ntpSynced; }
    bool hasCredentials()  const;
    String getSSID()       const;

private:
    void _loadCredentials();
    void _startNTPSync();
    void _checkNTPSync();

    // ── state ──────────────────────────────────────────────
    StatusCallback   _wifiCb  = nullptr;
    NTPSyncCallback  _ntpCb   = nullptr;

    String _storedSSID;
    String _storedPass;

    bool   _wifiConnected  = false;
    bool   _wifiAttempted  = false;
    bool   _ntpSynced      = false;
    bool   _ntpInProgress  = false;
    bool   _ntpGiveUp      = false;   // true after NTP_RETRY_MAX consecutive failures

    int    _ntpRetries     = 0;

    unsigned long _wifiStartMs = 0;
    unsigned long _ntpStartMs  = 0;
    unsigned long _lastNTPSync = 0;
};

// WiFi connection timeout (ms)
constexpr unsigned long WIFI_TIMEOUT_MS = 15000UL;
