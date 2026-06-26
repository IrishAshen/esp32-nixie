#include <Arduino.h>
#include <Preferences.h>
#include <time.h>
#include <sys/time.h>  // for settimeofday
#include <WiFi.h>       // WiFi.localIP() etc.

#include "config.h"
#include "shift_register.h"
#include "nixie_display.h"
#include "rtc_manager.h"
#include "wifi_manager.h"
#include "ble_manager.h"
#include "command_handler.h"
#include "ota_manager.h"

// ═══════════════════════════════════════════════════════════════
//  Global state (shared across modules via extern in header
//  if needed; for now kept local to main.cpp for encapsulation).
// ═══════════════════════════════════════════════════════════════

static ShiftRegister   g_shiftReg;
static NixieDisplay    g_display(g_shiftReg);
static RTCManager      g_rtc;
static WiFiManager     g_wifi;
static BLEManager      g_ble;
static CommandHandler  g_cmdHandler;
static OtaManager      g_ota;

// ── persistent settings (stored in NVS) ─────────────────────────
static Preferences g_prefs;
static int8_t      g_tzOffset  = DEFAULT_TZ_OFFSET;  // hours from UTC
static bool        g_format12h = DEFAULT_FORMAT_12H;
static constexpr char PREFS_NS[]     = "nixie_cfg";
static constexpr char KEY_TZ[]       = "tz_offset";
static constexpr char KEY_12H[]      = "format_12h";

// ── timing ─────────────────────────────────────────────────────
static unsigned long g_lastDisplayMs = 0;
static constexpr unsigned long DISPLAY_INTERVAL_MS = 100;  // 10 Hz
static constexpr unsigned long BLANK_ON_FAIL_MS = 3000;    // blank after 3 s

// ───────────────────────────────────────────────────────────────
//  Persistent settings helpers
// ───────────────────────────────────────────────────────────────

static void loadSettings() {
    g_prefs.begin(PREFS_NS, true);
    g_tzOffset  = g_prefs.getChar(KEY_TZ, DEFAULT_TZ_OFFSET);
    g_format12h = g_prefs.getBool(KEY_12H, DEFAULT_FORMAT_12H);
    g_prefs.end();
    Serial.printf("[cfg] loaded: tz=%+d  12h=%s\n",
                  g_tzOffset, g_format12h ? "yes" : "no");
}

static void saveTZ(int8_t offset) {
    g_prefs.begin(PREFS_NS, false);
    g_prefs.putChar(KEY_TZ, offset);
    g_prefs.end();
}

static void saveFormat12h(bool v) {
    g_prefs.begin(PREFS_NS, false);
    g_prefs.putBool(KEY_12H, v);
    g_prefs.end();
}

// ───────────────────────────────────────────────────────────────
//  BLE event helpers
// ───────────────────────────────────────────────────────────────

static void sendBLEEvent(const String &eventJson) {
    if (g_ble.isConnected() && !eventJson.isEmpty()) {
        g_ble.send(eventJson);
    }
}

// ───────────────────────────────────────────────────────────────
//  WiFi / NTP callbacks
// ───────────────────────────────────────────────────────────────

static void onWiFiStatus(bool connected) {
    String json;
    if (connected) {
        json  = "{\"event\":\"wifi\",\"status\":\"connected\",";
        json += "\"ip\":\"" + WiFi.localIP().toString() + "\"}";
    } else {
        json = "{\"event\":\"wifi\",\"status\":\"disconnected\"}";
    }
    sendBLEEvent(json);
}

static void onNTPSync(bool ok) {
    if (ok) {
        // NTP succeeded — update RTC with the fresh time
        time_t now = ::time(nullptr);
        if (now > 100000) {
            g_rtc.setTime(now);
            Serial.println(F("[main] RTC updated from NTP"));
        }
        sendBLEEvent("{\"event\":\"ntp\",\"status\":\"synced\"}");
    } else {
        sendBLEEvent(
            "{\"event\":\"ntp\",\"status\":\"failed\","
            "\"message\":\"All NTP attempts exhausted\"}");
    }
}

// ───────────────────────────────────────────────────────────────
//  Command handlers
// ───────────────────────────────────────────────────────────────

