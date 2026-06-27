# Архитектура Nixie Clock Android App

> Даты принятия решений: июнь 2026

---

## 1. Технологический стек

| Компонент | Решение | Обоснование |
|-----------|---------|-------------|
| Язык | Kotlin | Современный, лаконичный, официально рекомендован Google |
| UI | Jetpack Compose + Material3 | Декларативный UI, реактивное обновление, современный стандарт Android |
| Архитектура | Single Activity + ViewModel | Одна Activity управляет навигацией через состояние |
| Асинхронность | Kotlin Coroutines + Flow | Structured concurrency, реактивные потоки данных |
| JSON | Gson | Лёгкая, проверенная библиотека |
| Хранение настроек | SharedPreferences | Встроенное Android-хранилище для пар ключ-значение |
| BLE | Чистый Android BLE API (без сторонних библиотек) | Протокол простой (один сервис, две характеристики), библиотека не нужна |

### Почему чистый BLE API?

Задача приложения — работа с одним BLE-устройством, одним сервисом и парой TX/RX характеристик. Android BLE API с корутинами (suspendCancellableCoroutine, callbackFlow) даёт компактный и прозрачный код. Сторонняя библиотека (FastBleLib/RxAndroidBle) добавила бы чужую абстракцию без существенного упрощения.

---

## 2. Структура проекта

```
app/src/main/java/com/nixieclock/
├── NixieApp.kt              # Application — синглтоны BLEManager, SettingsStore, UpdateChecker
├── MainActivity.kt          # Single Activity + навигация по состоянию + запрос разрешений
├── ble/
│   └── BLEManager.kt        # Весь BLE в одном классе
├── viewmodel/
│   └── ClockViewModel.kt    # Единый ViewModel на всё приложение
├── model/
│   └── Models.kt            # Data-классы и sealed-интерфейсы
├── data/
│   ├── SettingsStore.kt     # SharedPreferences
│   └── UpdateChecker.kt     # HTTP-клиент для проверки новой прошивки
├── util/
│   └── Config.kt            # UUIDs, константы, URL-заглушка мастер-сервера
├── scan/
│   └── ScanScreen.kt        # Экран BLE-сканирования
└── home/
    └── HomeScreen.kt        # Главный экран управления часами
```

### Почему пакеты по фичам, а не по слоям?

Экранов всего два (scan + home). Разрастаться нечему. Плоская структура с явным разделением обязанностей проще, чем многоуровневая иерархия.

### Почему один ViewModel?

BLEManager — синглтон. Всё состояние (сканирование, подключение, статус, лог) взаимосвязано. Разделение на несколько ViewModel создало бы лишнюю сложность синхронизации. `ClockViewModel` остаётся компактным (~350 строк с комментариями).

---

## 3. BLE-протокол

### Подключение

```
Приложение                  ESP32 (Nixie Clock)
    │                              │
    │  Scan (filter by Service UUID)│
    │◄───────────────────────────── │
    │  Выбор устройства из списка    │
    │──────────────────────────────►│
    │  Connect + Discover Services  │
    │◄───────────────────────────── │
    │  Subscribe TX Notify          │
    │──────────────────────────────►│
    │         Connected             │
    │◄───────────────────────────── │
```

### Характеристики

| Направление | Характеристика | UUID | Свойства |
|-------------|---------------|------|----------|
| ESP32 → телефон | TX | e20a39f5-... | NOTIFY |
| телефон → ESP32 | RX | e20a39f6-... | WRITE |

### Формат данных

Все команды и ответы — JSON-строки. Подробное описание — в ARCHITECTURE.md прошивки.

### Сканирование

- Фильтр по Service UUID (`e20a39f4-...`) — аппаратная фильтрация на уровне ОС
- Результаты дедуплицируются по MAC-адресу
- Максимум 20 устройств в списке
- При отключении — автоматический возврат к сканированию

---

## 4. Навигация

Навигация управляется состоянием `ConnectionState`:

```
┌──────────────────┐
│   Application    │
│  ┌────────────┐  │
│  │ ScanScreen │──│── Disconnected / Connecting
│  └─────┬──────┘  │
│        │ connect │
│  ┌─────▼──────┐  │
│  │ HomeScreen │──│── Connected
│  └────────────┘  │
│        │         │
│        │ disconnect / BLE disconnect
│        ▼         │
│  ┌────────────┐  │
│  │ ScanScreen │──│
│  └────────────┘  │
└──────────────────┘
```

---

## 5. Команды прошивки

Приложение поддерживает все 10 команд прошивки ESP32:

| Команда | UI-элемент | Параметры |
|---------|-----------|-----------|
| `set_wifi` | Текстовые поля SSID/Password + кнопка Connect | `ssid`, `password` |
| `forget_wifi` | Кнопка Forget | — |
| `set_time` | DatePicker + TimePicker | `timestamp` (Unix epoch) |
| `set_timezone` | Slider -12..+14 | `offset` |
| `set_format` | SegmentedButton 12h/24h | `value` ("12h"/"24h") |
| `get_status` | Кнопка Status | — |
| `get_version` | Кнопка Version | — |
| `ota` | Текстовое поле URL + кнопка Start OTA | `url` |
| `list_commands` | Кнопка Cmds | — |
| `reboot` | Кнопка Reboot (красная) | — |

