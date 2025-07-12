package com.infrastructure.services.transformer;

import com.domain.entities.Function;
import com.domain.entities.FunctionBuildContext;
import com.domain.exceptions.ValidationException;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.infrastructure.exceptions.FileOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.infrastructure.services.template.TemplateConstants;
import com.infrastructure.services.template.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Transformer for Java Spring functions.
 * This class is responsible for generating the specific Java files for serverless functions.
 */
@Component
public class JavaFunctionTransformer extends AbstractFunctionTransformer {
    private static final Logger logger = LoggerFactory.getLogger(JavaFunctionTransformer.class);
    private static final Set<String> STANDARD_LIBRARY_PACKAGES = Set.of(
            "java.", "javax.", "org.springframework.", "com.fasterxml.jackson.",
            "org.slf4j.", "ch.qos.logback.", "org.apache.commons."
    );

    private final TemplateService templateService;

    @Autowired
    public JavaFunctionTransformer(TemplateService templateService) {
        super("java", "spring");
        this.templateService = templateService;
    }

    @Override
    public String createMainApplication(FunctionBuildContext context) {
        try {
            if (context == null) {
                throw new ValidationException("context", "Build context cannot be null");
            }
            context.validate();

            Function function = context.getFunction();
            
            // Prepare template variables
            Map<String, String> variables = new HashMap<>();
            variables.put("FUNCTION_NAME", function.getName());
            
            // Build additional imports
            StringBuilder additionalImports = new StringBuilder();
            addImportsFromAnalyzer(additionalImports, function);
            variables.put("ADDITIONAL_IMPORTS", additionalImports.toString());
            
            // Build global variables section
            StringBuilder globalVars = new StringBuilder();
            appendGlobalVars(globalVars, function);
            variables.put("GLOBAL_VARIABLES", globalVars.toString());
            
            // Build static initialization section
            StringBuilder staticInit = new StringBuilder();
            if (function.getEnvVars() != null && !function.getEnvVars().isEmpty()) {
                staticInit.append("        // Load environment variables\n");
                for (String envVar : function.getEnvVars()) {
                    staticInit.append("        String ").append(envVar.toLowerCase()).append(" = System.getenv(\"").append(envVar).append("\");\n");
                    staticInit.append("        if (").append(envVar.toLowerCase()).append(" == null) {\n");
                    staticInit.append("            logger.warn(\"Environment variable {} not set\");\n".replace("{}", envVar));
                    staticInit.append("        }\n");
                }
            }
            variables.put("STATIC_INITIALIZATION", staticInit.toString());
            
            // Build database setup if needed
            StringBuilder dbSetup = new StringBuilder();
            if (function.isRequiresDb() || containsDatabaseCode(function)) {
                appendDatabaseSetup(dbSetup, function);
            }
            variables.put("DATABASE_SETUP", dbSetup.toString());
            
            // Build function dependencies - trust the analyzer and include actual dependency sources
            StringBuilder functionDeps = new StringBuilder();
            if (function.getDependencies() != null && !function.getDependencies().isEmpty()) {
                functionDeps.append("    // --- Function Dependencies from Analyzer ---\n");
                for (String depName : function.getDependencies()) {
                    if (function.getDependencySources() != null && function.getDependencySources().containsKey(depName)) {
                        String depSource = function.getDependencySources().get(depName);
                        if (depSource != null && !depSource.trim().isEmpty()) {
                            functionDeps.append("    // Dependency: ").append(depName).append("\n");
                            String cleanedDependency = cleanFunctionSource(depSource);
                            functionDeps.append("    ").append(cleanedDependency.replace("\n", "\n    ")).append("\n\n");
                            logger.info("Added analyzer-provided dependency: {}", depName);
                        }
                    }
                }
                functionDeps.append("    // --- End Function Dependencies ---");
            }
            variables.put("FUNCTION_DEPENDENCIES", functionDeps.toString());
            
            // Add the cleaned function source
            StringBuilder functionSource = new StringBuilder();
            functionSource.append("    // --- Main Function ---\n");
            String cleanedFunctionSource = cleanFunctionSource(function.getSource());
            functionSource.append("    ").append(cleanedFunctionSource.replace("\n", "\n    ")).append("\n");
            functionSource.append("    // --- End Main Function ---");
            variables.put("FUNCTION_SOURCE", functionSource.toString());
            
            // Process template
            String mainApplicationContent = templateService.processTemplate(
                TemplateConstants.JAVA_MAIN_APPLICATION_TEMPLATE, variables);
            
            // Write main application file
            File mainFile = new File(context.getBuildPath().toFile(), "src/main/java/com/serverless/ServerlessApplication.java");
            mainFile.getParentFile().mkdirs();
            FileUtils.writeStringToFile(mainFile, mainApplicationContent, "UTF-8");

            logger.debug("Created template-based main application for function: {}", function.getName());
            return mainApplicationContent;

        } catch (ValidationException e) {
            logger.error("Validation error creating main application: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating main application: {}", e.getMessage());
            throw new FileOperationException("write", "ServerlessApplication.java", "Failed to write main application file", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating main application for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage());
            throw new CodeAnalysisException("Failed to create main application: " + e.getMessage(), e);
        }
    }