static void registerCommands() {

    // ── set_wifi ───────────────────────────────────────────────
    g_cmdHandler.addCommand("set_wifi", [](const JsonObject &cmd,
                                            JsonObject &resp) {
        if (!cmd["ssid"].is<const char *>() || !cmd["password"].is<const char *>()) {
            resp["status"]  = "error";
            resp["message"] = "Missing 'ssid' or 'password'";
            return;
        }
        const char *ssid = cmd["ssid"];
        const char *pass = cmd["password"];
        g_wifi.setCredentials(ssid, pass);
        resp["message"] = "Credentials saved, connecting ...";
    });

    // ── set_time (manual override) ──────────────────────────────
    g_cmdHandler.addCommand("set_time", [](const JsonObject &cmd,
                                            JsonObject &resp) {
        if (!cmd["timestamp"].is<time_t>()) {
            resp["status"]  = "error";
            resp["message"] = "Missing 'timestamp' (Unix epoch seconds)";
            return;
        }
        time_t t = cmd["timestamp"].as<time_t>();
        if (t < 100000) {
            resp["status"]  = "error";
            resp["message"] = "Timestamp looks invalid";
            return;
        }
        g_rtc.setTime(t);

        // Also set system time (may be overwritten by NTP later)
        struct timeval tv = { .tv_sec = t };
        settimeofday(&tv, nullptr);
        resp["message"] = "Time set";
    });

    // ── set_timezone ───────────────────────────────────────────
    g_cmdHandler.addCommand("set_timezone", [](const JsonObject &cmd,
                                                JsonObject &resp) {
        if (!cmd["offset"].is<int>()) {
            resp["status"]  = "error";
            resp["message"] = "Missing 'offset' (hours from UTC, e.g. 3)";
            return;
        }
        int8_t off = cmd["offset"].as<int8_t>();
        if (off < -12 || off > 14) {
            resp["status"]  = "error";
            resp["message"] = "Offset out of range (-12 … +14)";
            return;
        }
        g_tzOffset = off;
        saveTZ(off);
        resp["message"] = "Timezone set";
    });

    // ── set_format ─────────────────────────────────────────────
    g_cmdHandler.addCommand("set_format", [](const JsonObject &cmd,
                                              JsonObject &resp) {
        if (!cmd["value"].is<const char *>()) {
            resp["status"]  = "error";
            resp["message"] = "Missing 'value' (\"12h\" or \"24h\")";
            return;
        }
        const char *val = cmd["value"];
        if (!strcmp(val, "12h")) {
            g_format12h = true;
            saveFormat12h(true);
        } else if (!strcmp(val, "24h")) {
            g_format12h = false;
            saveFormat12h(false);
        } else {
            resp["status"]  = "error";
            resp["message"] = "Invalid value – use \"12h\" or \"24h\"";
            return;
        }
        resp["message"] = "Display format updated";
    });

    // ── get_status ─────────────────────────────────────────────
    g_cmdHandler.addCommand("get_status", [](const JsonObject &cmd,
                                              JsonObject &resp) {
        JsonObject st = resp["state"].to<JsonObject>();
        st["wifi"]       = g_wifi.isConnected() ? "connected" : "disconnected";
        st["ssid"]       = g_wifi.getSSID();
        st["ntp"]        = g_wifi.isTimeSynced() ? "synced" : "pending";
        st["rtc"]        = g_rtc.isAvailable() ? "ok" : "missing";
        st["timezone"]   = g_tzOffset;
        st["format"]     = g_format12h ? "12h" : "24h";
        st["lamps"]      = static_cast<int>(LAMP_COUNT);

        // Local time string for verification
        time_t now = ::time(nullptr);
        if (now > 100000) {
            time_t local = now + g_tzOffset * 3600;
            struct tm ti;
            gmtime_r(&local, &ti);
            char buf[20];
            snprintf(buf, sizeof(buf), "%02d:%02d:%02d",
                     ti.tm_hour, ti.tm_min, ti.tm_sec);
            st["local_time"] = buf;
        }
    });

    // ── get_version ────────────────────────────────────────────
    g_cmdHandler.addCommand("get_version", [](const JsonObject &cmd,
                                               JsonObject &resp) {
        resp["version"] = "1.0.0";
        resp["build"]   = __DATE__ " " __TIME__;
        resp["lamps"]   = static_cast<int>(LAMP_COUNT);
    });

    // ── ota ──────────────────────────────────────────────────────
    g_cmdHandler.addCommand("ota", [](const JsonObject &cmd,
                                       JsonObject &resp) {
        if (!g_wifi.isConnected()) {
            resp["status"]  = "error";
            resp["message"] = "WiFi not connected";
            return;
        }
        if (!cmd["url"].is<const char *>()) {
            resp["status"]  = "error";
            resp["message"] = "Missing 'url' parameter";
            return;
        }
        const char *url = cmd["url"];
        if (!g_ota.startOTA(url)) {
            resp["status"]  = "error";
            resp["message"] = "OTA already in progress";
            return;
        }
        resp["message"] = "OTA started";
    });

    // ── list_commands ──────────────────────────────────────────
    g_cmdHandler.addCommand("list_commands", [](const JsonObject &cmd,
                                                 JsonObject &resp) {
        resp["commands"] = g_cmdHandler.listCommands();
    });

    // ── forget_wifi ────────────────────────────────────────────
    g_cmdHandler.addCommand("forget_wifi", [](const JsonObject &cmd,
                                               JsonObject &resp) {
        g_wifi.forgetCredentials();
        resp["message"] = "WiFi credentials cleared";
    });

    // ── reboot ─────────────────────────────────────────────────
    g_cmdHandler.addCommand("reboot", [](const JsonObject &cmd,
                                          JsonObject &resp) {
        resp["message"] = "Rebooting ...";
        resp["status"]  = "ok";
        // Flush serial before restart
        Serial.flush();
        delay(100);
        ESP.restart();
    });
}

