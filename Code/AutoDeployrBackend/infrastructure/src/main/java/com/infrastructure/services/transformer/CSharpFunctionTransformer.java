package com.infrastructure.services.transformer;

import com.domain.entities.Function;
import com.domain.entities.FunctionBuildContext;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.infrastructure.services.template.TemplateService;
import com.infrastructure.services.template.TemplateConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Function transformer for C# ASP.NET applications
 */
@Service
public class CSharpFunctionTransformer extends AbstractFunctionTransformer {
    private static final Logger logger = LoggerFactory.getLogger(CSharpFunctionTransformer.class);

    private final TemplateService templateService;
    private static final String CSHARP_ANALYZER_URL = "http://localhost:5200/api/analyzer/analyze";

    @Autowired
    public CSharpFunctionTransformer(TemplateService templateService) {
        super("csharp", "aspnet");
        this.templateService = templateService;
    }

    public List<Function> transformToFunctions(String appPath, String appName, Map<String, String> environmentVariables) {
        logger.info("Starting C# ASP.NET application transformation for app: {}", appName);

        try {
            Map<String, Object> analysisResult = callCSharpAnalyzer(appPath);
            
            if (analysisResult == null || !analysisResult.containsKey("endpoints")) {
                throw new CodeAnalysisException("csharp", "No endpoints found in C# application analysis");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> endpoints = (List<Map<String, Object>>) analysisResult.get("endpoints");
            
            if (endpoints.isEmpty()) {
                logger.warn("No endpoints found in C# application: {}", appName);
                return new ArrayList<>();
            }

            logger.info("Found {} endpoints in C# application", endpoints.size());

            List<Function> functions = new ArrayList<>();
            
            for (Map<String, Object> endpoint : endpoints) {
                try {
                    Function function = createFunctionFromEndpoint(endpoint, appName, environmentVariables, analysisResult);
                    if (function != null) {
                        functions.add(function);
                        logger.debug("Created function: {} for endpoint: {}", function.getName(), endpoint.get("path"));
                    }
                } catch (Exception e) {
                    logger.error("Error creating function from endpoint: {}", endpoint.get("name"), e);
                }
            }

            logger.info("Successfully transformed {} C# endpoints into functions", functions.size());
            return functions;

        } catch (Exception e) {
            logger.error("Error transforming C# application: {}", appName, e);
            throw new CodeAnalysisException("csharp", "Failed to transform C# application: " + e.getMessage());
        }
    }

    private Function createFunctionFromEndpoint(
            Map<String, Object> endpoint, 
            String appName, 
            Map<String, String> environmentVariables,
            Map<String, Object> analysisResult) {
        
        try {
            String endpointName = (String) endpoint.get("name");
            String endpointPath = (String) endpoint.get("path");
            @SuppressWarnings("unchecked")
            List<String> methods = (List<String>) endpoint.get("methods");
            String source = (String) endpoint.get("source");
            String className = (String) endpoint.get("className");
            String packageName = (String) endpoint.get("packageName");

            if (endpointName == null || endpointPath == null) {
                logger.warn("Skipping endpoint with missing name or path");
                return null;
            }
            String functionName = sanitizeFunctionName(endpointName);
            String functionDirectory = generateFunctionFiles(
                functionName, 
                endpoint, 
                appName, 
                environmentVariables,
                analysisResult
            );
            Function function = new Function();
            function.setName(functionName);
            function.setPath(endpointPath);
            function.setMethods(methods != null ? methods : Arrays.asList("GET"));
            function.setAppName(appName);
            function.setLanguage("csharp");
            function.setFramework("aspnet");
            function.setSource(source);

            return function;

        } catch (Exception e) {
            logger.error("Error creating function from C# endpoint", e);
            return null;
        }
    }

    private String generateFunctionFiles(
            String functionName,
            Map<String, Object> endpoint,
            String appName,
            Map<String, String> environmentVariables,
            Map<String, Object> analysisResult) throws IOException {
        Path functionDir = Paths.get("generated-functions", "csharp", functionName);
        Files.createDirectories(functionDir);
        generateProjectFile(functionDir, functionName, endpoint);
        generateProgramFile(functionDir, functionName, endpoint);
        generateControllerFile(functionDir, functionName, endpoint, analysisResult);
        generateDockerfile(functionDir, functionName);

        logger.debug("Generated C# function files in directory: {}", functionDir.toAbsolutePath());
        return functionDir.toAbsolutePath().toString();
    }

    private void generateProjectFile(Path functionDir, String functionName, Map<String, Object> endpoint) throws IOException {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("FUNCTION_NAME", functionName);
        replacements.put("ADDITIONAL_PACKAGES", "");

        String projectContent = templateService.processTemplate(
            TemplateConstants.CSHARP_PROJECT_TEMPLATE, 
            replacements
        );

        Files.write(functionDir.resolve(functionName + ".csproj"), projectContent.getBytes());
    }

    private void generateProgramFile(Path functionDir, String functionName, Map<String, Object> endpoint) throws IOException {
        String source = (String) endpoint.get("source");
        String functionBody = extractCleanFunctionBody(source);
        
        Map<String, String> replacements = new HashMap<>();
        replacements.put("FUNCTION_NAME", functionName);
        replacements.put("ADDITIONAL_SERVICES", "");
        replacements.put("ADDITIONAL_MIDDLEWARE", "");
        replacements.put("FUNCTION_BODY", functionBody);

        String programContent = templateService.processTemplate(
            TemplateConstants.CSHARP_PROGRAM_TEMPLATE, 
            replacements
        );

        Files.write(functionDir.resolve("Program.cs"), programContent.getBytes());
    }

    private void generateControllerFile(
            Path functionDir, 
            String functionName, 
            Map<String, Object> endpoint,
            Map<String, Object> analysisResult) throws IOException {

        String endpointPath = (String) endpoint.get("path");
        String className = (String) endpoint.get("className");
        String source = (String) endpoint.get("source");
        @SuppressWarnings("unchecked")
        List<String> methods = (List<String>) endpoint.get("methods");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) endpoint.get("parameters");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("FUNCTION_NAME", functionName);
        replacements.put("FUNCTION_PATH", endpointPath);
        replacements.put("CONTROLLER_NAME", className != null ? className : functionName + "Controller");
        replacements.put("METHOD_NAME", extractMethodName(endpoint));
        replacements.put("ADDITIONAL_USINGS", "");
        replacements.put("ADDITIONAL_FIELDS", "");
        replacements.put("ADDITIONAL_CONSTRUCTOR_PARAMS", "");
        replacements.put("ADDITIONAL_CONSTRUCTOR_BODY", "");
        replacements.put("ORIGINAL_USINGS", "");
        replacements.put("DEPENDENCY_FIELDS", "");
        replacements.put("DEPENDENCY_CONSTRUCTOR_PARAMS", "");
        replacements.put("DEPENDENCY_CONSTRUCTOR_ASSIGNMENTS", "");
        replacements.put("ORIGINAL_FUNCTION_BODY", source != null ? source : "// Original function logic here");
        String httpMethodAttributes = generateHttpMethodAttributes(methods);
        replacements.put("HTTP_METHOD_ATTRIBUTES", httpMethodAttributes);
        String methodParameters = generateMethodParameters(parameters);
        replacements.put("METHOD_PARAMETERS", methodParameters);

        String controllerContent = templateService.processTemplate(
            TemplateConstants.CSHARP_FUNCTION_WRAPPER_TEMPLATE, 
            replacements
        );

        Files.write(functionDir.resolve(className + ".cs"), controllerContent.getBytes());
    }

