package com.nixieclock

import android.app.Application
import com.nixieclock.ble.BLEManager
import com.nixieclock.data.SettingsStore
import com.nixieclock.data.UpdateChecker

/**
 * Application class — хранит синглтоны [BLEManager], [SettingsStore], [UpdateChecker].
 *
 * Синглтоны нужны, чтобы [ClockViewModel] и разные экраны
 * работали с одним и тем же экземпляром BLE-менеджера.
 */
class NixieApp : Application() {

    /** BLE-менеджер (один на всё приложение) */
    lateinit var bleManager: BLEManager
        private set

    /** Локальное хранилище настроек */
    lateinit var settingsStore: SettingsStore
        private set

    /** Проверщик обновлений прошивки */
    lateinit var updateChecker: UpdateChecker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        bleManager = BLEManager(this)
        settingsStore = SettingsStore(this)
        updateChecker = UpdateChecker()
    }

    companion object {
        lateinit var instance: NixieApp
            private set
    }
}
