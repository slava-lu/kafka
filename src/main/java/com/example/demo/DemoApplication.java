package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application entry point for the Spring Boot Kafka demo.
 *
 * Key URLs:
 * - Swagger UI: http://localhost:8080/swagger-ui/index.html
 * - Conduktor Console: http://localhost:8090
 */
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}
