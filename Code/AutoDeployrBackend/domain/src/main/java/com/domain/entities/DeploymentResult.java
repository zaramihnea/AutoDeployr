package com.domain.entities;

import com.domain.exceptions.BusinessRuleException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents the result of a deployment operation
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeploymentResult {
    private boolean success;
    private String message;
    private List<String> deployedFunctions;
    private List<String> failedFunctions;
    private Exception error;

    /**
     * Create a successful deployment result
     *
     * @param deployedFunctions List of deployed function names
     * @return Success result
     */
    public static DeploymentResult success(List<String> deployedFunctions) {
        if (deployedFunctions == null || deployedFunctions.isEmpty()) {
            throw new BusinessRuleException("deployment", "No functions were deployed");
        }

        return DeploymentResult.builder()
                .success(true)
                .message("Successfully deployed " + deployedFunctions.size() + " functions")
                .deployedFunctions(deployedFunctions)
                .build();
    }

    /**
     * Validate the deployment result
     *
     * @throws BusinessRuleException If the result is invalid
     */
    public void validate() {
        if (success && (deployedFunctions == null || deployedFunctions.isEmpty())) {
            throw new BusinessRuleException("deploymentResult",
                    "A successful deployment must have at least one deployed function");
        }

        if (!success && error == null) {
            throw new BusinessRuleException("deploymentResult",
                    "A failed deployment must have an error");
        }
    }
}