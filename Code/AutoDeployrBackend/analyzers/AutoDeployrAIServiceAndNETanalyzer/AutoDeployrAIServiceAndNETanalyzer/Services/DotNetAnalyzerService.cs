using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using AutoDeployrAIServiceAndNETanalyzer.Models;
using System.Text.RegularExpressions;
using System.Xml.Linq;

namespace AutoDeployrAIServiceAndNETanalyzer.Services;

public interface IDotNetAnalyzerService
{
    Task<DotNetAnalysisResult> AnalyzeApplicationAsync(string appPath);
    Task<FileAnalysisResult> AnalyzeFileAsync(string filePath);
    Task<TransformationResult> TransformToServerlessAsync(TransformApplicationRequest request);
}

public class DotNetAnalyzerService : IDotNetAnalyzerService
{
    private readonly ILogger<DotNetAnalyzerService> _logger;
    private readonly IServerlessFunctionGenerator _functionGenerator;

    public DotNetAnalyzerService(ILogger<DotNetAnalyzerService> logger, IServerlessFunctionGenerator functionGenerator)
    {
        _logger = logger;
        _functionGenerator = functionGenerator;
    }

    public async Task<DotNetAnalysisResult> AnalyzeApplicationAsync(string appPath)
    {
        _logger.LogInformation("Starting analysis of .NET application at: {AppPath}", appPath);

        var result = new DotNetAnalysisResult();
        var allEndpoints = new List<DotNetEndpoint>();
        var allDependencies = new HashSet<string>();
        var allEnvironmentVariables = new HashSet<string>();
        var serviceSources = new Dictionary<string, string>();

        // Find all C# files
        var csFiles = Directory.GetFiles(appPath, "*.cs", SearchOption.AllDirectories)
            .Where(f => !f.Contains("bin") && !f.Contains("obj"))
            .ToList();

        _logger.LogInformation("Found {FileCount} C# files to analyze", csFiles.Count);

        // Note: Project info analysis removed to match Java backend format

        // Analyze each C# file
        foreach (var filePath in csFiles)
        {
            try
            {
                var fileResult = await AnalyzeFileAsync(filePath);
                
                allEndpoints.AddRange(fileResult.Endpoints);
                allDependencies.UnionWith(fileResult.Dependencies);
                allEnvironmentVariables.UnionWith(fileResult.EnvironmentVariables);

                // If this file contains endpoints, add its source to service sources
                if (fileResult.Endpoints.Any())
                {
                    var fileName = Path.GetFileNameWithoutExtension(filePath);
                    var sourceCode = await File.ReadAllTextAsync(filePath);
                    serviceSources[fileName] = sourceCode;
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error analyzing file: {FilePath}", filePath);
            }
        }

        // Analyze dependencies between endpoints
        await AnalyzeDependenciesAsync(allEndpoints, csFiles);

        // Look for configuration files and environment variables
        await AnalyzeConfigurationAsync(appPath, allEnvironmentVariables);

        result.Endpoints = allEndpoints;
        result.EnvironmentVariables = allEnvironmentVariables;
        result.ServiceSources = serviceSources;

        _logger.LogInformation("Analysis completed. Found {EndpointCount} endpoints, {DependencyCount} dependencies, {EnvVarCount} environment variables",
            allEndpoints.Count, allDependencies.Count, allEnvironmentVariables.Count);

        return result;
    }

    public async Task<FileAnalysisResult> AnalyzeFileAsync(string filePath)
    {
        _logger.LogDebug("Analyzing file: {FilePath}", filePath);

        var fileName = Path.GetFileName(filePath);
        var sourceCode = await File.ReadAllTextAsync(filePath);
        
        var result = new FileAnalysisResult
        {
            FileName = fileName,
            FilePath = filePath
        };

        try
        {
            var syntaxTree = CSharpSyntaxTree.ParseText(sourceCode);
            var root = syntaxTree.GetRoot();

            // Check file type
            result.IsController = IsControllerFile(root);
            result.IsService = IsServiceFile(root);
            result.IsModel = IsModelFile(root);

            if (result.IsController)
            {
                result.Endpoints = ExtractEndpointsFromController(root, fileName);
            }

            // Extract dependencies and environment variables
            result.Dependencies = ExtractDependencies(root);
            result.EnvironmentVariables = ExtractEnvironmentVariables(sourceCode);

            _logger.LogDebug("File analysis completed for: {FileName}. Found {EndpointCount} endpoints",
                fileName, result.Endpoints.Count);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error parsing file: {FilePath}", filePath);
        }

        return result;
    }

    public async Task<TransformationResult> TransformToServerlessAsync(TransformApplicationRequest request)
    {
        _logger.LogInformation("Starting transformation of .NET application: {AppPath}", request.AppPath);

        try
        {
            // First analyze the application
            var analysisResult = await AnalyzeApplicationAsync(request.AppPath);

            if (!analysisResult.Endpoints.Any())
            {
                return new TransformationResult
                {
                    Success = false,
                    Error = "No endpoints found in the application"
                };
            }

            // Generate serverless functions
            var transformationResult = await _functionGenerator.GenerateServerlessFunctionsAsync(
                analysisResult, request);

            _logger.LogInformation("Transformation completed successfully. Generated {FunctionCount} functions",
                transformationResult.GeneratedFunctions.Count);

            return transformationResult;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error during transformation: {AppPath}", request.AppPath);
            return new TransformationResult
            {
                Success = false,
                Error = ex.Message
            };
        }
    }



    private bool IsControllerFile(SyntaxNode root)
    {
        return root.DescendantNodes()
            .OfType<ClassDeclarationSyntax>()
            .Any(c => c.BaseList?.Types.Any(t => t.ToString().Contains("Controller")) == true ||
                     c.AttributeLists.Any(al => al.Attributes.Any(a => a.Name.ToString().Contains("ApiController"))));
    }

    private bool IsServiceFile(SyntaxNode root)
    {
        return root.DescendantNodes()
            .OfType<ClassDeclarationSyntax>()
            .Any(c => c.Identifier.ValueText.EndsWith("Service") ||
                     c.BaseList?.Types.Any(t => t.ToString().Contains("Service")) == true);
    }

    private bool IsModelFile(SyntaxNode root)
    {
        var classes = root.DescendantNodes().OfType<ClassDeclarationSyntax>();
        return classes.Any() && classes.All(c => 
            c.Members.OfType<PropertyDeclarationSyntax>().Any() &&
            !c.Members.OfType<MethodDeclarationSyntax>().Any(m => !IsPropertyAccessor(m)));
    }

    private bool IsPropertyAccessor(MethodDeclarationSyntax method)
    {
        return method.Identifier.ValueText.StartsWith("get_") || 
               method.Identifier.ValueText.StartsWith("set_");
    }

    private List<DotNetEndpoint> ExtractEndpointsFromController(SyntaxNode root, string fileName)
    {
        var endpoints = new List<DotNetEndpoint>();
        var controllerClasses = root.DescendantNodes()
            .OfType<ClassDeclarationSyntax>()
            .Where(IsControllerClass);

        foreach (var controllerClass in controllerClasses)
        {
            var controllerName = controllerClass.Identifier.ValueText;
            var controllerRoute = ExtractControllerRoute(controllerClass);

            var actionMethods = controllerClass.Members
                .OfType<MethodDeclarationSyntax>()
                .Where(IsActionMethod);

            foreach (var method in actionMethods)
            {
                var endpoint = ExtractEndpointFromMethod(method, controllerName, controllerRoute);
                if (endpoint != null)
                {
                    endpoints.Add(endpoint);
                }
            }
        }

        return endpoints;
    }

    private bool IsControllerClass(ClassDeclarationSyntax classDecl)
    {
        return classDecl.BaseList?.Types.Any(t => t.ToString().Contains("Controller")) == true ||
               classDecl.AttributeLists.Any(al => al.Attributes.Any(a => a.Name.ToString().Contains("ApiController")));
    }

    private string ExtractControllerRoute(ClassDeclarationSyntax controllerClass)
    {
        var routeAttribute = controllerClass.AttributeLists
            .SelectMany(al => al.Attributes)
            .FirstOrDefault(a => a.Name.ToString().Contains("Route"));

        if (routeAttribute?.ArgumentList?.Arguments.FirstOrDefault()?.Expression is LiteralExpressionSyntax literal)
        {
            return literal.Token.ValueText;
        }

        // Default route based on controller name
        var controllerName = controllerClass.Identifier.ValueText;
        if (controllerName.EndsWith("Controller"))
        {
            controllerName = controllerName[..^10]; // Remove "Controller"
        }
        return $"api/[controller]".Replace("[controller]", controllerName);
    }

    private bool IsActionMethod(MethodDeclarationSyntax method)
    {
        return method.Modifiers.Any(m => m.IsKind(SyntaxKind.PublicKeyword)) &&
               !method.Modifiers.Any(m => m.IsKind(SyntaxKind.StaticKeyword));
    }

    private DotNetEndpoint? ExtractEndpointFromMethod(MethodDeclarationSyntax method, string controllerName, string controllerRoute)
    {
        var actionName = method.Identifier.ValueText;
        var httpMethods = ExtractHttpMethods(method);
        var route = ExtractMethodRoute(method, actionName);
        var fullPath = CombineRoutes(controllerRoute, route);

        var parameters = method.ParameterList.Parameters
            .Select(p => new ParameterInfo
            {
                Name = p.Identifier.ValueText,
                Type = p.Type?.ToString() ?? "object",
                Annotation = ExtractParameterSource(p)
            }).ToList();

        var returnType = method.ReturnType?.ToString();
        var attributes = method.AttributeLists
            .SelectMany(al => al.Attributes)
            .Select(a => a.Name.ToString())
            .ToHashSet();

        // Get the namespace from the parent compilation unit
        var namespaceDeclaration = method.Ancestors().OfType<NamespaceDeclarationSyntax>().FirstOrDefault();
        var packageName = namespaceDeclaration?.Name?.ToString() ?? "";

        return new DotNetEndpoint
        {
            Name = $"{controllerName}.{actionName}",
            Path = fullPath,
            Methods = httpMethods,
            Source = method.ToString(),
            ClassName = controllerName,
            PackageName = packageName,
            ReturnType = returnType,
            Parameters = parameters,
            Annotations = attributes
        };
    }

    private List<string> ExtractHttpMethods(MethodDeclarationSyntax method)
    {
        var httpMethods = new List<string>();
        
        var httpAttributes = method.AttributeLists
            .SelectMany(al => al.Attributes)
            .Where(a => IsHttpMethodAttribute(a.Name.ToString()));

        foreach (var attr in httpAttributes)
        {
            var methodName = attr.Name.ToString();
            if (methodName.Contains("Get")) httpMethods.Add("GET");
            else if (methodName.Contains("Post")) httpMethods.Add("POST");
            else if (methodName.Contains("Put")) httpMethods.Add("PUT");
            else if (methodName.Contains("Delete")) httpMethods.Add("DELETE");
            else if (methodName.Contains("Patch")) httpMethods.Add("PATCH");
        }

        // If no HTTP method attributes, assume GET for simple methods
        if (!httpMethods.Any())
        {
            httpMethods.Add("GET");
        }

        return httpMethods;
    }

    private bool IsHttpMethodAttribute(string attributeName)
    {
        var httpMethodAttributes = new[] { "HttpGet", "HttpPost", "HttpPut", "HttpDelete", "HttpPatch", "Route" };
        return httpMethodAttributes.Any(attr => attributeName.Contains(attr));
    }

    private string ExtractMethodRoute(MethodDeclarationSyntax method, string actionName)
    {
        var routeAttribute = method.AttributeLists
            .SelectMany(al => al.Attributes)
            .FirstOrDefault(a => a.Name.ToString().Contains("Route") || IsHttpMethodAttribute(a.Name.ToString()));

        if (routeAttribute?.ArgumentList?.Arguments.FirstOrDefault()?.Expression is LiteralExpressionSyntax literal)
        {
            return literal.Token.ValueText;
        }

        return actionName.ToLower();
    }

    private string CombineRoutes(string controllerRoute, string methodRoute)
    {
        var combined = $"{controllerRoute.TrimEnd('/')}/{methodRoute.TrimStart('/')}";
        return combined.Replace("//", "/");
    }

    private string? ExtractParameterSource(ParameterSyntax parameter)
    {
        var sourceAttributes = parameter.AttributeLists
            .SelectMany(al => al.Attributes)
            .Select(a => a.Name.ToString())
            .FirstOrDefault(name => name.Contains("From"));

        return sourceAttributes;
    }



    private List<string> ExtractDependencies(SyntaxNode root)
    {
        var dependencies = new HashSet<string>();

        // Extract using statements
        var usingDirectives = root.DescendantNodes()
            .OfType<UsingDirectiveSyntax>()
            .Select(u => u.Name?.ToString())
            .Where(name => !string.IsNullOrEmpty(name));

        dependencies.UnionWith(usingDirectives!);

        // Extract method calls that might indicate dependencies
        var invocations = root.DescendantNodes()
            .OfType<InvocationExpressionSyntax>()
            .Select(i => i.Expression.ToString())
            .Where(expr => !string.IsNullOrEmpty(expr));

        dependencies.UnionWith(invocations);

        return dependencies.ToList();
    }

    private HashSet<string> ExtractEnvironmentVariables(string sourceCode)
    {
        var envVars = new HashSet<string>();

        // Look for Environment.GetEnvironmentVariable calls
        var envVarPattern = @"Environment\.GetEnvironmentVariable\([""']([^""']+)[""']\)";
        var matches = Regex.Matches(sourceCode, envVarPattern);
        
        foreach (Match match in matches)
        {
            envVars.Add(match.Groups[1].Value);
        }

        // Look for configuration access patterns
        var configPatterns = new[]
        {
            @"Configuration\[[""']([^""']+)[""']\]",
            @"GetConnectionString\([""']([^""']+)[""']\)",
            @"GetValue<[^>]+>\([""']([^""']+)[""']\)"
        };

        foreach (var pattern in configPatterns)
        {
            var configMatches = Regex.Matches(sourceCode, pattern);
            foreach (Match match in configMatches)
            {
                envVars.Add(match.Groups[1].Value);
            }
        }

        return envVars;
    }

    private async Task AnalyzeDependenciesAsync(List<DotNetEndpoint> endpoints, List<string> csFiles)
    {
        // This is a simplified dependency analysis
        // In a real implementation, you might want to use Roslyn's semantic model for more accurate analysis
        
        foreach (var endpoint in endpoints)
        {
            var dependencies = new HashSet<string>();
            
            // Look for service injections in the controller
            foreach (var file in csFiles)
            {
                var content = await File.ReadAllTextAsync(file);
                if (content.Contains(endpoint.ClassName))
                {
                    // Extract constructor dependencies
                    var constructorPattern = @"public\s+" + endpoint.ClassName + @"\s*\([^)]*\)";
                    var match = Regex.Match(content, constructorPattern);
                    if (match.Success)
                    {
                        // Simple extraction of parameter types
                        var paramPattern = @"(\w+)\s+\w+";
                        var paramMatches = Regex.Matches(match.Value, paramPattern);
                        foreach (Match paramMatch in paramMatches)
                        {
                            dependencies.Add(paramMatch.Groups[1].Value);
                        }
                    }
                }
            }
            
            endpoint.Dependencies = dependencies;
        }
    }

    private async Task AnalyzeConfigurationAsync(string appPath, HashSet<string> environmentVariables)
    {
        // Look for appsettings.json files
        var configFiles = Directory.GetFiles(appPath, "appsettings*.json", SearchOption.AllDirectories);
        
        foreach (var configFile in configFiles)
        {
            try
            {
                var content = await File.ReadAllTextAsync(configFile);
                // Extract configuration keys (simplified)
                var keyPattern = @"""([^""]+)""\s*:";
                var matches = Regex.Matches(content, keyPattern);
                
                foreach (Match match in matches)
                {
                    environmentVariables.Add(match.Groups[1].Value);
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error reading config file: {ConfigFile}", configFile);
            }
        }
    }


} 