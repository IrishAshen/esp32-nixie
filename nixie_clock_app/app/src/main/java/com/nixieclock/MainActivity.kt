package com.nixieclock

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nixieclock.home.HomeScreen
import com.nixieclock.model.ConnectionState
import com.nixieclock.scan.ScanScreen
import com.nixieclock.viewmodel.ClockViewModel

/**
 * Single Activity — навигация между ScanScreen и HomeScreen
 * управляется состоянием BLE-подключения.
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ClockViewModel

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* BT enable result — ignore, scan will fail gracefully if still off */ }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions result — ignored, scan will fail gracefully if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NixieApp
        viewModel = ViewModelProvider(
            this,
            ViewModelFactory(app.bleManager, app.settingsStore, app.updateChecker),
        )[ClockViewModel::class.java]

        // Check & request Bluetooth permissions
        checkPermissions()

        setContent {
            NixieClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            viewModel.disconnect()
        }
    }

    override fun onResume() {
        super.onResume()
        // Перезапускаем сканирование, если мы не подключены
        val state = viewModel.connectionState.value
        if (state is ConnectionState.Disconnected) {
            viewModel.startScan()
        }
    }

    // ── Permissions ──────────────────────────────────────────────

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ нужны BLUETOOTH_SCAN и BLUETOOTH_CONNECT
            val missing = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missing.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missing.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
            }
        } else {
            // Android 6-11 нужен ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
            }
        }

        // Запрос включения Bluetooth
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isEnabled == false) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Theme
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun NixieClockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        content = content,
    )
}

// ═══════════════════════════════════════════════════════════════════
//  Main screen — navigates between Scan and Home
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MainScreen(viewModel: ClockViewModel = viewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()

    // Автостарт сканирования при загрузке
    LaunchedEffect(Unit) {
        if (connectionState is ConnectionState.Disconnected) {
            viewModel.startScan()
        }
    }

    when (connectionState) {
        is ConnectionState.Disconnected, is ConnectionState.Connecting -> {
            ScanScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
        is ConnectionState.Connected -> {
            HomeScreen(
                viewModel = viewModel,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ViewModel Factory
// ═══════════════════════════════════════════════════════════════════

class ViewModelFactory(
    private val bleManager: com.nixieclock.ble.BLEManager,
    private val settingsStore: com.nixieclock.data.SettingsStore,
    private val updateChecker: com.nixieclock.data.UpdateChecker,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClockViewModel::class.java)) {
            return ClockViewModel(bleManager, settingsStore, updateChecker) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
