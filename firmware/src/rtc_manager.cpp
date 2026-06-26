#include "rtc_manager.h"
#include <Arduino.h>
#include <Wire.h>

bool RTCManager::begin() {
    // DS3231 typically answers on the default I2C bus of the ESP32
    // (Wire with SDA=21, SCL=22).
    if (!_rtc.begin()) {
        Serial.println(F("[rtc] DS3231 not found – check wiring / I2C addr"));
        _available = false;
        return false;
    }

    // Check oscillator stop flag (battery was drained / first boot).
    if (_rtc.lostPower()) {
        Serial.println(F("[rtc] lost power detected – time needs to be set"));
        // Do NOT auto-compile-time-adjust here; let the caller decide.
    }

    _available = true;
    Serial.println(F("[rtc] DS3231 initialised"));
    return true;
}

time_t RTCManager::getTime() {
    if (!_available) return 0;
    return _rtc.now().unixtime();
}

void RTCManager::setTime(time_t t) {
    if (!_available) return;
    _rtc.adjust(DateTime(t));
    Serial.printf("[rtc] time set to %lu\n", static_cast<unsigned long>(t));
}

bool RTCManager::lostPower() {
    if (!_available) return false;
    return _rtc.lostPower();
}
