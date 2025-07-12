package com.domain.repositories;

import com.domain.entities.Function;
import com.domain.exceptions.ValidationException;

/**
 * Repository interface for function transformation
 */
public interface IFunctionTransformerRepository {
    /**
     * Create a serverless function from a Function entity
     *
     * @param function Function to transform
     * @param appPath Path to the original application
     * @param buildPath Path to the build directory
     * @return Success status
     * @throws ValidationException If the function is invalid
     */
    boolean createServerlessFunction(Function function, String appPath, String buildPath);

}