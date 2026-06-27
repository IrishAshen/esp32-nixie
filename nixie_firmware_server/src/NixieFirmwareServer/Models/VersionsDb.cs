namespace NixieFirmwareServer.Models;

/// <summary>
/// Корневой объект файла data/versions.json.
/// </summary>
public class VersionsDb
{
    public List<FirmwareVersion> Versions { get; set; } = new();
}
