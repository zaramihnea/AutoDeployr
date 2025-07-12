package com.application.usecases.queryhandlers.function;

import com.application.exceptions.QueryException;
import com.domain.entities.Function;
import com.domain.repositories.IFunctionRepository;
import com.application.usecases.queries.function.GetDeployedFunctionsQuery;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handler for the GetDeployedFunctionsQuery
 */
@Service
@RequiredArgsConstructor
public class GetDeployedFunctionsQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetDeployedFunctionsQueryHandler.class);

    private final IFunctionRepository functionRepository;

    /**
     * Handle the get deployed functions query
     *
     * @param query Query to handle
     * @return List of deployed functions
     * @throws QueryException If retrieving functions fails
     */
    public List<Function> handle(GetDeployedFunctionsQuery query) {
        query.validate();

        logger.info("Getting all deployed functions");

        try {
            return functionRepository.findAll();
        } catch (Exception e) {
            logger.error("Error retrieving deployed functions: {}", e.getMessage(), e);
            throw new QueryException("GetDeployedFunctions",
                    "Error retrieving deployed functions: " + e.getMessage(), e);
        }
    }
}