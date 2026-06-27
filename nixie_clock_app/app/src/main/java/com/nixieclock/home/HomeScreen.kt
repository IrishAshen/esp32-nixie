package com.nixieclock.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisconnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SignalWifiStatusbar4Bar
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nixieclock.model.LogEntry
import com.nixieclock.model.LogType
import com.nixieclock.model.UpdateCheckResult
import com.nixieclock.viewmodel.ClockViewModel
import java.util.Calendar
import java.util.TimeZone

/**
 * Экран управления часами. Отображается после BLE-подключения.
 *
 * Секции:
 * 1. Информация о подключении + кнопка отключения
 * 2. WiFi (SSID, пароль, connect/forget)
 * 3. Настройки времени (zone, format)
 * 4. Статус часов
 * 5. OTA-обновление
 * 6. Быстрые команды
 * 7. Лог событий
 */
@Composable
fun HomeScreen(
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier,
) {
    val clockStatus by viewModel.clockStatus.collectAsState()
    val clockVersion by viewModel.clockVersion.collectAsState()
    val eventLog by viewModel.eventLog.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Connection header ────────────────────────────────────
        item { ConnectionHeader(viewModel) }

        // ── WiFi section ─────────────────────────────────────────
        item { WifiSection(viewModel) }

        // ── Time settings section ────────────────────────────────
        item { TimeSection(viewModel) }

        // ── Status section ───────────────────────────────────────
        item { StatusSection(clockStatus, clockVersion) }

        // ── OTA section ──────────────────────────────────────────
        item {
            OtaSection(
                viewModel = viewModel,
                isCheckingUpdate = isCheckingUpdate,
                updateResult = updateResult,
            )
        }

        // ── Quick commands ─────────────────────────────────────────
        item { QuickCommandsSection(viewModel) }

        // ── Event log ────────────────────────────────────────────
        item {
            EventLogSection(
                entries = eventLog,
                onClear = viewModel::clearLog,
            )
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Connection header
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ConnectionHeader(viewModel: ClockViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Watch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nixie Clock",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            OutlinedButton(onClick = viewModel::disconnect) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Disconnect",
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Disconnect")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  WiFi section
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun WifiSection(viewModel: ClockViewModel) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    SectionCard(title = "WiFi", icon = Icons.Default.Wifi) {
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("SSID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { viewModel.setWifi(ssid, password) },
                enabled = ssid.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.SignalWifiStatusbar4Bar, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Connect")
            }
            OutlinedButton(onClick = viewModel::forgetWifi) {
                Icon(Icons.Default.WifiOff, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Forget")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Time section
// ═══════════════════════════════════════════════════════════════════

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimeSection(viewModel: ClockViewModel) {
    val context = LocalContext.current
    var timezoneOffset by remember { mutableFloatStateOf(3f) }
    var is12hFormat by remember { mutableStateOf(false) }

    // Date/time picker state
    val calendar = remember { Calendar.getInstance() }

    SectionCard(title = "Time Settings", icon = Icons.Default.Schedule) {
        // ── Set date/time button ─────────────────────────────────
        Button(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                calendar.set(year, month, dayOfMonth, hourOfDay, minute, 0)
                                calendar.set(Calendar.MILLISECOND, 0)
                                viewModel.setTime(calendar.timeInMillis / 1000)
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true,
                        ).show()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Timer, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Set Date & Time")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Timezone slider ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("UTC", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = timezoneOffset,
                onValueChange = { timezoneOffset = it },
                onValueChangeFinished = {
                    viewModel.setTimezone(timezoneOffset.toInt())
                },
                valueRange = -12f..14f,
                steps = 25,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = "UTC${if (timezoneOffset >= 0) "+" else ""}${timezoneOffset.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 12h / 24h toggle ─────────────────────────────────────
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !is12hFormat,
                onClick = {
                    is12hFormat = false
                    viewModel.setFormat(false)
                },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) {
                Text("24h")
            }
            SegmentedButton(
                selected = is12hFormat,
                onClick = {
                    is12hFormat = true
                    viewModel.setFormat(true)
                },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) {
                Text("12h")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Status section
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StatusSection(
    status: com.nixieclock.model.ClockStatus?,
    version: Map<String, Any?>?,
) {
    SectionCard(title = "Status", icon = Icons.Default.Info) {
        if (status != null) {
            StatusRow("WiFi", status.wifi, status.ssid ?: "-")
            StatusRow("NTP", status.ntp, "")
            StatusRow("RTC", status.rtc, "")
            StatusRow("Timezone", "UTC${if (status.timezone >= 0) "+" else ""}${status.timezone}", "")
            StatusRow("Format", status.format, "")
            StatusRow("Lamps", "${status.lamps}", "")
            if (status.localTime != null) {
                StatusRow("Local Time", status.localTime, "", isHighlight = true)
            }
        } else {
            Text(
                text = "Press 'Get Status' to load",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (version != null) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            version.forEach { (key, value) ->
                StatusRow(key.replace("_", " ").replaceFirstChar { it.uppercase() }, "$value", "")
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    sub: String,
    isHighlight: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlight) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.onSurface,
        )
        if (sub.isNotBlank()) {
            Text(
                text = " · $sub",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  OTA section
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun OtaSection(
    viewModel: ClockViewModel,
    isCheckingUpdate: Boolean,
    updateResult: UpdateCheckResult?,
) {
    var otaUrl by remember { mutableStateOf("") }

    SectionCard(title = "Firmware Update", icon = Icons.Default.SystemUpdateAlt) {
        // ── Check for updates ────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = viewModel::checkFirmwareUpdate,
                enabled = !isCheckingUpdate,
                modifier = Modifier.weight(1f),
            ) {
                if (isCheckingUpdate) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Check Updates")
            }
        }

        // ── Update result ────────────────────────────────────────
        when (val result = updateResult) {
            is UpdateCheckResult.Available -> {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "v${result.manifest.latestVersion} available",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = result.manifest.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = result.manifest.firmwareUrl,
                            onValueChange = { otaUrl = it },
                            label = { Text("Firmware URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.startOTA(otaUrl) },
                            enabled = otaUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update Now")
                        }
                    }
                }
            }
            is UpdateCheckResult.UpToDate -> {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = "Firmware is up to date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            is UpdateCheckResult.Error -> {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            null -> { /* not checked yet */ }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Manual OTA ───────────────────────────────────────────
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Manual OTA",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = otaUrl,
            onValueChange = { otaUrl = it },
            label = { Text("Firmware URL") },
            placeholder = { Text("https://example.com/firmware.bin") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.startOTA(otaUrl) },
            enabled = otaUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
            ),
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Start OTA")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Quick commands section
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun QuickCommandsSection(viewModel: ClockViewModel) {
    SectionCard(title = "Commands", icon = Icons.Default.Lock) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = viewModel::getStatus,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Status", fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = viewModel::getVersion,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Version", fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = viewModel::listCommands,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cmds", fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = viewModel::reboot,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reboot", fontSize = 12.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Event log section
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EventLogSection(
    entries: List<LogEntry>,
    onClear: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    SectionCard(
        title = "Event Log",
        icon = Icons.Default.Info,
        trailing = {
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Clear", fontSize = 12.sp)
                }
            }
        },
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "No events yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            ) {
                items(entries, key = { it.timestamp }) { entry ->
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = when (entry.type) {
                            LogType.SUCCESS -> MaterialTheme.colorScheme.primary
                            LogType.WARNING -> MaterialTheme.colorScheme.tertiary
                            LogType.ERROR -> MaterialTheme.colorScheme.error
                            LogType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Shared section card wrapper
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
