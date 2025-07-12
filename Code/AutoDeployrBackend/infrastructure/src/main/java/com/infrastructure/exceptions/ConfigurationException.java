package com.infrastructure.exceptions;

/**
 * Exception for errors related to configuration loading or validation
 */
public class ConfigurationException extends InfrastructureException {
    public ConfigurationException(String message) {
        super(message, "CONFIGURATION_ERROR");
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, "CONFIGURATION_ERROR", cause);
    }

    public ConfigurationException(String configName, String message) {
        super("Configuration error for '" + configName + "': " + message, "CONFIGURATION_ERROR");
    }

    public ConfigurationException(String configName, String message, Throwable cause) {
        super("Configuration error for '" + configName + "': " + message, "CONFIGURATION_ERROR", cause);
    }

    /**
     * Create a missing configuration exception
     *
     * @param configName Name of the missing configuration property
     * @return ConfigurationException with appropriate message
     */
    public static ConfigurationException missingConfig(String configName) {
        return new ConfigurationException(configName, "Required configuration property is missing");
    }

    /**
     * Create an invalid configuration value exception
     *
     * @param configName Name of the configuration property
     * @param value Current value
     * @param expectedFormat Expected format or constraints
     * @return ConfigurationException with appropriate message
     */
    public static ConfigurationException invalidValue(String configName, String value, String expectedFormat) {
        return new ConfigurationException(configName,
                "Invalid value: '" + value + "', expected: " + expectedFormat);
    }
}