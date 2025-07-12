package com.analyzer.model;

import java.util.*;

public class SpringEndpoint {
    private String name;
    private String path;
    private List<String> methods;
    private String source;
    private String className;
    private String packageName;
    private Set<String> dependencies = new HashSet<>();
    private Map<String, String> dependencySources = new HashMap<>();
    private List<ParameterInfo> parameters = new ArrayList<>();
    private String returnType;
    private Set<String> annotations = new HashSet<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public List<String> getMethods() { return methods; }
    public void setMethods(List<String> methods) { this.methods = methods; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public Set<String> getDependencies() { return dependencies; }
    public void setDependencies(Set<String> dependencies) { this.dependencies = dependencies; }

    public Map<String, String> getDependencySources() { return dependencySources; }
    public void setDependencySources(Map<String, String> dependencySources) { this.dependencySources = dependencySources; }

    public List<ParameterInfo> getParameters() { return parameters; }
    public void setParameters(List<ParameterInfo> parameters) { this.parameters = parameters; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public Set<String> getAnnotations() { return annotations; }
    public void setAnnotations(Set<String> annotations) { this.annotations = annotations; }

    public static class ParameterInfo {
        private String name;
        private String type;
        private String annotation;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getAnnotation() { return annotation; }
        public void setAnnotation(String annotation) { this.annotation = annotation; }
    }
}