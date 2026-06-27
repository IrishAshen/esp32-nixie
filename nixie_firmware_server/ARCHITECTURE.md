# Архитектура Nixie Firmware Server

> Даты принятия решений: июнь 2026

---

## 1. Назначение

HTTP-сервер для хранения и распространения прошивок Nixie Clock (ESP32).
Android-приложение запрашивает у сервера манифест с последней версией
и получает URL для OTA-обновления.

---

## 2. Технологический стек

| Компонент | Решение | Обоснование |
|-----------|---------|-------------|
| Платформа | ASP.NET Core 8 | Кроссплатформенность, встроенный DI, Minimal API |
| API-стиль | Minimal API | Минимум бойлерплейта для 5 эндпоинтов |
| Хранение метаданных | JSON-файл (data/versions.json) | Для такого объёма данных SQLite избыточен |
| Хранение файлов | Файловая система (firmware/) | .bin файлы на диске, отдаются через `FileStream` |
| Аутентификация | API Key (Middleware) | Проще JWT/OAuth для одного ключа |
| Документация | Swagger/OpenAPI (встроенный) | В Minimal API через `WithOpenApi()` |

### Почему не база данных?

Ожидаемое число версий прошивки — десятки, не тысячи. JSON-файл с синхронизацией
через `SemaphoreSlim` даёт достаточную производительность и нулевые накладные
расходы на администрирование БД.

---

## 3. Структура проекта

```
nixie_firmware_server/
├── Program.cs                           # Точка входа, DI, все эндпоинты
├── appsettings.json                     # Конфигурация (API Key, пути)
├── appsettings.Development.json         # dev-конфиг
├── Models/
│   ├── FirmwareVersion.cs               # Метаданные одной версии
│   ├── FirmwareManifest.cs              # Ответ для GET /manifest
│   ├── FirmwareStoreConfig.cs           # Сильно типизированная конфигурация
│   ├── VersionsDb.cs                    # Корень data/versions.json
│   └── UploadResult.cs                  # Ответ на POST /upload
├── Services/
│   ├── IFirmwareService.cs              # Интерфейс
│   ├── FirmwareService.cs               # Реализация (файлы + JSON)
│   └── ApiKeyMiddleware.cs              # Проверка X-API-Key
├── firmware/                            # Директория с .bin файлами
├── data/                                # Директория с versions.json
└── tests/NixieFirmwareServer.Tests/
    ├── FirmwareServiceTests.cs           # Unit-тесты сервиса (17 тестов)
    └── ApiEndpointTests.cs              # Интеграционные тесты (11 тестов)
```

---

## 4. API-эндпоинты

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| `GET` | `/health` | Нет | Health check |
| `GET` | `/api/firmware/manifest` | Нет | Манифест с последней версией |
| `GET` | `/api/firmware/versions` | Нет | Все версии (история) |
| `GET` | `/api/firmware/download/{version}` | Нет | Скачать .bin файл |
| `GET` | `/api/firmware/download/{version}/sha256` | Нет | SHA256 хеш версии |
| `POST` | `/api/firmware/upload` | ✅ API Key | Загрузить новую прошивку |

### Манифест (GET /api/firmware/manifest)

```json
{
  "latestVersion": "1.1.0",
  "minimumVersion": "1.0.0",
  "firmwareUrl": "https://host/api/firmware/download/1.1.0",
  "releaseNotes": "Bug fixes and improvements",
  "publishedAt": "2026-06-27"
}
```

### Загрузка (POST /api/firmware/upload)

- Content-Type: `multipart/form-data`
- Поля формы: `version`, `releaseNotes` (опционально), `file` (.bin)
- Валидация: версия по regex `^\d+\.\d+\.\d+$`, размер файла > 0
- Аутентификация: заголовок `X-API-Key`

---

## 5. Хранилище

### Структура `data/versions.json`

```json
{
  "versions": [
    {
      "version": "1.0.0",
      "releaseNotes": "Initial release",
      "firmwareFileName": "firmware_1.0.0.bin",
      "fileSize": 1234567,
      "sha256": "abc123...",
      "publishedAt": "2026-06-27T12:00:00Z"
    }
  ]
}
```

### Именование файлов

```
firmware/firmware_{version}.bin
firmware/firmware_1.0.0.bin
firmware/firmware_1.1.0.bin
```

---

## 6. Workflow загрузки прошивки