    /**
     * Clean function source by removing Spring annotations and adjusting for standalone execution
     */
    private String cleanFunctionSource(String source) {
        if (source == null) return "";
        
        String cleaned = source;
        cleaned = cleaned.replaceAll("@RequestMapping[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@GetMapping[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@PostMapping[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@PutMapping[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@DeleteMapping[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@PatchMapping[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@RestController[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@Controller[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@Autowired[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@Service[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@Component[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@CrossOrigin[^\\n]*\\n\\s*", "");
        cleaned = cleaned.replaceAll("@RequestParam[^)]*\\)\\s*", "");
        cleaned = cleaned.replaceAll("@PathVariable[^)]*\\)\\s*", "");
        cleaned = cleaned.replaceAll("@RequestBody\\s*", "");
        cleaned = cleaned.replaceAll("@RequestHeader[^)]*\\)\\s*", "");
        cleaned = cleaned.replaceAll("@Valid\\s*", "");
        if (!cleaned.contains("static")) {
            if (cleaned.contains("public ")) {
                cleaned = cleaned.replaceFirst("public ", "public static ");
            } else if (cleaned.contains("private ")) {
                cleaned = cleaned.replaceFirst("private ", "private static ");
            } else if (cleaned.contains("protected ")) {
                cleaned = cleaned.replaceFirst("protected ", "protected static ");
            }
        }
        
        return cleaned.trim();
    }

    /**
     * Copy only essential dependencies needed for function execution
     */
    private void copyEssentialDependencies(FunctionBuildContext context, Function function) throws IOException {
        Path buildPath = context.getBuildPath();
        Path appPath = context.getAppPath();
        File srcMainJava = new File(buildPath.toFile(), "src/main/java");
        srcMainJava.mkdirs();

        File srcMainResources = new File(buildPath.toFile(), "src/main/resources");
        srcMainResources.mkdirs();
        
        logger.info("Copying essential dependencies from {} to {}", appPath, buildPath);
        if (function.getDependencySources() != null && !function.getDependencySources().isEmpty()) {
            for (Map.Entry<String, String> entry : function.getDependencySources().entrySet()) {
                String dependencyName = entry.getKey();
                if (!dependencyName.startsWith("__") && dependencyName.contains(".")) {
                    copyDependencySourceFile(appPath, buildPath, dependencyName);
                }
            }
        }
        for (String ext : new String[]{"properties"}) {
            File[] files = appPath.toFile().listFiles((dir, name) -> 
                name.endsWith("." + ext) && !name.contains("application") && !name.contains("bootstrap"));
            if (files != null) {
                for (File file : files) {
                    FileUtils.copyFile(file, new File(buildPath.toFile(), file.getName()));
                    logger.debug("Copied configuration file: {}", file.getName());
                }
            }
        }
    }
    
    private void copyDependencySourceFile(Path appPath, Path buildPath, String className) {
        try {
            String filePath = className.replace(".", "/") + ".java";
            File sourceFile = new File(appPath.toFile(), "src/main/java/" + filePath);
            
            if (sourceFile.exists()) {
                File targetFile = new File(buildPath.toFile(), "src/main/java/" + filePath);
                targetFile.getParentFile().mkdirs();
                FileUtils.copyFile(sourceFile, targetFile);
                logger.debug("Copied dependency source file: {}", filePath);
            }
        } catch (Exception e) {
            logger.warn("Could not copy dependency source file for {}: {}", className, e.getMessage());
        }
    }

    /**
     * Legacy method - redirects to new simplified approach
     */
    private void copyDependencies(FunctionBuildContext context, Function function, Set<String> importedPackages) throws IOException {
        copyEssentialDependencies(context, function);
    }

