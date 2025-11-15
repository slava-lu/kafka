package com.example.demo.config;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayCleanMigrationStrategy() {
        return flyway -> {
            try {
                flyway.validate();
            } catch(FlywayException ex) {
                log.info("Flyway clean");
                flyway.clean();
                flyway.migrate();

            }
        };
    }
}
