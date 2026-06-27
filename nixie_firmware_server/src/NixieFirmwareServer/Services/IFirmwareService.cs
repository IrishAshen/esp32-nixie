using NixieFirmwareServer.Models;

namespace NixieFirmwareServer.Services;

/// <summary>
/// Сервис управления версиями прошивок и файлами .bin.
/// </summary>
public interface IFirmwareService
{
    /// <summary>Получить манифест для Android-приложения (последняя версия).</summary>
    Task<FirmwareManifest?> GetManifestAsync(string baseUrl);

    /// <summary>Получить список всех версий.</summary>
    Task<List<FirmwareVersion>> GetAllVersionsAsync();

    /// <summary>Получить метаданные конкретной версии.</summary>
    Task<FirmwareVersion?> GetVersionAsync(string version);

    /// <summary>Получить путь к .bin файлу для скачивания.</summary>
    string? GetFilePath(string version);

    /// <summary>
    /// Загрузить новую прошивку.
    ///   - Сохраняет .bin файл на диск
    ///   - Вычисляет SHA256
    ///   - Добавляет запись в versions.json
    /// </summary>
    Task<UploadResult> UploadFirmwareAsync(
        string version,
        string releaseNotes,
        Stream fileStream,
        long fileSize);

    /// <summary>
    /// Сравнение семантических версий (X.Y.Z).
    /// Возвращает &gt;0 если v1 &gt; v2, &lt;0 если v1 &lt; v2, 0 если равны.
    /// </summary>
    int CompareVersions(string v1, string v2);
}
