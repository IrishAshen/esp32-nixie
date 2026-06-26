#pragma once

#include <cstdint>
#include <cstddef>

// ─────────────────────────────────────────────────────────────────
//  74HC595 daisy-chained shift register driver
//  Pushes bytes through N registers and latches them all at once.
// ─────────────────────────────────────────────────────────────────

class ShiftRegister {
public:
    /// Empty constructor – call begin() later.
    ShiftRegister() = default;

    /// Init GPIO pins and set outputs low.
    void begin(uint8_t dataPin, uint8_t clockPin, uint8_t latchPin,
               uint8_t regCount);

    /// Shift out a byte to the register chain.
    /// The byte travels through all registers; the caller controls
    /// the order by passing bytes in reverse-chain sequence.
    void shiftByte(uint8_t data);

    /// Latch – copies shift-register contents to storage registers.
    void latch();

    /// Convenience: shift out `count` bytes (reversed internally:
    /// swap LSB-first vs MSB-first order so that data[0] → register
    /// closest to MCU).  Then latch once.
    void send(const uint8_t *data, size_t count);

    /// Set all outputs low without changing buffered data.
    void clear();

private:
    uint8_t _dataPin  = 0;
    uint8_t _clockPin = 0;
    uint8_t _latchPin = 0;
    uint8_t _regCount = 0;
    bool    _begun    = false;
};
