#include "wifi_manager.h"
#include "config.h"
#include <WiFi.h>
#include <Preferences.h>
#include <time.h>

static constexpr char NVS_NS[]   = "nixie_wifi";
static constexpr char KEY_SSID[] = "ssid";
static constexpr char KEY_PASS[] = "pass";

// ═══════════════════════════════════════════════════════════════
//  init / credentials
// ═══════════════════════════════════════════════════════════════

void WiFiManager::begin() {
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    _loadCredentials();
    Serial.println(F("[wifi] manager ready"));
}

bool WiFiManager::hasCredentials() const {
    return !_storedSSID.isEmpty();
}

String WiFiManager::getSSID() const {
    return _storedSSID;
}

void WiFiManager::_loadCredentials() {
    Preferences prefs;
    prefs.begin(NVS_NS, true);
    _storedSSID = prefs.getString(KEY_SSID, "");
    _storedPass = prefs.getString(KEY_PASS, "");
    prefs.end();
    Serial.printf("[wifi] loaded creds for SSID: %s\n",
                  _storedSSID.isEmpty() ? "(none)" : _storedSSID.c_str());
}

void WiFiManager::setCredentials(const String &ssid, const String &pass) {
    _storedSSID = ssid;
    _storedPass = pass;
    Preferences prefs;
    prefs.begin(NVS_NS, false);
    prefs.putString(KEY_SSID, ssid);
    prefs.putString(KEY_PASS, pass);
    prefs.end();
    Serial.printf("[wifi] saved creds for %s\n", ssid.c_str());
    connect();
}

void WiFiManager::forgetCredentials() {
    Preferences prefs;
    prefs.begin(NVS_NS, false);
    prefs.remove(KEY_SSID);
    prefs.remove(KEY_PASS);
    prefs.end();
    _storedSSID = "";
    _storedPass = "";
    WiFi.disconnect(true);
    _wifiConnected = false;
    _wifiAttempted = false;
    Serial.println(F("[wifi] creds forgotten"));
}

// ═══════════════════════════════════════════════════════════════
//  connect
// ═══════════════════════════════════════════════════════════════

void WiFiManager::connect() {
    if (_storedSSID.isEmpty()) {
        Serial.println(F("[wifi] no creds to connect with"));
        return;
    }
    if (WiFi.isConnected()) WiFi.disconnect(false);
    _wifiConnected = false;
    _wifiAttempted = true;
    _wifiStartMs   = millis();
    // Reset NTP state so we try again on new connection
    _ntpSynced     = false;
    _ntpInProgress = false;
    _ntpGiveUp     = false;
    _ntpRetries    = 0;
    WiFi.begin(_storedSSID.c_str(), _storedPass.c_str());
    Serial.printf("[wifi] connecting to %s ...\n", _storedSSID.c_str());
}

// ═══════════════════════════════════════════════════════════════
//  NTP
// ═══════════════════════════════════════════════════════════════

void WiFiManager::_startNTPSync() {
    if (_ntpInProgress) return;
    _ntpInProgress = true;
    _ntpStartMs    = millis();
    // Reset SNTP – configTime(offset, daylight, server1, server2)
    configTime(0, 0, NTP_SERVER1, NTP_SERVER2);
    Serial.printf("[ntp] syncing (attempt %d/%d) ...\n",
                  _ntpRetries + 1, NTP_RETRY_MAX);
}

void WiFiManager::_checkNTPSync() {
    time_t now = ::time(nullptr);

    // Success – time() returns epoch > 100000 once SNTP responds.
    if (now > 100000) {
        _ntpInProgress = false;
        _ntpSynced     = true;
        _ntpGiveUp     = false;
        _ntpRetries    = 0;
        _lastNTPSync   = millis();
        struct tm ti;
        gmtime_r(&now, &ti);
        Serial.printf("[ntp] ok  %04d-%02d-%02d %02d:%02d:%02d UTC\n",
                      ti.tm_year + 1900, ti.tm_mon + 1, ti.tm_mday,
                      ti.tm_hour, ti.tm_min, ti.tm_sec);
        if (_ntpCb) _ntpCb(true);
        return;
    }

    // Timeout
    if (millis() - _ntpStartMs > static_cast<unsigned long>(NTP_TIMEOUT_MS)) {
        _ntpInProgress = false;
        _ntpRetries++;
        Serial.printf("[ntp] timeout %d/%d\n", _ntpRetries, NTP_RETRY_MAX);
    }
}

// ═══════════════════════════════════════════════════════════════
//  main loop tick
// ═══════════════════════════════════════════════════════════════

static constexpr unsigned long WIFI_TIMEOUT_MS = 15000UL;

void WiFiManager::update() {
    // ── WiFi connection state machine ──────────────────────────

    if (_wifiAttempted && !_wifiConnected) {
        if (WiFi.status() == WL_CONNECTED) {
            _wifiConnected = true;
            Serial.printf("[wifi] connected  IP: %s\n",
                          WiFi.localIP().toString().c_str());
            if (_wifiCb) _wifiCb(true);
            // NTP sync will start on the next update() tick
        }
        else if (millis() - _wifiStartMs > WIFI_TIMEOUT_MS) {
            _wifiAttempted = false;
            Serial.println(F("[wifi] timeout"));
            if (_wifiCb) _wifiCb(false);
        }
    }

    // ── Detect unexpected disconnection ────────────────────────
    if (_wifiConnected && WiFi.status() != WL_CONNECTED) {
        _wifiConnected = false;
        _wifiAttempted = false;
        Serial.println(F("[wifi] disconnected"));
        if (_wifiCb) _wifiCb(false);
    }

    // ── Auto-reconnect ────────────────────────────────────────
    if (!_wifiAttempted && !_wifiConnected && hasCredentials()) {
        connect();
    }

    // ── NTP – poll in-progress sync ───────────────────────────
    if (_ntpInProgress) {
        _checkNTPSync();
    }

    // ── NTP – start new sync ──────────────────────────────────
    if (_wifiConnected && !_ntpInProgress && !_ntpSynced && !_ntpGiveUp) {
        _startNTPSync();
    }

    // ── NTP – give-up logic ──────────────────────────────────
    if (!_ntpInProgress && !_ntpSynced && !_ntpGiveUp &&
        _ntpRetries >= NTP_RETRY_MAX) {
        if (_lastNTPSync > 0) {
            // Was previously synced → daily sync failed.
            // Revert to synced so the clock keeps showing RTC time.
            // Reset timer so we don't hammer NTP every second.
            _ntpSynced = true;
            _lastNTPSync = millis();
            Serial.println(F("[ntp] daily sync failed, retry in 24h"));
        } else {
            // Never synced → give up until next WiFi reconnect.
            _ntpGiveUp = true;
            Serial.println(F("[ntp] initial sync failed, giving up"));
            if (_ntpCb) _ntpCb(false);
        }
    }

    // ── NTP – daily re-sync ───────────────────────────────────
    if (_wifiConnected && _ntpSynced && !_ntpInProgress) {
        if (millis() - _lastNTPSync >= NTP_SYNC_INTERVAL_MS) {
            // Allow one fresh sync attempt; reset give-up so a
            // previously-failed boot sync gets another chance too.
            _ntpSynced   = false;
            _ntpGiveUp   = false;
            _ntpRetries  = 0;
            Serial.println(F("[ntp] daily re-sync scheduled"));
        }
    }
}
