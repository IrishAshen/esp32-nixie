using NixieFirmwareServer.Models;
using NixieFirmwareServer.Services;

var builder = WebApplication.CreateBuilder(args);

// ── Configuration ────────────────────────────────────────────────
var storeConfig = builder.Configuration
    .GetSection(FirmwareStoreConfig.SectionName)
    .Get<FirmwareStoreConfig>() ?? new FirmwareStoreConfig();

var apiKey = builder.Configuration["ApiKey"]
             ?? throw new InvalidOperationException("ApiKey is not configured");

builder.Services.AddSingleton(storeConfig);
builder.Services.AddSingleton<IFirmwareService, FirmwareService>();

// ── CORS (для разработки) ───────────────────────────────────────
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyOrigin()
              .AllowAnyMethod()
              .AllowAnyHeader();
    });
});

var app = builder.Build();

app.UseCors();

// ── Middleware ───────────────────────────────────────────────────
app.UseMiddleware<ApiKeyMiddleware>(apiKey);

// ═══════════════════════════════════════════════════════════════════
//  Endpoints
// ═══════════════════════════════════════════════════════════════════

// ── GET /api/firmware/manifest ───────────────────────────────────
app.MapGet("/api/firmware/manifest", async (
    IFirmwareService service,
    HttpRequest request) =>
{
    var baseUrl = $"{request.Scheme}://{request.Host}";
    var manifest = await service.GetManifestAsync(baseUrl);

    if (manifest == null)
        return Results.NotFound(new { error = "No firmware versions available" });

    return Results.Json(manifest);
});

// ── GET /api/firmware/versions ───────────────────────────────────
app.MapGet("/api/firmware/versions", async (IFirmwareService service) =>
{
    var versions = await service.GetAllVersionsAsync();
    return Results.Json(versions);
});

// ── GET /api/firmware/download/{version} ─────────────────────────
app.MapGet("/api/firmware/download/{version}", async (
    string version,
    IFirmwareService service) =>
{
    var filePath = service.GetFilePath(version);
    if (filePath == null)
        return Results.NotFound(new { error = $"Version '{version}' not found" });

    var fileName = $"firmware_{version}.bin";
    return Results.File(
        new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read),
        contentType: "application/octet-stream",
        fileDownloadName: fileName);
});

// ── POST /api/firmware/upload ────────────────────────────────────
app.MapPost("/api/firmware/upload", async (
    HttpRequest request,
    IFirmwareService service) =>
{
    if (!request.HasFormContentType)
        return Results.BadRequest(new { error = "Content-Type must be multipart/form-data" });

    var form = await request.ReadFormAsync();
    var version = form["version"].FirstOrDefault();
    var releaseNotes = form["releaseNotes"].FirstOrDefault() ?? "";
    var file = form.Files.GetFile("file");

    if (string.IsNullOrWhiteSpace(version))
        return Results.BadRequest(new { error = "Field 'version' is required" });

    if (file == null || file.Length == 0)
        return Results.BadRequest(new { error = "Field 'file' (.bin firmware) is required" });

    if (!System.Text.RegularExpressions.Regex.IsMatch(version, @"^\d+\.\d+\.\d+$"))
        return Results.BadRequest(new { error = "Version must be in format X.Y.Z (e.g. 1.0.0)" });

    UploadResult result;
    await using (var stream = file.OpenReadStream())
    {
        result = await service.UploadFirmwareAsync(version, releaseNotes, stream, file.Length);
    }

    if (!result.Success)
        return Results.BadRequest(result);

    return Results.Json(result, statusCode: StatusCodes.Status201Created);
})
.WithMetadata(new RequireApiKeyAttribute());

// ── GET /api/firmware/download/{version}/sha256 ──────────────────
app.MapGet("/api/firmware/download/{version}/sha256", async (
    string version,
    IFirmwareService service) =>
{
    var meta = await service.GetVersionAsync(version);
    if (meta == null || string.IsNullOrEmpty(meta.Sha256))
        return Results.NotFound(new { error = $"Version '{version}' not found" });

    return Results.Json(new { version = meta.Version, sha256 = meta.Sha256, fileSize = meta.FileSize });
});

// ── Health check ─────────────────────────────────────────────────
app.MapGet("/health", () => Results.Json(new { status = "healthy", timestamp = DateTime.UtcNow }));

app.Run();

// ── Для интеграционных тестов ───────────────────────────────────
public partial class Program { }
