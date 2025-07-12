using System.Text.Json.Serialization;

namespace AutoDeployrAIServiceAndNETanalyzer.Models;

// Request Models
public class AnalyzeApplicationRequest
{
    public required string AppPath { get; init; }
    public string? OutputFile { get; init; }
    public bool FixMethods { get; init; } = true;
}

public class AnalyzeFileRequest
{
    public required string FilePath { get; init; }
    public string? OutputFile { get; init; }
}

public class TransformApplicationRequest
{
    public required string AppPath { get; init; }
    public string? OutputPath { get; init; }
    public Dictionary<string, string>? EnvironmentVariables { get; init; }
    public string? AppName { get; init; }
}

// Analysis Result Models - Compatible with Java backend format
public class DotNetAnalysisResult
{
    public string Language { get; set; } = "csharp";
    public string Framework { get; set; } = "aspnet";
    public List<DotNetEndpoint> Endpoints { get; set; } = new();
    public Dictionary<string, string> ServiceSources { get; set; } = new();
    public HashSet<string> EnvironmentVariables { get; set; } = new();
}

public class DotNetEndpoint
{
    public required string Name { get; init; }
    public required string Path { get; init; }
    public List<string> Methods { get; init; } = new();
    public string? Source { get; init; } // Source code of the method
    public string? ClassName { get; init; } // Controller class name
    public string? PackageName { get; init; } // Namespace
    public HashSet<string> Dependencies { get; set; } = new();
    public Dictionary<string, string> DependencySources { get; init; } = new();
    public List<ParameterInfo> Parameters { get; init; } = new();
    public string? ReturnType { get; init; }
    public HashSet<string> Annotations { get; init; } = new();
}

public class ParameterInfo
{
    public required string Name { get; init; }
    public required string Type { get; init; }
    public string? Annotation { get; init; } // e.g., FromBody, FromQuery, FromRoute
}



// Transformation Result Models
public class TransformationResult
{
    public bool Success { get; init; }
    public string? Error { get; init; }
    public List<GeneratedFunction> GeneratedFunctions { get; init; } = new();
    public string? OutputPath { get; init; }
    public Dictionary<string, string> GeneratedFiles { get; init; } = new();
}

public class GeneratedFunction
{
    public required string Name { get; init; }
    public required string Path { get; init; }
    public List<string> Methods { get; init; } = new();
    public required string SourceCode { get; init; }
    public string? ProjectFile { get; init; }
    public List<string> Dependencies { get; init; } = new();
    public Dictionary<string, string> EnvironmentVariables { get; init; } = new();
    public string? AppName { get; init; }
}

// File Analysis Models
public class FileAnalysisResult
{
    public required string FileName { get; init; }
    public required string FilePath { get; init; }
    public List<DotNetEndpoint> Endpoints { get; set; } = new();
    public List<string> Dependencies { get; set; } = new();
    public HashSet<string> EnvironmentVariables { get; set; } = new();
    public bool IsController { get; set; }
    public bool IsService { get; set; }
    public bool IsModel { get; set; }
} 