# ─────────────────────────────────────────────────────────────────────
#  Nixie Firmware Server — Dockerfile
#  Multi-stage build для production (ASP.NET Core 10, Linux)
# ─────────────────────────────────────────────────────────────────────

# ── Stage 1: Restore + Build ─────────────────────────────────────────
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Копируем только csproj — NuGet restore кешируется, пока не меняются
# зависимости (ускорение повторных сборок)
COPY NixieFirmwareServer.slnx .
COPY src/NixieFirmwareServer/NixieFirmwareServer.csproj src/NixieFirmwareServer/
RUN dotnet restore

# Копируем всё остальное и собираем Release
COPY . .
RUN dotnet publish src/NixieFirmwareServer/NixieFirmwareServer.csproj \
    --no-restore \
    --configuration Release \
    --output /publish

# ── Stage 2: Runtime image ───────────────────────────────────────────
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app

# Порт, на котором слушает ASP.NET (можно переопределить через
# ASPNETCORE_HTTP_PORTS или ASPNETCORE_URLS)
EXPOSE 8080

# Директории для хранения прошивок и метаданных —
# рекомендуются volume/bind mount для persistence
RUN mkdir -p /app/firmware /app/data

# Non-root пользователь (app — UID 1654 в aspnet образе)
USER $APP_UID

# Копируем собранное приложение из build-стадии
COPY --from=build /publish .

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl --fail http://localhost:8080/health || exit 1

ENTRYPOINT ["dotnet", "NixieFirmwareServer.dll"]
