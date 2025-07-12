using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using AutoDeployrAIServiceAndNETanalyzer.Models;

namespace AutoDeployrAIServiceAndNETanalyzer.Services;

public interface ICodeGenerationService
{
    Task<GenerateCodeResponse> GenerateCodeAsync(GenerateCodeRequest request);
}

public class CodeGenerationService : ICodeGenerationService
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<CodeGenerationService> _logger;
    private readonly IWebHostEnvironment _hostEnvironment;

    private readonly string _templatesDirectory;
    
    public CodeGenerationService(
        IHttpClientFactory httpClientFactory, 
        ILogger<CodeGenerationService> logger,
        IWebHostEnvironment hostEnvironment)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
        _hostEnvironment = hostEnvironment;
        
        // Set the templates directory path
        _templatesDirectory = Path.Combine(_hostEnvironment.ContentRootPath, "Templates");
    }

    public async Task<GenerateCodeResponse> GenerateCodeAsync(GenerateCodeRequest request)
    {
        try
        {
            // Check if Ollama is available
            bool ollamaAvailable = await IsOllamaAvailableAsync();
            
            string generatedCode;
            
            if (ollamaAvailable)
            {
                generatedCode = await GenerateCodeWithOllamaAsync(request);
            }
            else
            {
                _logger.LogWarning("Ollama is not available. Using fallback code generator.");
                generatedCode = GenerateFallbackCode(request.Language, request.Prompt, request.TargetFramework);
            }

            return new GenerateCodeResponse
            {
                Code = generatedCode,
                Language = request.Language,
                TargetFramework = request.TargetFramework,
                Success = !string.IsNullOrEmpty(generatedCode)
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error generating code");
            throw;
        }
    }

    private async Task<bool> IsOllamaAvailableAsync()
    {
        try
        {
            var httpClient = _httpClientFactory.CreateClient();
            var versionResponse = await httpClient.GetAsync("http://localhost:11434/api/version");
            
            bool available = versionResponse.IsSuccessStatusCode;
            
            if (available)
            {
                _logger.LogInformation("✅ Ollama is available");
                _logger.LogInformation("Ollama version: {Version}", await versionResponse.Content.ReadAsStringAsync());
            }
            else
            {
                _logger.LogWarning("❌ Ollama responded with an error status code: {StatusCode}", versionResponse.StatusCode);
            }
            
            return available;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "❌ Failed to connect to Ollama");
            return false;
        }
    }

    private async Task<string> GenerateCodeWithOllamaAsync(GenerateCodeRequest request)
    {
        var httpClient = _httpClientFactory.CreateClient();
        
        // Get the appropriate system prompt based on the language and framework
        string systemPrompt = await GetSystemPromptForLanguageAsync(request.Language, request.TargetFramework);
        
        // Example of operation from prompt - extract core functionality
        string coreOperation = ExtractCoreOperation(request.Prompt);
        
        // Generate code using Ollama with appropriate prompt
        string promptType = GetPromptTypeForLanguage(request.Language, request.TargetFramework);
        string prompt = $"Create a {promptType} that does this: {coreOperation}\n\nImplement this as a single file with minimal code, focused ONLY on the core functionality. Return only the code without explanation.";
        
        try
        {
            var ollamaRequest = new Dictionary<string, object>
            {
                ["model"] = "codellama",
                ["prompt"] = $"{systemPrompt}\n\n{prompt}"
            };
            
            var ollamaResponse = await httpClient.PostAsJsonAsync("http://localhost:11434/api/generate", ollamaRequest);
            
            if (ollamaResponse.IsSuccessStatusCode)
            {
                var content = await ollamaResponse.Content.ReadAsStringAsync();
                _logger.LogDebug("Ollama response received: {ContentLength} characters", content.Length);
                
                return ProcessOllamaResponse(content, request.Language, request.Prompt, request.TargetFramework);
            }
            else
            {
                _logger.LogError("Ollama error: {Error}", await ollamaResponse.Content.ReadAsStringAsync());
                return GenerateFallbackCode(request.Language, request.Prompt, request.TargetFramework);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Exception calling Ollama");
            return GenerateFallbackCode(request.Language, request.Prompt, request.TargetFramework);
        }
    }

    // Get the appropriate prompt type based on language and framework
    private string GetPromptTypeForLanguage(string language, string? targetFramework)
    {
        return (language.ToLower(), targetFramework?.ToLower()) switch
        {
            ("python", _) => "Python Flask application",
            ("csharp" or "c#" or "cs", "aspnet") => "ASP.NET application",
            ("java", "spring") => "Java Spring application",
            ("php", "laravel") => "PHP Laravel application",
            _ => "application"
        };
    }

    private string ProcessOllamaResponse(string content, string language, string prompt, string? targetFramework)
    {
        try
        {
            StringBuilder codeBuilder = new();
            
            // Split the response by newlines (JSON Lines format)
            string[] jsonLines = content.Split('\n', StringSplitOptions.RemoveEmptyEntries);
            foreach (var line in jsonLines)
            {
                try
                {
                    var jsonObj = JsonDocument.Parse(line);
                    if (jsonObj.RootElement.TryGetProperty("response", out var responseChunk))
                    {
                        string chunk = responseChunk.GetString() ?? string.Empty;
                        codeBuilder.Append(chunk);
                    }
                }
                catch (JsonException lineEx)
                {
                    _logger.LogWarning("Error parsing JSON line: {Error}", lineEx.Message);
                    // Continue with next line
                }
            }
            
            string rawCode = codeBuilder.ToString().Trim();
            
            if (string.IsNullOrEmpty(rawCode))
            {
                _logger.LogWarning("Generated code is empty or could not be extracted");
                return GenerateFallbackCode(language, prompt, targetFramework);
            }

            // Clean up markdown code block formatting from all languages
            rawCode = CleanMarkdownCodeBlocks(rawCode);
            
            // Process the code based on language
            switch (language.ToLower())
            {
                case "python":
                    // ... existing Python code processing ...
                    break;
                case "csharp":
                case "c#":
                case "cs":
                    // Process ASP.NET code if needed
                    break;
                case "java":
                    // Process Java code if needed
                    break;
                case "php":
                    // Process PHP code if needed
                    break;
            }

            return rawCode;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error processing Ollama response");
            return GenerateFallbackCode(language, prompt, targetFramework);
        }
    }

    // Helper method to clean markdown code block delimiters and remove explanatory text
    private string CleanMarkdownCodeBlocks(string code)
    {
        // Remove code block start (```language)
        string cleanedCode = Regex.Replace(code, @"^```[\w#]*\s*", "");
        
        // Remove code block end (```)
        cleanedCode = Regex.Replace(cleanedCode, @"```\s*$", "");
        
        // Remove explanatory text after code (common patterns)
        cleanedCode = RemoveExplanatoryText(cleanedCode);
        
        return cleanedCode.Trim();
    }

    // Remove explanatory text that commonly appears after code
    private string RemoveExplanatoryText(string code)
    {
        // Find the last occurrence of app.run() or similar patterns that indicate end of code
        var endPatterns = new[]
        {
            @"app\.run\([^)]*\)\s*$",
            @"if\s+__name__\s*==\s*['""]__main__['""]:\s*\n\s*app\.run\([^)]*\)",
            @"}\s*$", // For other languages
            @";\s*$"  // For other languages
        };

        foreach (var pattern in endPatterns)
        {
            var match = Regex.Match(code, pattern, RegexOptions.Multiline | RegexOptions.RightToLeft);
            if (match.Success)
            {
                // Cut off everything after this match
                code = code.Substring(0, match.Index + match.Length);
                break;
            }
        }

        // Also remove common explanatory phrases that might appear
        var explanatoryPatterns = new[]
        {
            @"\n\s*This code.*$",
            @"\n\s*The above.*$", 
            @"\n\s*This creates.*$",
            @"\n\s*This endpoint.*$",
            @"\n\s*This function.*$",
            @"\n\s*This application.*$",
            @"\n\s*Note:.*$",
            @"\n\s*Explanation:.*$"
        };

        foreach (var pattern in explanatoryPatterns)
        {
            code = Regex.Replace(code, pattern, "", RegexOptions.Multiline | RegexOptions.IgnoreCase);
        }

        return code;
    }

    // Extracts the core operation from the prompt
    private string ExtractCoreOperation(string prompt)
    {
        // Clean up the prompt to get the core operation
        string operation = prompt.Trim();
        
        // Remove serverless function boilerplate if present
        if (operation.Contains("def handler") || operation.Contains("return {"))
        {
            // Try to extract the actual operation from the handler function
            Match match = Regex.Match(operation, @"'body':\s*str\((.+?)\)");
            if (match.Success)
            {
                operation = match.Groups[1].Value.Trim();
                _logger.LogInformation("Extracted operation from handler: {Operation}", operation);
            }
        }
        
        // Remove phrases like "Write a function that" or "Create an app that"
        var removePatterns = new[]
        {
            @"^(?:write|create|implement|make|develop|code|generate)(?:\s+an?)?(?:\s+\w+)?(?:\s+that)?(?:\s+can)?(?:\s+will)?\s+",
            @"^(?:an?|the)\s+(?:function|app|application|code|program|script)\s+(?:that|which|to)?\s+",
        };
        
        foreach (var pattern in removePatterns)
        {
            operation = Regex.Replace(operation, pattern, "", RegexOptions.IgnoreCase);
        }
        
        return operation;
    }

    // Check if the prompt explicitly asks for a health check endpoint
    private bool PromptRequiresHealthCheck(string prompt)
    {
        string lowercasePrompt = prompt.ToLowerInvariant();
        
        return lowercasePrompt.Contains("health") || 
               lowercasePrompt.Contains("status endpoint") || 
               lowercasePrompt.Contains("health check") || 
               lowercasePrompt.Contains("monitoring") || 
               lowercasePrompt.Contains("heartbeat");
    }

    // Remove health check endpoints from the generated code
    private string RemoveHealthCheckEndpoint(string code)
    {
        try
        {
            // Match health check endpoint route and function
            string pattern = @"(@app\.route\(['""]\/health['""](?:[^)]*\))(?:\s*[^\n]*\n)*?\s*def\s+health\([^)]*\):(?:\s*[^\n]*\n)*?(?:\s+[^\n]*\n)*?)(?=\s*@app\.route|\s*if\s+__name__|$)";
            return Regex.Replace(code, pattern, "");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error removing health check endpoint");
            return code;
        }
    }

    // Detect if the Flask code is overengineered
    private bool IsOverengineered(string code)
    {
        int classCount = Regex.Matches(code, @"^\s*class\s+\w+", RegexOptions.Multiline).Count;
        int functionCount = Regex.Matches(code, @"^\s*def\s+\w+", RegexOptions.Multiline).Count;
        int endpointCount = Regex.Matches(code, @"@app\.route").Count;
        int importCount = Regex.Matches(code, @"^\s*import", RegexOptions.Multiline).Count;
        
        // Consider code overengineered if:
        // - It has more than 2 classes
        // - It has more than 5 functions
        // - It has more than 3 endpoints
        // - It has more than 5 import statements
        return classCount > 2 || functionCount > 5 || endpointCount > 3 || importCount > 5;
    }

    // Load system prompt from file based on language and framework
    private async Task<string> GetSystemPromptForLanguageAsync(string language, string? targetFramework = null)
    {
        try
        {
            string languageDir = GetLanguageDirectory(language);
            string promptFilePath = Path.Combine(_templatesDirectory, languageDir, "system-prompt.txt");
            
            if (File.Exists(promptFilePath))
            {
                return await File.ReadAllTextAsync(promptFilePath);
            }
            else
            {
                _logger.LogWarning("System prompt file not found: {FilePath}. Using default system prompt.", promptFilePath);
                return "You are an expert programmer. Create a function that implements the requested functionality in a single file. Return only the code without explanation.";
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error reading system prompt file for language: {Language}", language);
            return "You are an expert programmer. Create a function that implements the requested functionality in a single file. Return only the code without explanation.";
        }
    }
    
    // Get template code from file based on language
    private string GetTemplateForLanguage(string language)
    {
        try
        {
            string languageDir = GetLanguageDirectory(language);
            string templateFilePath = Path.Combine(_templatesDirectory, languageDir, "template.txt");
            
            if (File.Exists(templateFilePath))
            {
                return File.ReadAllText(templateFilePath);
            }
            else
            {
                _logger.LogWarning("Template file not found: {FilePath}. Using simple fallback template.", templateFilePath);
                
                // Simple fallback templates based on language
                return language.ToLower() switch
                {
                    "python" => "from flask import Flask, jsonify\n\napp = Flask(__name__)\n\n@app.route('/api/execute')\ndef execute():\n    result = 1 + 2\n    return jsonify({'result': result})\n\nif __name__ == '__main__':\n    app.run()",
                    "csharp" or "c#" or "cs" => "using Microsoft.AspNetCore.Http;\nusing Microsoft.AspNetCore.Mvc;\n\nnamespace ServerlessFunctions\n{\n    public class Function\n    {\n        [Function(\"Execute\")]\n        public IActionResult Run([HttpTrigger(\"get\")] HttpRequest req)\n        {\n            int result = 1 + 2;\n            return new OkObjectResult(new { result = result });\n        }\n    }\n}",
                    "java" => "package com.example;\n\nimport org.springframework.web.bind.annotation.*;\n\n@RestController\npublic class Function {\n    @GetMapping(\"/execute\")\n    public Object execute() {\n        int result = 1 + 2;\n        return Map.of(\"result\", result);\n    }\n}",
                    "php" => "<?php\nfunction handle($event) {\n    $result = 1 + 2;\n    return ['statusCode' => 200, 'body' => json_encode(['result' => $result])];\n}",
                    _ => "// Code template not available for this language"
                };
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error reading template file for language: {Language}", language);
            return "// Failed to load template for " + language;
        }
    }
    
    // Map language input to directory name
    private string GetLanguageDirectory(string language)
    {
        return language.ToLower() switch
        {
            "python" or "py" => "Python",
            "csharp" or "c#" or "cs" => "CSharp",
            "java" => "Java",
            "php" => "PHP",
            _ => "Python" // Default to Python for unknown languages
        };
    }

    // Fallback code generation when Ollama is not available
    private string GenerateFallbackCode(string language, string prompt, string? targetFramework = null)
    {
        string operation = ExtractCoreOperation(prompt);
        
        // Get template from file
        string template = GetTemplateForLanguage(language);
        
        // Replace operation placeholder if present
        if (template.Contains("// Core functionality") || template.Contains("# Core functionality"))
        {
            // Try to extract numeric operation if it looks like math
            if (operation.Contains("+") || operation.Contains("add") || Regex.IsMatch(operation, @"\d+\s*[\+\-\*\/]\s*\d+"))
            {
                Match mathMatch = Regex.Match(operation, @"(\d+)\s*([\+\-\*\/])\s*(\d+)");
                if (mathMatch.Success)
                {
                    string mathOperation = mathMatch.Value;
                    
                    // Different replacements based on language
                    switch (language.ToLower())
                    {
                        case "python":
                            template = template.Replace("result = 1 + 2", $"result = {mathOperation}");
                            break;
                        case "csharp":
                        case "c#":
                        case "cs":
                            template = template.Replace("int result = 1 + 2", $"int result = {mathOperation}");
                            break;
                        case "java":
                            template = template.Replace("int result = 1 + 2", $"int result = {mathOperation}");
                            break;
                        case "php":
                            template = template.Replace("$result = 1 + 2", $"$result = {mathOperation}");
                            break;
                    }
                }
            }
            
            // Add a comment about the operation
            if (language.ToLower() == "python")
            {
                template = template.Replace("# Core functionality", $"# Core functionality: {EscapeString(operation)}");
            }
            else
            {
                template = template.Replace("// Core functionality", $"// Core functionality: {EscapeString(operation)}");
            }
        }
        
        return template;
    }

    // Helper to escape strings for code
    private static string EscapeString(string input)
    {
        return input
            .Replace("\\", "\\\\")
            .Replace("\"", "\\\"");
    }
} 