### Обработка ответов

Все входящие JSON-сообщения парсятся в `ClockViewModel.parseEvent()`:
- `event: "response"` — ответ на команду, парсится в `ClockStatus` или Version
- `event: "wifi"` / `"ntp"` / `"ota"` — асинхронные события, отображаются в логе
- Все события также попадают в EventLog

---

## 6. Проверка обновлений прошивки

### Мастер-сервер

Заглушка URL: `https://api.nixie-clock.example.com/firmware/manifest.json`

Ожидаемый формат ответа:
```json
{
  "latest_version": "1.1.0",
  "minimum_version": "1.0.0",
  "firmware_url": "https://...",
  "release_notes": "Bug fixes and improvements",
  "published_at": "2026-06-27"
}
```

### Логика проверки

1. При подключении (или по кнопке) → `GET` на мастер-сервер
2. Парсинг манифеста → сравнение `latest_version` с текущей версией
3. Если новее → показать карточку с release notes и кнопкой OTA
4. Если актуально → "Firmware is up to date"
5. Ошибка сети/парсинга → сообщение об ошибке

### Сравнение версий

Реализовано в `UpdateChecker.compareVersions()` — семантическое сравнение "major.minor.patch". Каждая часть парсится как целое число, сравнение поразрядно.

---

## 7. UI-компоненты (Compose)

### ScanScreen

- Анимация поиска (BluetoothSearching icon + пульсирующая прозрачность)
- Список устройств с RSSI-индикатором (цвет: зелёный/жёлтый/оранжевый/красный)
- EmptyState — при отсутствии результатов
- Кнопка повторного сканирования

### HomeScreen

Секции (Card с иконкой и заголовком):

1. **ConnectionHeader** — информация о подключении, кнопка Disconnect
2. **WiFi** — SSID, Password (маскированный), Connect / Forget
3. **Time Settings** — Set Date/Time (DatePickerDialog + TimePickerDialog), Timezone slider, SegmentedButton 12h/24h
4. **Status** — таблица параметров (WiFi, NTP, RTC, TZ, Format, Lamps, Local Time)
5. **Firmware Update** — Check Updates → карточка Available или UpToDate или Error; Manual OTA
6. **Quick Commands** — 4 кнопки ряда (Status / Version / Cmds / Reboot)
7. **Event Log** — прокручиваемый LazyColumn с автоскроллом к последнему, разных цветов для INFO/SUCCESS/ERROR

---

## 8. Разрешения (Permissions)

| Android API | Требуемые разрешения |
|-------------|---------------------|
| Android 12+ (API 31+) | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` |
| Android 6-11 (API 23-30) | `ACCESS_FINE_LOCATION` |
| Все версии | Запрос включения Bluetooth через `ACTION_REQUEST_ENABLE` |

---

## 9. Тестирование

| Тестовый класс | Тип | Что проверяет |
|---------------|-----|---------------|
| `ModelsTest` | Unit (15 тестов) | Data-классы, sealed-иерархии, иммутабельность |
| `SettingsStoreTest` | Unit с Robolectric (9 тестов) | SharedPreferences: чтение, запись, перезапись, очистка |
| `UpdateCheckerTest` | Unit с mock HTTP (10 тестов) | Парсинг манифеста, сравнение версий, весь цикл check() |
| `ClockViewModelTest` | Unit с MockK (16 тестов) | Состояния, сканирование, connect/disconnect, команды, OTA, очистка |

### Инструменты

- **JUnit 4** — runner
- **MockK** — мокирование Kotlin-объектов
- **Robolectric** — Android-контекст без эмулятора
- **Turbine** — тестирование Flow
- **kotlinx-coroutines-test** — виртуальное время

---

## 10. Зависимости (Gradle)

```kotlin
// AndroidX + Compose
implementation("androidx.activity:activity-compose:1.9.0")
implementation(platform("androidx.compose:compose-bom:2024.05.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

// JSON
implementation("com.google.code.gson:gson:2.11.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// Tests
testImplementation("io.mockk:mockk:1.13.11")
testImplementation("app.cash.turbine:turbine:1.1.0")
testImplementation("org.robolectric:robolectric:4.12.2")
```

---

## 11. Локальное хранение (SharedPreferences)

| Ключ | Тип | Назначение |
|------|-----|-----------|
| `last_ota_url` | String | Последний введённый URL OTA |
| `last_timezone` | Int | Последний часовой пояс (по умолч. 3) |
| `last_format_12h` | Boolean | Последний формат времени (по умолч. false = 24h) |

Очистка: метод `SettingsStore.clear()`.
