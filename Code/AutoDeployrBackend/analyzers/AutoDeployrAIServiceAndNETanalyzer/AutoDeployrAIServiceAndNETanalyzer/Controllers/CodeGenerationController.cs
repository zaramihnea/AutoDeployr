using Microsoft.AspNetCore.Mvc;
using AutoDeployrAIServiceAndNETanalyzer.Models;
using AutoDeployrAIServiceAndNETanalyzer.Services;

namespace AutoDeployrAIServiceAndNETanalyzer.Controllers;

[ApiController]
[Route("api/[controller]")]
public class CodeGenerationController : ControllerBase
{
    private readonly ICodeGenerationService _codeGenerationService;
    private readonly ILogger<CodeGenerationController> _logger;

    public CodeGenerationController(ICodeGenerationService codeGenerationService, ILogger<CodeGenerationController> logger)
    {
        _codeGenerationService = codeGenerationService;
        _logger = logger;
    }

    [HttpPost("generate")]
    public async Task<IActionResult> GenerateCode([FromBody] GenerateCodeRequest request)
    {
        try
        {
            if (request == null || string.IsNullOrEmpty(request.Prompt) || string.IsNullOrEmpty(request.Language))
            {
                return BadRequest("Invalid request. Prompt and Language are required.");
            }

            _logger.LogInformation("Generating code for prompt: {Prompt}, language: {Language}", request.Prompt, request.Language);
            
            var response = await _codeGenerationService.GenerateCodeAsync(request);
            
            _logger.LogInformation("Code generation completed. Success: {Success}", response.Success);
            
            return Ok(response);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error generating code");
            
            var errorResponse = new
            {
                Success = false,
                Error = ex.Message,
                ErrorType = ex.GetType().Name,
                Code = string.Empty,
                Language = request?.Language ?? string.Empty
            };
            
            return StatusCode(500, errorResponse);
        }
    }
} 