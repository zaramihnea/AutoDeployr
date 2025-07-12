package com.infrastructure.security;

import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;
import com.domain.repositories.IUserRepository;
import com.domain.services.IAuthenticationService;
import com.domain.services.IPasswordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements IAuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private final IUserRepository userRepository;
    private final IPasswordService passwordService;

    @Override
    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessRuleException("Invalid username or password"));

        if (!passwordService.matches(password, user.getPassword())) {
            throw new BusinessRuleException("Invalid username or password");
        }

        if (!user.isActive()) {
            throw new BusinessRuleException("User account is disabled");
        }

        logger.info("User authenticated successfully: {}", username);
        return user;
    }
}