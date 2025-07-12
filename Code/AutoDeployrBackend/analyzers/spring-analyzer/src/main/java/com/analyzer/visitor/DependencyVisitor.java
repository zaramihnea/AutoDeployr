package com.analyzer.visitor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.analyzer.model.SpringEndpoint;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class DependencyVisitor {
    private final List<File> javaFiles;
    private final JavaParser javaParser;
    private final Map<String, String> methodSources = new HashMap<>();
    private final Map<String, String> classSources = new HashMap<>(); // className -> full class source
    private final Map<String, String> classPackages = new HashMap<>(); // className -> package name

    public DependencyVisitor(List<File> javaFiles, JavaParser javaParser) {
        this.javaFiles = javaFiles;
        this.javaParser = javaParser;
        preloadSources();
    }

    private void preloadSources() {
        for (File file : javaFiles) {
            try {
                String fileContent = Files.readString(file.toPath());
                ParseResult<CompilationUnit> parseResult = javaParser.parse(file);
                if (parseResult.isSuccessful()) {
                    CompilationUnit cu = parseResult.getResult().get();

                    // package name
                    String packageName = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString())
                            .orElse("");

                    // Find all classes and their methods
                    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                        String className = classDecl.getNameAsString();
                        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
                        
                        // Store class information
                        classSources.put(className, classDecl.toString());
                        classSources.put(fullClassName, classDecl.toString());
                        classPackages.put(className, packageName);
                        classPackages.put(fullClassName, packageName);

                        System.err.println("Loaded class: " + fullClassName);

                        // Store individual methods
                        classDecl.getMethods().forEach(method -> {
                            String methodName = method.getNameAsString();
                            String fullMethodName = fullClassName + "." + methodName;
                            methodSources.put(fullMethodName, method.toString());
                        });
                    });
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + file + ": " + e.getMessage());
            }
        }
    }

    public Set<String> findDependencies(SpringEndpoint endpoint) {
        Set<String> dependencies = new HashSet<>();
        Set<String> processedMethods = new HashSet<>();

        try {
            Map<String, String> autowiredServices = endpoint.getDependencySources();
            System.err.println("Autowired services for " + endpoint.getName() + ": " + autowiredServices);
            ParseResult<MethodDeclaration> parseResult = javaParser.parseMethodDeclaration(endpoint.getSource());
            if (parseResult.isSuccessful()) {
                MethodDeclaration method = parseResult.getResult().get();
                method.findAll(MethodCallExpr.class).forEach(methodCall -> {
                    String resolvedDependency = resolveMethodCallToService(methodCall, autowiredServices, endpoint.getPackageName());
                    if (resolvedDependency != null && !resolvedDependency.isEmpty()) {
                        dependencies.add(resolvedDependency);
                        System.err.println("Found dependency: " + resolvedDependency);
                        if (!processedMethods.contains(resolvedDependency)) {
                            processedMethods.add(resolvedDependency);
                            dependencies.addAll(findTransitiveDependencies(resolvedDependency, processedMethods));
                        }
                    }
                });
                for (Map.Entry<String, String> service : autowiredServices.entrySet()) {
                    String serviceType = service.getValue();
                    String serviceClassDependency = "class:" + serviceType;
                    dependencies.add(serviceClassDependency);
                    System.err.println("Including complete service class: " + serviceType);
                }
                addModelDependencies(endpoint, dependencies);
            }
        } catch (Exception e) {
            System.err.println("Error finding dependencies for endpoint " + endpoint.getName() + ": " + e.getMessage());
        }

        return dependencies;
    }

    private void addModelDependencies(SpringEndpoint endpoint, Set<String> dependencies) {
        endpoint.getParameters().forEach(param -> {
            String paramType = param.getType();
            if (isCustomType(paramType)) {
                String modelDependency = "model:" + paramType;
                dependencies.add(modelDependency);
                System.err.println("Including model class from parameter: " + paramType);
            }
        });

        String returnType = endpoint.getReturnType();
        if (returnType != null && returnType.contains("<")) {
            String genericPart = returnType.substring(returnType.indexOf('<') + 1, returnType.lastIndexOf('>'));
            String[] genericTypes = genericPart.split(",");
            for (String type : genericTypes) {
                String cleanType = type.trim().replaceAll("<.*>", "");
                if (isCustomType(cleanType)) {
                    String modelDependency = "model:" + cleanType;
                    dependencies.add(modelDependency);
                    System.err.println("Including model class from return type: " + cleanType);
                }
            }
        }
    }

    private boolean isCustomType(String type) {
        return !type.equals("String") && !type.equals("Integer") && !type.equals("Long") && 
               !type.equals("Boolean") && !type.equals("Double") && !type.equals("Float") &&
               !type.startsWith("java.") && !type.startsWith("javax.") && 
               !type.equals("Object") && !type.equals("Map") && !type.equals("List") &&
               !type.equals("Set") && !type.equals("ResponseEntity") && 
               !Character.isLowerCase(type.charAt(0));
    }

    private String resolveMethodCallToService(MethodCallExpr methodCall, Map<String, String> autowiredServices, String packageName) {
        if (methodCall.getScope().isPresent()) {
            String scope = methodCall.getScope().get().toString();
            if (autowiredServices.containsKey(scope)) {
                String serviceType = autowiredServices.get(scope);
                String methodName = methodCall.getNameAsString();
                String fullMethodName = serviceType + "." + methodName;
                if (methodSources.containsKey(fullMethodName)) {
                    return fullMethodName;
                }
                for (String className : classSources.keySet()) {
                    if (className.endsWith("." + serviceType) || className.equals(serviceType)) {
                        String candidateMethod = className + "." + methodName;
                        if (methodSources.containsKey(candidateMethod)) {
                            return candidateMethod;
                        }
                    }
                }
            }
        }
        
        return null;
    }

    private Set<String> findTransitiveDependencies(String methodName, Set<String> processedMethods) {
        Set<String> dependencies = new HashSet<>();
        
        if (processedMethods.size() > 50) {
            return dependencies;
        }

        String methodSource = methodSources.get(methodName);
        if (methodSource == null) {
            return dependencies;
        }

        try {
            ParseResult<MethodDeclaration> parseResult = javaParser.parseMethodDeclaration(methodSource);
            if (parseResult.isSuccessful()) {
                MethodDeclaration method = parseResult.getResult().get();
                method.findAll(MethodCallExpr.class).forEach(methodCall -> {
                    try {
                        String calledMethod = resolveBasicMethodCall(methodCall, methodName);
                        if (calledMethod != null && !calledMethod.isEmpty() &&
                                methodSources.containsKey(calledMethod) &&
                                !processedMethods.contains(calledMethod)) {
                            
                            dependencies.add(calledMethod);
                            processedMethods.add(calledMethod);
                            dependencies.addAll(findTransitiveDependencies(calledMethod, processedMethods));
                        }
                    } catch (Exception e) {
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error finding transitive dependencies for method " + methodName + ": " + e.getMessage());
        }

        return dependencies;
    }

    private String resolveBasicMethodCall(MethodCallExpr methodCall, String currentMethodName) {
        String[] parts = currentMethodName.split("\\.");
        if (parts.length < 3) return null;
        
        String packageName = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 2));
        String className = parts[parts.length - 2];

        String scope = "";
        if (methodCall.getScope().isPresent()) {
            scope = methodCall.getScope().get().toString();
        }
        if (scope.isEmpty() || scope.equals("this")) {
            return packageName + "." + className + "." + methodCall.getNameAsString();
        }

        return null;
    }

    public String getSourceForMethod(String methodName) {
        if (methodName.startsWith("class:")) {
            String className = methodName.substring(6);
            return classSources.get(className);
        } else if (methodName.startsWith("model:")) {
            String modelName = methodName.substring(6);
            return classSources.get(modelName);
        } else {
            return methodSources.get(methodName);
        }
    }
}