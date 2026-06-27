using NixieFirmwareServer.Models;
using NixieFirmwareServer.Services;
using Xunit;

namespace NixieFirmwareServer.Tests;

public class FirmwareServiceTests
{
    private readonly FirmwareStoreConfig _config = new()
    {
        FilesDirectory = Path.Combine(Path.GetTempPath(), "fw_test_files"),
        DataDirectory = Path.Combine(Path.GetTempPath(), "fw_test_data"),
    };

    public FirmwareServiceTests()
    {
        if (Directory.Exists(_config.FilesDirectory))
            Directory.Delete(_config.FilesDirectory, true);
        if (Directory.Exists(_config.DataDirectory))
            Directory.Delete(_config.DataDirectory, true);
    }

    [Fact]
    public void Constructor_CreatesDirectories()
    {
        var svc = new FirmwareService(_config);
        Assert.True(Directory.Exists(_config.FilesDirectory));
        Assert.True(Directory.Exists(_config.DataDirectory));
    }

    [Fact]
    public async Task GetAllVersionsAsync_ReturnsEmpty_WhenNoVersions()
    {
        var svc = new FirmwareService(_config);
        var versions = await svc.GetAllVersionsAsync();
        Assert.Empty(versions);
    }

    [Fact]
    public async Task UploadFirmwareAsync_SavesVersionAndFile()
    {
        var svc = new FirmwareService(_config);
        var content = new byte[] { 0x00, 0x11, 0x22, 0x33 };

        var result = await svc.UploadFirmwareAsync(
            "1.0.0", "Initial", new MemoryStream(content), content.Length);

        Assert.True(result.Success);

        var versions = await svc.GetAllVersionsAsync();
        var v = Assert.Single(versions);
        Assert.Equal("1.0.0", v.Version);
        Assert.Equal(4, v.FileSize);
        Assert.Equal(64, v.Sha256.Length);
        Assert.Matches(@"^[a-f0-9]{64}$", v.Sha256);

        var path = svc.GetFilePath("1.0.0");
        Assert.NotNull(path);
        Assert.True(File.Exists(path));
    }

    [Fact]
    public async Task UploadFirmwareAsync_RejectsEmptyVersion()
    {
        var svc = new FirmwareService(_config);
        var result = await svc.UploadFirmwareAsync(
            "", "", new MemoryStream(new byte[] { 0x01 }), 1);
        Assert.False(result.Success);
    }

    [Fact]
    public async Task UploadFirmwareAsync_RejectsEmptyFile()
    {
        var svc = new FirmwareService(_config);
        var result = await svc.UploadFirmwareAsync(
            "1.0.0", "", new MemoryStream([]), 0);
        Assert.False(result.Success);
    }

    [Fact]
    public async Task UploadFirmwareAsync_RejectsSizeMismatch()
    {
        var svc = new FirmwareService(_config);
        var result = await svc.UploadFirmwareAsync(
            "1.0.0", "", new MemoryStream(new byte[] { 0x01 }), 999);
        Assert.False(result.Success);
    }

    [Fact]
    public async Task UploadFirmwareAsync_UpdatesExistingVersion()
    {
        var svc = new FirmwareService(_config);
        await svc.UploadFirmwareAsync(
            "1.0.0", "Old", new MemoryStream(new byte[] { 0x01 }), 1);
        var result = await svc.UploadFirmwareAsync(
            "1.0.0", "Updated", new MemoryStream(new byte[] { 0x02 }), 1);

        Assert.True(result.Success);
        Assert.Contains("updated", result.Message, StringComparison.OrdinalIgnoreCase);

        var versions = await svc.GetAllVersionsAsync();
        var v = Assert.Single(versions);
        Assert.Equal("Updated", v.ReleaseNotes);
    }

    [Fact]
    public async Task GetManifestAsync_ReturnsManifest()
    {
        var svc = new FirmwareService(_config);
        await svc.UploadFirmwareAsync(
            "1.0.0", "First", new MemoryStream(new byte[] { 0x01 }), 1);
        await svc.UploadFirmwareAsync(
            "1.1.0", "Second", new MemoryStream(new byte[] { 0x02 }), 1);

        var manifest = await svc.GetManifestAsync("https://example.com");

        Assert.NotNull(manifest);
        Assert.Equal("1.1.0", manifest.LatestVersion);
        Assert.Equal("1.0.0", manifest.MinimumVersion);
        Assert.Contains("1.1.0", manifest.FirmwareUrl);
    }

    [Fact]
    public async Task GetManifestAsync_ReturnsNull_WhenNoVersions()
    {
        var svc = new FirmwareService(_config);
        var manifest = await svc.GetManifestAsync("https://example.com");
        Assert.Null(manifest);
    }

    [Fact]
    public async Task GetVersionAsync_ReturnsVersion_WhenExists()
    {
        var svc = new FirmwareService(_config);
        await svc.UploadFirmwareAsync(
            "2.0.0", "Major", new MemoryStream(new byte[] { 0x01 }), 1);

        var v = await svc.GetVersionAsync("2.0.0");
        Assert.NotNull(v);
        Assert.Equal("2.0.0", v.Version);
    }

    [Fact]
    public async Task GetVersionAsync_ReturnsNull_WhenNotExists()
    {
        var svc = new FirmwareService(_config);
        var v = await svc.GetVersionAsync("9.9.9");
        Assert.Null(v);
    }

    [Fact]
    public async Task GetFilePath_ReturnsPath_WhenFileExists()
    {
        var svc = new FirmwareService(_config);
        await svc.UploadFirmwareAsync(
            "1.0.0", "", new MemoryStream(new byte[] { 0x01 }), 1);
        var path = svc.GetFilePath("1.0.0");
        Assert.NotNull(path);
        Assert.True(File.Exists(path));
    }

    [Fact]
    public void GetFilePath_ReturnsNull_WhenFileNotExists()
    {
        var svc = new FirmwareService(_config);
        Assert.Null(svc.GetFilePath("0.0.1"));
    }

    [Fact]
    public void CompareVersions_Works()
    {
        var svc = new FirmwareService(_config);
        Assert.True(svc.CompareVersions("1.1.0", "1.0.0") > 0);
        Assert.True(svc.CompareVersions("1.0.0", "1.1.0") < 0);
        Assert.Equal(0, svc.CompareVersions("1.0.0", "1.0.0"));
        Assert.True(svc.CompareVersions("2.0.0", "1.9.9") > 0);
    }
}
