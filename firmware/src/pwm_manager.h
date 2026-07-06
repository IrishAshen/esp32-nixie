#pragma once

#include <cstdint>

// ═══════════════════════════════════════════════════════════════════════════
//  PwmManager — ESP32 LEDC PWM for Nixie tube brightness control
//
//  Drives the high-voltage module's enable input (active-low or active-high
//  depending on module) via PWM at ~2 kHz so the tubes never visibly flicker.
//
//  Brightness range : 0 (off) … 100 (full)
//  Duty resolution  : 13-bit → 0 … 8191
//  PWM frequency    : 2000 Hz
//  GPIO pin         : 4 (configured in config.h)
//
//  Usage:
//      PwmManager pwm;
//      pwm.begin();              // starts at DEFAULT_BRIGHTNESS
//      pwm.setBrightness(75);    // 0–100
//      uint8_t v = pwm.getBrightness();
// ═══════════════════════════════════════════════════════════════════════════

class PwmManager {
public:
    /// Initialise LEDC channel, attach pin, apply default brightness.
    void begin();

    /// Set brightness 0–100. Clamped internally.
    void setBrightness(uint8_t percent);

    /// Get current brightness (0–100).
    uint8_t getBrightness() const { return _brightness; }

private:
    uint8_t _brightness = 100;  // last-set value, matches DEFAULT_BRIGHTNESS
};
