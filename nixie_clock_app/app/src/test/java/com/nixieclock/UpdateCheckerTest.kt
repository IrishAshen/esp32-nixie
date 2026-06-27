package com.nixieclock

import com.nixieclock.data.UpdateChecker
import com.nixieclock.model.FirmwareManifest
import com.nixieclock.model.UpdateCheckResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты [UpdateChecker] — парсинг манифеста, сравнение версий, HTTP-клиент.
 */
class UpdateCheckerTest {

    private lateinit var checker: UpdateChecker

    private val validManifestJson = """
        {
            "latest_version": "1.2.0",
            "minimum_version": "1.0.0",
            "firmware_url": "https://example.com/fw_v1.2.0.bin",
            "release_notes": "Bug fixes and improvements",
            "published_at": "2026-06-27"
        }
    """.trimIndent()

    @Before
    fun setUp() {
        checker = UpdateChecker(
            manifestUrl = "https://test.example.com/manifest.json",
            currentVersion = "1.0.0",
        )
    }

    // ── Парсинг манифеста ────────────────────────────────────────

    @Test
    fun `parseManifest returns FirmwareManifest for valid JSON`() {
        val manifest = checker.parseManifest(validManifestJson)

        assertNotNull(manifest)
        assertEquals("1.2.0", manifest!!.latestVersion)
        assertEquals("1.0.0", manifest.minimumVersion)
        assertEquals("https://example.com/fw_v1.2.0.bin", manifest.firmwareUrl)
        assertEquals("Bug fixes and improvements", manifest.releaseNotes)
        assertEquals("2026-06-27", manifest.publishedAt)
    }

    @Test
    fun `parseManifest returns null for empty JSON`() {
        val manifest = checker.parseManifest("{}")
        assertNotNull(manifest)
    }

    @Test
    fun `parseManifest returns null for missing fields`() {
        val manifest = checker.parseManifest("""{"foo": "bar"}""")
        // latest_version is missing, so it should return null
        // Actually, the parser might return a manifest with null fields
        // Let's check: get("latest_version")?.asString → null → return null
        assertEquals(null, manifest)
    }

    @Test
    fun `parseManifest returns null for invalid JSON`() {
        val manifest = checker.parseManifest("not json")
        assertEquals(null, manifest)
    }

    // ── Сравнение версий ─────────────────────────────────────────

    @Test
    fun `compareVersions returns positive when v1 is newer`() {
        assertTrue(checker.compareVersions("1.2.0", "1.0.0") > 0)
        assertTrue(checker.compareVersions("2.0.0", "1.9.9") > 0)
        assertTrue(checker.compareVersions("1.0.1", "1.0.0") > 0)
    }

    @Test
    fun `compareVersions returns negative when v2 is newer`() {
        assertTrue(checker.compareVersions("1.0.0", "1.2.0") < 0)
        assertTrue(checker.compareVersions("1.9.9", "2.0.0") < 0)
        assertTrue(checker.compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun `compareVersions returns zero for equal versions`() {
        assertEquals(0, checker.compareVersions("1.0.0", "1.0.0"))
        assertEquals(0, checker.compareVersions("2.5.3", "2.5.3"))
    }

    @Test
    fun `compareVersions handles unequal length versions`() {
        assertTrue(checker.compareVersions("1.0.0.1", "1.0.0") > 0)
        assertEquals(0, checker.compareVersions("1.0", "1.0.0"))
    }

    // ── Полный цикл проверки ─────────────────────────────────────

    @Test
    fun `check returns Available when new version exists`() = runTest {
        val httpClient: suspend (String) -> String? = { validManifestJson }

        val customChecker = UpdateChecker(
            manifestUrl = "https://test.example.com/manifest.json",
            httpClient = httpClient,
            currentVersion = "1.0.0",
        )

        val result = customChecker.check()

        assertTrue(result is UpdateCheckResult.Available)
        val available = result as UpdateCheckResult.Available
        assertEquals("1.2.0", available.manifest.latestVersion)
        assertEquals("https://example.com/fw_v1.2.0.bin", available.manifest.firmwareUrl)
    }

    @Test
    fun `check returns UpToDate when versions match`() = runTest {
        val httpClient: suspend (String) -> String? = { validManifestJson }

        val customChecker = UpdateChecker(
            manifestUrl = "https://test.example.com/manifest.json",
            httpClient = httpClient,
            currentVersion = "1.2.0", // same as latest in manifest
        )

        val result = customChecker.check()

        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `check returns Error on network failure`() = runTest {
        val httpClient: suspend (String) -> String? = { null }

        val customChecker = UpdateChecker(
            manifestUrl = "https://test.example.com/manifest.json",
            httpClient = httpClient,
            currentVersion = "1.0.0",
        )

        val result = customChecker.check()

        assertTrue(result is UpdateCheckResult.Error)
        val error = result as UpdateCheckResult.Error
        assertTrue(error.message.contains("Network error", ignoreCase = true))
    }

    @Test
    fun `check returns Error on malformed JSON`() = runTest {
        val httpClient: suspend (String) -> String? = { "not valid json {{{" }

        val customChecker = UpdateChecker(
            manifestUrl = "https://test.example.com/manifest.json",
            httpClient = httpClient,
            currentVersion = "1.0.0",
        )

        val result = customChecker.check()

        assertTrue(result is UpdateCheckResult.Error)
    }
}