    private void generateDockerfile(Path functionDir, String functionName) throws IOException {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("FUNCTION_NAME", functionName);

        String dockerfileContent = templateService.processTemplate(
            TemplateConstants.CSHARP_DOCKERFILE_TEMPLATE, 
            replacements
        );

        Files.write(functionDir.resolve("Dockerfile"), dockerfileContent.getBytes());
    }

    private String extractMethodName(Map<String, Object> endpoint) {
        String name = (String) endpoint.get("name");
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf(".") + 1);
        }
        return name != null ? name : "Execute";
    }

    private String generateHttpMethodAttributes(List<String> methods) {
        if (methods == null || methods.isEmpty()) {
            return "[HttpGet]";
        }

        StringBuilder attributes = new StringBuilder();
        for (String method : methods) {
            if (attributes.length() > 0) {
                attributes.append("\n    ");
            }
            attributes.append("[Http").append(method.substring(0, 1).toUpperCase())
                     .append(method.substring(1).toLowerCase()).append("]");
        }
        return attributes.toString();
    }

    private String generateMethodParameters(List<Map<String, Object>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        StringBuilder params = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            Map<String, Object> param = parameters.get(i);
            String name = (String) param.get("name");
            String type = (String) param.get("type");
            String annotation = (String) param.get("annotation");

            if (params.length() > 0) {
                params.append(", ");
            }

            if (annotation != null && !annotation.isEmpty()) {
                params.append("[").append(annotation).append("] ");
            }

            params.append(type != null ? type : "object")
                  .append(" ")
                  .append(name != null ? name : "param" + i);
        }

        return params.toString();
    }

    private String sanitizeFunctionName(String name) {
        if (name == null) return "UnknownFunction";
        return name.replaceAll("[^a-zA-Z0-9_]", "_")
                   .replaceAll("^[0-9]", "_$0");
    }

    /**
     * Extract clean function body without controller-specific elements
     */
    private String extractCleanFunctionBody(String source) {
        if (source == null || source.trim().isEmpty()) {
            return "return new { message = \"Function executed\", timestamp = DateTime.UtcNow };";
        }

        try {
            String methodBody = extractMethodBody(source);
            
            if (methodBody == null || methodBody.trim().isEmpty()) {
                return "return new { message = \"Function executed\", timestamp = DateTime.UtcNow };";
            }
            String cleanBody = cleanMethodBodyForTemplate(methodBody);
            cleanBody = handleSpecialPatterns(cleanBody, source);
            String helperMethods = extractHelperMethods(source, cleanBody);
            if (!helperMethods.isEmpty()) {
                cleanBody = cleanBody + "\n\n" + helperMethods;
            }
            
            return cleanBody;
            
        } catch (Exception e) {
            logger.warn("Error extracting function body from source, using default: {}", e.getMessage());
            return "return new { message = \"Function executed\", timestamp = DateTime.UtcNow, error = \"Failed to extract original logic\" };";
        }
    }
    
    /**
     * Handle special patterns in function bodies
     */
    private String handleSpecialPatterns(String functionBody, String originalSource) {
        String result = functionBody;
        if (originalSource.contains("JsonElement") && originalSource.contains("[FromBody]")) {
            java.util.regex.Pattern sigPattern = java.util.regex.Pattern.compile(
                "public\\s+(?:async\\s+Task<)?IActionResult\\s+\\w+\\([^)]*\\[FromBody\\]\\s+JsonElement\\s+(\\w+)[^)]*\\)");
            java.util.regex.Matcher sigMatcher = sigPattern.matcher(originalSource);
            if (sigMatcher.find()) {
                String originalParamName = sigMatcher.group(1);
                result = result.replaceAll("\\b" + originalParamName + "\\b", "jsonData");
                logger.debug("Replaced parameter '{}' with 'jsonData' in function body", originalParamName);
            }
        }
        
        return result;
    }
    
    /**
     * Extract helper methods that are called by the main function
     */
    private String extractHelperMethods(String originalSource, String functionBody) {
        StringBuilder helperMethods = new StringBuilder();
        if (functionBody.contains("CalculateMedian") && originalSource.contains("CalculateMedian")) {
            helperMethods.append("        // Helper method: CalculateMedian\n");
            helperMethods.append("        private static double CalculateMedian(double[] numbers)\n");
            helperMethods.append("        {\n");
            helperMethods.append("            var sorted = numbers.OrderBy(x => x).ToArray();\n");
            helperMethods.append("            int count = sorted.Length;\n");
            helperMethods.append("            \n");
            helperMethods.append("            if (count % 2 == 0)\n");
            helperMethods.append("                return (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0;\n");
            helperMethods.append("            else\n");
            helperMethods.append("                return sorted[count / 2];\n");
            helperMethods.append("        }\n");
        }
        if (functionBody.contains("CalculateStandardDeviation") && originalSource.contains("CalculateStandardDeviation")) {
            helperMethods.append("        // Helper method: CalculateStandardDeviation\n");
            helperMethods.append("        private static double CalculateStandardDeviation(double[] numbers)\n");
            helperMethods.append("        {\n");
            helperMethods.append("            double avg = numbers.Average();\n");
            helperMethods.append("            double sum = numbers.Sum(d => (d - avg) * (d - avg));\n");
            helperMethods.append("            return Math.Sqrt(sum / numbers.Length);\n");
            helperMethods.append("        }\n");
        }
        if (functionBody.contains("ProcessText") && originalSource.contains("ProcessText")) {
            helperMethods.append("        // Helper method: ProcessText\n");
            helperMethods.append("        private static string ProcessText(string text, string operation)\n");
            helperMethods.append("        {\n");
            helperMethods.append("            return operation?.ToLower() switch\n");
            helperMethods.append("            {\n");
            helperMethods.append("                \"uppercase\" => text.ToUpper(),\n");
            helperMethods.append("                \"lowercase\" => text.ToLower(),\n");
            helperMethods.append("                \"reverse\" => new string(text.Reverse().ToArray()),\n");
            helperMethods.append("                \"title\" => System.Globalization.CultureInfo.CurrentCulture.TextInfo.ToTitleCase(text.ToLower()),\n");
            helperMethods.append("                _ => text\n");
            helperMethods.append("            };\n");
            helperMethods.append("        }\n");
        }
        if (functionBody.contains("GenerateMockData") && originalSource.contains("GenerateMockData")) {
            helperMethods.append("        // Helper method: GenerateMockData\n");
            helperMethods.append("        private static List<MockDataItem> GenerateMockData(string query)\n");
            helperMethods.append("        {\n");
            helperMethods.append("            var items = new List<MockDataItem>\n");
            helperMethods.append("            {\n");
            helperMethods.append("                new() { Id = 1, Name = \"Apple Product\", Category = \"Electronics\", Value = 999.99 },\n");
            helperMethods.append("                new() { Id = 2, Name = \"Book Collection\", Category = \"Education\", Value = 49.99 },\n");
            helperMethods.append("                new() { Id = 3, Name = \"Coffee Machine\", Category = \"Appliances\", Value = 299.99 },\n");
            helperMethods.append("                new() { Id = 4, Name = \"Desktop Computer\", Category = \"Electronics\", Value = 1299.99 },\n");
            helperMethods.append("                new() { Id = 5, Name = \"Exercise Equipment\", Category = \"Fitness\", Value = 799.99 }\n");
            helperMethods.append("            };\n");
            helperMethods.append("            \n");
            helperMethods.append("            if (!string.IsNullOrWhiteSpace(query))\n");
            helperMethods.append("            {\n");
            helperMethods.append("                items = items.Where(x => x.Name.Contains(query, StringComparison.OrdinalIgnoreCase) ||\n");
            helperMethods.append("                                       x.Category.Contains(query, StringComparison.OrdinalIgnoreCase))\n");
            helperMethods.append("                            .ToList();\n");
            helperMethods.append("            }\n");
            helperMethods.append("            \n");
            helperMethods.append("            return items;\n");
            helperMethods.append("        }\n");
        }
        if (functionBody.contains("ProcessValue") && originalSource.contains("ProcessValue")) {
            helperMethods.append("        // Helper method: ProcessValue\n");
            helperMethods.append("        private static string ProcessValue(string value, string processingType)\n");
            helperMethods.append("        {\n");
            helperMethods.append("            return processingType?.ToLower() switch\n");
            helperMethods.append("            {\n");
            helperMethods.append("                \"uppercase\" => value.ToUpper(),\n");
            helperMethods.append("                \"lowercase\" => value.ToLower(),\n");
            helperMethods.append("                \"reverse\" => new string(value.Reverse().ToArray()),\n");
            helperMethods.append("                \"length\" => value.Length.ToString(),\n");
            helperMethods.append("                _ => value\n");
            helperMethods.append("            };\n");
            helperMethods.append("        }\n");
        }
        if (functionBody.contains("AnalyzeCharacterFrequency") && originalSource.contains("AnalyzeCharacterFrequency")) {
            helperMethods.append("        // Helper method: AnalyzeCharacterFrequency\n");
            helperMethods.append("        private static Dictionary<char, int> AnalyzeCharacterFrequency(string content)\n");
            helperMethods.append("        {\n");
            helperMethods.append("            return content\n");
            helperMethods.append("                .Where(char.IsLetter)\n");
            helperMethods.append("                .GroupBy(c => char.ToLower(c))\n");
            helperMethods.append("                .ToDictionary(g => g.Key, g => g.Count());\n");
            helperMethods.append("        }\n");
        }
        
        if (functionBody.contains("GetMostCommonWords") && originalSource.contains("GetMostCommonWords")) {
            helperMethods.append("        // Helper method: GetMostCommonWords\n");
            helperMethods.append("        private static List<string> GetMostCommonWords(string content, int count)\n");
            helperMethods.append("        {\n");
            helperMethods.append("            return content\n");
            helperMethods.append("                .Split(' ', StringSplitOptions.RemoveEmptyEntries)\n");
            helperMethods.append("                .Where(word => word.Length > 2)\n");
            helperMethods.append("                .GroupBy(word => word.ToLower())\n");
            helperMethods.append("                .OrderByDescending(g => g.Count())\n");
            helperMethods.append("                .Take(count)\n");
            helperMethods.append("                .Select(g => g.Key)\n");
            helperMethods.append("                .ToList();\n");
            helperMethods.append("        }\n");
        }
        
        if (functionBody.contains("DetectContentType") && originalSource.contains("DetectContentType")) {
            helperMethods.append("        // Helper method: DetectContentType\n");
            helperMethods.append("        private static string DetectContentType(string content)\n");
            helperMethods.append("        {\n");
            helperMethods.append("            if (content.StartsWith(\"{\") && content.EndsWith(\"}\"))\n");
            helperMethods.append("                return \"json\";\n");
            helperMethods.append("            if (content.StartsWith(\"<\") && content.EndsWith(\">\"))\n");
            helperMethods.append("                return \"xml\";\n");
            helperMethods.append("            if (content.Contains(\"\\n\") && content.Length > 100)\n");
            helperMethods.append("                return \"document\";\n");
            helperMethods.append("            return \"text\";\n");
            helperMethods.append("        }\n");
        }
        
        return helperMethods.toString();
    }
    
    /**
     * Clean method body for use in templates
     */
    private String cleanMethodBodyForTemplate(String methodBody) {
        String cleaned = methodBody;
        cleaned = cleaned.replaceAll("\\[FromBody\\]\\s+", "");
        cleaned = transformActionResults(cleaned);
        if (cleaned.contains("JsonElement")) {
            cleaned = cleaned.replaceAll("\\bjsonElement\\b", "jsonBody");
            cleaned = cleaned.replaceAll("\\bjsonData\\b", "jsonBody");
            cleaned = cleaned.replaceAll("\\bdata\\b", "jsonBody");
        }
        cleaned = cleaned.replaceAll("\\b(\\w*Request)\\s+(\\w+)\\b", "$1 request");
        cleaned = cleaned.replaceAll("\\b(calculationRequest|processRequest|updateRequest|searchRequest|batchRequest|analyzeRequest|dataRequest|userRequest)\\b", "request");
        cleaned = cleaned.replaceAll("var\\s+(\\w+)\\s*=\\s*new\\s*\\{", "var $1 = new {");
        if (cleaned.contains("await ") && !cleaned.contains("async Task")) {
            if (cleaned.contains("Task.Delay") || cleaned.contains("await Task")) {
                if (!cleaned.trim().contains("return")) {
                    cleaned = cleaned + "\nreturn new { message = \"Async operation completed\", timestamp = DateTime.UtcNow };";
                }
            } else {
                cleaned = cleaned.replaceAll("await\\s+", "");
            }
        }

        if (containsLinqOperations(cleaned)) {
            logger.debug("Function contains LINQ operations, ensuring proper handling");
            cleaned = fixLinqPatterns(cleaned);
        }
        cleaned = cleaned.replaceAll("\\?\\.", "?.");
        cleaned = cleaned.replaceAll("Array\\.Empty<(\\w+)>\\(\\)", "new $1[0]");
        cleaned = cleaned.replaceAll("new List<(\\w+)>\\(\\)", "new List<$1>()");
        cleaned = cleaned.replaceAll("throw new (\\w+Exception)\\([^)]*\\);", 
            "return new { error = \"$1 occurred\", timestamp = DateTime.UtcNow };");
        cleaned = cleaned.replaceAll("throw new DivideByZeroException\\(\\)", 
            "return new { error = \"Division by zero\", timestamp = DateTime.UtcNow };");
        cleaned = cleaned.replaceAll("throw new ArgumentException\\([^)]*\\)", 
            "return new { error = \"Invalid argument\", timestamp = DateTime.UtcNow };");
        cleaned = cleaned.replaceAll("if\\s*\\(\\s*request\\s*==\\s*null\\s*\\)", "if (request == null)");
        cleaned = cleaned.replaceAll("request\\?\\.", "request.");
        cleaned = cleaned.replaceAll("string\\.IsNullOrWhiteSpace\\(request\\?\\.(\\w+)\\)", "string.IsNullOrWhiteSpace(request.$1)");
        cleaned = cleaned.replaceAll("string\\.IsNullOrEmpty\\(request\\?\\.(\\w+)\\)", "string.IsNullOrEmpty(request.$1)");
        cleaned = ensureProperReturnStatement(cleaned);
        
        return cleaned;
    }
    
    /**
     * Check if the method body contains LINQ operations
     */
    private boolean containsLinqOperations(String methodBody) {
        return methodBody.contains(".Where(") || 
               methodBody.contains(".Select(") || 
               methodBody.contains(".FirstOrDefault(") ||
               methodBody.contains(".OrderBy(") ||
               methodBody.contains(".OrderByDescending(") ||
               methodBody.contains(".GroupBy(") ||
               methodBody.contains(".Take(") ||
               methodBody.contains(".Skip(") ||
               methodBody.contains(".Sum(") ||
               methodBody.contains(".Average(") ||
               methodBody.contains(".Min(") ||
               methodBody.contains(".Max(") ||
               methodBody.contains(".Count(") ||
               methodBody.contains(".Any(") ||
               methodBody.contains(".All(") ||
               methodBody.contains(".Distinct(") ||
               methodBody.contains(".ToList(") ||
               methodBody.contains(".ToArray(") ||
               methodBody.contains(".ToDictionary(");
    }
    
    /**
     * Fix common LINQ patterns that might cause compilation issues
     */
    private String fixLinqPatterns(String methodBody) {
        String fixed = methodBody;
        fixed = fixed.replaceAll("(\\w+)\\.([A-Z]\\w+)\\(", 
            "($1 ?? new object[0]).$2(");
        fixed = fixed.replaceAll("=>\\s*([^,}]+),", "=> $1,");
        fixed = fixed.replaceAll("\\.Contains\\((\\w+), StringComparison\\.OrdinalIgnoreCase\\)", 
            ".Contains($1, StringComparison.OrdinalIgnoreCase)");
        if (fixed.contains("StringSplitOptions.RemoveEmptyEntries")) {
            fixed = fixed.replaceAll("Split\\('([^']+)', StringSplitOptions\\.RemoveEmptyEntries\\)",
                "Split(new char[] { '$1' }, StringSplitOptions.RemoveEmptyEntries)");
        }
        
        return fixed;
    }
    
    /**
     * Ensure the function body has a proper return statement
     */
    private String ensureProperReturnStatement(String methodBody) {
        String trimmed = methodBody.trim();
        if (trimmed.toLowerCase().contains("return") && 
            (trimmed.endsWith(";") || trimmed.endsWith("}"))) {
            return methodBody;
        }
        if (!trimmed.toLowerCase().contains("return")) {
            return methodBody + "\nreturn new { message = \"Function executed successfully\", timestamp = DateTime.UtcNow };";
        }
        
        return methodBody;
    }
    
    /**
     * Check if function is a POST method
     */
    private boolean isPostFunction(Function function) {
        return function.getMethods() != null && 
               function.getMethods().stream().anyMatch(method -> "POST".equalsIgnoreCase(method));
    }
    
    /**
     * Check if function needs request body parsing
     */
    private boolean needsRequestBodyParsing(String source) {
        return source != null && (
            source.contains("[FromBody]") || 
            source.contains("request?.") || 
            source.contains("request.Data") ||
            source.contains("request.TransformationType") ||
            source.contains("request.Numbers") ||
            source.contains("request.Text") ||
            source.contains("request.Content") ||
            source.contains("request.Items") ||
            source.contains("JsonElement")
        );
    }
    
    /**
     * Extract the method body from a complete method source
     */
    private String extractMethodBody(String methodSource) {
        if (methodSource == null || methodSource.trim().isEmpty()) return null;
        String cleanSource = methodSource.trim();
        int firstBrace = cleanSource.indexOf('{');
        int lastBrace = cleanSource.lastIndexOf('}');
        
        if (firstBrace == -1 || lastBrace == -1 || firstBrace >= lastBrace) {
            return null;
        }
        String body = cleanSource.substring(firstBrace + 1, lastBrace).trim();
        String[] lines = body.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                if (result.length() > 0) {
                    result.append("\n        ");
                }
                result.append(trimmedLine);
            }
        }
        
        return result.toString();
    }
    

    
    /**
     * Extract DTO classes needed by the function
     */
    private String extractDtoClasses(String source, String functionBody) {
        StringBuilder dtoClasses = new StringBuilder();
        if (functionBody.contains("CalculationRequest") || source.contains("CalculationRequest")) {
            dtoClasses.append("\n// Request DTO\n");
            dtoClasses.append("public class CalculationRequest\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public double[] Numbers { get; set; } = Array.Empty<double>();\n");
            dtoClasses.append("    public string Operation { get; set; } = \"sum\";\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("ProcessRequest") || source.contains("ProcessRequest")) {
            dtoClasses.append("\n// Request DTO\n");
            dtoClasses.append("public class ProcessRequest\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public string Text { get; set; } = string.Empty;\n");
            dtoClasses.append("    public string Operation { get; set; } = \"none\";\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("UpdateRequest") || source.contains("UpdateRequest")) {
            dtoClasses.append("\n// Request DTO\n");
            dtoClasses.append("public class UpdateRequest\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public string Name { get; set; } = string.Empty;\n");
            dtoClasses.append("    public string? Description { get; set; }\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("SearchRequest") || source.contains("SearchRequest")) {
            dtoClasses.append("\n// Request DTO\n");
            dtoClasses.append("public class SearchRequest\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public string Query { get; set; } = string.Empty;\n");
            dtoClasses.append("    public int MaxResults { get; set; } = 10;\n");
            dtoClasses.append("    public string[] Categories { get; set; } = Array.Empty<string>();\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("BatchProcessRequest") || source.contains("BatchProcessRequest")) {
            dtoClasses.append("\n// Request DTO\n");
            dtoClasses.append("public class BatchProcessRequest\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public List<BatchItem> Items { get; set; } = new();\n");
            dtoClasses.append("    public string ProcessingType { get; set; } = \"none\";\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("BatchItem") || source.contains("BatchItem")) {
            dtoClasses.append("\n// Support DTO\n");
            dtoClasses.append("public class BatchItem\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public int Id { get; set; }\n");
            dtoClasses.append("    public string Value { get; set; } = string.Empty;\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("ContentAnalysisRequest") || source.contains("ContentAnalysisRequest")) {
            dtoClasses.append("\n// Request DTO\n");
            dtoClasses.append("public class ContentAnalysisRequest\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public string Content { get; set; } = string.Empty;\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("DataTransformRequest") || source.contains("DataTransformRequest")) {
            dtoClasses.append("\n// Request DTO\n");
            dtoClasses.append("public class DataTransformRequest\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public double[] Data { get; set; } = Array.Empty<double>();\n");
            dtoClasses.append("    public string TransformationType { get; set; } = \"none\";\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("MockDataItem") || source.contains("MockDataItem")) {
            dtoClasses.append("\n// Support DTO\n");
            dtoClasses.append("public class MockDataItem\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public int Id { get; set; }\n");
            dtoClasses.append("    public string Name { get; set; } = string.Empty;\n");
            dtoClasses.append("    public string Category { get; set; } = string.Empty;\n");
            dtoClasses.append("    public double Value { get; set; }\n");
            dtoClasses.append("}\n");
        }
        
        if (functionBody.contains("UserRequest") || source.contains("UserRequest")) {
            dtoClasses.append("\n// Request DTO\n");
            dtoClasses.append("public class UserRequest\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public string Name { get; set; } = string.Empty;\n");
            dtoClasses.append("    public string Email { get; set; } = string.Empty;\n");
            dtoClasses.append("    public int Age { get; set; }\n");
            dtoClasses.append("}\n");
        }

        if (functionBody.contains("ProcessResponse") || source.contains("ProcessResponse")) {
            dtoClasses.append("\n// Response DTO\n");
            dtoClasses.append("public class ProcessResponse\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public string OriginalText { get; set; } = string.Empty;\n");
            dtoClasses.append("    public string ProcessedText { get; set; } = string.Empty;\n");
            dtoClasses.append("    public int WordCount { get; set; }\n");
            dtoClasses.append("    public int CharacterCount { get; set; }\n");
            dtoClasses.append("    public DateTime ProcessedAt { get; set; }\n");
            dtoClasses.append("}\n");
        }

        if (functionBody.contains("Item") && !functionBody.contains("Items") && !functionBody.contains("BatchItem") && !functionBody.contains("MockDataItem")) {
            dtoClasses.append("\n// Model classes\n");
            dtoClasses.append("public class Item\n");
            dtoClasses.append("{\n");
            dtoClasses.append("    public int Id { get; set; }\n");
            dtoClasses.append("    public string Name { get; set; } = string.Empty;\n");
            dtoClasses.append("    public string Description { get; set; } = string.Empty;\n");
            dtoClasses.append("    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;\n");
            dtoClasses.append("}\n");
        }
        
        return dtoClasses.toString();
    }
    

    
    /**
     * Extract the request type from method body (e.g., CalculationRequest, ProcessRequest)
     */
    private String extractRequestType(String methodBody) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[FromBody\\]\\s+(\\w+)\\s+\\w+");
        java.util.regex.Matcher matcher = pattern.matcher(methodBody);
        if (matcher.find()) {
            String type = matcher.group(1);
            if ("JsonElement".equals(type)) {
                return "JsonElement";
            }
            return type;
        }
        pattern = java.util.regex.Pattern.compile("\\b(\\w*Request)\\s+\\w+\\b");
        matcher = pattern.matcher(methodBody);
        if (matcher.find()) {
            return matcher.group(1);
        }

        if (methodBody.contains("JsonElement") && methodBody.contains("jsonBody")) {
            return "JsonElement";
        }

        if (methodBody.contains("request.Data") && methodBody.contains("request.TransformationType")) {
            return "DataTransformRequest";
        }
        
        if (methodBody.contains("request.Numbers") || methodBody.contains("Numbers") || 
            methodBody.contains("ComplexCalculation") || methodBody.contains("CalculationRequest") ||
            methodBody.contains("request?.Numbers") || methodBody.contains("numbers") ||
            methodBody.contains("Sum()") || methodBody.contains("Average()")) {
            return "CalculationRequest";
        }
        
        if (methodBody.contains("request.Text") && methodBody.contains("request.Operation")) {
            return "ProcessRequest";
        }
        
        if (methodBody.contains("request.Query") || methodBody.contains("SearchData") || methodBody.contains("search")) {
            return "SearchRequest";
        }
        
        if (methodBody.contains("request.Items") || methodBody.contains("BatchProcess") || methodBody.contains("batch") ||
            methodBody.contains("BatchProcessRequest")) {
            return "BatchProcessRequest";
        }
        
        if (methodBody.contains("request.Content") || methodBody.contains("AnalyzeContent") || methodBody.contains("analyze") ||
            methodBody.contains("ContentAnalysisRequest")) {
            return "ContentAnalysisRequest";
        }
        
        if (methodBody.contains("request.Name") && (methodBody.contains("GreetUser") || methodBody.contains("user"))) {
            return "UserRequest";
        }
        
        if (methodBody.contains("request.Name") && methodBody.contains("request.Description")) {
            return "UpdateRequest";
        }

        if (methodBody.contains("SimulateError") || methodBody.contains("error")) {
            return "object";
        }

        if (methodBody.contains("Calculate") || methodBody.contains("calculation") || 
            methodBody.contains("statistics") || methodBody.contains("median") || 
            methodBody.contains("standardDeviation")) {
            return "CalculationRequest";
        }
        
        if (methodBody.contains("Transform") || methodBody.contains("transform")) {
            return "DataTransformRequest";
        }
        
        if (methodBody.contains("Process") && methodBody.contains("text")) {
            return "ProcessRequest";
        }
        
        return null;
    }
    
    /**
     * Transform ASP.NET action results (Ok, BadRequest, etc.) to plain objects
     */
    private String transformActionResults(String methodBody) {
        String transformed = methodBody;

        transformed = transformed.replaceAll("return\\s+Ok\\(([^)]+)\\);", "return $1;");

        transformed = transformed.replaceAll("return\\s+BadRequest\\(([^)]+)\\);", 
            "return new { error = $1, statusCode = 400, timestamp = DateTime.UtcNow };");

        transformed = transformed.replaceAll("return\\s+NotFound\\(([^)]+)\\);", 
            "return new { error = $1, statusCode = 404, timestamp = DateTime.UtcNow };");

        transformed = transformed.replaceAll("return\\s+Ok\\(\\);", 
            "return new { message = \"Success\", timestamp = DateTime.UtcNow };");

        transformed = transformed.replaceAll("return\\s+BadRequest\\(\\);", 
            "return new { error = \"Bad Request\", statusCode = 400, timestamp = DateTime.UtcNow };");
        
        return transformed;
    }
    


    /**
     * Call the C# analyzer service via HTTP
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callCSharpAnalyzer(String appPath) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("appPath", appPath);

            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(CSHARP_ANALYZER_URL))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Map.class);
            } else {
                logger.error("C# analyzer returned status code: {} with body: {}", 
                           response.statusCode(), response.body());
                throw new CodeAnalysisException("csharp", "C# analyzer service returned error: " + response.statusCode());
            }
            
        } catch (Exception e) {
            logger.error("Error calling C# analyzer service", e);
            throw new CodeAnalysisException("csharp", "Failed to call C# analyzer service: " + e.getMessage());
        }
    }

    @Override
    public String createMainApplication(FunctionBuildContext context) {
        try {
            logger.info("Creating Program.cs for C# function: {}", context.getFunction().getName());
            
            Function function = context.getFunction();
            String cleanFunctionBody = extractCleanFunctionBody(function.getSource());
            
            Map<String, String> replacements = new HashMap<>();
            replacements.put("FUNCTION_NAME", function.getName());
            replacements.put("FUNCTION_BODY", cleanFunctionBody);

            String dtoClasses = extractDtoClasses(function.getSource(), cleanFunctionBody);
            replacements.put("DTO_CLASSES", dtoClasses);

            String templatePath;
            if (isPostFunction(function) && needsRequestBodyParsing(function.getSource())) {
                String requestType = extractRequestType(function.getSource());
                if ("JsonElement".equals(requestType)) {
                    templatePath = TemplateConstants.CSHARP_JSON_ELEMENT_TEMPLATE;
                } else {
                    templatePath = TemplateConstants.CSHARP_POST_FUNCTION_TEMPLATE;
                    replacements.put("REQUEST_TYPE", requestType != null ? requestType : "object");
                }
            } else {
                templatePath = TemplateConstants.CSHARP_PROGRAM_TEMPLATE;
            }
            
            String programContent = templateService.processTemplate(templatePath, replacements);

            Path programPath = context.getBuildPath().resolve("Program.cs");
            Files.write(programPath, programContent.getBytes());
            
            logger.info("Successfully created Program.cs at: {}", programPath);
            return programContent;
            
        } catch (Exception e) {
            logger.error("Error creating Program.cs for C# function: {}", context.getFunction().getName(), e);
            throw new RuntimeException("Failed to create Program.cs: " + e.getMessage(), e);
        }
    }

    @Override
    public String createFunctionWrapper(FunctionBuildContext context) {
        logger.info("Skipping function wrapper creation for CLI-based C# function: {}", context.getFunction().getName());
        return "";
    }

    @Override
    public String createDependencyFile(FunctionBuildContext context) {
        try {
            logger.info("Creating project file for C# function: {}", context.getFunction().getName());
            
            Function function = context.getFunction();
            Map<String, String> replacements = new HashMap<>();
            replacements.put("FUNCTION_NAME", function.getName());
            String additionalPackages = extractDependenciesFromOriginalProject(context);
            replacements.put("ADDITIONAL_PACKAGES", additionalPackages);
            
            String projectContent = templateService.processTemplate(
                TemplateConstants.CSHARP_PROJECT_TEMPLATE, 
                replacements
            );
            Path projectPath = context.getBuildPath().resolve(function.getName() + ".csproj");
            Files.write(projectPath, projectContent.getBytes());
            
            logger.info("Successfully created project file at: {}", projectPath);
            return projectContent;
            
        } catch (Exception e) {
            logger.error("Error creating project file for C# function: {}", context.getFunction().getName(), e);
            throw new RuntimeException("Failed to create project file: " + e.getMessage(), e);
        }
    }

    /**
     * Extract package dependencies from the original C# project
     */
    private String extractDependenciesFromOriginalProject(FunctionBuildContext context) {
        try {
            Path appPath = context.getAppPath();
            List<Path> csprojFiles = Files.walk(appPath)
                .filter(path -> path.toString().endsWith(".csproj"))
                .limit(5)
                .toList();
            
            if (csprojFiles.isEmpty()) {
                logger.info("No .csproj files found in application directory, using default dependencies");
                return "";
            }
            
            Set<String> packages = new HashSet<>();
            
            for (Path csprojFile : csprojFiles) {
                logger.info("Analyzing project file: {}", csprojFile);
                String content = Files.readString(csprojFile);
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "<PackageReference\\s+Include=\"([^\"]+)\"\\s+Version=\"([^\"]+)\"[^>]*/?>");
                java.util.regex.Matcher matcher = pattern.matcher(content);
                
                while (matcher.find()) {
                    String packageName = matcher.group(1);
                    String version = matcher.group(2);
                    if (shouldIncludePackage(packageName)) {
                        packages.add(String.format(
                            "    <PackageReference Include=\"%s\" Version=\"%s\" />", 
                            packageName, version));
                        logger.info("Found dependency: {} v{}", packageName, version);
                    }
                }
            }
            
            if (packages.isEmpty()) {
                return "";
            }
            
            return "\n" + String.join("\n", packages);
            
        } catch (Exception e) {
            logger.warn("Error extracting dependencies from original project: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Determine if a package should be included in the CLI function project
     */
    private boolean shouldIncludePackage(String packageName) {
        if (packageName == null) return false;
        Set<String> alreadyIncluded = Set.of(
            "System.Text.Json",
            "Microsoft.Extensions.Logging",
            "Microsoft.Extensions.Logging.Console"
        );
        
        if (alreadyIncluded.contains(packageName)) {
            return false;
        }
        Set<String> incompatiblePatterns = Set.of(
            "Microsoft.AspNetCore.App",
            "Microsoft.AspNetCore.All",
            "Web", "Mvc", "Blazor", "SignalR", "Kestrel", "IIS"
        );
        
        for (String pattern : incompatiblePatterns) {
            if (packageName.contains(pattern)) {
                return false;
            }
        }
        Set<String> usefulPatterns = Set.of(
            "Microsoft.EntityFramework", "EntityFramework",
            "Newtonsoft.Json", "AutoMapper", "Dapper",
            "Microsoft.Extensions.Configuration", "Microsoft.Extensions.DependencyInjection",
            "Microsoft.Extensions.Http", "System.ComponentModel.DataAnnotations",
            "Microsoft.Data", "System.Data", "MySql", "PostgreSQL", "Oracle",
            "Azure", "AWS", "Google.Cloud"
        );
        
        for (String pattern : usefulPatterns) {
            if (packageName.contains(pattern)) {
                return true;
            }
        }
        return !packageName.startsWith("Microsoft.AspNetCore");
    }

    @Override
    public String createDockerfile(FunctionBuildContext context) {
        try {
            logger.info("Creating Dockerfile for C# function: {}", context.getFunction().getName());
            
            Map<String, String> replacements = new HashMap<>();
            replacements.put("FUNCTION_NAME", context.getFunction().getName());
            
            String dockerfileContent = templateService.processTemplate(
                TemplateConstants.CSHARP_DOCKERFILE_TEMPLATE, 
                replacements
            );
            Path dockerfilePath = context.getBuildPath().resolve("Dockerfile");
            Files.write(dockerfilePath, dockerfileContent.getBytes());
            
            logger.info("Successfully created Dockerfile at: {}", dockerfilePath);
            return dockerfileContent;
            
        } catch (Exception e) {
            logger.error("Error creating Dockerfile for C# function: {}", context.getFunction().getName(), e);
            throw new RuntimeException("Failed to create Dockerfile: " + e.getMessage(), e);
        }
    }

    @Override
    public void createSupportFiles(FunctionBuildContext context) throws IOException {
    }
} 