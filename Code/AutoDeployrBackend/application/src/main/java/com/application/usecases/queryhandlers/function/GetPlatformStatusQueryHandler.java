package com.application.usecases.queryhandlers.function;

import com.application.dtos.response.PlatformStatusResponse;
import com.application.exceptions.QueryException;
import com.domain.entities.Function;
import com.domain.repositories.IFunctionRepository;
import com.application.usecases.queries.function.GetPlatformStatusQuery;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler for the GetPlatformStatusQuery
 */
@Service
@RequiredArgsConstructor
public class GetPlatformStatusQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetPlatformStatusQueryHandler.class);

    private final IFunctionRepository functionRepository;

    /**
     * Handle the get platform status query
     *
     * @param query Query to handle
     * @return Platform status response
     * @throws QueryException If retrieving status fails
     */
    public PlatformStatusResponse handle(GetPlatformStatusQuery query) {
        query.validate();

        logger.info("Getting platform status");

        try {
            List<Function> functions;
            try {
                functions = functionRepository.findAll();
            } catch (Exception e) {
                throw new QueryException("GetFunctions",
                        "Error retrieving functions: " + e.getMessage(), e);
            }

            // Extract function names
            List<String> functionNames = functions.stream()
                    .map(Function::getName)
                    .collect(Collectors.toList());

            // Get build directory
            String buildDirectory = System.getProperty("user.dir") + File.separator + "build";

            // Check if build directory exists
            File buildDir = new File(buildDirectory);
            if (!buildDir.exists()) {
                logger.warn("Build directory does not exist: {}", buildDirectory);
            }

            // Create response
            return PlatformStatusResponse.builder()
                    .activeFunctions(functionNames)
                    .buildDirectory(buildDirectory)
                    .systemStatus("healthy")
                    .build();

        } catch (QueryException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error retrieving platform status: {}", e.getMessage(), e);
            throw new QueryException("GetPlatformStatus",
                    "Unexpected error retrieving platform status: " + e.getMessage(), e);
        }
    }
}