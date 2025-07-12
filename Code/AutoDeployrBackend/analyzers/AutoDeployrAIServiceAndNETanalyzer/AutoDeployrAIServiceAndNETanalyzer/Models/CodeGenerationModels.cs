using System.Text.Json.Serialization;

namespace AutoDeployrAIServiceAndNETanalyzer.Models;

public class GenerateCodeRequest
{
    public required string Prompt { get; init; }
    public required string Language { get; init; }
    public string? TargetFramework { get; init; }
}

public class GenerateCodeResponse
{
    public required string Code { get; init; }
    public required string Language { get; init; }
    public string? TargetFramework { get; init; }
    public bool Success { get; init; }
} 