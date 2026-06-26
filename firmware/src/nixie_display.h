#pragma once

#include <cstdint>
#include "shift_register.h"

// ─────────────────────────────────────────────────────────────────
//  Nixie display – converts H/M/S to BCD nibbles and pushes them
//  through the 74HC595 → K155ID1 chain.
//
//  Each 8-bit register holds two BCD nibbles (4-bit each):
//    [high nibble] → К155ИД1 Lamp N+1
//    [low nibble]  → К155ИД1 Lamp N
// ─────────────────────────────────────────────────────────────────

class NixieDisplay {
public:
    /// Bind to an already-initialised ShiftRegister driver.
    explicit NixieDisplay(ShiftRegister &sr);

    /// Update the display with new time values.
    /// @param hours   0–23 (converted to 12h inside if enabled)
    /// @param minutes 0–59
    /// @param seconds 0–59
    /// @param format12h  true → 12-hour display
    void show(uint8_t hours, uint8_t minutes, uint8_t seconds,
              bool format12h = false);

    /// Blank all tubes (write values ≥10 → K155ID1 all-outputs-off).
    void blank();

private:
    ShiftRegister &_sr;

    /// Compute register byte from two digit values (each 0–9).
    static uint8_t packNibbles(uint8_t lowNibble, uint8_t highNibble);
};