    @Override
    public String createFunctionWrapper(FunctionBuildContext context) {
        try {
            if (context == null) {
                throw new ValidationException("context", "Build context cannot be null");
            }
            context.validate();

            Function function = context.getFunction();
            Map<String, String> variables = new HashMap<>();
            variables.put("FUNCTION_NAME", function.getName());
            String functionCall = generateComprehensiveFunctionCall(function);
            variables.put("FUNCTION_CALL", functionCall);
            String wrapperContent = templateService.processTemplate(
                TemplateConstants.JAVA_FUNCTION_WRAPPER_TEMPLATE, variables);
            File wrapperFile = new File(context.getBuildPath().toFile(), "src/main/java/com/serverless/FunctionWrapper.java");
            wrapperFile.getParentFile().mkdirs();
            FileUtils.writeStringToFile(wrapperFile, wrapperContent, "UTF-8");

            logger.debug("Created comprehensive function wrapper for function: {}", function.getName());
            return wrapperContent;

        } catch (ValidationException e) {
            logger.error("Validation error creating function wrapper: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating function wrapper: {}", e.getMessage());
            throw new FileOperationException("write", "FunctionWrapper.java", "Failed to write function wrapper file", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating function wrapper for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage());
            throw new CodeAnalysisException("Failed to create function wrapper: " + e.getMessage(), e);
        }
    }

    @Override
    public String createDependencyFile(FunctionBuildContext context) {
        try {
            if (context == null) {
                throw new ValidationException("context", "Build context cannot be null");
            }
            context.validate();

            Function function = context.getFunction();
            logger.info("Generating simplified pom.xml for function: {}", function.getName());
            Map<String, String> variables = new HashMap<>();
            variables.put("ARTIFACT_ID", sanitizeForArtifactId(function.getName()));
            variables.put("FUNCTION_NAME", function.getName());
            variables.put("FUNCTION_PATH", function.getPath());
            if (function.isRequiresDb()) {
                variables.put("#REQUIRES_DB", "true");
            } else {
                variables.put("#REQUIRES_DB", null);
            }
            boolean requiresZxing = false;
            if (function.getDependencySources() != null && !function.getDependencySources().isEmpty()) {
                for (String dependencySource : function.getDependencySources().values()) {
                    if (dependencySource != null && (dependencySource.contains("QRCodeWriter") || 
                        dependencySource.contains("com.google.zxing") || 
                        dependencySource.contains("BarcodeFormat") ||
                        dependencySource.contains("BitMatrix"))) {
                        requiresZxing = true;
                        break;
                    }
                }
            }
            
            if (requiresZxing) {
                variables.put("#REQUIRES_ZXING", "true");
                logger.info("Adding ZXing dependencies for function: {}", function.getName());
            } else {
                variables.put("#REQUIRES_ZXING", null);
            }
            boolean requiresRss = false;
            if (function.getSource() != null && 
                (function.getSource().contains("SyndFeed") || 
                 function.getSource().contains("SyndEntry") || 
                 function.getSource().contains("SyndFeedInput") ||
                 function.getSource().contains("XmlReader") ||
                 function.getSource().contains("com.rometools.rome"))) {
                requiresRss = true;
            }
            boolean requiresSpring = false;
            if (function.getSource() != null && 
                (function.getSource().contains("ResponseEntity") || 
                 function.getSource().contains("org.springframework"))) {
                requiresSpring = true;
            }
            
            if (requiresRss) {
                variables.put("#REQUIRES_RSS", "true");
                logger.info("Adding RSS/Rome dependencies for function: {}", function.getName());
            } else {
                variables.put("#REQUIRES_RSS", null);
            }
            
            if (requiresSpring) {
                variables.put("#REQUIRES_SPRING", "true");
                logger.info("Adding Spring dependencies for function: {}", function.getName());
            } else {
                variables.put("#REQUIRES_SPRING", null);
            }
            String pomContent = templateService.processTemplate(
                TemplateConstants.JAVA_POM_TEMPLATE, variables);
            File pomFile = new File(context.getBuildPath().toFile(), "pom.xml");
            FileUtils.writeStringToFile(pomFile, pomContent, "UTF-8");

            logger.debug("Created pom.xml for function: {}", function.getName());
            return pomContent;

        } catch (ValidationException e) {
            logger.error("Validation error creating pom.xml: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating pom.xml: {}", e.getMessage());
            throw new FileOperationException("write", "pom.xml", "Failed to write pom.xml file", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating pom.xml for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage());
            throw new CodeAnalysisException("Failed to create pom.xml: " + e.getMessage(), e);
        }
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
                TemplateConstants.JAVA_DOCKERFILE_TEMPLATE, variables);
            File dockerFile = new File(context.getBuildPath().toFile(), "Dockerfile");
            FileUtils.writeStringToFile(dockerFile, dockerfileContent, "UTF-8");
            
