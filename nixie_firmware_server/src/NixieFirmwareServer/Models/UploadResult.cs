namespace NixieFirmwareServer.Models;

/// <summary>
/// Результат загрузки новой прошивки для ответа клиенту.
/// </summary>
public class UploadResult
{
    public bool Success { get; set; }
    public string Version { get; set; } = "";
    public string Message { get; set; } = "";
}
