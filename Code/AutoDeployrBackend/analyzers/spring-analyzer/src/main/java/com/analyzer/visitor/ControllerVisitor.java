package com.analyzer.visitor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.analyzer.model.SpringEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ControllerVisitor extends VoidVisitorAdapter<Void> {
    private final List<SpringEndpoint> endpoints = new ArrayList<>();
    private final Map<String, String> autowiredServices = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(ControllerVisitor.class);

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        boolean isController = false;
        String baseRequestMapping = "";
        for (AnnotationExpr annotation : n.getAnnotations()) {
            String annotationName = annotation.getNameAsString();
            if (annotationName.equals("RestController") || annotationName.equals("Controller")) {
                isController = true;
            }
            if (annotationName.equals("RequestMapping")) {
                baseRequestMapping = extractMappingPath(annotation);
            }
        }

        if (isController) {
            String packageName = n.findCompilationUnit()
                    .map(CompilationUnit::getPackageDeclaration)
                    .flatMap(pd -> pd)
                    .map(pd -> pd.getNameAsString())
                    .orElse("");
            collectAutowiredServices(n);
            String finalBaseRequestMapping = baseRequestMapping;
            n.getMethods().forEach(method -> {
                processControllerMethod(method, n.getNameAsString(), packageName, finalBaseRequestMapping);
            });
        }

        super.visit(n, arg);
    }

    private void collectAutowiredServices(ClassOrInterfaceDeclaration classDecl) {
        classDecl.getFields().forEach(field -> {
            boolean isAutowired = field.getAnnotations().stream()
                    .anyMatch(annotation -> 
                        annotation.getNameAsString().equals("Autowired") ||
                        annotation.getNameAsString().equals("Inject"));
            
            if (isAutowired) {
                String fieldName = field.getVariables().get(0).getNameAsString();
                String fieldType = field.getVariables().get(0).getType().asString();
                autowiredServices.put(fieldName, fieldType);
                System.err.println("Found @Autowired service: " + fieldName + " of type " + fieldType);
            }
        });
    }

    private void processControllerMethod(MethodDeclaration method, String className, String packageName, String baseRequestMapping) {
        Optional<String> mapping = findRequestMapping(method);
        if (!mapping.isPresent()) {
            return;
        }

        SpringEndpoint endpoint = new SpringEndpoint();
        endpoint.setName(method.getNameAsString());
        endpoint.setClassName(className);
        endpoint.setPackageName(packageName);
        endpoint.setPath(baseRequestMapping + mapping.get());
        endpoint.setMethods(extractHttpMethods(method));
        endpoint.setSource(method.toString());
        String returnType = extractReturnType(method);
        endpoint.setReturnType(returnType);
        Map<String, String> endpointServices = new HashMap<>(autowiredServices);
        endpoint.getDependencySources().putAll(endpointServices);
        method.getParameters().forEach(param -> {
            SpringEndpoint.ParameterInfo paramInfo = new SpringEndpoint.ParameterInfo();
            paramInfo.setName(param.getNameAsString());
            paramInfo.setType(param.getType().asString());
            param.getAnnotations().forEach(anno -> {
                String annoName = anno.getNameAsString();
                if (annoName.equals("RequestParam") || annoName.equals("PathVariable") ||
                        annoName.equals("RequestBody") || annoName.equals("RequestHeader")) {
                    paramInfo.setAnnotation(annoName);
                }
            });

            endpoint.getParameters().add(paramInfo);
        });
        method.getAnnotations().forEach(anno -> {
            endpoint.getAnnotations().add(anno.getNameAsString());
        });

        endpoints.add(endpoint);
    }

    private Optional<String> findRequestMapping(MethodDeclaration method) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String name = annotation.getNameAsString();

            if (name.equals("RequestMapping") || name.equals("GetMapping") ||
                    name.equals("PostMapping") || name.equals("PutMapping") ||
                    name.equals("DeleteMapping") || name.equals("PatchMapping")) {
                return Optional.of(extractMappingPath(annotation));
            }
        }
        return Optional.empty();
    }

    private String extractMappingPath(AnnotationExpr annotation) {
        AtomicReference<String> path = new AtomicReference<>("/");
        if (annotation.isNormalAnnotationExpr()) {
            annotation.asNormalAnnotationExpr().getPairs().forEach(pair -> {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path")) {
                    path.set(pair.getValue().toString().replaceAll("\"", ""));
                }
            });
        } else if (annotation.isSingleMemberAnnotationExpr()) {
            path.set(annotation.asSingleMemberAnnotationExpr().getMemberValue().toString().replaceAll("\"", ""));
        }

        return path.get();
    }

    private List<String> extractHttpMethods(MethodDeclaration method) {
        List<String> methods = new ArrayList<>();

        for (AnnotationExpr annotation : method.getAnnotations()) {
            String name = annotation.getNameAsString();

            switch (name) {
                case "GetMapping":
                    methods.add("GET");
                    break;
                case "PostMapping":
                    methods.add("POST");
                    break;
                case "PutMapping":
                    methods.add("PUT");
                    break;
                case "DeleteMapping":
                    methods.add("DELETE");
                    break;
                case "PatchMapping":
                    methods.add("PATCH");
                    break;
                case "RequestMapping":
                    methods.addAll(extractMethodsFromRequestMapping(annotation));
                    break;
            }
        }
        if (methods.isEmpty()) {
            methods.add("GET");
        }

        return methods;
    }

    private List<String> extractMethodsFromRequestMapping(AnnotationExpr annotation) {
        List<String> methods = new ArrayList<>();

        if (annotation.isNormalAnnotationExpr()) {
            annotation.asNormalAnnotationExpr().getPairs().forEach(pair -> {
                if (pair.getNameAsString().equals("method") || pair.getNameAsString().equals("methods")) {
                    String methodsStr = pair.getValue().toString();
                    if (methodsStr.contains("GET")) methods.add("GET");
                    if (methodsStr.contains("POST")) methods.add("POST");
                    if (methodsStr.contains("PUT")) methods.add("PUT");
                    if (methodsStr.contains("DELETE")) methods.add("DELETE");
                    if (methodsStr.contains("PATCH")) methods.add("PATCH");
                }
            });
        }
        if (methods.isEmpty()) {
            methods.add("GET");
        }

        return methods;
    }

    /**
     * Extract return type from method with improved handling for complex types
     */
    private String extractReturnType(MethodDeclaration method) {
        try {
            String returnTypeStr = method.getType().asString();
            if (returnTypeStr == null || returnTypeStr.trim().isEmpty()) {
                logger.warn("Empty return type found for method: {}, defaulting to Object", method.getNameAsString());
                return "Object";
            }
            returnTypeStr = returnTypeStr.trim();
            if (returnTypeStr.startsWith("ResponseEntity")) {
                if (returnTypeStr.contains("Map") && returnTypeStr.contains("String") && returnTypeStr.contains("Object")) {
                    return "ResponseEntity<Map<String,Object>>";
                } else if (returnTypeStr.contains("<") && returnTypeStr.contains(">")) {
                    return returnTypeStr;
                } else {
                    return "ResponseEntity<Object>";
                }
            }
            if (returnTypeStr.contains("<") && returnTypeStr.contains(">")) {
                return returnTypeStr;
            }
            if (returnTypeStr.equals("String") || returnTypeStr.equals("void") || 
                returnTypeStr.equals("boolean") || returnTypeStr.equals("int") ||
                returnTypeStr.equals("long") || returnTypeStr.equals("double")) {
                return returnTypeStr;
            }
            return returnTypeStr;
            
        } catch (Exception e) {
            logger.error("Error extracting return type for method {}: {}", method.getNameAsString(), e.getMessage());
            return "Object";
        }
    }

    public List<SpringEndpoint> getEndpoints() {
        return endpoints;
    }

    public Map<String, String> getAutowiredServices() {
        return autowiredServices;
    }
}