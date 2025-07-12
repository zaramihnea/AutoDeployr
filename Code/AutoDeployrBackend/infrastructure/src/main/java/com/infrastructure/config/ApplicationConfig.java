package com.infrastructure.config;

import com.infrastructure.exceptions.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

/**
 * Application configuration
 */
@Configuration
@EnableAsync
public class ApplicationConfig {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

    @Value("${serverless.build.directory:#{systemProperties['user.dir'].concat('/build')}}")
    private String buildDirectory;

    @Value("${serverless.executor.core-pool-size:10}")
    private int corePoolSize;

    @Value("${serverless.executor.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${serverless.queue-capacity:500}")
    private int queueCapacity;

    /**
     * Validate configuration on startup
     */
    @PostConstruct
    public void validateConfig() {
        if (buildDirectory == null || buildDirectory.trim().isEmpty()) {
            throw ConfigurationException.missingConfig("serverless.build.directory");
        }

        if (corePoolSize <= 0) {
            throw ConfigurationException.invalidValue("serverless.executor.core-pool-size",
                    String.valueOf(corePoolSize), "Must be greater than 0");
        }

        if (maxPoolSize <= 0) {
            throw ConfigurationException.invalidValue("serverless.executor.max-pool-size",
                    String.valueOf(maxPoolSize), "Must be greater than 0");
        }

        if (maxPoolSize < corePoolSize) {
            throw ConfigurationException.invalidValue("serverless.executor.max-pool-size",
                    String.valueOf(maxPoolSize), "Must be greater than or equal to core pool size (" + corePoolSize + ")");
        }
        logger.info("Application configured with buildDirectory: {}", buildDirectory);
        logger.info("Thread pool configured with corePoolSize: {}, maxPoolSize: {}, queueCapacity: {}",
                corePoolSize, maxPoolSize, queueCapacity);
        try {
            Path buildPath = Paths.get(buildDirectory);
            if (!Files.exists(buildPath)) {
                Files.createDirectories(buildPath);
                logger.info("Created build directory: {}", buildPath);
            }
        } catch (Exception e) {
            throw new ConfigurationException("buildDirectory",
                    "Failed to create build directory: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the build directory path where function artifacts are stored
     *
     * @return Build directory path
     */
    @Bean
    public Path buildDirectoryPath() {
        try {
            Path path = Paths.get(buildDirectory);
            File dir = path.toFile();
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    throw new ConfigurationException("buildDirectory",
                            "Failed to create build directory: " + buildDirectory);
                }
            }

            if (!dir.isDirectory()) {
                throw new ConfigurationException("buildDirectory",
                        "Path is not a directory: " + buildDirectory);
            }

            if (!dir.canWrite()) {
                throw new ConfigurationException("buildDirectory",
                        "Build directory is not writable: " + buildDirectory);
            }

            return path;
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("buildDirectory",
                    "Error accessing build directory: " + e.getMessage(), e);
        }
    }

    /**
     * Configure async task executor for parallel function deployments
     *
     * @return Configured executor
     */
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("serverless-");
        executor.setRejectedExecutionHandler((r, e) -> {
            logger.warn("Task rejected by executor. Queue capacity reached.");
            throw new ConfigurationException("taskExecutor",
                    "Task rejected. Thread pool and queue capacity reached. " +
                            "Consider increasing serverless.executor.max-pool-size or serverless.queue-capacity");
        });
        executor.initialize();
        return executor;
    }
}