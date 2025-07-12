package com.webapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application class for the Serverless Platform Web API
 */
@SpringBootApplication
@ComponentScan(basePackages = {
		"com.webapi",
		"com.application",
		"com.domain",
		"com.infrastructure"
})
@EntityScan(basePackages = {
		"com.infrastructure.persistence.entity"
})
@EnableJpaRepositories(basePackages = {
		"com.infrastructure.persistence.repository"
})
@EnableJpaAuditing
public class WebapiApplication {

	private static final Logger logger = LoggerFactory.getLogger(WebapiApplication.class);

	/**
	 * Application entry point
	 *
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		logger.info("Starting Serverless Platform Web API...");
		SpringApplication.run(WebapiApplication.class, args);
		logger.info("Serverless Platform Web API started successfully");
	}
}