package com.infrastructure.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.infrastructure.exceptions.ConfigurationException;
import com.infrastructure.exceptions.DockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * Configuration for Docker client
 */
@Configuration
public class DockerConfig {
    private static final Logger logger = LoggerFactory.getLogger(DockerConfig.class);

    @Value("${docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    @Value("${docker.connection.timeout:30}")
    private int connectionTimeout;

    @Value("${docker.response.timeout:45}")
    private int responseTimeout;

    @Value("${docker.max.connections:100}")
    private int maxConnections;

    /**
     * Validate Docker configuration on startup
     */
    @PostConstruct
    public void validateConfig() {
        if (dockerHost == null || dockerHost.trim().isEmpty()) {
            throw ConfigurationException.missingConfig("docker.host");
        }
        if (connectionTimeout <= 0) {
            throw ConfigurationException.invalidValue("docker.connection.timeout",
                    String.valueOf(connectionTimeout), "Must be greater than 0");
        }

        if (responseTimeout <= 0) {
            throw ConfigurationException.invalidValue("docker.response.timeout",
                    String.valueOf(responseTimeout), "Must be greater than 0");
        }

        if (maxConnections <= 0) {
            throw ConfigurationException.invalidValue("docker.max.connections",
                    String.valueOf(maxConnections), "Must be greater than 0");
        }

        logger.info("Docker configuration: host={}, connectionTimeout={}, responseTimeout={}, maxConnections={}",
                dockerHost, connectionTimeout, responseTimeout, maxConnections);
    }

    /**
     * Create Docker client
     *
     * @return Configured Docker client
     * @throws ConfigurationException If client configuration fails
     */
    @Bean
    public DockerClient dockerClient() {
        try {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(maxConnections)
                    .connectionTimeout(Duration.ofSeconds(connectionTimeout))
                    .responseTimeout(Duration.ofSeconds(responseTimeout))
                    .build();

            DockerClient client = DockerClientImpl.getInstance(config, httpClient);
            try {
                client.pingCmd().exec();
                logger.info("Successfully connected to Docker daemon at {}", dockerHost);
            } catch (Exception e) {
                throw new DockerException("ping",
                        "Failed to connect to Docker daemon at " + dockerHost + ": " + e.getMessage(), e);
            }

            return client;
        } catch (DockerException e) {
            logger.error("Docker connection error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Error creating Docker client: {}", e.getMessage(), e);
            throw new ConfigurationException("dockerClient",
                    "Failed to create Docker client: " + e.getMessage(), e);
        }
    }
}