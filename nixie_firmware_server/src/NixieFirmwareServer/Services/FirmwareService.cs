using System.Security.Cryptography;
using System.Text.Json;
using NixieFirmwareServer.Models;

namespace NixieFirmwareServer.Services;

/// <summary>
/// Реализация <see cref="IFirmwareService"/>.
///
/// Хранение:
///   - .bin файлы — на диске в папке _filesDir (firmware/)
///   - Метаданные версий — JSON-файл _versionsDbPath (data/versions.json)
///
/// Потокобезопасность: все операции с versions.json синхронизированы через _lock.
/// </summary>
public class FirmwareService : IFirmwareService
{
    private readonly string _filesDir;
    private readonly string _versionsDbPath;
    private readonly SemaphoreSlim _lock = new(1, 1);
    private readonly JsonSerializerOptions _jsonOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public FirmwareService(FirmwareStoreConfig config)
    {
        _filesDir = Path.GetFullPath(config.FilesDirectory);
        _versionsDbPath = Path.GetFullPath(
            Path.Combine(config.DataDirectory, "versions.json"));

        Directory.CreateDirectory(_filesDir);
        Directory.CreateDirectory(Path.GetDirectoryName(_versionsDbPath)!);
    }

    // ═══════════════════════════════════════════════════════════════
    //  GetManifestAsync
    // ═══════════════════════════════════════════════════════════════

    public async Task<FirmwareManifest?> GetManifestAsync(string baseUrl)
    {
        var versions = await GetAllVersionsAsync();
        var latest = versions
            .OrderByDescending(v => v.PublishedAt)
            .FirstOrDefault();

        if (latest == null) return null;

        // Ищем минимальную версию среди всех
        var minVersion = versions
            .OrderBy(v => v.Version, new VersionComparer())
            .First()
            .Version;

        var baseUri = baseUrl.TrimEnd('/');
        return new FirmwareManifest
        {
            LatestVersion = latest.Version,
            MinimumVersion = minVersion,
            FirmwareUrl = $"{baseUri}/api/firmware/download/{latest.Version}",
            ReleaseNotes = latest.ReleaseNotes,
            PublishedAt = latest.PublishedAt.ToString("yyyy-MM-dd"),
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  GetAllVersionsAsync / GetVersionAsync
    // ═══════════════════════════════════════════════════════════════

    public async Task<List<FirmwareVersion>> GetAllVersionsAsync()
    {
        await _lock.WaitAsync();
        try
        {
            var db = await ReadDbAsync();
            return db.Versions
                .OrderByDescending(v => v.PublishedAt)
                .ToList();
        }
        finally
        {
            _lock.Release();
        }
    }

    public async Task<FirmwareVersion?> GetVersionAsync(string version)
    {
        var versions = await GetAllVersionsAsync();
        return versions.FirstOrDefault(v =>
            v.Version.Equals(version, StringComparison.OrdinalIgnoreCase));
    }

    // ═══════════════════════════════════════════════════════════════
    //  GetFilePath
    // ═══════════════════════════════════════════════════════════════

    public string? GetFilePath(string version)
    {
        var path = Path.Combine(_filesDir, $"firmware_{version}.bin");
        return File.Exists(path) ? path : null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  UploadFirmwareAsync
    // ═══════════════════════════════════════════════════════════════

    public async Task<UploadResult> UploadFirmwareAsync(
        string version,
        string releaseNotes,
        Stream fileStream,
        long fileSize)
    {
        if (string.IsNullOrWhiteSpace(version))
            return Fail("Version is required");

        if (fileSize <= 0)
            return Fail("File is empty");

        var filePath = Path.Combine(_filesDir, $"firmware_{version}.bin");

        // Сохраняем файл на диск
        await using (var fs = new FileStream(filePath, FileMode.Create, FileAccess.Write))
        {
            await fileStream.CopyToAsync(fs);
        }

        // Проверяем размер
        var fileInfo = new FileInfo(filePath);
        if (fileInfo.Length != fileSize)
        {
            File.Delete(filePath);
            return Fail($"Size mismatch: expected {fileSize}, got {fileInfo.Length}");
        }

        // Вычисляем SHA256
        string sha256;
        await using (var fs = new FileStream(filePath, FileMode.Open, FileAccess.Read))
        {
            using var sha = SHA256.Create();
            var hash = await sha.ComputeHashAsync(fs);
            sha256 = BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
        }

        // Сохраняем метаданные
        await _lock.WaitAsync();
        try
        {
            var db = await ReadDbAsync();
            var existing = db.Versions
                .FirstOrDefault(v =>
                    v.Version.Equals(version, StringComparison.OrdinalIgnoreCase));

            if (existing != null)
            {
                // Версия уже существует — обновляем
                existing.ReleaseNotes = releaseNotes;
                existing.FirmwareFileName = $"firmware_{version}.bin";
                existing.FileSize = fileSize;
                existing.Sha256 = sha256;
                existing.PublishedAt = DateTime.UtcNow;
            }
            else
            {
                db.Versions.Add(new FirmwareVersion
                {
                    Version = version,
                    ReleaseNotes = releaseNotes,
                    FirmwareFileName = $"firmware_{version}.bin",
                    FileSize = fileSize,
                    Sha256 = sha256,
                    PublishedAt = DateTime.UtcNow,
                });
            }

            await WriteDbAsync(db);

            return new UploadResult
            {
                Success = true,
                Version = version,
                Message = existing != null
                    ? $"Version {version} updated"
                    : $"Version {version} uploaded",
            };
        }
        finally
        {
            _lock.Release();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CompareVersions
    // ═══════════════════════════════════════════════════════════════

    public int CompareVersions(string v1, string v2)
    {
        var parts1 = v1.Split('.').Select(s =>
            int.TryParse(s, out var n) ? n : 0).ToArray();
        var parts2 = v2.Split('.').Select(s =>
            int.TryParse(s, out var n) ? n : 0).ToArray();

        for (int i = 0; i < Math.Max(parts1.Length, parts2.Length); i++)
        {
            var a = i < parts1.Length ? parts1[i] : 0;
            var b = i < parts2.Length ? parts2[i] : 0;
            if (a != b) return a - b;
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════════

    private async Task<VersionsDb> ReadDbAsync()
    {
        if (!File.Exists(_versionsDbPath))
            return new VersionsDb();

        var json = await File.ReadAllTextAsync(_versionsDbPath);
        return JsonSerializer.Deserialize<VersionsDb>(json, _jsonOptions)
               ?? new VersionsDb();
    }

    private async Task WriteDbAsync(VersionsDb db)
    {
        var json = JsonSerializer.Serialize(db, _jsonOptions);
        await File.WriteAllTextAsync(_versionsDbPath, json);
    }

    private static UploadResult Fail(string message) =>
        new() { Success = false, Message = message };
}

/// <summary>
/// Компаратор для семантических версий. Используется при сортировке.
/// </summary>
internal class VersionComparer : IComparer<string>
{
    public int Compare(string? x, string? y)
    {
        if (x == null && y == null) return 0;
        if (x == null) return -1;
        if (y == null) return 1;

        var partsX = x.Split('.').Select(s =>
            int.TryParse(s, out var n) ? n : 0).ToArray();
        var partsY = y.Split('.').Select(s =>
            int.TryParse(s, out var n) ? n : 0).ToArray();

        for (int i = 0; i < Math.Max(partsX.Length, partsY.Length); i++)
        {
            var a = i < partsX.Length ? partsX[i] : 0;
            var b = i < partsY.Length ? partsY[i] : 0;
            if (a != b) return a.CompareTo(b);
        }
        return 0;
    }
}
