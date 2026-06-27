package com.nixieclock.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nixieclock.model.FirmwareManifest
import com.nixieclock.model.UpdateCheckResult
import com.nixieclock.util.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка наличия новой прошивки на мастер-сервере.
 *
 * HTTP-клиент можно подменить через [httpClient] для тестирования.
 * По умолчанию использует [java.net.HttpURLConnection].
 *
 * Сравнивает версии через семантическое версионирование (major.minor.patch).
 */
class UpdateChecker(
    /** URL до manifest.json на мастер-сервере */
    private val manifestUrl: String = Config.FIRMWARE_MANIFEST_URL,
    /** HTTP-клиент для тестирования. Принимает URL, возвращает тело ответа или null. */
    private val httpClient: suspend (String) -> String? = { url -> defaultFetch(url) },
    /** Текущая версия прошивки для сравнения */
    private val currentVersion: String = "1.0.0",
) {
    private val gson = Gson()

    /**
     * Проверить наличие обновлений.
     * Выполняется в I/O потоке.
     */
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val json = httpClient(manifestUrl)
                ?: return@withContext UpdateCheckResult.Error("Network error: no response")

            val manifest = parseManifest(json)
                ?: return@withContext UpdateCheckResult.Error("Invalid manifest format")

            if (compareVersions(manifest.latestVersion, currentVersion) > 0) {
                UpdateCheckResult.Available(manifest)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Парсинг JSON-манифеста.
     * Ожидаемый формат:
     * {
     *   "latest_version": "1.1.0",
     *   "minimum_version": "1.0.0",
     *   "firmware_url": "https://...",
     *   "release_notes": "...",
     *   "published_at": "2026-06-27"
     * }
     */
    fun parseManifest(json: String): FirmwareManifest? {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            FirmwareManifest(
                latestVersion = obj.get("latest_version")?.asString ?: return null,
                minimumVersion = obj.get("minimum_version")?.asString ?: return null,
                firmwareUrl = obj.get("firmware_url")?.asString ?: return null,
                releaseNotes = obj.get("release_notes")?.asString ?: "",
                publishedAt = obj.get("published_at")?.asString ?: "",
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Сравнение семантических версий "X.Y.Z".
     * Возвращает положительное число, если v1 > v2.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }

    companion object {
        /** Default HTTP client using HttpURLConnection */
        private suspend fun defaultFetch(url: String): String? {
            var connection: HttpURLConnection? = null
            return try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection?.disconnect()
            }
        }
    }
}
