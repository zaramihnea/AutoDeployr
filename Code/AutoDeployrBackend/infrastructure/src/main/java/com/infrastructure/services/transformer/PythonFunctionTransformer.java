package com.infrastructure.services.transformer;

import com.domain.entities.Function;
import com.domain.entities.FunctionBuildContext;
import com.domain.exceptions.ValidationException;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.infrastructure.exceptions.FileOperationException;
import com.infrastructure.exceptions.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.infrastructure.services.template.TemplateConstants;
import com.infrastructure.services.template.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Transformer for Python Flask functions.
 * This class is responsible for generating the specific Python files (main.py, wrapper, etc.)
 * including handling function dependencies and external module imports.
 */
@Component
public class PythonFunctionTransformer extends AbstractFunctionTransformer {
    private static final Logger logger = LoggerFactory.getLogger(PythonFunctionTransformer.class);
    private final String pythonExecutable;
    private final Path analyzerScriptPath;

    private final TemplateService templateService;
    private static final Set<String> STANDARD_LIBRARY_MODULES = Set.of(
            "os", "sys", "json", "datetime", "math", "random", "time",
            "logging", "re", "collections", "itertools", "functools",
            "io", "csv", "unittest", "flask", "werkzeug", "requests", "urllib",
            "hashlib", "base64", "uuid", "threading", "argparse", "tempfile"
    );

    /**
     * Create a new Python function transformer
     */
    @Autowired
    public PythonFunctionTransformer(TemplateService templateService) {
        super("python", "flask");
        this.pythonExecutable = findPythonExecutable();
        this.analyzerScriptPath = findAnalyzerScript();
        this.templateService = templateService;

        logger.info("Python Function Transformer initialized with executable: {}", pythonExecutable);
        logger.info("Using analyzer script at: {}", analyzerScriptPath);

        if (!Files.exists(analyzerScriptPath)) {
            logger.warn("Python analyzer script not found at: {}", analyzerScriptPath);
        }
    }

