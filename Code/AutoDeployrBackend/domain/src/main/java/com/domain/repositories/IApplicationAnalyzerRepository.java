package com.domain.repositories;

import com.domain.entities.ApplicationAnalysisResult;
import com.domain.entities.Function;
import com.domain.entities.Route;
import com.domain.exceptions.ResourceNotFoundException;

import java.util.List;

/**
 * Repository interface for application analysis
 */
public interface IApplicationAnalyzerRepository {
    /**
     * Detect the programming language of an application
     *
     * @param appPath Path to the application directory
     * @return Detected language identifier (e.g., "python", "java")
     * @throws ResourceNotFoundException If the app path doesn't exist or is invalid
     */
    String detectLanguage(String appPath);

    /**
     * Analyze an application to extract its structure
     *
     * @param appPath Path to the application directory
     * @return Analysis result containing routes, functions, etc.
     * @throws ResourceNotFoundException If the app path doesn't exist or is invalid
     */
    ApplicationAnalysisResult analyzeApplication(String appPath);

    /**
     * Find routes in an application
     *
     * @param appPath Path to the application directory
     * @return List of routes found in the application
     * @throws ResourceNotFoundException If the app path doesn't exist or is invalid
     */
    List<Route> findRoutes(String appPath);

    /**
     * Extract functions from analysis results
     *
     * @param analysisResult Analysis result to extract functions from
     * @return List of functions extracted from the analysis result
     */
    List<Function> extractFunctions(ApplicationAnalysisResult analysisResult);
    
    /**
     * Extract functions directly for Python applications using the advanced analyzer
     * This method bypasses the ApplicationAnalysisResult and returns properly parsed Function objects
     * 
     * @param appPath Application path
     * @return List of properly analyzed Function objects
     */
    List<Function> extractPythonFunctions(String appPath);
    
    /**
     * Extract functions directly for Java applications using the advanced analyzer
     * This method bypasses the ApplicationAnalysisResult and returns properly parsed Function objects
     * 
     * @param appPath Application path
     * @return List of properly analyzed Function objects
     */
    List<Function> extractJavaFunctions(String appPath);
    
    /**
     * Extract functions directly for Laravel applications using the advanced analyzer
     * This method bypasses the ApplicationAnalysisResult and returns properly parsed Function objects
     * 
     * @param appPath Application path
     * @return List of properly analyzed Function objects
     */
    List<Function> extractLaravelFunctions(String appPath);

}