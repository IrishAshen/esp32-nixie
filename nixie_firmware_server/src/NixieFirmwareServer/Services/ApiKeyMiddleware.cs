namespace NixieFirmwareServer.Services;

/// <summary>
/// Middleware для аутентификации по API Key.
///
/// Проверяет заголовок X-API-Key.
/// Защищённые эндпоинты помечаются ключом "RequiresApiKey" в метаданных.
/// </summary>
public class ApiKeyMiddleware
{
    private readonly RequestDelegate _next;
    private readonly string _apiKey;

    public ApiKeyMiddleware(RequestDelegate next, string apiKey)
    {
        _next = next;
        _apiKey = apiKey;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        // Проверяем только если эндпоинт помечен атрибутом
        var endpoint = context.GetEndpoint();
        var requiresAuth = endpoint?.Metadata
            .GetMetadata<RequireApiKeyAttribute>() != null;

        if (requiresAuth)
        {
            var providedKey = context.Request.Headers["X-API-Key"].FirstOrDefault();

            if (string.IsNullOrEmpty(providedKey) || providedKey != _apiKey)
            {
                context.Response.StatusCode = StatusCodes.Status401Unauthorized;
                context.Response.ContentType = "application/json";
                await context.Response.WriteAsync(
                    """{"error":"Unauthorized","message":"Invalid or missing API key"}""");
                return;
            }
        }

        await _next(context);
    }
}

/// <summary>
/// Атрибут для маркировки эндпоинтов, требующих API Key.
/// </summary>
[AttributeUsage(AttributeTargets.Method)]
public class RequireApiKeyAttribute : Attribute
{
}
