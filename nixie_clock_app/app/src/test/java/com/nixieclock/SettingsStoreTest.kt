package com.nixieclock

import com.nixieclock.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Тесты [SettingsStore] с Robolectric (Android-контекст без эмулятора).
 *
 * Проверяем чтение/запись/очистку настроек в SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        store = SettingsStore(RuntimeEnvironment.getApplication().applicationContext)
        store.clear()
    }

    @Test
    fun `lastOtaUrl defaults to empty string`() {
        assertEquals("", store.lastOtaUrl)
    }

    @Test
    fun `lastOtaUrl stores and retrieves value`() {
        store.lastOtaUrl = "https://example.com/firmware.bin"
        assertEquals("https://example.com/firmware.bin", store.lastOtaUrl)
    }

    @Test
    fun `lastOtaUrl overwrites previous value`() {
        store.lastOtaUrl = "https://example.com/v1.bin"
        store.lastOtaUrl = "https://example.com/v2.bin"
        assertEquals("https://example.com/v2.bin", store.lastOtaUrl)
    }

    @Test
    fun `lastTimezone defaults to 3`() {
        assertEquals(3, store.lastTimezone)
    }

    @Test
    fun `lastTimezone stores value`() {
        store.lastTimezone = -5
        assertEquals(-5, store.lastTimezone)
    }

    @Test
    fun `lastFormat12h defaults to false`() {
        assertFalse(store.lastFormat12h)
    }

    @Test
    fun `lastFormat12h stores boolean`() {
        store.lastFormat12h = true
        assertTrue(store.lastFormat12h)
    }

    @Test
    fun `lastBrightness defaults to 100`() {
        assertEquals(100, store.lastBrightness)
    }

    @Test
    fun `lastBrightness stores value`() {
        store.lastBrightness = 42
        assertEquals(42, store.lastBrightness)
    }

    @Test
    fun `clear removes all stored values`() {
        store.lastOtaUrl = "https://example.com/fw.bin"
        store.lastTimezone = 8
        store.lastFormat12h = true
        store.lastBrightness = 75

        store.clear()

        assertEquals("", store.lastOtaUrl)
        assertEquals(100, store.lastBrightness)
        assertEquals(3, store.lastTimezone)
        assertFalse(store.lastFormat12h)
    }
}
