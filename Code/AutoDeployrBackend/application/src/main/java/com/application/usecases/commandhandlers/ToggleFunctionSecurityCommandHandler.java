package com.application.usecases.commandhandlers;

import com.application.exceptions.CommandException;
import com.application.usecases.commands.ToggleFunctionSecurityCommand;
import com.domain.entities.Function;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.exceptions.BusinessRuleException;
import com.domain.repositories.IFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Handler for toggling function security between public and private
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ToggleFunctionSecurityCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(ToggleFunctionSecurityCommandHandler.class);
    
    private final IFunctionRepository functionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Handle the toggle function security command
     *
     * @param command Command to handle
     * @return Updated function with new security settings
     * @throws ResourceNotFoundException If the function doesn't exist
     * @throws BusinessRuleException If the user doesn't own the function
     * @throws CommandException If the operation fails
     */
    public Function handle(ToggleFunctionSecurityCommand command) {
        command.validate();

        String functionId = command.getFunctionId();
        String userId = command.getUserId();
        boolean makePrivate = command.isMakePrivate();

        logger.info("Toggling function security: functionId={}, userId={}, makePrivate={}", 
                functionId, userId, makePrivate);

        try {
            // 1. Find the function
            Function function = functionRepository.findById(functionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Function", functionId));

            // 2. Verify the user owns the function
            if (!userId.equals(function.getUserId())) {
                throw new BusinessRuleException("User does not have permission to modify this function");
            }

            // 3. Update security settings
            if (makePrivate) {
                // Generate new API key if making private
                String apiKey = generateApiKey();
                function.setPrivate(true);
                function.setApiKey(apiKey);
                function.setApiKeyGeneratedAt(LocalDateTime.now());
                
                logger.info("Generated new API key for function: {}", functionId);
            } else {
                // Remove API key if making public
                function.setPrivate(false);
                function.setApiKey(null);
                function.setApiKeyGeneratedAt(null);
                
                logger.info("Removed API key for function: {}", functionId);
            }

            // 4. Save the updated function
            Function updatedFunction = functionRepository.save(function);
            
            logger.info("Successfully toggled function security: functionId={}, isPrivate={}", 
                    functionId, updatedFunction.isPrivate());
            
            return updatedFunction;

        } catch (ResourceNotFoundException | BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error toggling function security for functionId {}: {}", functionId, e.getMessage(), e);
            throw new CommandException("ToggleFunctionSecurity", 
                    "Failed to toggle function security: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a secure API key for function access
     *
     * @return Generated API key with "func_" prefix
     */
    private String generateApiKey() {
        byte[] keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        return "func_" + randomPart;
    }
} 