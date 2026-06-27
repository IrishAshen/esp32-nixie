namespace NixieFirmwareServer.Models;

/// <summary>
/// Метаданные одной версии прошивки.
/// </summary>
public class FirmwareVersion
{
    /// <summary>Семантическая версия (например, "1.0.0")</summary>
    public string Version { get; set; } = "0.0.0";

    /// <summary>Описание изменений</summary>
    public string ReleaseNotes { get; set; } = "";

    /// <summary>Имя файла в хранилище (firmware/firmware_1.0.0.bin)</summary>
    public string FirmwareFileName { get; set; } = "";

    /// <summary>Размер файла в байтах</summary>
    public long FileSize { get; set; }

    /// <summary>SHA256 хеш файла для проверки целостности</summary>
    public string Sha256 { get; set; } = "";

    /// <summary>Дата публикации (UTC)</summary>
    public DateTime PublishedAt { get; set; } = DateTime.UtcNow;
}