```
Клиент (curl/CI/CD)              Сервер
      │                              │
      │  POST /api/firmware/upload    │
      │  X-API-Key: secret           │
      │  multipart/form-data:        │
      │    version=1.1.0             │
      │    file=firmware.bin         │
      │─────────────────────────────►│
      │                              │
      │  1. Проверка API Key         │
      │  2. Валидация версии (X.Y.Z) │
      │  3. Сохранение .bin на диск  │
      │  4. Вычисление SHA256        │
      │  5. Проверка размера         │
      │  6. Обновление versions.json │
      │                              │
      │  201 Created                 │
      │◄─────────────────────────────│
```

## 7. Workflow проверки обновлений

```
Android App                       Сервер
      │                              │
      │  GET /api/firmware/manifest   │
      │─────────────────────────────►│
      │                              │
      │  1. Чтение versions.json     │
      │  2. Поиск последней версии   │
      │  3. Формирование URL         │
      │                              │
      │  200 OK (FirmwareManifest)   │
      │◄─────────────────────────────│
      │                              │
      │  Сравнение версий            │
      │  Если latest > current →     │
      │  показать уведомление        │
      │                              │
      │  GET /api/firmware/download/1.1.0 │
      │─────────────────────────────►│
      │  .bin file (stream)          │
      │◄─────────────────────────────│
```

---

## 8. Аутентификация

### API Key Middleware

- Проверяет заголовок `X-API-Key` на эндпоинтах с атрибутом `[RequireApiKey]`
- Ключ хранится в `appsettings.json` → секция `ApiKey`
- Для dev используется `"dev-key"`
- При несовпадении → 401 + JSON-ошибка
- Только `POST /api/firmware/upload` защищён

---

## 9. Обработка ошибок

Все ошибки возвращают JSON с полями `error` и опционально `message`.

| Сценарий | Код | Ответ |
|----------|-----|-------|
| Неверный API Key | 401 | `{"error":"Unauthorized","message":"..."}` |
| Пустой versions.json (нет версий) | 404 | `{"error":"No firmware versions available"}` |
| Неизвестная версия для скачивания | 404 | `{"error":"Version 'X' not found"}` |
| Неверный формат версии | 400 | `{"error":"Version must be in semantic format..."}` |
| Файл не загружен | 400 | `{"error":"Field 'file' is required"}` |
| Нет Content-Type multipart | 400 | `{"error":"Content-Type must be multipart/form-data"}` |

---

## 10. Сравнение версий

В `FirmwareService.CompareVersions()` реализовано поразрядное сравнение
семантических версий "major.minor.patch". Каждая часть парсится как `int`,
сравнение идёт слева направо. Отсутствующие части считаются 0.

Примеры:
- `1.9.9 < 2.0.0`
- `1.1.0 > 1.0.0`
- `1.0 == 1.0.0`
- `1.0.0.1 > 1.0.0`

---

## 11. Зависимости (NuGet)

```xml
<Project Sdk="Microsoft.NET.Sdk.Web">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
  </PropertyGroup>
</Project>
```

Проект использует только встроенные библиотеки .NET 8 без внешних NuGet-пакетов
для production-сборки. Для тестов:

| Пакет | Назначение |
|-------|-----------|
| `Microsoft.NET.Test.Sdk` | Запуск тестов |
| `xunit` + `xunit.runner.visualstudio` | Test runner |
| `Microsoft.AspNetCore.Mvc.Testing` | WebApplicationFactory для интеграционных тестов |

---

## 12. CORS

Настроено разрешение всех origins (`AllowAnyOrigin`) для упрощения разработки.
В production рекомендуется ограничить конкретными доменами.

---

## 13. Тестирование

### Unit-тесты (FirmwareServiceTests — 17 тестов)

| Категория | Число тестов | Что проверяют |
|-----------|-------------|---------------|
| Конструктор | 1 | Создание директорий |
| getAllVersionsAsync | 1 | Пустой список |
| uploadFirmwareAsync | 6 | Успешная загрузка, пустая версия, пустой файл, size mismatch, обновление существующей, SHA256 |
| getManifestAsync | 2 | С манифестом и без версий |
| getVersionAsync | 2 | Существующая и отсутствующая |
| getFilePath | 2 | Существующий и отсутствующий файл |
| compareVersions | 5 | Все случаи: новее/старее/равно/разная длина |

### Интеграционные тесты (ApiEndpointTests — 11 тестов)

| Категория | Число тестов | Что проверяют |
|-----------|-------------|---------------|
| Health | 1 | GET /health |
| manifest | 2 | Без версий → 404, с версиями → OK |
| versions | 2 | Пустой и с версиями |
| download | 2 | Файл существует → OK, не существует → 404 |
| upload | 5 | Без API Key, неверный ключ, верный ключ, неверный формат версии, без файла |
| sha256 | 2 | Существующая и отсутствующая версия |

**Всего: 28 тестов**