// ───────────────────────────────────────────────────────────────
//  BLE command callback → command handler → response
// ───────────────────────────────────────────────────────────────

static void onBLECommand(const String &raw) {
    String response = g_cmdHandler.process(raw);
    if (!response.isEmpty()) {
        sendBLEEvent(response);
    }
}

// ───────────────────────────────────────────────────────────────
//  Time source (RTC primary, NTP fallback)
// ───────────────────────────────────────────────────────────────

/// Returns a valid UTC Unix timestamp, or 0 if unavailable.
static time_t getCurrentUTC() {
    // 1. If SNTP has synced, system time is the most accurate.
    if (g_wifi.isTimeSynced()) {
        time_t t = ::time(nullptr);
        if (t > 100000) return t;
    }
    // 2. Fall back to RTC (was set from NTP or manually).
    if (g_rtc.isAvailable()) {
        time_t t = g_rtc.getTime();
        if (t > 100000) return t;
    }
    // 3. Last resort: system time (e.g. set manually via BLE).
    time_t t = ::time(nullptr);
    if (t > 100000) return t;
    return 0;
}

// ───────────────────────────────────────────────────────────────
//  Arduino entry points
// ───────────────────────────────────────────────────────────────

void setup() {
    Serial.begin(115200);
    delay(500);
    Serial.println();
    Serial.println(F("══════════════════════════════════════════"));
    Serial.println(F("  Nixie Clock v1.0.0  (IN-12 / ESP32)   "));
    Serial.println(F("══════════════════════════════════════════"));
    Serial.printf("  Lamp count: %d (%d shift registers)\n",
                  LAMP_COUNT, SHIFT_REG_COUNT);

    // ── Shift registers ────────────────────────────────────────
    g_shiftReg.begin(PIN_SH_DATA, PIN_SH_CLOCK,
                     PIN_SH_LATCH, SHIFT_REG_COUNT);
    g_display.blank();
    Serial.println(F("[hw] shift registers initialised"));

    // ── RTC ────────────────────────────────────────────────────
    g_rtc.begin();

    // ── Load persistent settings ───────────────────────────────
    loadSettings();

    // ── WiFi & NTP ─────────────────────────────────────────────
    g_wifi.onWiFiStatus(onWiFiStatus);
    g_wifi.onNTPSync(onNTPSync);
    g_wifi.begin();

    // ── Command handler ────────────────────────────────────────
    registerCommands();

    // ── BLE ────────────────────────────────────────────────────
    g_ble.onCommand(onBLECommand);
    g_ble.begin();

    // ── OTA ────────────────────────────────────────────────────
    g_ota.onEvent(sendBLEEvent);

    // Auto-connect if credentials exist
    if (g_wifi.hasCredentials()) {
        g_wifi.connect();
    }

    Serial.println(F("[main] ready."));
}

void loop() {
    // ── BLE housekeeping ───────────────────────────────────────
    g_ble.update();

    // ── WiFi / NTP ─────────────────────────────────────────────
    g_wifi.update();

    // ── OTA (non-blocking, does nothing if idle) ──────────────
    g_ota.update();

    // ── Display update ─────────────────────────────────────────
    unsigned long now = millis();
    if (now - g_lastDisplayMs >= DISPLAY_INTERVAL_MS) {
        g_lastDisplayMs = now;

        // Static helpers for blank-on-fail tracking.
        static unsigned long blankStart = 0;
        bool timeIsValid = false;

        time_t utcTime = getCurrentUTC();

        if (utcTime >= 100000) {
            timeIsValid = true;
            blankStart = 0;  // reset blank timer

            // Valid time – apply timezone and show.
            time_t local       = utcTime + g_tzOffset * 3600;
            struct tm timeinfo;
            gmtime_r(&local, &timeinfo);

            g_display.show(
                static_cast<uint8_t>(timeinfo.tm_hour),
                static_cast<uint8_t>(timeinfo.tm_min),
                static_cast<uint8_t>(timeinfo.tm_sec),
                g_format12h
            );
        }

        if (!timeIsValid) {
            // No valid time – blank after a grace period.
            if (blankStart == 0) blankStart = millis();
            if (millis() - blankStart > BLANK_ON_FAIL_MS) {
                g_display.blank();
            }
        }
    }
}
