package com.nixieclock

import com.nixieclock.model.ClockStatus
import com.nixieclock.model.ConnectionState
import com.nixieclock.model.FirmwareManifest
import com.nixieclock.model.LogEntry
import com.nixieclock.model.LogType
import com.nixieclock.model.NixieDevice
import com.nixieclock.model.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты модельных data-классов.
 *
 * Data class в Kotlin автоматически генерируют equals/hashCode/toString/copy,
 * но убедимся, что всё работает как ожидается.
 */
class ModelsTest {

    // ── NixieDevice ──────────────────────────────────────────────

    @Test
    fun `NixieDevice stores all fields`() {
        val device = NixieDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Nixie Clock",
            rssi = -65,
        )

        assertEquals("AA:BB:CC:DD:EE:FF", device.address)
        assertEquals("Nixie Clock", device.name)
        assertEquals(-65, device.rssi)
    }

    @Test
    fun `NixieDevice equality`() {
        val a = NixieDevice("AA:BB:CC:DD:EE:FF", "Nixie Clock", -65)
        val b = NixieDevice("AA:BB:CC:DD:EE:FF", "Nixie Clock", -65)
        val c = NixieDevice("11:22:33:44:55:66", "Nixie Clock", -70)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    // ── ConnectionState ──────────────────────────────────────────

    @Test
    fun `ConnectionState sealed hierarchy`() {
        val disconnected: ConnectionState = ConnectionState.Disconnected
        val connecting: ConnectionState = ConnectionState.Connecting
        val connected: ConnectionState = ConnectionState.Connected(
            NixieDevice("AA:BB:CC:DD:EE:FF", "Nixie Clock", -65),
        )

        assertTrue(disconnected is ConnectionState.Disconnected)
        assertTrue(connecting is ConnectionState.Connecting)
        assertTrue(connected is ConnectionState.Connected)
    }

    @Test
    fun `Connected state holds device info`() {
        val device = NixieDevice("AA:BB:CC:DD:EE:FF", "Nixie Clock", -50)
        val state = ConnectionState.Connected(device)

        assertEquals("AA:BB:CC:DD:EE:FF", (state as ConnectionState.Connected).device.address)
    }

    // ── ClockStatus ──────────────────────────────────────────────

    @Test
    fun `ClockStatus stores all fields`() {
        val status = ClockStatus(
            wifi = "connected",
            ssid = "MyWiFi",
            ntp = "synced",
            rtc = "ok",
            timezone = 3,
            format = "24h",
            lamps = 6,
            localTime = "15:30:00",
        )

        assertEquals("connected", status.wifi)
        assertEquals("MyWiFi", status.ssid)
        assertEquals("synced", status.ntp)
        assertEquals(3, status.timezone)
    }

    @Test
    fun `ClockStatus with null ssid and localTime`() {
        val status = ClockStatus(
            wifi = "disconnected",
            ssid = null,
            ntp = "pending",
            rtc = "ok",
            timezone = 0,
            format = "12h",
            lamps = 4,
            localTime = null,
        )

        assertEquals(null, status.ssid)
        assertEquals(null, status.localTime)
    }

    // ── FirmwareManifest ─────────────────────────────────────────

    @Test
    fun `FirmwareManifest stores all fields`() {
        val manifest = FirmwareManifest(
            latestVersion = "1.2.0",
            minimumVersion = "1.0.0",
            firmwareUrl = "https://example.com/fw.bin",
            releaseNotes = "Bug fixes",
            publishedAt = "2026-06-27",
        )

        assertEquals("1.2.0", manifest.latestVersion)
        assertEquals("1.0.0", manifest.minimumVersion)
        assertEquals("https://example.com/fw.bin", manifest.firmwareUrl)
        assertEquals("Bug fixes", manifest.releaseNotes)
    }

    // ── UpdateCheckResult ────────────────────────────────────────

    @Test
    fun `UpdateCheckResult sealed hierarchy`() {
        val available = UpdateCheckResult.Available(
            FirmwareManifest("1.1.0", "1.0.0", "url", "notes", "date"),
        )
        val upToDate = UpdateCheckResult.UpToDate
        val error = UpdateCheckResult.Error("Network error")

        assertTrue(available is UpdateCheckResult.Available)
        assertTrue(upToDate is UpdateCheckResult.UpToDate)
        assertTrue(error is UpdateCheckResult.Error)
    }

    // ── LogEntry ─────────────────────────────────────────────────

    @Test
    fun `LogEntry defaults to INFO type`() {
        val entry = LogEntry(text = "Test message")
        assertEquals(LogType.INFO, entry.type)
        assertEquals("Test message", entry.text)
    }

    @Test
    fun `LogEntry stores all types`() {
        val info = LogEntry(text = "Info", type = LogType.INFO)
        val success = LogEntry(text = "Success", type = LogType.SUCCESS)
        val warning = LogEntry(text = "Warning", type = LogType.WARNING)
        val error = LogEntry(text = "Error", type = LogType.ERROR)

        assertEquals(LogType.INFO, info.type)
        assertEquals(LogType.SUCCESS, success.type)
        assertEquals(LogType.WARNING, warning.type)
        assertEquals(LogType.ERROR, error.type)
    }
}
