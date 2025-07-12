package com.application.usecases.queryhandlers.user;

import com.application.dtos.response.user.UserResponse;
import com.application.exceptions.QueryException;
import com.application.usecases.queries.user.GetUserDataQuery;
import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;
import com.domain.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetUserDataQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetUserDataQueryHandler.class);

    private final IUserService userService;

    /**
     * Handle the get user data query
     *
     * @param query Query to handle
     * @return User response
     * @throws QueryException If retrieval fails
     */
    public UserResponse handle(GetUserDataQuery query) {
        try {
            logger.info("Getting user data for ID: {}", query.getUserId());

            User user = userService.getUserById(query.getUserId());

            return UserResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .roles(user.getRoles())
                    .createdAt(user.getCreatedAt())
                    .build();

        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error getting user data: {}", e.getMessage(), e);
            throw new QueryException("GetUserData", "Error retrieving user data: " + e.getMessage(), e);
        }
    }
}