namespace NixieFirmwareServer.Models;

/// <summary>
/// Манифест, который отдаётся Android-приложению по GET /api/firmware/manifest.
/// </summary>
public class FirmwareManifest
{
    /// <summary>Последняя доступная версия</summary>
    public string LatestVersion { get; set; } = "";

    /// <summary>Минимальная поддерживаемая версия (для совместимости протокола)</summary>
    public string MinimumVersion { get; set; } = "";

    /// <summary>Полный URL для скачивания .bin файла</summary>
    public string FirmwareUrl { get; set; } = "";

    /// <summary>Описание изменений</summary>
    public string ReleaseNotes { get; set; } = "";

    /// <summary>Дата публикации</summary>
    public string PublishedAt { get; set; } = "";
}
