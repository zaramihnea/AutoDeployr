package com.application.usecases.queryhandlers.function;

import com.application.dtos.response.FunctionSummaryResponse;
import com.application.exceptions.QueryException;
import com.application.usecases.queries.function.GetUserFunctionsQuery;
import com.domain.entities.Function;
import com.domain.repositories.IFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetUserFunctionsQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetUserFunctionsQueryHandler.class);

    private final IFunctionRepository functionRepository;

    /**
     * Handle the get user functions query
     *
     * @param query Query to handle
     * @return List of function summaries
     * @throws QueryException If retrieval fails
     */
    public List<FunctionSummaryResponse> handle(GetUserFunctionsQuery query) {
        try {
            logger.info("Getting functions for user ID: {}", query.getUserId());

            List<Function> functions = functionRepository.findByUserId(query.getUserId());

            return functions.stream()
                    .map(this::mapToSummary)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error getting user functions: {}", e.getMessage(), e);
            throw new QueryException("GetUserFunctions", "Error retrieving user functions: " + e.getMessage(), e);
        }
    }

    private FunctionSummaryResponse mapToSummary(Function function) {
        return FunctionSummaryResponse.builder()
                .id(function.getId())
                .name(function.getName())
                .path(function.getPath())
                .methods(function.getMethods())
                .appName(function.getAppName())
                .invocationCount(function.getInvocationCount())
                .averageExecutionTimeMs(function.getAverageExecutionTimeMs())
                .lastInvoked(function.getLastInvoked())
                .language(function.getLanguage())
                .framework(function.getFramework())
                .isPrivate(function.isPrivate())
                .apiKey(function.getApiKey())
                .apiKeyGeneratedAt(function.getApiKeyGeneratedAt())
                .build();
    }
}