package com.analyzer.model;

import java.util.*;

public class AnalysisResult {
    private String language;
    private String framework;
    private List<SpringEndpoint> endpoints = new ArrayList<>();
    private Map<String, String> serviceSources = new HashMap<>();
    private Set<String> environmentVariables = new HashSet<>();

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public List<SpringEndpoint> getEndpoints() { return endpoints; }
    public void setEndpoints(List<SpringEndpoint> endpoints) { this.endpoints = endpoints; }

    public Map<String, String> getServiceSources() { return serviceSources; }
    public void setServiceSources(Map<String, String> serviceSources) { this.serviceSources = serviceSources; }

    public Set<String> getEnvironmentVariables() { return environmentVariables; }
    public void setEnvironmentVariables(Set<String> environmentVariables) { this.environmentVariables = environmentVariables; }
}