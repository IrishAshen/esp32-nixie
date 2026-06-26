#ifndef OTA_MANAGER_H
#define OTA_MANAGER_H

#include <Arduino.h>
#include <functional>

// ─────────────────────────────────────────────────────────────────
//  OTA manager (firmware update over WiFi, triggered via BLE)
//
//  Downloads a firmware binary from a URL and writes it to the
//  inactive OTA partition.  On success the device reboots.
//
//  Non-blocking – call update() from loop() while in progress.
// ─────────────────────────────────────────────────────────────────

class OtaManager {
public:
    using EventCallback = std::function<void(const String &eventJson)>;

    /// Set callback for progress / status events.
    void onEvent(EventCallback cb) { _eventCb = std::move(cb); }

    /// Start OTA: download firmware from `url` and flash it.
    /// Returns false if another OTA is already running.
    bool startOTA(const String &url);

    /// Must be called from loop() while OTA is in progress.
    void update();

    bool isInProgress() const { return _state != IDLE; }
    bool isIdle()       const { return _state == IDLE; }
    int  getProgress()  const { return _percent; }

private:
    enum State { IDLE, CONNECTING, DOWNLOADING, FINALIZING, ERROR };

    State  _state         = IDLE;
    String _url;
    int    _percent       = 0;

    void _sendEvent(const String &status, int percent = -1,
                    const String &msg = "");
    void _fail(const String &msg);
    void _cleanup();
    void _reboot();
    void _runOTA();  // does the actual HTTP+Update work

    EventCallback _eventCb = nullptr;
};

#endif // OTA_MANAGER_H
