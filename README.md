# Nixie Clock — ИН-12 на ESP32

Монорепозиторий проекта Nixie Clock — часы на газоразрядных индикаторах ИН-12,
управляемые ESP32.

## Состав проекта

| Директория | Проект | Стек |
|-----------|--------|------|
| [`firmware/`](firmware/) | Прошивка ESP32 | C++ (PlatformIO, Arduino) |
| [`nixie_clock_app/`](nixie_clock_app/) | Android-приложение | Kotlin + Jetpack Compose |
| [`nixie_firmware_server/`](nixie_firmware_server/) | Сервер обновлений | ASP.NET Core (C#) |

## CI/CD

| Workflow | Статус | Описание |
|----------|--------|----------|
| [Firmware](.github/workflows/firmware.yml) | — | PlatformIO build → firmware.bin |
| [Android](.github/workflows/android.yml) | — | Gradle build + unit tests → APK |
| [Server](.github/workflows/server.yml) | — | dotnet build + test → publish |

## Быстрый старт

### Прошивка

```bash
cd firmware
pio run
pio run --target upload
```

### Android-приложение

Открыть `nixie_clock_app/` в Android Studio →
Sync Gradle → Run.

### Сервер

```bash
cd nixie_firmware_server
dotnet build
dotnet test
dotnet run --project src/NixieFirmwareServer
```

## Архитектура

Подробное описание архитектурных решений в `ARCHITECTURE.md` каждого проекта.

## Лицензия

MIT
