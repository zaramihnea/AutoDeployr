using System.Text.Json;
using System.Text.Json.Serialization;
using AutoDeployrAIServiceAndNETanalyzer.Services;

var builder = WebApplication.CreateBuilder(args);

// Add services
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddHttpClient();

// Add application services
builder.Services.AddScoped<ICodeGenerationService, CodeGenerationService>();
builder.Services.AddScoped<IDotNetAnalyzerService, DotNetAnalyzerService>();
builder.Services.AddScoped<IServerlessFunctionGenerator, ServerlessFunctionGenerator>();

// Configure CORS
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowAll", policy =>
    {
        policy.AllowAnyOrigin()
              .AllowAnyMethod()
              .AllowAnyHeader();
    });
});

// Configure JSON serialization options
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    options.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
    options.SerializerOptions.WriteIndented = true;
});

// Build the app
var app = builder.Build();

// Configure middleware
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseCors("AllowAll");
app.UseAuthorization();

// Map routes
app.MapControllers();

// Add backward compatibility for the old endpoint
app.MapPost("/generate-code", async (HttpContext context, ICodeGenerationService codeGenerationService) =>
{
    try
    {
        // Read request body
        using var reader = new StreamReader(context.Request.Body, System.Text.Encoding.UTF8);
        var requestBody = await reader.ReadToEndAsync();
        
        // Log the raw request body to help diagnose issues
        Console.WriteLine($"Raw request body: {requestBody}");
        
        var requestData = JsonSerializer.Deserialize<AutoDeployrAIServiceAndNETanalyzer.Models.GenerateCodeRequest>(requestBody, 
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });

        Console.WriteLine($"Deserialized request: Prompt={requestData?.Prompt}, Language={requestData?.Language}");
        
        if (requestData == null || string.IsNullOrEmpty(requestData.Prompt) || string.IsNullOrEmpty(requestData.Language))
        {
            context.Response.StatusCode = 400;
            await context.Response.WriteAsync("Invalid request. Prompt and Language are required.");
            return;
        }

        var response = await codeGenerationService.GenerateCodeAsync(requestData);
        
        // Send response
        context.Response.ContentType = "application/json";
        await context.Response.WriteAsJsonAsync(response);
    }
    catch (Exception ex)
    {
        Console.WriteLine("==== EXCEPTION DETAILS ====");
        Console.WriteLine($"Exception type: {ex.GetType().Name}");
        Console.WriteLine($"Message: {ex.Message}");
        Console.WriteLine($"StackTrace: {ex.StackTrace}");
        
        // Create error response
        var errorResponse = new
        {
            Success = false,
            Error = ex.Message,
            ErrorType = ex.GetType().Name,
            Code = string.Empty,
            Language = string.Empty
        };
        
        if (ex is JsonException)
        {
            context.Response.StatusCode = 400;
            await context.Response.WriteAsJsonAsync(errorResponse);
        }
        else
        {
            context.Response.StatusCode = 500;
            await context.Response.WriteAsJsonAsync(errorResponse);
        }
    }
});

// Run the app
app.Run("http://localhost:5200");