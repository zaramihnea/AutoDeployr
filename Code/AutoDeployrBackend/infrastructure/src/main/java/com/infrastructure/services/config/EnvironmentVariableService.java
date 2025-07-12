package com.infrastructure.services.config;

import com.infrastructure.persistence.entity.EnvironmentVariableEntity;
import com.infrastructure.persistence.repository.SpringEnvironmentVariableRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for securely managing environment variables using PBKDF2 with AES encryption
 */
@Service
@RequiredArgsConstructor
public class EnvironmentVariableService {
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentVariableService.class);

    private final SpringEnvironmentVariableRepository environmentVariableRepository;

    @Value("${app.env.encryption.password:${ENCRYPTION_PASSWORD:defaultPasswordForDevelopment}}")
    private String encryptionPassword;

    @Value("${app.env.encryption.salt:${ENCRYPTION_SALT:defaultSaltForDevelopment}}")
    private String encryptionSalt;

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;

    /**
     * Store environment variables securely for an application
     *
     * @param appName Application name
     * @param userId User ID
     * @param variables Map of environment variables
     * @return Number of variables stored
     */
    @Transactional
    public int storeEnvironmentVariables(String appName, String userId, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            logger.info("No environment variables to store for app: {}", appName);
            return 0;
        }
        appName = appName.replace("-", "_");

        logger.info("Storing {} environment variables for app: {}", variables.size(), appName);

        environmentVariableRepository.deleteByAppNameAndUserId(appName, userId);
        int count = 0;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            try {
                String key = entry.getKey();
                String value = entry.getValue();

                if (key == null || key.trim().isEmpty()) {
                    continue;
                }
                if (value == null) {
                    value = "";
                }
                byte[] iv = generateIv();
                String ivBase64 = Base64.getEncoder().encodeToString(iv);
                String encryptedValue = encrypt(value, iv);
                String storedValue = ivBase64 + ":" + encryptedValue;

                EnvironmentVariableEntity entity = EnvironmentVariableEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .appName(appName)
                        .userId(userId)
                        .name(key)
                        .value(storedValue)
                        .build();

                environmentVariableRepository.save(entity);
                count++;
                logger.debug("Stored encrypted environment variable: {}", key);
            } catch (Exception e) {
                logger.error("Error storing environment variable: {}", e.getMessage(), e);
            }
        }

        logger.info("Successfully stored {} environment variables for app: {}", count, appName);
        return count;
    }

    /**
     * Retrieve environment variables for an application
     *
     * @param appName Application name
     * @param userId User ID
     * @return Map of environment variables
     */
    @Transactional(readOnly = true)
    public Map<String, String> getEnvironmentVariables(String appName, String userId) {
        Map<String, String> variables = new HashMap<>();

        try {
            String dbAppName = appName.replace("-", "_");
            if (userId == null || userId.trim().isEmpty()) {
                userId = "unknown";
                logger.warn("Empty user ID provided when retrieving environment variables, using 'unknown'");
            }
            
            logger.info("Retrieving environment variables for app: '{}' (DB format: '{}') and user: '{}'", 
                appName, dbAppName, userId);
            List<EnvironmentVariableEntity> entities = environmentVariableRepository.findByAppNameAndUserId(dbAppName, userId);
            
            if (entities.isEmpty() && !dbAppName.equals(appName)) {
                logger.debug("No variables found with DB format, trying original format: '{}'", appName);
                entities = environmentVariableRepository.findByAppNameAndUserId(appName, userId);
            }

            logger.info("Retrieved {} encrypted environment variables for app: {}", entities.size(), appName);

            for (EnvironmentVariableEntity entity : entities) {
                try {
                    String storedValue = entity.getValue();
                    String[] parts = storedValue.split(":", 2);

                    if (parts.length != 2) {
                        logger.warn("Invalid encrypted value format for variable: {}", entity.getName());
                        continue;
                    }
                    byte[] iv = Base64.getDecoder().decode(parts[0]);
                    String encryptedValue = parts[1];
                    String decryptedValue = decrypt(encryptedValue, iv);
                    variables.put(entity.getName(), decryptedValue);
                    logger.debug("Decrypted environment variable: {}", entity.getName());
                } catch (Exception e) {
                    logger.error("Error decrypting environment variable {}: {}", entity.getName(), e.getMessage());
                }
            }

            logger.info("Successfully decrypted {} environment variables for app: {}", variables.size(), appName);
            return variables;
        } catch (Exception e) {
            logger.error("Error retrieving environment variables: {}", e.getMessage(), e);
            return variables;
        }
    }

    /**
     * Generate a random initialization vector
     */
    private byte[] generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Get secret key based on password and salt
     */
    private SecretKey getSecretKey() throws Exception {
        byte[] saltBytes = encryptionSalt.getBytes(StandardCharsets.UTF_8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM);
        KeySpec spec = new PBEKeySpec(encryptionPassword.toCharArray(), saltBytes, ITERATION_COUNT, KEY_LENGTH);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    /**
     * Encrypt a string value with a specific IV
     */
    private String encrypt(String value, byte[] iv) throws Exception {
        SecretKey secretKey = getSecretKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));

        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypt a string value with a specific IV
     */
    private String decrypt(String encryptedValue, byte[] iv) throws Exception {
        SecretKey secretKey = getSecretKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

        byte[] decoded = Base64.getDecoder().decode(encryptedValue);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}