            logger.debug("Created Dockerfile for function: {}", function.getName());
            return dockerfileContent;

        } catch (ValidationException e) {
            logger.error("Validation error creating Dockerfile: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating Dockerfile: {}", e.getMessage());
            throw new FileOperationException("write", "Dockerfile", "Failed to write Dockerfile", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating Dockerfile for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage());
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

            Function function = context.getFunction();
            createMavenWrapper(context);
            createBuildScript(context);
            createRunScript(context, function);

            logger.debug("Created support files for function: {}", function.getName());
        } catch (ValidationException e) {
            logger.error("Validation error creating support files: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            logger.error("File operation error creating support files: {}", e.getMessage());
            throw new FileOperationException("write", "support files", "Failed to write support files", e);
        } catch (Exception e) {
            logger.error("Unexpected error creating support files for function {}: {}",
                    context != null && context.getFunction() != null ? context.getFunction().getName() : "unknown",
                    e.getMessage());
            throw new CodeAnalysisException("Failed to create support files: " + e.getMessage(), e);
        }
    }

    /**
     * Add imports from analyzer (matching Python's import handling)
     */
    private void addImportsFromAnalyzer(StringBuilder mainCode, Function function) {
        mainCode.append("import com.fasterxml.jackson.databind.ObjectMapper;\n");
        mainCode.append("import java.util.Map;\n");
        mainCode.append("import java.util.HashMap;\n");
        mainCode.append("import java.util.List;\n");
        mainCode.append("import java.util.ArrayList;\n");
        mainCode.append("import java.util.Properties;\n");
        mainCode.append("import java.io.InputStream;\n");
        mainCode.append("import org.slf4j.Logger;\n");
        mainCode.append("import org.slf4j.LoggerFactory;\n");
        mainCode.append("import org.springframework.http.ResponseEntity;\n");
        if (function.getSource() != null && 
            (function.getSource().contains("SyndFeed") || 
             function.getSource().contains("SyndEntry") || 
             function.getSource().contains("SyndFeedInput") ||
             function.getSource().contains("XmlReader"))) {
            mainCode.append("import com.rometools.rome.feed.synd.SyndEntry;\n");
            mainCode.append("import com.rometools.rome.feed.synd.SyndFeed;\n");
            mainCode.append("import com.rometools.rome.io.SyndFeedInput;\n");
            mainCode.append("import com.rometools.rome.io.XmlReader;\n");
            mainCode.append("import java.net.URL;\n");
            logger.info("Added RSS parsing imports for function: {}", function.getName());
        }
        if (function.getSource() != null && function.getSource().contains("Arrays.asList")) {
            mainCode.append("import java.util.Arrays;\n");
            logger.info("Added Arrays import for function: {}", function.getName());
        }
        if (function.getImports() != null && !function.getImports().isEmpty()) {
            mainCode.append("// Imports from analyzer\n");
            for (Function.ImportDefinition imp : function.getImports()) {
                String javaImport = convertPythonImportToJava(imp);
                if (javaImport != null) {
                    mainCode.append(javaImport).append("\n");
                }
            }
            logger.info("Added {} analyzer imports for function: {}", function.getImports().size(), function.getName());
        }
        Set<String> sourceImports = extractImports(function.getSource());
        for (String importLine : sourceImports) {
            if (!importLine.contains("springframework")) {
                mainCode.append(importLine).append("\n");
            }
        }
        
        mainCode.append("\n");
    }

    /**
     * Convert Python import to Java equivalent (simple mapping)
     */
    private String convertPythonImportToJava(Function.ImportDefinition pythonImport) {
        logger.debug("Python import detected: {} as {}", pythonImport.getModule(), pythonImport.getAlias());
        return null;
    }

    /**
     * Append global variables (matching Python's appendGlobalVars)
     */
    private void appendGlobalVars(StringBuilder mainCode, Function function) {
        if (function.getGlobalVars() != null && !function.getGlobalVars().isEmpty()) {
            mainCode.append("// Global Variables\n");
            for (Map.Entry<String, String> entry : function.getGlobalVars().entrySet()) {
                mainCode.append("// Global Var: ").append(entry.getKey()).append("\n");
                mainCode.append("// ").append(entry.getValue().replace("\n", "\n// ")).append("\n");
            }
            mainCode.append("\n");
        }
    }

    /**
     * Check if function contains database-related code (matching Python's check)
     */
    private boolean containsDatabaseCode(Function function) {
        if (function == null || function.getSource() == null) {
            return false;
        }
        
        String source = function.getSource();
        return source.contains("Connection") || 
               source.contains("PreparedStatement") || 
               source.contains("ResultSet") || 
               source.contains("DriverManager.getConnection") ||
               source.contains("DataSource") ||
               source.contains("JdbcTemplate") ||
               source.contains("@Repository") ||
               source.contains("DATABASE_URL");
    }

    /**
     * Append database setup (matching Python's database connection setup)
     */
    private void appendDatabaseSetup(StringBuilder mainCode, Function function) {
        mainCode.append("    // --- Database Connection Setup ---\n");
        mainCode.append("    private static java.sql.Connection dbConnection;\n");
        mainCode.append("    \n");
        mainCode.append("    static {\n");
        mainCode.append("        try {\n");
        mainCode.append("            String dbUrl = System.getenv(\"DATABASE_URL\");\n");
        mainCode.append("            if (dbUrl != null) {\n");
        mainCode.append("                dbConnection = java.sql.DriverManager.getConnection(dbUrl);\n");
        mainCode.append("                logger.info(\"Database connection established\");\n");
        mainCode.append("            } else {\n");
        mainCode.append("                logger.warn(\"DATABASE_URL not set - database operations may fail\");\n");
        mainCode.append("            }\n");
        mainCode.append("        } catch (Exception e) {\n");
        mainCode.append("            logger.error(\"Failed to establish database connection: {}\", e.getMessage());\n");
        mainCode.append("        }\n");
        mainCode.append("    }\n");
        mainCode.append("    // --- End Database Setup ---\n\n");
    }

    /**
     * Generate comprehensive function call with proper parameter handling
     * IMPORTANT: This should analyze the TRANSFORMED function signature, not the original!
     */
    private String generateComprehensiveFunctionCall(Function function) {
        String functionName = function.getName();
        
        logger.debug("Generating function call for: {} - calling static method on ServerlessApplication with userData", functionName);
        return "return ServerlessApplication." + functionName + "(userData);";
    }

    /**
     * Extract imports from a Java source string.
     */
    private Set<String> extractImports(String source) {
        Set<String> imports = new HashSet<>();
        for (String line : source.split("\n")) {
            line = line.trim();
            if (line.startsWith("import ") && line.endsWith(";")) {
                imports.add(line);
            }
        }
        return imports;
    }

    /**
     * Check if a package is a standard library package.
     */
    private boolean isStandardLibraryPackage(String packageName) {
        for (String stdPackage : STANDARD_LIBRARY_PACKAGES) {
            if (packageName.startsWith(stdPackage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract method parameters from Java source.
     */
    private List<String> extractMethodParameters(String source) {
        List<String> parameters = new ArrayList<>();
        int paramStart = source.indexOf("(");
        int paramEnd = source.indexOf(")", paramStart);

        if (paramStart >= 0 && paramEnd > paramStart) {
            String paramString = source.substring(paramStart + 1, paramEnd).trim();
            if (!paramString.isEmpty()) {
                String[] params = paramString.split(",");
                for (String param : params) {
                    parameters.add(param.trim());
                }
            }
        }

        return parameters;
    }

    /**
     * Generate parameters string for method signature from extracted parameters.
     */
    private String generateParametersString(List<String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        
        return String.join(", ", parameters);
    }

    /**
     * Generate parameters string from the JSON parameters stored in function's dependencySources.
     */
    private String generateParametersFromJson(Function function) {
        try {
            Map<String, String> dependencySources = function.getDependencySources();
            
            if (dependencySources == null || !dependencySources.containsKey("__ENDPOINT_PARAMETERS__")) {
                logger.warn("No parameters found for function: {}", function.getName());
                return "";
            }

            String parametersJson = dependencySources.get("__ENDPOINT_PARAMETERS__");
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode parametersNode = objectMapper.readTree(parametersJson);
            
            if (!parametersNode.isArray() || parametersNode.size() == 0) {
                logger.warn("Parameters JSON is not an array or is empty for function: {}", function.getName());
                return "";
            }

            List<String> parameters = new ArrayList<>();
            for (JsonNode paramNode : parametersNode) {
                String name = paramNode.path("name").asText();
                String type = paramNode.path("type").asText();
                String annotation = paramNode.path("annotation").asText();

                StringBuilder paramBuilder = new StringBuilder();
                if (annotation != null && !annotation.isEmpty()) {
                    paramBuilder.append("@").append(annotation);
                    if ("RequestParam".equals(annotation) && name != null && !name.isEmpty()) {
                        paramBuilder.append("(\"").append(name).append("\") ");
                    } else {
                        paramBuilder.append(" ");
                    }
                }
                paramBuilder.append(type != null ? type : "String")
                           .append(" ")
                           .append(name != null ? name : "param");

                parameters.add(paramBuilder.toString());
            }

            String result = String.join(", ", parameters);
            logger.info("Generated parameters string for function {}: {}", function.getName(), result);
            return result;

        } catch (Exception e) {
            logger.error("Error generating parameters from JSON for function {}: {}", function.getName(), e.getMessage(), e);
            List<String> parameters = extractMethodParameters(function.getSource());
            return generateParametersString(parameters);
        }
    }

    /**
     * Sanitize function name for use as Maven artifact ID.
     */
    private String sanitizeForArtifactId(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9-_]", "-");
    }

    /**
     * Create Maven wrapper files.
     */
    private void createMavenWrapper(FunctionBuildContext context) throws IOException {
        File mvnWrapperDir = new File(context.getBuildPath().toFile(), ".mvn/wrapper");
        mvnWrapperDir.mkdirs();
        String wrapperProperties = "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.6/apache-maven-3.8.6-bin.zip\n" +
                "wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar\n";

        FileUtils.writeStringToFile(new File(mvnWrapperDir, "maven-wrapper.properties"), wrapperProperties, "UTF-8");
        String mvnw = "#!/bin/sh\n" +
                "# Maven Wrapper Script\n" +
                "exec java -jar .mvn/wrapper/maven-wrapper.jar \"$@\"\n";

        String mvnwCmd = "@echo off\n" +
                "java -jar .mvn\\wrapper\\maven-wrapper.jar %*\n";

        File mvnwFile = new File(context.getBuildPath().toFile(), "mvnw");
        FileUtils.writeStringToFile(mvnwFile, mvnw, "UTF-8");
        mvnwFile.setExecutable(true);

        FileUtils.writeStringToFile(new File(context.getBuildPath().toFile(), "mvnw.cmd"), mvnwCmd, "UTF-8");
    }

    /**
     * Create build script.
     */
    private void createBuildScript(FunctionBuildContext context) throws IOException {
        String buildSh = "#!/bin/sh\n" +
                "# Build the application\n" +
                "mvn clean package -DskipTests\n";

        File buildFile = new File(context.getBuildPath().toFile(), "build.sh");
        FileUtils.writeStringToFile(buildFile, buildSh, "UTF-8");
        buildFile.setExecutable(true);
    }

    /**
     * Create run script for local testing.
     */
    private void createRunScript(FunctionBuildContext context, Function function) throws IOException {
        String runSh = "#!/bin/sh\n" +
                "# Build and run the function directly (no Spring Boot)\n" +
                "mvn clean package -DskipTests\n" +
                "java -jar target/*.jar '{\"path\":\"" + function.getPath() + "\", \"method\":\"" +
                (function.getMethods() != null && !function.getMethods().isEmpty() ? function.getMethods().get(0) : "GET") +
                "\", \"headers\":{\"Content-Type\":\"application/json\"}, \"body\":{}}'\n";

        File runFile = new File(context.getBuildPath().toFile(), "run.sh");
        FileUtils.writeStringToFile(runFile, runSh, "UTF-8");
        runFile.setExecutable(true);
        String testSh = "#!/bin/sh\n" +
                "# Test the function with a sample event\n" +
                "echo \"Building the application...\"\n" +
                "./build.sh\n\n" +
                "echo \"Testing the function with sample event...\"\n" +
                "java -jar target/*.jar '{\"path\":\"" + function.getPath() + "\", \"method\":\"" +
                (function.getMethods() != null && !function.getMethods().isEmpty() ? function.getMethods().get(0) : "GET") +
                "\", \"headers\":{\"Content-Type\":\"application/json\"}, \"body\":{}}'\n";

        File testFile = new File(context.getBuildPath().toFile(), "test.sh");
        FileUtils.writeStringToFile(testFile, testSh, "UTF-8");
        testFile.setExecutable(true);
    }
}