// ═══════════════════════════════════════════════════════════════════════════
//  Nixie Counter — test firmware
//
//  Два газоразрядных индикатора ИН-12 (через 74HC595 + К155ИД1)
//  одновременно перебирают цифры 0→1→…→9→0→… с периодом CYCLES_MS.
//
//  Других функций нет — ни BLE, ни WiFi, ни RTC.
// ═══════════════════════════════════════════════════════════════════════════

#include <Arduino.h>

// Период смены цифр (мс)
static constexpr unsigned long CYCLES_MS = 5000;

// Пины 74HC595 (ESP32 DevKit 30 pin)
static constexpr uint8_t PIN_DATA  = 27;   // DS
static constexpr uint8_t PIN_CLOCK = 26;   // SH_CP
static constexpr uint8_t PIN_LATCH = 25;   // ST_CP

static constexpr uint8_t digitMap[10] = { 3, 1, 9, 8, 0, 12, 4, 2, 10, 11 };

// ═══════════════════════════════════════════════════════════════════════════
//  74HC595 bit-bang helpers
// ═══════════════════════════════════════════════════════════════════════════

/// Показать одну и ту же BCD-цифру (0–9) на обеих ИН-12.
///
/// Восьмибитное слово: мл. тетрада → К155ИД1 #1 (лампа 1),
/// ст. тетрада → К155ИД1 #2 (лампа 2). Обе тетрады одинаковые,
/// так что обе лампы горят одной цифрой.
static void showDigit(uint8_t d) {
    uint8_t bcd = digitMap[d];
    bcd = bcd & 0x0F;                       // 4-bit BCD, на всякий случай маска

    digitalWrite(PIN_LATCH, LOW);
    shiftOut(PIN_DATA, PIN_CLOCK, LSBFIRST, (bcd << 4) | bcd);

    digitalWrite(PIN_LATCH, HIGH);
}

static void showDigits(uint8_t d[]) {
    uint8_t hh = (digitMap[d[0]] << 4) | digitMap[d[1]];
    uint8_t mm = (digitMap[d[2]] << 4) | digitMap[d[3]];
    uint8_t ss = (digitMap[d[4]] << 4) | digitMap[d[5]];

    digitalWrite(PIN_LATCH, LOW);

    shiftOut(PIN_DATA, PIN_CLOCK, LSBFIRST, ss);
    shiftOut(PIN_DATA, PIN_CLOCK, LSBFIRST, mm);
    shiftOut(PIN_DATA, PIN_CLOCK, LSBFIRST, hh);

    digitalWrite(PIN_LATCH, HIGH);
}

// ═══════════════════════════════════════════════════════════════════════════
//  Arduino entry points
// ═══════════════════════════════════════════════════════════════════════════

void setup() {
    Serial.begin(115200);

    pinMode(PIN_DATA,  OUTPUT);
    pinMode(PIN_CLOCK, OUTPUT);
    pinMode(PIN_LATCH, OUTPUT);

    // Начальное состояние — все выходы в 0
    digitalWrite(PIN_DATA,  LOW);
    digitalWrite(PIN_CLOCK, LOW);
    digitalWrite(PIN_LATCH, LOW);
}

void loop() {
    static uint8_t digits[6] = { 0, 1, 2, 3, 4, 5 };          // текущая цифра (0–9)
    static unsigned long prevMs = 0;

    static uint8_t test = 0;

    unsigned long now = millis();
    if (now - prevMs >= CYCLES_MS) {
        prevMs = now;

        Serial.printf(
            "1: %d, 2: %d, 3: %d, 4: %d, 5: %d, 6: %d\n", 
            digits[0], digits[1], digits[2], digits[3], digits[4], digits[5]);
        showDigits(digits);

        // 0 → 1 → … → 9 → 0 → …
        for (auto &d: digits) {
            ++d;
            if (d > 9) d = 0;
        }
    }
}
