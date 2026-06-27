# Nixie Clock — Android App

Приложение-компаньон для часов на ИН-12 (ESP32 + BLE).

## Возможности

| Функция | Описание |
|---------|----------|
| **BLE scan** | Поиск Nixie Clock по Service UUID в эфире |
| **WiFi setup** | Ввод SSID/пароля → `set_wifi` / `forget_wifi` |
| **Set time** | Date/Time picker → `set_time` |
| **Timezone** | Ползунок -12..+14 → `set_timezone` |
| **12/24h** | Переключатель → `set_format` |
| **Status** | `get_status` + `get_version` — таблица параметров |
| **OTA update** | Проверка мастер-сервера + ручной ввод URL |
| **Event log** | Прокручиваемый лог BLE-команд и ответов |
| **Reboot** | Команда `reboot` |

## Технологии

- **Kotlin** + **Jetpack Compose** + **Material3**
- **Чистый Android BLE API** (без сторонних BLE-библиотек)
- **Gson** — парсинг JSON
- **SharedPreferences** — сохранение настроек
- **ViewModel + StateFlow** — реактивное состояние
- **Coroutines + Flow** — асинхронность

## Структура проекта

```
app/src/main/java/com/nixieclock/
├── NixieApp.kt              # Application (синглтоны)
├── MainActivity.kt          # Single Activity, навигация
├── ble/
│   └── BLEManager.kt        # Чистый Android BLE API
├── viewmodel/
│   └── ClockViewModel.kt    # Единый ViewModel
├── model/
│   └── Models.kt            # Data classes
├── data/
│   ├── SettingsStore.kt     # SharedPreferences
│   └── UpdateChecker.kt     # HTTP-клиент для manifest
├── util/
│   └── Config.kt            # UUIDs, URL-заглушка
├── scan/
│   └── ScanScreen.kt        # Экран сканирования BLE
└── home/
    └── HomeScreen.kt        # Главный экран управления
```

## BLE-подключение

Приложение подключается к ESP32 через GATT:

1. **Сканирование** — фильтр по Service UUID `e20a39f4-...`
2. **Список** — пользователь выбирает устройство из найденных
3. **Connect** — discoverServices, subscribe TX notify
4. **Команды** — JSON → write RX characteristic
5. **Ответы** — TX notify → JSON → парсинг
6. **Disconnect** → автоматический возврат к сканированию

## Мастер-сервер (заглушка)

URL: `https://api.nixie-clock.example.com/firmware/manifest.json`

Ожидаемый формат ответа:
```json
{
  "latest_version": "1.1.0",
  "minimum_version": "1.0.0",
  "firmware_url": "https://...",
  "release_notes": "Описание изменений",
  "published_at": "2026-06-27"
}
```

## Сборка

```bash
cd nixie_clock_app/
./gradlew assembleDebug
./gradlew test                 # Unit-тесты
./gradlew connectedCheck       # Instrumented-тесты (требуется эмулятор)
```

## Зависимости

- compileSdk 34 / targetSdk 34 / minSdk 26
- Compose BOM 2024.05.00
- Gson 2.11
- Kotlin Coroutines 1.8
- MockK 1.13 + Turbine 1.1 + Robolectric 4.12 (тесты)
