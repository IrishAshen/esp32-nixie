// ═══════════════════════════════════════════════════════════════════════════
//  Nixie Counter — test firmware
//
//  Два газоразрядных индикатора ИН-12 (через 74HC595 + К155ИД1)
//  одновременно перебирают цифры 0→1→…→9→0→… с периодом CYCLES_MS.
//
//  Других функций нет — ни BLE, ни WiFi, ни RTC.
// ═══════════════════════════════════════════════════════════════════════════

#include <Arduino.h>

// ═══════════════════════════════════════════════════════════════════════════
//  Конфигурация — правь здесь
// ═══════════════════════════════════════════════════════════════════════════

// Период смены цифр (мс)
static constexpr unsigned long CYCLES_MS = 1000;

// Пины 74HC595 (ESP32 DevKit 30 pin)
static constexpr uint8_t PIN_DATA  = 13;   // DS
static constexpr uint8_t PIN_CLOCK = 14;   // SH_CP
static constexpr uint8_t PIN_LATCH = 15;   // ST_CP

// ═══════════════════════════════════════════════════════════════════════════
//  74HC595 bit-bang helpers
// ═══════════════════════════════════════════════════════════════════════════

/// Сдвинуть один байт в регистр (LSB first).
/// После вызова всех байт нужно дёрнуть latchPin чтобы применить.
static void shiftByte(uint8_t data) {
    for (uint8_t i = 0; i < 8; ++i) {
        digitalWrite(PIN_DATA, (data >> i) & 1U);
        digitalWrite(PIN_CLOCK, HIGH);
        digitalWrite(PIN_CLOCK, LOW);
    }
}

/// Вывести данные на выходы 74HC595 (latch).
static void latch() {
    digitalWrite(PIN_LATCH, HIGH);
    digitalWrite(PIN_LATCH, LOW);
}

/// Показать одну и ту же BCD-цифру (0–9) на обеих ИН-12.
///
/// Восьмибитное слово: мл. тетрада → К155ИД1 #1 (лампа 1),
/// ст. тетрада → К155ИД1 #2 (лампа 2). Обе тетрады одинаковые,
/// так что обе лампы горят одной цифрой.
static void showDigit(uint8_t d) {
    uint8_t bcd = d & 0x0F;                // 4-bit BCD, на всякий случай маска
    shiftByte((bcd << 4) | bcd);           // обе лампы — одно и то же число
    latch();
}

// ═══════════════════════════════════════════════════════════════════════════
//  Arduino entry points
// ═══════════════════════════════════════════════════════════════════════════

void setup() {
    pinMode(PIN_DATA,  OUTPUT);
    pinMode(PIN_CLOCK, OUTPUT);
    pinMode(PIN_LATCH, OUTPUT);

    // Начальное состояние — все выходы в 0
    digitalWrite(PIN_DATA,  LOW);
    digitalWrite(PIN_CLOCK, LOW);
    digitalWrite(PIN_LATCH, LOW);
}

void loop() {
    static uint8_t digit = 0;          // текущая цифра (0–9)
    static unsigned long prevMs = 0;

    unsigned long now = millis();
    if (now - prevMs >= CYCLES_MS) {
        prevMs = now;

        showDigit(digit);

        // 0 → 1 → … → 9 → 0 → …
        ++digit;
        if (digit > 9) digit = 0;
    }
}
