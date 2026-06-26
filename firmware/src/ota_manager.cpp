#include "ota_manager.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <Update.h>

// ── helpers ────────────────────────────────────────────────────

void OtaManager::_sendEvent(const String &status, int percent,
                             const String &msg) {
    if (!_eventCb) return;
    String json = "{\"event\":\"ota\",\"status\":\"" + status + "\"";
    if (percent >= 0) json += ",\"percent\":" + String(percent);
    if (!msg.isEmpty()) json += ",\"message\":\"" + msg + "\"";
    json += "}";
    _eventCb(json);
}

void OtaManager::_fail(const String &msg) {
    _sendEvent("failed", -1, msg);
    _cleanup();
    _state = IDLE;
    Serial.printf("[ota] FAILED: %s\n", msg.c_str());
}

void OtaManager::_cleanup() {
    _url = "";
    _percent = 0;
}

void OtaManager::_reboot() {
    _sendEvent("rebooting");
    Serial.println(F("[ota] rebooting into new firmware ..."));
    delay(500);
    ESP.restart();
}

// ── public API ─────────────────────────────────────────────────

bool OtaManager::startOTA(const String &url) {
    if (_state != IDLE) {
        Serial.println(F("[ota] already in progress, ignoring"));
        return false;
    }
    _url = url;
    _state = CONNECTING;
    _percent = 0;
    Serial.printf("[ota] start: %s\n", url.c_str());
    return true;
}

void OtaManager::update() {
    if (_state == IDLE) return;

    if (_state == CONNECTING) {
        _state = DOWNLOADING;
        _runOTA();                        // synchronous download
        // _runOTA sets _state to IDLE (success/error) or FINALIZING
    }

    if (_state == FINALIZING) {
        _reboot();
    }
}

// ── core download + flash ──────────────────────────────────────

void OtaManager::_runOTA() {
    if (!WiFi.isConnected()) {
        _fail("WiFi not connected");
        return;
    }

    _sendEvent("started");

    HTTPClient http;
    http.setTimeout(15000);               // 15 s connect timeout
    http.setFollowRedirects(HTTPC_FORCE_FOLLOW_REDIRECTS);
    http.begin(_url);

    int httpCode = http.GET();
    if (httpCode <= 0) {
        _fail("HTTP GET failed: " + String(http.errorToString(httpCode).c_str()));
        http.end();
        return;
    }
    if (httpCode != HTTP_CODE_OK) {
        _fail("HTTP " + String(httpCode));
        http.end();
        return;
    }

    int contentLength = http.getSize();
    if (contentLength <= 0) {
        _fail("Unknown content length");
        http.end();
        return;
    }

    Serial.printf("[ota] content length: %d bytes\n", contentLength);

    // Prepare OTA partition
    bool canBegin = Update.begin(contentLength, U_FLASH);
    if (!canBegin) {
        _fail("Partition too small: " + String(Update.errorString()));
        http.end();
        return;
    }

    // Stream download → Update
    WiFiClient *stream = http.getStreamPtr();
    uint8_t buf[256];
    int totalRead = 0;
    int lastPercent = 0;

    while (http.connected() && totalRead < contentLength) {
        size_t avail = stream->available();
        if (avail == 0) {
            delay(5);                     // wait for more data
            continue;
        }

        size_t toRead = (avail > sizeof(buf)) ? sizeof(buf) : avail;
        size_t n = stream->readBytes(buf, toRead);
        if (n == 0) {
            _fail("Stream read timeout");
            http.end();
            Update.abort();
            return;
        }

        size_t written = Update.write(buf, n);
        if (written != n) {
            _fail("Update.write error: " + String(Update.errorString()));
            http.end();
            Update.abort();
            return;
        }

        totalRead += n;
        int pct = (totalRead * 100) / contentLength;
        if (pct - lastPercent >= 5 || pct == 100) {
            lastPercent = pct;
            _percent = pct;
            _sendEvent("progress", pct);
        }
    }

    http.end();

    if (totalRead != contentLength) {
        _fail("Size mismatch: read " + String(totalRead) +
              " / expected " + String(contentLength));
        Update.abort();
        return;
    }

    if (!Update.end()) {
        _fail("Update.end: " + String(Update.errorString()));
        return;
    }

    if (!Update.isFinished()) {
        _fail("Update not finished");
        return;
    }

    Serial.printf("[ota] success (%d bytes), rebooting ...\n", totalRead);
    _sendEvent("success");
    _cleanup();
    _state = FINALIZING;   // reboot on next update() tick with delay
}
