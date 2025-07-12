package com.application.usecases.queries.function;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class GetPlatformStatusQuery {

    /**
     * No-op validation as this query doesn't have parameters
     */
    public void validate() {
    }
}