    /**
     * Find the appropriate Python executable for the current system
     *
     * @return Python executable path
     */
    private String findPythonExecutable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            Process p = pb.start();
            if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                return "python3";
            }
        } catch (Exception e) {
            logger.debug("python3 not available: {}", e.getMessage());
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "--version");
            Process p = pb.start();
            if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                return "python";
            }
        } catch (Exception e) {
            logger.debug("python not available: {}", e.getMessage());
        }

        logger.warn("Could not verify Python installation, defaulting to 'python'. Ensure Python is in the system PATH.");
        return "python";
    }

    private Path findAnalyzerScript() {
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path analyzerPath = projectRoot.resolve("analyzers/flask-ast-analyzer/analyzer.py");
        
        if (Files.exists(analyzerPath)) {
            logger.debug("Found analyzer script at: {}", analyzerPath);
            return analyzerPath;
        }
        List<Path> candidatePaths = List.of(
            Paths.get("analyzers/flask-ast-analyzer/analyzer.py"),
            Paths.get("../analyzers/flask-ast-analyzer/analyzer.py"),
            Paths.get("infrastructure/analyzers/flask-ast-analyzer/analyzer.py")
        );
        
        for (Path candidate : candidatePaths) {
            if (Files.exists(candidate)) {
                logger.debug("Found analyzer script at fallback location: {}", candidate);
                return candidate.toAbsolutePath();
            }
        }
        
        logger.warn("Analyzer script not found, some functionality may be limited");
        return projectRoot.resolve("analyzers/flask-ast-analyzer/analyzer.py");
    }

    @Override
    public String createMainApplication(FunctionBuildContext context) {
        try {
            if (context == null) {
                throw new ValidationException("context", "Build context cannot be null");
            }
            context.validate();

            Function function = context.getFunction();
            StringBuilder mainCode = new StringBuilder();
            logger.info("=== ANALYZER DATA FOR FUNCTION {} ===", function.getName());
            logger.info("Dependencies: {}", function.getDependencies());
            logger.info("Dependency sources keys: {}", 
                       function.getDependencySources() != null ? function.getDependencySources().keySet() : "null");
            if (function.getDependencySources() != null) {
                for (Map.Entry<String, String> entry : function.getDependencySources().entrySet()) {
                    logger.info("Dependency source {}: {}", entry.getKey(), 
                               entry.getValue() != null ? entry.getValue().substring(0, Math.min(50, entry.getValue().length())) + "..." : "null");
                }
            }
            logger.info("Imports count: {}", function.getImports() != null ? function.getImports().size() : 0);
            if (function.getImports() != null) {
                for (Function.ImportDefinition imp : function.getImports()) {
                    logger.info("Import: {} -> {}", imp.getModule(), imp.getAlias());
                }
            }
            logger.info("Env vars: {}", function.getEnvVars());
            logger.info("=== END ANALYZER DATA ===");
            if (function.getDependencySources() == null) {
                function.setDependencySources(new HashMap<>());
            }
            if (function.getSource().contains("library.add") || function.getSource().contains("library.")) {
                mainCode.append("# Import library module detected in function\n");
                mainCode.append("import library\n\n");
                logger.info("Added explicit import for library module");
            }
            mainCode.append("import os\n");
            mainCode.append("import logging\n");
            mainCode.append("import json\n");
            logger.info("Function {} has {} analyzer imports", function.getName(), 
                       function.getImports() != null ? function.getImports().size() : 0);
            
            if (function.getImports() != null && !function.getImports().isEmpty()) {
                for (Function.ImportDefinition imp : function.getImports()) {
                    String importStatement = formatImport(imp);
                    logger.info("Adding analyzer import: {} (module: {}, alias: {})", importStatement, imp.getModule(), imp.getAlias());
                    mainCode.append(importStatement).append("\n");
                }
            } else {
                logger.info("No analyzer imports for function: {}", function.getName());
            }
            mainCode.append("\n");
            mainCode.append("# Set up logging\n");
            mainCode.append("logging.basicConfig(\n");
            mainCode.append("    level=logging.INFO,\n");
            mainCode.append("    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'\n");
            mainCode.append(")\n");
            mainCode.append("logger = logging.getLogger(__name__)\n\n");
            logger.info("Using analyzer-provided dependencies for function: {}", function.getName());
            logger.info("Using analyzer-provided imports only for function: {}", function.getName());

            appendEnvVars(mainCode, function);
            appendConfigCode(mainCode, function);
            appendGlobalVars(mainCode, function);

            mainCode.append("# Initialize Flask app\n");
            String originalFlaskVarName = "app";
            try {
                Path appPath = context.getAppPath();
                Path appFilePath = null;
                String[] commonAppFiles = {"app.py", "main.py", "server.py", "wsgi.py", "application.py"};
                for (String filename : commonAppFiles) {
                    Path candidate = appPath.resolve(filename);
                    if (Files.exists(candidate)) {
                        appFilePath = candidate;
                        break;
                    }
                }
                if (appFilePath != null) {
                    String fileContent = Files.readString(appFilePath);
                    Pattern flaskAppPattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*Flask\\s*\\(");
                    Matcher matcher = flaskAppPattern.matcher(fileContent);
                    if (matcher.find()) {
                        originalFlaskVarName = matcher.group(1);
                        logger.info("Found original Flask app variable name: {}", originalFlaskVarName);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error detecting original Flask app variable name: {}", e.getMessage());
            }
            
            mainCode.append(originalFlaskVarName).append(" = Flask(__name__)\n\n");
            appendDbCode(mainCode, function);

            //Add dependencies
            if (function.getDependencies() != null && !function.getDependencies().isEmpty()) {
                mainCode.append("\n# --- Function Dependencies ---\n");
                
                for (String depName : function.getDependencies()) {
                    if (function.getDependencySources() != null && function.getDependencySources().containsKey(depName)) {
                        String depSource = function.getDependencySources().get(depName);
                        if (depSource != null && !depSource.trim().isEmpty()) {
                            mainCode.append("# Dependency: ").append(depName).append("\n");
                            mainCode.append(depSource).append("\n\n");
                            logger.info("Added analyzer-provided dependency: {}", depName);
                        }
                    } else {
                        logger.warn("Analyzer detected dependency '{}' but no source provided. Creating minimal stub.", depName);
                        mainCode.append("# Minimal stub for analyzer-detected dependency: ").append(depName).append("\n");
                        mainCode.append("def ").append(depName).append("(*args, **kwargs):\n");
                        mainCode.append("    logger.warning(\"Minimal stub for dependency ").append(depName).append(" called\")\n");
                        mainCode.append("    return args[0] if args else None\n\n");
                    }
                }
                
                mainCode.append("# --- End Function Dependencies ---\n\n");
            }

            // Add Database Connection
            if (function.isRequiresDb() || containsDatabaseCode(function)) {
                appendDatabaseConnectionSetup(mainCode, function);
            }
            if (usesGoogleGenerativeAI(function)) {
                appendGeminiConfiguration(mainCode, function);
            }

            // Add Route Function
            mainCode.append("# --- Main Route Function ---\n");
            boolean hasRouteDecorator = function.getSource().contains("@" + originalFlaskVarName + ".route(") ||
                                       function.getSource().contains("@app.route(");
            
            if (!hasRouteDecorator) {
                mainCode.append("@").append(originalFlaskVarName).append(".route('")
                        .append(function.getPath()).append("', methods=[");
                List<String> methods = function.getMethods();
                for (int i = 0; i < methods.size(); i++) {
                    mainCode.append("'").append(methods.get(i)).append("'");
                    if (i < methods.size() - 1) {
                        mainCode.append(", ");
                    }
                }
                mainCode.append("])\n");
                logger.info("Added route decorator for function: {}", function.getName());
            } else {
                logger.info("Function {} already has route decorator in source, skipping duplicate", function.getName());
            }
            
            mainCode.append(function.getSource()).append("\n");
            mainCode.append("# --- End Main Route Function ---\n\n");

            File mainFile = new File(context.getBuildPath().toFile(), "main.py");
            FileUtils.writeStringToFile(mainFile, mainCode.toString(), "UTF-8");
            fixHttpMethods(mainFile.getAbsolutePath());

            logger.debug("Created main.py for function: {}", function.getName());
            return mainCode.toString();

        } catch (ValidationException e) {
            logger.error("Validation error creating main.py: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating main.py: {}", e.getMessage(), e);
            throw new FileOperationException("write", "main.py", "Failed to write main.py file", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating main.py for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage(), e);
            throw new CodeAnalysisException("Failed to create main.py: " + e.getMessage(), e);
        }
    }

    /**
     * Generate import statements and track imported modules (legacy method)
     */
    private Set<String> generateImportStatements(Function function, Set<String> importedModules) {
        Set<String> importStatements = new HashSet<>();
        if (function.getSource().contains("library.add") || function.getSource().contains("library.")) {
            importedModules.add("library");
            importStatements.add("import library");
            logger.info("Added 'library' to imported modules based on usage in function");
        }
        for (Function.ImportDefinition imp : function.getImports()) {
            importStatements.add(formatImport(imp));
            String topLevelModule = imp.getModule().split("\\.")[0];
            if (!isStandardLibraryModule(topLevelModule)) {
                importedModules.add(topLevelModule);
            }
        }
        for (Function.ImportDefinition imp : function.getDbImports()) {
            importStatements.add(formatImport(imp));
            String topLevelModule = imp.getModule().split("\\.")[0];
            if (!isStandardLibraryModule(topLevelModule)) {
                importedModules.add(topLevelModule);
            }
        }

        return importStatements;
    }

    /**
     * Check if a module is a standard library module
     */
    private boolean isStandardLibraryModule(String moduleName) {
        return STANDARD_LIBRARY_MODULES.contains(moduleName);
    }

    /**
     * Check if a module exists locally in the application directory
     * 
     * @param moduleName The module name to check
     * @param context The function build context
     * @return true if the module exists locally as a file or directory
     */
    private boolean isLocalModule(String moduleName, FunctionBuildContext context) {
        try {
            Path appPath = context.getAppPath();
            if (appPath == null || !Files.exists(appPath)) {
                return false;
            }
            Path moduleFile = appPath.resolve(moduleName + ".py");
            if (Files.exists(moduleFile)) {
                logger.debug("Found local module file: {}", moduleFile);
                return true;
            }
            Path moduleDir = appPath.resolve(moduleName);
            if (Files.exists(moduleDir) && Files.isDirectory(moduleDir)) {
                Path initFile = moduleDir.resolve("__init__.py");
                if (Files.exists(initFile)) {
                    logger.debug("Found local module directory: {}", moduleDir);
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            logger.warn("Error checking if module '{}' is local: {}", moduleName, e.getMessage());
            return false;
        }
    }

    private String formatImport(Function.ImportDefinition imp) {
        if (imp.getModule().equals(imp.getAlias())) {
            return "import " + imp.getModule();
        } else {
            // Handle 'from X import Y as Z' or 'import X.Y as Z'
            if (imp.getModule().contains(".")) {
                // Basic check for 'from module import name' pattern
                int lastDot = imp.getModule().lastIndexOf('.');
                if (lastDot > 0) {
                    String modulePart = imp.getModule().substring(0, lastDot);
                    String namePart = imp.getModule().substring(lastDot + 1);
                    // Check if alias matches the imported name part
                    if (namePart.equals(imp.getAlias())) {
                        return "from " + modulePart + " import " + namePart;
                    }
                    // Check if alias is different, representing 'as'
                    if (!namePart.equals(imp.getAlias())) {
                        return "from " + modulePart + " import " + namePart + " as " + imp.getAlias();
                    }
                }
            }
            return "import " + imp.getModule() + " as " + imp.getAlias();
        }
    }

    private void appendEnvVars(StringBuilder mainCode, Function function) {
        if (function.getEnvVars() != null && !function.getEnvVars().isEmpty()) {
            mainCode.append("# Environment variables used by this function (from analyzer)\n");
            mainCode.append("# Variables: ")
                    .append(String.join(", ", function.getEnvVars()))
                    .append("\n\n");
            logger.info("Function {} uses {} env vars: {}", function.getName(), 
                       function.getEnvVars().size(), function.getEnvVars());
        } else {
            logger.info("Function {} uses no environment variables", function.getName());
        }
    }

    private void appendConfigCode(StringBuilder mainCode, Function function) {
        if (function.getConfigCode() != null && !function.getConfigCode().isEmpty()) {
            mainCode.append("# Configuration Code Blocks\n");
            for (Map.Entry<String, String> entry : function.getConfigCode().entrySet()) {
                mainCode.append("# Config: ").append(entry.getKey()).append("\n");
                mainCode.append(entry.getValue()).append("\n");
            }
            mainCode.append("\n");
        }
    }

    private void appendGlobalVars(StringBuilder mainCode, Function function) {
        if (function.getGlobalVars() != null && !function.getGlobalVars().isEmpty()) {
            mainCode.append("# Global Variables\n");
            for (Map.Entry<String, String> entry : function.getGlobalVars().entrySet()) {
                mainCode.append("# Global Var: ").append(entry.getKey()).append("\n");
                mainCode.append(entry.getValue()).append("\n");
            }
            mainCode.append("\n");
        }
    }

    private void appendDbCode(StringBuilder mainCode, Function function) {
        if (function.getDbCode() != null && !function.getDbCode().isEmpty()) {
            mainCode.append("# Database Configuration/Initialization Code Blocks\n");
            for (Map.Entry<String, String> entry : function.getDbCode().entrySet()) {
                mainCode.append("# DB Config: ").append(entry.getKey()).append("\n");
                mainCode.append(entry.getValue()).append("\n\n"); // Extra newline for separation
            }
        }
    }

    /**
     * Check if the function contains database-related code
     * 
     * @param function The function to check
     * @return true if the function uses database code
     */
    private boolean containsDatabaseCode(Function function) {
        if (function == null || function.getSource() == null) {
            return false;
        }
        
        String source = function.getSource();
        return source.contains("conn.") || 
               source.contains("cursor()") || 
               source.contains("psycopg2") || 
               source.contains("connect(") ||
               source.contains("rollback()") ||
               source.contains("commit()") ||
               source.contains("execute(") ||
               source.contains("fetchone()") ||
               source.contains("DATABASE_URL") ||
               source.contains("RealDictCursor");
    }

    /**
     * Check if the function uses Google Generative AI
     * 
     * @param function The function to check
     * @return true if the function uses Google Generative AI
     */
    private boolean usesGoogleGenerativeAI(Function function) {
        if (function == null || function.getSource() == null) {
            return false;
        }
        
        String source = function.getSource();
        return source.contains("genai.") || 
               source.contains("GenerativeModel") ||
               source.contains("generate_content");
    }

    /**
     * Append Google Generative AI configuration code to the main code
     * 
     * @param mainCode The StringBuilder to append to
     * @param function The function that needs Google Generative AI
     */
    private void appendGeminiConfiguration(StringBuilder mainCode, Function function) {
        mainCode.append("# --- Google Generative AI Configuration ---\n");
        mainCode.append("# Configure Gemini API\n");
        mainCode.append("genai.configure(api_key=os.getenv('GEMINI_API_KEY'))\n\n");
        mainCode.append("# --- End Google Generative AI Configuration ---\n\n");
    }

    /**
     * Append database connection setup code to the main code
     * 
     * @param mainCode The StringBuilder to append to
     * @param function The function that needs database connection
     */
    private void appendDatabaseConnectionSetup(StringBuilder mainCode, Function function) {
        mainCode.append("# --- Database Connection Setup ---\n");
        boolean usesDotenv = function.getSource().contains("load_dotenv") || 
                            function.getSource().contains("getenv") ||
                            function.getSource().contains("DATABASE_URL");
        
        if (usesDotenv) {
            mainCode.append("load_dotenv()\n\n");
        }
        
        mainCode.append("# Database connection setup\n");
        mainCode.append("DATABASE_URL = os.getenv('DATABASE_URL')\n\n");
        mainCode.append("if DATABASE_URL:\n");
        mainCode.append("    conn = psycopg2.connect(DATABASE_URL, cursor_factory=RealDictCursor)\n");
        mainCode.append("else:\n");
        mainCode.append("    DB_HOST = os.getenv('DB_HOST')\n");
        mainCode.append("    DB_PORT = os.getenv('DB_PORT', '5432')\n");
        mainCode.append("    DB_NAME = os.getenv('DB_NAME', 'postgres')\n");
        mainCode.append("    DB_USER = os.getenv('DB_USER', 'postgres')\n");
        mainCode.append("    DB_PASSWORD = os.getenv('DB_PASSWORD')\n\n");
        mainCode.append("    logger.info(f\"Connecting to {DB_USER}@{DB_HOST}:{DB_PORT}/{DB_NAME}\")\n\n");
        mainCode.append("    conn = psycopg2.connect(\n");
        mainCode.append("        host=DB_HOST,\n");
        mainCode.append("        port=DB_PORT,\n");
        mainCode.append("        dbname=DB_NAME,\n");
        mainCode.append("        user=DB_USER,\n");
        mainCode.append("        password=DB_PASSWORD,\n");
        mainCode.append("        cursor_factory=RealDictCursor\n");
        mainCode.append("    )\n\n");
        mainCode.append("# --- End Database Connection Setup ---\n\n");
    }

    /**
     * Scan the function source for all potential dependencies and add them to the function's dependencies list
     */
    private void scanAllDependenciesFromSource(Function function, FunctionBuildContext context) {
        try {
            if (function.getDependencies() == null) {
                function.setDependencies(new HashSet<>());
            }
            extractFunctionCalls(function);
            extractModuleReferences(function);
            if (context.getAppPath() != null && Files.exists(context.getAppPath())) {
                scanAppFilesForFunctionDefinitions(function, context);
            }
            
            logger.info("Updated function dependencies after scanning: {}", function.getDependencies());
        } catch (Exception e) {
            logger.warn("Error scanning dependencies from source: {}", e.getMessage());
        }
    }
    
    /**
     * Extract all function calls from the source code
     */
    private void extractFunctionCalls(Function function) {
        Pattern functionCallPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
        Matcher matcher = functionCallPattern.matcher(function.getSource());
        
        while (matcher.find()) {
            String potentialDep = matcher.group(1);
            if (!isCommonPythonFunction(potentialDep) && !potentialDep.equals(function.getName())) {
                String sourceAroundMatch = getContextAroundMatch(function.getSource(), matcher.start(), 20);
                if (sourceAroundMatch.contains("import " + potentialDep) || 
                    sourceAroundMatch.contains("from " + potentialDep)) {
                    continue;
                }
                if (sourceAroundMatch.matches(".*\\bfor\\s+\\w+\\s+in\\s+" + potentialDep + ".*")) {
                    continue;
                }
                
                function.getDependencies().add(potentialDep);
            }
        }
    }
    
    /**
     * Get context around a match position for better pattern analysis
     */
    private String getContextAroundMatch(String source, int position, int contextLength) {
        int start = Math.max(0, position - contextLength);
        int end = Math.min(source.length(), position + contextLength);
        return source.substring(start, end);
    }
    
    /**
     * Extract all module references from the source code
     */
    private void extractModuleReferences(Function function) {
        Pattern modulePattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\.\\s*([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher matcher = modulePattern.matcher(function.getSource());
        
        while (matcher.find()) {
            String moduleName = matcher.group(1);
            if (!isCommonPythonModule(moduleName)) {
                function.getDependencies().add(moduleName);
            }
        }
    }
    
    /**
     * Check if a module name is a common Python module that shouldn't be considered a dependency
     */
    private boolean isCommonPythonModule(String moduleName) {
        Set<String> commonModules = new HashSet<>(Arrays.asList(
            "os", "sys", "json", "datetime", "math", "random", "time",
            "logging", "re", "collections", "itertools", "functools",
            "io", "csv", "unittest", "flask", "werkzeug", "requests"
        ));
        
        return commonModules.contains(moduleName);
    }
    
    /**
     * Scan all Python files in the app directory for function definitions matching dependencies
     */
    private void scanAppFilesForFunctionDefinitions(Function function, FunctionBuildContext context) {
        try {
            Path appDir = context.getAppPath();
            if (function.getDependencySources() == null) {
                function.setDependencySources(new HashMap<>());
            }
            for (String depName : new HashSet<>(function.getDependencies())) {
                if (function.getDependencySources().containsKey(depName)) {
                    continue;
                }
                try (Stream<Path> paths = Files.walk(appDir)) {
                    paths.filter(p -> p.toString().endsWith(".py"))
                        .forEach(file -> {
                            try {
                                String content = Files.readString(file);
                                Pattern pattern = Pattern.compile("def\\s+" + Pattern.quote(depName) + "\\s*\\(");
                                Matcher matcher = pattern.matcher(content);
                                
                                if (matcher.find()) {
                                    int startPos = matcher.start();
                                    String functionCode = extractFunctionSource(content, startPos);
                                    function.getDependencySources().put(depName, functionCode);
                                    logger.info("Found function definition for '{}' in {}", depName, file);
                                }
                            } catch (IOException e) {
                                logger.warn("Error reading file: {}", file, e);
                            }
                        });
                }
            }
        } catch (Exception e) {
            logger.warn("Error scanning app files for function definitions: {}", e.getMessage());
        }
    }

    /**
     * Extract a function's source code from a string
     *
     * @param source Source code to extract from
     * @param startPos Position where the function definition begins
     * @return Extracted function source code
     */
    private String extractFunctionSource(String source, int startPos) {
        StringBuilder result = new StringBuilder();
        String[] lines = source.substring(startPos).split("\n");
        int baseIndentation = 0;
        if (lines.length > 0) {
            String firstLine = lines[0];
            baseIndentation = firstLine.indexOf("def ");
        }

        boolean inFunction = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (i > 0) {
                if (!line.isEmpty() && !line.trim().startsWith("#")) {
                    int currentIndent = line.length() - line.stripLeading().length();
                    if (currentIndent <= baseIndentation) {
                        inFunction = false;
                        break;
                    }
                }
            }

            if (inFunction) {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    @Override
    public String createFunctionWrapper(FunctionBuildContext context) {
        try {
            if (context == null) {
                throw new ValidationException("context", "Build context cannot be null");
            }
            context.validate();

            Function function = context.getFunction();
            String originalFlaskVarName = "app";
            
            try {
                Path appPath = context.getAppPath();
                Path appFilePath = null;
                String[] commonAppFiles = {"app.py", "main.py", "server.py", "wsgi.py", "application.py"};
                for (String filename : commonAppFiles) {
                    Path candidate = appPath.resolve(filename);
                    if (Files.exists(candidate)) {
                        appFilePath = candidate;
                        break;
                    }
                }
                if (appFilePath != null) {
                    String fileContent = Files.readString(appFilePath);
                    // find Flask app initialization: varname = Flask(__name__)
                    Pattern flaskAppPattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*Flask\\s*\\(");
                    Matcher matcher = flaskAppPattern.matcher(fileContent);
                    if (matcher.find()) {
                        originalFlaskVarName = matcher.group(1);
                        logger.info("Found original Flask app variable name for wrapper: {}", originalFlaskVarName);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error detecting original Flask app variable name for wrapper: {}", e.getMessage());
            }

            String sanitizedAppName = originalFlaskVarName.replaceAll("-", "_");

            Map<String, String> variables = new HashMap<>();
            variables.put("APP_NAME", sanitizedAppName);
            variables.put("ORIGINAL_APP_NAME", originalFlaskVarName);
            variables.put("FUNCTION_NAME", function.getName());
            variables.put("FUNCTION_ARGS", getFunctionArgs(function));

            String wrapperContent = templateService.processTemplate(
                TemplateConstants.PYTHON_FUNCTION_WRAPPER_TEMPLATE, variables);

            wrapperContent = enhanceWrapperForFlask(wrapperContent);

            if (!sanitizedAppName.equals(originalFlaskVarName)) {
                String importLine = "from main import " + sanitizedAppName;
                String newImportLine = "from main import " + sanitizedAppName + " as " + originalFlaskVarName;
                wrapperContent = wrapperContent.replace(importLine, newImportLine);
                wrapperContent = wrapperContent.replace("with " + sanitizedAppName + ".app_context()",
                                                       "with " + originalFlaskVarName + ".app_context()");
            }
            File wrapperFile = new File(context.getBuildPath().toFile(), "function_wrapper.py");
            FileUtils.writeStringToFile(wrapperFile, wrapperContent, "UTF-8");

            fixHttpMethods(wrapperFile.getAbsolutePath());

            logger.debug("Created function_wrapper.py for function: {}", function.getName());
            return wrapperContent;

        } catch (ValidationException e) {
            logger.error("Validation error creating function wrapper: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating function wrapper: {}", e.getMessage(), e);
            throw new FileOperationException("write", "function_wrapper.py", "Failed to write function_wrapper.py file", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating function wrapper for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage(), e);
            throw new CodeAnalysisException("Failed to create function wrapper: " + e.getMessage(), e);
        }
    }

    /**
     * Enhance the function wrapper template to better handle Flask objects and requests
     */
    private String enhanceWrapperForFlask(String wrapperContent) {
        wrapperContent = wrapperContent.replace(
            "import json",
            "import json\nfrom flask import Flask, request, jsonify"
        );
        wrapperContent = wrapperContent.replace(
            "        # Return the result as JSON",
            "        # Properly handle Flask response objects\n" +
            "        if isinstance(result, tuple) and len(result) == 2 and isinstance(result[0], dict):\n" +
            "            # Handle Flask (json_data, status_code) tuple format\n" +
            "            return {'statusCode': result[1], 'headers': {'Content-Type': 'application/json'}, 'body': json.dumps(result[0])}\n" +
            "        # Handle standard Flask response objects"
        );
        wrapperContent = wrapperContent.replace(
            "        return {'statusCode': 500, 'headers': {'Content-Type': 'application/json'}, 'body': json.dumps({'error': f'Error executing function {function_name}: {str(e)}', 'details': traceback.format_exc()})}",
            "        error_msg = f'Error executing function {function_name}: {str(e)}'\n" +
            "        details = traceback.format_exc()\n" +
            "        print(error_msg)\n" +
            "        print(details)\n" +
            "        return {'statusCode': 500, 'headers': {'Content-Type': 'application/json'}, 'body': json.dumps({'error': error_msg, 'details': details})}"
        );
        
        return wrapperContent;
    }

    @Override
    public String createDependencyFile(FunctionBuildContext context) {
        try {
            if (context == null) {
                throw new ValidationException("context", "Build context cannot be null");
            }
            context.validate();

            Function function = context.getFunction();
            Set<String> requirements = new HashSet<>();
            Map<String, String> commonPackages = getCommonPythonPackages();
            requirements.add(commonPackages.get("flask"));
            requirements.add(commonPackages.get("werkzeug"));
            requirements.add(commonPackages.get("jinja2"));
            requirements.add(commonPackages.get("markupsafe"));
            requirements.add(commonPackages.get("itsdangerous"));
            requirements.add(commonPackages.get("click"));
            if (function.getImports() != null) {
                for (Function.ImportDefinition imp : function.getImports()) {
                    String topLevelModule = imp.getModule().split("\\.")[0];
                    if (!isStandardLibraryModule(topLevelModule)) {
                        String packageName = topLevelModule.toLowerCase();
                        if (commonPackages.containsKey(packageName)) {
                            requirements.add(commonPackages.get(packageName));
                            logger.info("Added requirement for analyzer import '{}': {}", topLevelModule, commonPackages.get(packageName));
                        } else {
                            if (!isLocalModule(topLevelModule, context)) {
                                requirements.add(topLevelModule);
                                logger.info("Added direct requirement for unknown analyzer import: {}", topLevelModule);
                            }
                        }
                    }
                }
            }
            Path appPath = context.getAppPath();
            if (appPath != null) {
                Path requirmentsPath = appPath.resolve("requirements.txt");
                if (Files.exists(requirmentsPath)) {
                    try {
                        List<String> appRequirements = Files.readAllLines(requirmentsPath);
                        for (String line : appRequirements) {
                            line = line.trim();
                            if (!line.isEmpty() && !line.startsWith("#")) {
                                String pkgName = line.split("[=<>~!]")[0].trim().toLowerCase();
                                if (!commonPackages.containsKey(pkgName)) {
                                    requirements.add(line);
                                }
                            }
                        }
                        logger.info("Added dependencies from application requirements.txt: {}", requirmentsPath);
                    } catch (IOException e) {
                        logger.warn("Could not read application requirements.txt: {}", e.getMessage());
                    }
                }
            }
            if (function.getImports() != null) {
                for (Function.ImportDefinition imp : function.getImports()) {
                    String topLevelModule = imp.getModule().split("\\.")[0];
                    String packageName = topLevelModule.toLowerCase();
                    if (commonPackages.containsKey(packageName)) {
                        if ("boto3".equals(packageName)) {
                            requirements.add(commonPackages.get("botocore"));
                        }
                    }
                }
            }
            List<String> sortedRequirements = new ArrayList<>(requirements);
            sortedRequirements.sort(String::compareToIgnoreCase);
            String requirementsText = String.join("\n", sortedRequirements);
            File requirementsFile = new File(context.getBuildPath().toFile(), "requirements.txt");
            FileUtils.writeStringToFile(requirementsFile, requirementsText + "\n", "UTF-8");
            logger.debug("Created requirements.txt for function: {}", function.getName());
            return requirementsText;

        } catch (ValidationException e) {
            logger.error("Validation error creating requirements.txt: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating requirements.txt: {}", e.getMessage(), e);
            throw new FileOperationException("write", "requirements.txt", "Failed to write requirements.txt file", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating requirements.txt for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage(), e);
            throw new CodeAnalysisException("Failed to create requirements file: " + e.getMessage(), e);
        }
    }

    /**
     * Extract all imported modules from the application
     *
     * @param context Function build context
     * @return Set of all imported module names
     */
    private Set<String> extractAllImportedModules(FunctionBuildContext context) {
        Set<String> allModules = new HashSet<>();
        Function function = context.getFunction();
        if (function.getImports() != null) {
            for (Function.ImportDefinition imp : function.getImports()) {
                String topLevelModule = imp.getModule().split("\\.")[0];
                if (!isStandardLibraryModule(topLevelModule)) {
                    allModules.add(topLevelModule);
                    logger.debug("Added module from function imports: {}", topLevelModule);
                }
            }
        }
        if (function.getSource() != null && !function.getSource().trim().isEmpty()) {
            Set<String> sourceModules = extractModulesFromContent(function.getSource());
            allModules.addAll(sourceModules);
            logger.info("Added {} modules from function source: {}", sourceModules.size(), sourceModules);
        }
        File modulesFile = new File(context.getBuildPath().toFile(), ".imported-modules");
        if (modulesFile.exists()) {
            try {
                List<String> modulesFromFile = FileUtils.readLines(modulesFile, "UTF-8");
                allModules.addAll(modulesFromFile);
            } catch (IOException e) {
                logger.warn("Could not read .imported-modules file: {}", e.getMessage());
            }
        }
        logger.info("Using function-specific imports only. Total modules for {}: {}", 
                   function.getName(), allModules.size());
        
        return allModules;
    }

    /**
     * Extract module names from Python source content
     *
     * @param content Python source code
     * @return Set of top-level module names
     */
    private Set<String> extractModulesFromContent(String content) {
        Set<String> modules = new HashSet<>();
        Pattern importPattern = Pattern.compile("^\\s*import\\s+([\\w\\.]+)(?:\\s+as\\s+\\w+)?.*$", Pattern.MULTILINE);
        Matcher importMatcher = importPattern.matcher(content);
        while (importMatcher.find()) {
            String fullModuleName = importMatcher.group(1);
            String topLevelModule = fullModuleName.split("\\.")[0];
            if (!isStandardLibraryModule(topLevelModule)) {
                modules.add(topLevelModule);
                logger.debug("Extracted top-level module '{}' from import: {}", topLevelModule, fullModuleName);
            }
        }
        Pattern fromImportPattern = Pattern.compile("^\\s*from\\s+([\\w\\.]+)\\s+import\\s+.*$", Pattern.MULTILINE);
        Matcher fromImportMatcher = fromImportPattern.matcher(content);
        while (fromImportMatcher.find()) {
            String fullModuleName = fromImportMatcher.group(1);
            String topLevelModule = fullModuleName.split("\\.")[0];
            if (!isStandardLibraryModule(topLevelModule)) {
                modules.add(topLevelModule);
                logger.debug("Extracted top-level module '{}' from from-import: {}", topLevelModule, fullModuleName);
            }
        }
        
        logger.info("Extracted {} unique modules from content: {}", modules.size(), modules);
        return modules;
    }

    /**
     * Get a map of common Python packages and their requirements.txt entries
     *
     * @return Map of package names to requirements.txt entries
     */
    private Map<String, String> getCommonPythonPackages() {
        Map<String, String> packages = new HashMap<>();

        // Web frameworks and related
        packages.put("flask", "Flask==2.0.1");
        packages.put("werkzeug", "Werkzeug==2.0.1");
        packages.put("jinja2", "Jinja2==3.0.1");
        packages.put("requests", "requests==2.26.0");
        packages.put("click", "click==8.0.1");
        packages.put("itsdangerous", "itsdangerous==2.0.1");
        packages.put("markupsafe", "MarkupSafe==2.0.1");
        
        // Database
        packages.put("sqlalchemy", "SQLAlchemy==1.4.23");
        packages.put("pymysql", "PyMySQL==1.0.2");
        packages.put("pymongo", "pymongo==3.12.0");
        packages.put("psycopg2", "psycopg2-binary==2.9.7");
        
        // AWS
        packages.put("boto3", "boto3==1.18.44");
        packages.put("botocore", "botocore==1.21.44");
        
        // Data processing
        packages.put("pandas", "pandas==1.3.3");
        packages.put("numpy", "numpy==1.21.2");
        packages.put("matplotlib", "matplotlib==3.4.3");
        
        // Others
        packages.put("pillow", "Pillow==8.3.2");
        packages.put("pyjwt", "PyJWT==2.1.0");
        packages.put("python-dotenv", "python-dotenv==0.19.0");
        packages.put("dotenv", "python-dotenv==0.19.0");
        packages.put("six", "six==1.16.0");
        packages.put("pytz", "pytz==2021.1");
        packages.put("urllib3", "urllib3==1.26.6");
        
        // Google AI/ML packages
        packages.put("google", "google-generativeai==0.3.2");
        
        return packages;
    }

    @Override
    public String createDockerfile(FunctionBuildContext context) {
        try {
            if (context == null) {
                throw new ValidationException("context", "Build context cannot be null");
            }
            context.validate();

            Function function = context.getFunction();
            Map<String, String> variables = new HashMap<>();
            variables.put("FUNCTION_NAME", function.getName());
            String dockerfileContent = templateService.processTemplate(
                TemplateConstants.PYTHON_DOCKERFILE_TEMPLATE, variables);
            File dockerFile = new File(context.getBuildPath().toFile(), "Dockerfile");
            FileUtils.writeStringToFile(dockerFile, dockerfileContent, "UTF-8");
            logger.debug("Created Dockerfile for function: {}", function.getName());
            
            return dockerfileContent;

        } catch (ValidationException e) {
            logger.error("Validation error creating Dockerfile: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating Dockerfile: {}", e.getMessage(), e);
            throw new FileOperationException("write", "Dockerfile", "Failed to write Dockerfile", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating Dockerfile for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage(), e);
            throw new CodeAnalysisException("Failed to create Dockerfile: " + e.getMessage(), e);
        }
    }


    @Override
    public void createSupportFiles(FunctionBuildContext context) throws IOException {
        try {
            if (context == null) {
                throw new ValidationException("context", "Build context cannot be null");
            }
            context.validate();

            Path platformDir = context.getBuildPath().resolve("serverless_platform");
            Files.createDirectories(platformDir); // Ensure directory exists

            // __init__.py
            Path initFile = platformDir.resolve("__init__.py");
            if (!Files.exists(initFile)) { // Create only if not exists
                String initContent = templateService.processTemplate(TemplateConstants.PYTHON_INIT_TEMPLATE, new HashMap<>());
                Files.writeString(initFile, initContent);
            }

            // adapter.py
            String adapterContent = generateAdapterCode();
            Path adapterFile = platformDir.resolve("adapter.py");
            Files.writeString(adapterFile, adapterContent); // Overwrite if exists

            // Copy external module files
            copyExternalModuleFiles(context);

            logger.debug("Created/Updated serverless_platform support files in {}", platformDir);

        } catch (ValidationException e) {
            logger.error("Validation error creating support files: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            logger.error("I/O error creating support files: {}", e.getMessage(), e);
            throw new FileOperationException("create/write", "serverless_platform/*", "Failed to create support files", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating support files for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage(), e);
            throw new CodeAnalysisException("Unexpected error creating support files: " + e.getMessage(), e);
        }
    }

    /**
     * Copy external module files to the function build directory
     *
     * @param context Function build context
     * @throws IOException If file operations fail
     */
    private void copyExternalModuleFiles(FunctionBuildContext context) throws IOException {
        Function function = context.getFunction();
        Set<String> importedModules = new HashSet<>();
        if (function.getImports() != null) {
            for (Function.ImportDefinition imp : function.getImports()) {
                String topLevelModule = imp.getModule().split("\\.")[0];
                if (!isStandardLibraryModule(topLevelModule)) {
                    importedModules.add(topLevelModule);
                }
            }
        }
        File modulesFile = new File(context.getBuildPath().toFile(), ".imported-modules");
        if (modulesFile.exists()) {
            List<String> modulesFromFile = FileUtils.readLines(modulesFile, "UTF-8");
            importedModules.addAll(modulesFromFile);
            modulesFile.delete();
        }

        if (importedModules.isEmpty()) {
            logger.info("No external modules to copy for function: {}", function.getName());
            return;
        }

        Path appPath = context.getAppPath();
        Path buildPath = context.getBuildPath();
        int modulesCopied = 0;

        logger.info("Looking for external modules to copy: {}", importedModules);

        for (String moduleName : importedModules) {
            if (isStandardLibraryModule(moduleName)) {
                logger.debug("Skipping standard library module: {}", moduleName);
                continue;
            }

            // Look for .py file
            Path sourceFile = appPath.resolve(moduleName + ".py");
            if (Files.exists(sourceFile)) {
                Path targetFile = buildPath.resolve(moduleName + ".py");
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied external module file: {} to {}", sourceFile, targetFile);
                modulesCopied++;
            } else {
                // Look for a package directory
                Path sourceDir = appPath.resolve(moduleName);
                if (Files.exists(sourceDir) && Files.isDirectory(sourceDir)) {
                    Path initFile = sourceDir.resolve("__init__.py");
                    if (Files.exists(initFile)) {
                        Path targetDir = buildPath.resolve(moduleName);
                        if (!Files.exists(targetDir)) {
                            Files.createDirectories(targetDir);
                        }

                        // Copy all Python files in the package
                        try (Stream<Path> paths = Files.walk(sourceDir)) {
                            for (Path path : paths.filter(p -> p.toString().endsWith(".py")).collect(Collectors.toList())) {
                                Path relativePath = sourceDir.relativize(path);
                                Path destPath = targetDir.resolve(relativePath);
                                if (!Files.exists(destPath.getParent())) {
                                    Files.createDirectories(destPath.getParent());
                                }

                                Files.copy(path, destPath, StandardCopyOption.REPLACE_EXISTING);
                                logger.debug("Copied package file: {} to {}", path, destPath);
                            }
                        }

                        logger.info("Copied package directory: {} to {}", sourceDir, targetDir);
                        modulesCopied++;
                    } else {
                        logger.warn("Found directory {} but it's not a Python package (no __init__.py)", sourceDir);
                    }
                } else {
                    logger.warn("External module {} not found in app directory", moduleName);
                }
            }
        }

        logger.info("Copied {} external module(s) for function: {}", modulesCopied, function.getName());
    }

    /**
     * Generate the adapter code for Flask response normalization
     *
     * @return Adapter code
     */
    private String generateAdapterCode() {
        try {
            return templateService.processTemplate(TemplateConstants.PYTHON_ADAPTER_TEMPLATE, new HashMap<>());
        } catch (Exception e) {
            logger.error("Error loading adapter template: {}", e.getMessage(), e);
            throw new TemplateException("Failed to load adapter template", e);
        }
    }


    /**
     * Determine function arguments for the function wrapper call.
     * Extracts path parameters from the route definition.
     *
     * @param function Function to analyze
     * @return Comma-separated string of argument names (e.g., "user_id, item_id") or empty string.
     */
    private String getFunctionArgs(Function function) {
        List<String> argNames = new ArrayList<>();
        String path = function.getPath();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<(?:[^:]+:)?([^>]+)>");
        java.util.regex.Matcher matcher = pattern.matcher(path);

        while (matcher.find()) {
            String argName = matcher.group(1);
            if (argName != null && !argName.trim().isEmpty()) {
                argNames.add(argName.trim());
            }
        }
        String source = function.getSource();
        String name = function.getName();
        int funcDefStart = source.indexOf("def " + name);
        if (funcDefStart != -1) {
            int openParenPos = source.indexOf("(", funcDefStart);
            int closeParenPos = source.indexOf(")", openParenPos);
            if (openParenPos != -1 && closeParenPos != -1) {
                String paramsStr = source.substring(openParenPos + 1, closeParenPos).trim();
                if (!paramsStr.isEmpty()) {
                    String[] params = paramsStr.split(",");
                    List<String> signatureArgs = new ArrayList<>();
                    for (String p : params) {
                        String trimmedParam = p.trim().split(":")[0].split("=")[0].trim();
                        if (!trimmedParam.isEmpty() && !"self".equals(trimmedParam)) {
                            signatureArgs.add(trimmedParam);
                        }
                    }
                    if (!new HashSet<>(signatureArgs).containsAll(argNames)) {
                        logger.warn("Path parameters {} for function {} might not fully match signature parameters {}", argNames, name, signatureArgs);
                    }
                }
            }
        } else {
            logger.warn("Could not find function definition 'def {}' in source to verify parameters.", name);
        }


        return String.join(", ", argNames);
    }

    /**
     * Fix HTTP methods in a single generated Python file.
     * Calls the external Python script.
     *
     * @param filePath Path to the file
     */
    private void fixHttpMethods(String filePath) {
        runPythonHttpMethodFixer("--file", filePath);
    }

    /**
     * Runs the Python analyzer script with specific arguments for fixing methods.
     *
     * @param modeArg "--file" or "--fix-directory"
     * @param pathArg The file or directory path
     */
    private void runPythonHttpMethodFixer(String modeArg, String pathArg) {
        if (!Files.exists(analyzerScriptPath)) {
            logger.warn("Skipping HTTP method fixing: Analyzer script not found at {}", analyzerScriptPath);
            return;
        }
        if (pythonExecutable == null || pythonExecutable.isEmpty()) {
            logger.warn("Skipping HTTP method fixing: Python executable not determined.");
            return;
        }

        try {
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add(analyzerScriptPath.toString());
            command.add(modeArg);
            command.add(pathArg);

            logger.debug("Running Python HTTP method fixer: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.trace("Fixer output: {}", line);
                }
            }
            boolean completed = process.waitFor(15, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                logger.warn("Python HTTP method fixer timed out for {}. Process destroyed.", pathArg);
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("Python HTTP method fixer exited with code {} for {}. Output:\n{}",
                        exitCode, pathArg, output.toString().trim());
            } else {
                logger.debug("Python HTTP method fixer completed successfully for {}.", pathArg);
                if(!output.isEmpty()) logger.debug("Fixer Output:\n{}", output.toString().trim());
            }

        } catch (IOException | InterruptedException e) {
            logger.warn("Error running Python HTTP method fixer for {}: {}. Continuing...", pathArg, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) { // Catch unexpected runtime errors
            logger.error("Unexpected error running Python HTTP method fixer for {}: {}", pathArg, e.getMessage(), e);
        }
    }

    /**
     * Check if a function name is a common Python built-in or standard library function
     * that should not be considered a user dependency
     * 
     * @param functionName Function name to check
     * @return true if it's a common function, false otherwise
     */
    private boolean isCommonPythonFunction(String functionName) {
        Set<String> commonFunctions = new HashSet<>(Arrays.asList(
            "abs", "all", "any", "ascii", "bin", "bool", "bytearray", "bytes", "callable", "chr",
            "classmethod", "compile", "complex", "delattr", "dict", "dir", "divmod", "enumerate",
            "eval", "exec", "filter", "float", "format", "frozenset", "getattr", "globals",
            "hasattr", "hash", "help", "hex", "id", "input", "int", "isinstance", "issubclass",
            "iter", "len", "list", "locals", "map", "max", "memoryview", "min",
            "next", "object", "oct", "open", "ord", "pow", "print", "property", "range", "repr",
            "reversed", "round", "set", "setattr", "slice", "sorted", "staticmethod",
            "str", "sum", "super", "tuple", "type", "vars", "zip",
            "jsonify", "request", "make_response", "redirect", "abort",
            "in", "and", "or", "not", "is", "if", "else", "elif", "for", "while", "try", 
            "except", "finally", "with", "as", "def", "class", "return", "yield", "import",
            "from", "global", "nonlocal", "lambda", "pass", "break", "continue",
            "SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE", "INTO", "VALUES", 
            "ORDER", "GROUP", "HAVING", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", 
            "AS", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX", "UNION", "LIMIT", "OFFSET",
            "CREATE", "DROP", "ALTER", "TABLE", "INDEX", "PRIMARY", "KEY", "FOREIGN", 
            "REFERENCES", "NOT", "NULL", "DEFAULT", "AUTO_INCREMENT", "UNIQUE",
            "users", "user", "id", "name", "email", "password", "books", "book", "title",
            "created_at", "updated_at", "data", "value", "status", "type", "category"
        ));
        
        return commonFunctions.contains(functionName) || commonFunctions.contains(functionName.toUpperCase());
    }
}