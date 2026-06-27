namespace NixieFirmwareServer.Models;

/// <summary>
/// Конфигурация хранилища прошивок (секция FirmwareStore в appsettings.json).
/// </summary>
public class FirmwareStoreConfig
{
    public const string SectionName = "FirmwareStore";

    public string FilesDirectory { get; set; } = "firmware";
    public string DataDirectory { get; set; } = "data";
}
