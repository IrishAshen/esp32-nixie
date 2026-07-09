#pragma once

#include <cstddef>
#include <cstdint>

// ═══════════════════════════════════════════════════════════════════
//  Nixie Clock – compile-time configuration
//  Change LAMP_COUNT between LAMPS_4 (HH:MM) and LAMPS_6 (HH:MM:SS)
// ═══════════════════════════════════════════════════════════════════

// ── Lamp / tube count ──────────────────────────────────────────
constexpr uint8_t LAMPS_4 = 4;   // format hh:mm
constexpr uint8_t LAMPS_6 = 6;   // format hh:mm:ss
constexpr uint8_t LAMP_COUNT = LAMPS_4;  // <── change here

// Validate at compile time
static_assert(LAMP_COUNT == LAMPS_4 || LAMP_COUNT == LAMPS_6,
              "LAMP_COUNT must be LAMPS_4 (4) or LAMPS_6 (6)");

// ── Number of daisy-chained 74HC595 registers ──────────────────
// Each 595 drives 2 lamps via two K155ID1 decoders.
constexpr uint8_t SHIFT_REG_COUNT = LAMP_COUNT / 2;

// ── 74HC595 GPIO pins (ESP32 DevKit 30-pin) ────────────────────
constexpr uint8_t PIN_SH_DATA  = 13;  // DS   – Serial data
constexpr uint8_t PIN_SH_CLOCK = 14;  // SH_CP – Shift register clock
constexpr uint8_t PIN_SH_LATCH = 15;  // ST_CP – Storage (latch) clock

// ── PWM (brightness control) ────────────────────────────────────
constexpr uint8_t PIN_PWM_BRIGHTNESS  = 4;   // → high-voltage module EN pin
constexpr uint32_t PWM_FREQ_HZ        = 2000;
constexpr uint8_t  PWM_RESOLUTION_BITS = 13;  // 13-bit → duty 0…8191
constexpr uint8_t  PWM_CHANNEL         = 0;   // LEDC channel 0
constexpr uint8_t  DEFAULT_BRIGHTNESS  = 100; // 0–100 %

// ── DS3231 RTC  (uses default I2C pins) ─────────────────────────
// SDA = GPIO21, SCL = GPIO22  (standard ESP32 Arduino I2C)

// ── NTP ─────────────────────────────────────────────────────────
constexpr char   NTP_SERVER1[]        = "pool.ntp.org";
constexpr char   NTP_SERVER2[]        = "time.google.com";
constexpr int    NTP_RETRY_MAX        = 3;
constexpr int    NTP_TIMEOUT_MS       = 20000;   // per attempt (SNTP first response 2-15 s)
constexpr size_t NTP_SYNC_INTERVAL_MS = 86400000; // 24 hours

// ── BLE ─────────────────────────────────────────────────────────
constexpr char   BLE_DEVICE_NAME[] = "Nixie Clock";
constexpr char   BLE_SERVICE_UUID[] = "e20a39f4-73f5-4bc4-a12f-17d1ad07a961";
constexpr char   BLE_CHAR_TX_UUID[] = "e20a39f5-73f5-4bc4-a12f-17d1ad07a962";  // notify → phone
constexpr char   BLE_CHAR_RX_UUID[] = "e20a39f6-73f5-4bc4-a12f-17d1ad07a963";  // write  ← phone

// ── Defaults ────────────────────────────────────────────────────
constexpr int8_t DEFAULT_TZ_OFFSET = 0;    // UTC
constexpr bool   DEFAULT_FORMAT_12H = false; // 24-hour

// ── Command JSON sizing ─────────────────────────────────────────
constexpr size_t CMD_DOC_SIZE   = 1024;  // incoming command
constexpr size_t RESP_DOC_SIZE  = 512;   // outgoing response
constexpr size_t JSON_BUFFER    = 1024;  // serialisation buffer
