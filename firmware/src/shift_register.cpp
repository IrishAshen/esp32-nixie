#include "shift_register.h"
#include <Arduino.h>

void ShiftRegister::begin(uint8_t dataPin, uint8_t clockPin,
                          uint8_t latchPin, uint8_t regCount) {
    _dataPin  = dataPin;
    _clockPin = clockPin;
    _latchPin = latchPin;
    _regCount = regCount;

    pinMode(_dataPin,  OUTPUT);
    pinMode(_clockPin, OUTPUT);
    pinMode(_latchPin, OUTPUT);

    clear();
    _begun = true;
}

void ShiftRegister::shiftByte(uint8_t data) {
    // MSB first – matches the schematic convention
    for (int8_t i = 7; i >= 0; --i) {
        digitalWrite(_dataPin, (data >> i) & 1U);
        digitalWrite(_clockPin, HIGH);
        digitalWrite(_clockPin, LOW);
    }
}

void ShiftRegister::latch() {
    digitalWrite(_latchPin, HIGH);
    // T_WH (min pulse width to storage clock) = 15 ns typical;
    // digitalWrite is far slower so no extra delay needed.
    digitalWrite(_latchPin, LOW);
}

void ShiftRegister::send(const uint8_t *data, size_t count) {
    if (count != _regCount) return;           // safety check
    // Data[0] should end up in the register closest to MCU,
    // so the last byte we shift out stays in the first register.
    for (int i = static_cast<int>(count) - 1; i >= 0; --i) {
        shiftByte(data[i]);
    }
    latch();
}

void ShiftRegister::clear() {
    digitalWrite(_dataPin,  LOW);
    digitalWrite(_clockPin, LOW);
    digitalWrite(_latchPin, LOW);
    // Shift zeroes through all registers for a clean initial state.
    for (uint8_t i = 0; i < _regCount; ++i) shiftByte(0x00);
    latch();
}
