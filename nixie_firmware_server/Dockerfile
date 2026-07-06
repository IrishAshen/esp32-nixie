# ─────────────────────────────────────────────────────────────────────────────
#  Nixie Firmware Server — Dockerfile
#  Multi-stage build: restore → build → publish → runtime
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Restore + Build ────────────────────────────────────────────────
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Restore (layer-cached: only reprojected when .csproj changes)
COPY NixieFirmwareServer.slnx ./
COPY src/NixieFirmwareServer/NixieFirmwareServer.csproj src/NixieFirmwareServer/
COPY tests/NixieFirmwareServer.Tests/NixieFirmwareServer.Tests.csproj tests/NixieFirmwareServer.Tests/
RUN dotnet restore

# Build + publish
COPY . .
RUN dotnet publish src/NixieFirmwareServer/NixieFirmwareServer.csproj \
    --configuration Release \
    --output /publish \
    --no-restore

# ── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app

# Install curl for healthcheck; create non-root user and data directories
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system --gid 1000 appgroup \
    && adduser --system --uid 1000 --gid 1000 appuser \
    && mkdir -p /app/data /app/firmware \
    && chown -R appuser:appgroup /app

# Copy published output
COPY --from=build --chown=appuser:appgroup /publish .

# Switch to non-root user
USER appuser

# Environment defaults (override at runtime)
ENV ASPNETCORE_URLS=http://+:8080
ENV ASPNETCORE_ENVIRONMENT=Production
ENV FirmwareStore__FilesDirectory=/app/firmware
ENV FirmwareStore__DataDirectory=/app/data
ENV ApiKey=""

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -fs http://localhost:8080/health || exit 1

ENTRYPOINT ["./NixieFirmwareServer"]
