using Microsoft.AspNetCore.Mvc;
using AutoDeployrAIServiceAndNETanalyzer.Services;
using AutoDeployrAIServiceAndNETanalyzer.Models;
using System.Text.Json;

namespace AutoDeployrAIServiceAndNETanalyzer.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AnalyzerController : ControllerBase
{
    private readonly ILogger<AnalyzerController> _logger;
    private readonly IDotNetAnalyzerService _analyzerService;

    public AnalyzerController(ILogger<AnalyzerController> logger, IDotNetAnalyzerService analyzerService)
    {
        _logger = logger;
        _analyzerService = analyzerService;
    }

    /// <summary>
    /// Analyze a .NET/ASP.NET application and extract endpoints, dependencies, and environment variables
    /// </summary>
    /// <param name="request">Analysis request containing application path</param>
    /// <returns>Analysis result with endpoints and dependencies</returns>
    [HttpPost("analyze")]
    public async Task<IActionResult> AnalyzeApplication([FromBody] AnalyzeApplicationRequest request)
    {
        try
        {
            _logger.LogInformation("Received analysis request for application: {AppPath}", request.AppPath);

            if (string.IsNullOrEmpty(request.AppPath))
            {
                return BadRequest(new { error = "Application path is required" });
            }

            if (!Directory.Exists(request.AppPath))
            {
                return BadRequest(new { error = $"Application path does not exist: {request.AppPath}" });
            }

            var result = await _analyzerService.AnalyzeApplicationAsync(request.AppPath);

            _logger.LogInformation("Analysis completed. Found {EndpointCount} endpoints, {DependencyCount} dependencies, {EnvVarCount} environment variables",
                result.Endpoints.Count, result.ServiceSources.Count, result.EnvironmentVariables.Count);

            return Ok(result);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error analyzing application: {AppPath}", request.AppPath);
            return StatusCode(500, new { error = ex.Message });
        }
    }

    /// <summary>
    /// Analyze a single .NET file
    /// </summary>
    /// <param name="request">File analysis request</param>
    /// <returns>Analysis result for the file</returns>
    [HttpPost("analyze-file")]
    public async Task<IActionResult> AnalyzeFile([FromBody] AnalyzeFileRequest request)
    {
        try
        {
            _logger.LogInformation("Received file analysis request for: {FilePath}", request.FilePath);

            if (string.IsNullOrEmpty(request.FilePath))
            {
                return BadRequest(new { error = "File path is required" });
            }

            if (!System.IO.File.Exists(request.FilePath))
            {
                return BadRequest(new { error = $"File does not exist: {request.FilePath}" });
            }

            var result = await _analyzerService.AnalyzeFileAsync(request.FilePath);

            _logger.LogInformation("File analysis completed for: {FilePath}", request.FilePath);

            return Ok(result);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error analyzing file: {FilePath}", request.FilePath);
            return StatusCode(500, new { error = ex.Message });
        }
    }

    /// <summary>
    /// Transform a .NET application to serverless functions
    /// </summary>
    /// <param name="request">Transformation request</param>
    /// <returns>Transformation result with generated serverless functions</returns>
    [HttpPost("transform")]
    public async Task<IActionResult> TransformToServerless([FromBody] TransformApplicationRequest request)
    {
        try
        {
            _logger.LogInformation("Received transformation request for application: {AppPath}", request.AppPath);

            if (string.IsNullOrEmpty(request.AppPath))
            {
                return BadRequest(new { error = "Application path is required" });
            }

            var result = await _analyzerService.TransformToServerlessAsync(request);

            _logger.LogInformation("Transformation completed. Generated {FunctionCount} serverless functions",
                result.GeneratedFunctions.Count);

            return Ok(result);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error transforming application: {AppPath}", request.AppPath);
            return StatusCode(500, new { error = ex.Message });
        }
    }

    [HttpGet("health")]
    public IActionResult Health()
    {
        return Ok(new { 
            status = "healthy", 
            service = "C# ASP.NET Analyzer",
            timestamp = DateTime.UtcNow,
            version = "1.0.0"
        });
    }
} 