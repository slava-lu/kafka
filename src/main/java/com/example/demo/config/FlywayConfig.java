package com.example.demo.config;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("local")
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayCleanMigrationStrategy() {
        return flyway -> {
            try {
                flyway.validate();
                flyway.migrate();

                log.info("Flyway validate -> migrate succeeded.");
            } catch(FlywayException ex) {
                flyway.clean();
                flyway.migrate();

                log.error("Flyway validation failed: {}", ex.getMessage());
                log.info("Flyway clean completed.");
            }
        };
    }
}
