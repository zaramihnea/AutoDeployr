package com.analyzer.visitor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visitor that identifies Spring environment variables from @Value annotations.
 */
public class EndpointVisitor extends VoidVisitorAdapter<Void> {
    private static final Logger logger = LoggerFactory.getLogger(EndpointVisitor.class);

    private final Set<String> environmentVariables = new HashSet<>();
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::[^}]*)?\\}");

    @Override
    public void visit(CompilationUnit n, Void arg) {
        List<AnnotationExpr> annotations = n.findAll(AnnotationExpr.class);
        System.err.println("Found " + annotations.size() + " annotations in compilation unit");
        
        for (AnnotationExpr annotation : annotations) {
            System.err.println("Processing annotation: " + annotation.getNameAsString());
            processAnnotation(annotation);
        }
        
        super.visit(n, arg);
    }

    private void processAnnotation(AnnotationExpr annotation) {
        String annotationName = annotation.getNameAsString();
        System.err.println("Checking annotation: " + annotationName);
        
        if (annotationName.equals("Value") || annotationName.contains("Value")) {
            System.err.println("Found @Value annotation: " + annotation);
            try {
                String value = null;

                if (annotation.isSingleMemberAnnotationExpr()) {
                    System.err.println("Processing SingleMemberAnnotationExpr");
                    if (annotation.asSingleMemberAnnotationExpr().getMemberValue().isStringLiteralExpr()) {
                        value = annotation.asSingleMemberAnnotationExpr().getMemberValue().asStringLiteralExpr().getValue();
                        System.err.println("Extracted value: " + value);
                    }
                } else if (annotation.isNormalAnnotationExpr()) {
                    System.err.println("Processing NormalAnnotationExpr");
                    annotation.asNormalAnnotationExpr().getPairs().forEach(pair -> {
                        System.err.println("Processing pair: " + pair.getNameAsString() + " = " + pair.getValue());
                        if (pair.getValue().isStringLiteralExpr()) {
                            String pairValue = pair.getValue().asStringLiteralExpr().getValue();
                            System.err.println("Extracting from pair value: " + pairValue);
                            extractEnvironmentVariables(pairValue);
                        }
                    });
                }

                if (value != null) {
                    System.err.println("Extracting environment variables from: " + value);
                    extractEnvironmentVariables(value);
                }
            } catch (Exception e) {
                System.err.println("Error extracting environment variable from @Value: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Extract environment variables from a string value.
     * Looks for patterns like ${ENV_VAR} or ${ENV_VAR:default}.
     */
    private void extractEnvironmentVariables(String value) {
        System.err.println("Extracting env vars from: " + value);
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        while (matcher.find()) {
            String envVar = matcher.group(1);
            environmentVariables.add(envVar);
            System.err.println("Found environment variable: " + envVar);
        }
    }

    public Set<String> getEnvironmentVariables() {
        return environmentVariables;
    }
}