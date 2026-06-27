using System.Net;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Xunit;

namespace NixieFirmwareServer.Tests;

public class ApiEndpointTests
{
    private WebApplicationFactory<Program> CreateFactory()
    {
        var uid = Guid.NewGuid().ToString("N")[..8];
        return new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.UseSetting("ApiKey", "test-key");
            builder.UseSetting("FirmwareStore:FilesDirectory",
                Path.Combine(Path.GetTempPath(), "nixie_api", uid, "files"));
            builder.UseSetting("FirmwareStore:DataDirectory",
                Path.Combine(Path.GetTempPath(), "nixie_api", uid, "data"));
        });
    }

    private static async Task<JsonElement> ParseJson(HttpContent content)
    {
        var json = await content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<JsonElement>(json,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
    }

    private static async Task UploadTestFirmware(HttpClient client, string version, byte[] content)
    {
        using var form = new MultipartFormDataContent
        {
            { new StringContent(version), "version" },
            { new StringContent("Test notes"), "releaseNotes" },
            { new ByteArrayContent(content), "file", $"fw_{version}.bin" },
        };
        var req = new HttpRequestMessage(HttpMethod.Post, "/api/firmware/upload")
        { Content = form };
        req.Headers.Add("X-API-Key", "test-key");
        var resp = await client.SendAsync(req);
        Assert.True(resp.IsSuccessStatusCode, $"Upload failed: {resp.StatusCode}");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Health
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public async Task Health_ReturnsOk()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        var resp = await client.GetAsync("/health");
        Assert.Equal(HttpStatusCode.OK, resp.StatusCode);
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/firmware/manifest
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public async Task GetManifest_ReturnsNotFound_WhenNoVersions()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        var resp = await client.GetAsync("/api/firmware/manifest");
        Assert.Equal(HttpStatusCode.NotFound, resp.StatusCode);
    }

    [Fact]
    public async Task GetManifest_ReturnsManifest()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        await UploadTestFirmware(client, "1.0.0", [0x01]);
        await UploadTestFirmware(client, "1.1.0", [0x02]);

        var resp = await client.GetAsync("/api/firmware/manifest");
        Assert.Equal(HttpStatusCode.OK, resp.StatusCode);
        var json = await ParseJson(resp.Content);
        Assert.Equal("1.1.0", json.GetProperty("latestVersion").GetString());
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/firmware/versions
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public async Task GetVersions_ReturnsEmpty_WhenNoVersions()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        var resp = await client.GetAsync("/api/firmware/versions");
        Assert.Equal(HttpStatusCode.OK, resp.StatusCode);
        var json = await ParseJson(resp.Content);
        Assert.Equal(0, json.GetArrayLength());
    }

    [Fact]
    public async Task GetVersions_ReturnsVersions()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        await UploadTestFirmware(client, "1.0.0", [0x01]);
        await UploadTestFirmware(client, "2.0.0", [0x02]);

        var resp = await client.GetAsync("/api/firmware/versions");
        Assert.Equal(HttpStatusCode.OK, resp.StatusCode);
        var json = await ParseJson(resp.Content);
        Assert.Equal(2, json.GetArrayLength());
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/firmware/download/{version}
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public async Task DownloadFirmware_ReturnsFile()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        var data = new byte[] { 0xDE, 0xAD, 0xBE, 0xEF };
        await UploadTestFirmware(client, "1.5.0", data);

        var resp = await client.GetAsync("/api/firmware/download/1.5.0");
        Assert.Equal(HttpStatusCode.OK, resp.StatusCode);
        Assert.Equal("application/octet-stream", resp.Content.Headers.ContentType?.MediaType);
        Assert.Equal(data, await resp.Content.ReadAsByteArrayAsync());
    }

    [Fact]
    public async Task DownloadFirmware_ReturnsNotFound()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        var resp = await client.GetAsync("/api/firmware/download/99.99.99");
        Assert.Equal(HttpStatusCode.NotFound, resp.StatusCode);
    }

    // ═══════════════════════════════════════════════════════════════
    //  POST /api/firmware/upload
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public async Task UploadFirmware_RequiresApiKey()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        using var form = new MultipartFormDataContent
        {
            { new StringContent("1.0.0"), "version" },
            { new ByteArrayContent([0x01]), "file", "fw.bin" },
        };
        var resp = await client.PostAsync("/api/firmware/upload", form);
        Assert.Equal(HttpStatusCode.Unauthorized, resp.StatusCode);
    }

    [Fact]
    public async Task UploadFirmware_RejectsWrongKey()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        using var form = new MultipartFormDataContent
        {
            { new StringContent("1.0.0"), "version" },
            { new ByteArrayContent([0x01]), "file", "fw.bin" },
        };
        var req = new HttpRequestMessage(HttpMethod.Post, "/api/firmware/upload")
        { Content = form };
        req.Headers.Add("X-API-Key", "wrong-key");
        var resp = await client.SendAsync(req);
        Assert.Equal(HttpStatusCode.Unauthorized, resp.StatusCode);
    }

    [Fact]
    public async Task UploadFirmware_AcceptsValidKey()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        using var form = new MultipartFormDataContent
        {
            { new StringContent("1.0.0"), "version" },
            { new StringContent("Notes"), "releaseNotes" },
            { new ByteArrayContent([0x01, 0x02, 0x03]), "file", "fw.bin" },
        };
        var req = new HttpRequestMessage(HttpMethod.Post, "/api/firmware/upload")
        { Content = form };
        req.Headers.Add("X-API-Key", "test-key");
        var resp = await client.SendAsync(req);
        Assert.Equal(HttpStatusCode.Created, resp.StatusCode);
    }

    [Fact]
    public async Task UploadFirmware_RejectsInvalidVersion()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        using var form = new MultipartFormDataContent
        {
            { new StringContent("invalid"), "version" },
            { new ByteArrayContent([0x01]), "file", "fw.bin" },
        };
        var req = new HttpRequestMessage(HttpMethod.Post, "/api/firmware/upload")
        { Content = form };
        req.Headers.Add("X-API-Key", "test-key");
        var resp = await client.SendAsync(req);
        Assert.Equal(HttpStatusCode.BadRequest, resp.StatusCode);
    }

    [Fact]
    public async Task UploadFirmware_RejectsMissingFile()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        using var form = new MultipartFormDataContent
        {
            { new StringContent("1.0.0"), "version" },
        };
        var req = new HttpRequestMessage(HttpMethod.Post, "/api/firmware/upload")
        { Content = form };
        req.Headers.Add("X-API-Key", "test-key");
        var resp = await client.SendAsync(req);
        Assert.Equal(HttpStatusCode.BadRequest, resp.StatusCode);
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/firmware/download/{version}/sha256
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public async Task GetChecksum_ReturnsSha256()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        await UploadTestFirmware(client, "1.0.0", [0x01]);

        var resp = await client.GetAsync("/api/firmware/download/1.0.0/sha256");
        Assert.Equal(HttpStatusCode.OK, resp.StatusCode);
        var json = await ParseJson(resp.Content);
        Assert.Equal("1.0.0", json.GetProperty("version").GetString());
        var sha = json.GetProperty("sha256").GetString();
        Assert.NotNull(sha);
        Assert.Equal(64, sha.Length);
        Assert.Matches("^[a-f0-9]{64}$", sha);
    }

    [Fact]
    public async Task GetChecksum_ReturnsNotFound()
    {
        using var factory = CreateFactory();
        var client = factory.CreateClient();
        var resp = await client.GetAsync("/api/firmware/download/99.99.99/sha256");
        Assert.Equal(HttpStatusCode.NotFound, resp.StatusCode);
    }
}
