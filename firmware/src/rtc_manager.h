#pragma once

#include <RTClib.h>
#include <cstdint>

// ─────────────────────────────────────────────────────────────────
//  DS3231 RTC manager
//  Provides UTC timestamps and handles initialisation / power-loss.
// ─────────────────────────────────────────────────────────────────

class RTCManager {
public:
    /// Begin I2C communication and initialise the RTC.
    /// Returns true if the DS3231 responds.
    bool begin();

    /// True after a successful begin().
    bool isAvailable() const { return _available; }

    /// Get current UTC time as Unix epoch (seconds since 1970-01-01).
    /// Returns 0 if RTC is not available or lost power.
    time_t getTime() const;

    /// Set RTC time from a Unix timestamp.
    void setTime(time_t t);

    /// True if the RTC battery has failed and the time was reset.
    bool lostPower() const;

private:
    RTC_DS3231 _rtc;
    bool _available = false;
};
