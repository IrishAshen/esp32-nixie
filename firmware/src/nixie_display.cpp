#include "nixie_display.h"
#include "config.h"

NixieDisplay::NixieDisplay(ShiftRegister &sr) : _sr(sr) {}

// ── private helpers ────────────────────────────────────────────

uint8_t NixieDisplay::packNibbles(uint8_t low, uint8_t high) {
    // Clamp each nibble to 0 – 15 (values 10–15 blank the tube
    // on K155ID1, useful for blanking unused positions).
    return ( (high & 0x0F) << 4 ) | (low & 0x0F);
}

// ── public API ─────────────────────────────────────────────────

void NixieDisplay::show(uint8_t hours, uint8_t minutes,
                        uint8_t seconds, bool format12h) {

    // ── 12‑hour conversion ────────────────────────────────────
    uint8_t dispHour = hours;
    if (format12h) {
        if      (hours == 0)  dispHour = 12;
        else if (hours > 12)  dispHour = hours - 12;
        // hours 1–12 pass through unchanged
    }

    // ── Extract decimal digits ─────────────────────────────────
    uint8_t hh_hi = dispHour / 10;
    uint8_t hh_lo = dispHour % 10;
    uint8_t mm_hi = minutes / 10;
    uint8_t mm_lo = minutes % 10;

#if LAMP_COUNT == LAMPS_6
    uint8_t ss_hi = seconds / 10;
    uint8_t ss_lo = seconds % 10;
#endif

    // ── Build register bytes ───────────────────────────────────
    //  Register 0 = lamp 0 (low nibble), lamp 1 (high nibble)
    //  Register 1 = lamp 2 (low nibble), lamp 3 (high nibble)
    //  Register 2 = lamp 4 (low nibble), lamp 5 (high nibble)
    uint8_t regs[SHIFT_REG_COUNT];

    regs[0] = packNibbles(hh_lo, hh_hi);   // lamp 1 / lamp 0
    regs[1] = packNibbles(mm_lo, mm_hi);   // lamp 3 / lamp 2

#if LAMP_COUNT == LAMPS_6
    if (SHIFT_REG_COUNT >= 3) {
        regs[2] = packNibbles(ss_lo, ss_hi); // lamp 5 / lamp 4
    }
#endif

    _sr.send(regs, SHIFT_REG_COUNT);
}

void NixieDisplay::blank() {
    // Value 0xAA = nibble 10 on both halves → all K155ID1 outputs off.
    uint8_t regs[SHIFT_REG_COUNT];
    for (auto &r : regs) r = 0xAA;
    _sr.send(regs, SHIFT_REG_COUNT);
}
