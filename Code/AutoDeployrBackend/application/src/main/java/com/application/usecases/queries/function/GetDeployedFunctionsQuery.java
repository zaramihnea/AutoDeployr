package com.application.usecases.queries.function;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Query to get all deployed functions
 */
@Data
@Builder
@AllArgsConstructor
public class GetDeployedFunctionsQuery {

    /**
     * No-op validation as this query doesn't have parameters
     */
    public void validate() {
    }
}