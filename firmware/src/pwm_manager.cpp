#include "pwm_manager.h"
#include "config.h"

#include <Arduino.h>

// ───────────────────────────────────────────────────────────────────────────
//  ESP32 LEDC PWM  (no external library needed — part of ESP32 Arduino Core)
// ───────────────────────────────────────────────────────────────────────────
//
//  ledcSetup(channel, freq_hz, resolution_bits)
//  ledcAttachPin(gpio, channel)
//  ledcWrite(channel, duty)
//
//  Resolution 13-bit → duty range 0 … 8191.  At 2000 Hz this comfortably
//  fits within the hardware limits (max ~9.7 kHz for 13-bit).
// ───────────────────────────────────────────────────────────────────────────

void PwmManager::begin() {
    ledcSetup(PWM_CHANNEL, PWM_FREQ_HZ, PWM_RESOLUTION_BITS);
    ledcAttachPin(PIN_PWM_BRIGHTNESS, PWM_CHANNEL);

    // Apply default brightness (full on by default — configured in config.h).
    setBrightness(DEFAULT_BRIGHTNESS);

    Serial.printf("[pwm] GPIO%d  %d Hz  %d-bit  channel %d  default=%d%%\n",
                  PIN_PWM_BRIGHTNESS, PWM_FREQ_HZ, PWM_RESOLUTION_BITS,
                  PWM_CHANNEL, DEFAULT_BRIGHTNESS);
}

void PwmManager::setBrightness(uint8_t percent) {
    if (percent > 100) percent = 100;

    _brightness = percent;

    // Map 0–100 linearly to 0–8191 (13-bit resolution).
    // For percent=0 we write 0 (PWM fully off, tubes dark).
    // For percent=100 we write 8191 (PWM fully on, max brightness).
    constexpr uint32_t MAX_DUTY = (1U << PWM_RESOLUTION_BITS) - 1;  // 8191
    uint32_t duty = (static_cast<uint32_t>(percent) * MAX_DUTY) / 100;

    ledcWrite(PWM_CHANNEL, duty);
}
