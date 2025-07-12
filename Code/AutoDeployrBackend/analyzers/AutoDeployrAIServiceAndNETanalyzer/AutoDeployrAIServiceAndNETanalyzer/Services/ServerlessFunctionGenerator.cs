using AutoDeployrAIServiceAndNETanalyzer.Models;

namespace AutoDeployrAIServiceAndNETanalyzer.Services;

public interface IServerlessFunctionGenerator
{
    Task<TransformationResult> GenerateServerlessFunctionsAsync(DotNetAnalysisResult analysisResult, TransformApplicationRequest request);
}

public class ServerlessFunctionGenerator : IServerlessFunctionGenerator
{
    private readonly ILogger<ServerlessFunctionGenerator> _logger;

    public ServerlessFunctionGenerator(ILogger<ServerlessFunctionGenerator> logger)
    {
        _logger = logger;
    }

    public async Task<TransformationResult> GenerateServerlessFunctionsAsync(DotNetAnalysisResult analysisResult, TransformApplicationRequest request)
    {
        _logger.LogInformation("Preparing transformation metadata for {EndpointCount} endpoints", analysisResult.Endpoints.Count);

        // This just prepares the metadata - the actual generation happens in the Java backend using templates
        var generatedFunctions = new List<GeneratedFunction>();

        foreach (var endpoint in analysisResult.Endpoints)
        {
            var function = new GeneratedFunction
            {
                Name = SanitizeFunctionName(endpoint.Name),
                Path = endpoint.Path,
                Methods = endpoint.Methods,
                SourceCode = endpoint.Source ?? "",
                Dependencies = endpoint.Dependencies.ToList(),
                EnvironmentVariables = ExtractEnvironmentVariables(endpoint, analysisResult, request),
                AppName = request.AppName ?? "DotNetApp"
            };

            generatedFunctions.Add(function);
        }

        return new TransformationResult
        {
            Success = true,
            GeneratedFunctions = generatedFunctions,
            OutputPath = request.OutputPath
        };
    }

    private Dictionary<string, string> ExtractEnvironmentVariables(
        DotNetEndpoint endpoint, 
        DotNetAnalysisResult analysisResult, 
        TransformApplicationRequest request)
    {
        var envVars = new Dictionary<string, string>();

        // Add global environment variables from request
        if (request.EnvironmentVariables != null)
        {
            foreach (var kvp in request.EnvironmentVariables)
            {
                envVars[kvp.Key] = kvp.Value;
            }
        }

        // Add environment variables found in analysis
        foreach (var envVar in analysisResult.EnvironmentVariables)
        {
            if (!envVars.ContainsKey(envVar))
            {
                envVars[envVar] = $"${{{envVar}}}"; // Placeholder value
            }
        }

        return envVars;
    }

    private string SanitizeFunctionName(string name)
    {
        return System.Text.RegularExpressions.Regex.Replace(name, @"[^a-zA-Z0-9_]", "_");
    }
} 