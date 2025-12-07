package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerStartupListener {

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted() {
        log.info("ApplicationStartedEvent received. Starting Kafka listeners...");

        kafkaListenerEndpointRegistry.getListenerContainerIds().forEach(id -> {
            log.info("Starting Kafka listener: {}", id);
            kafkaListenerEndpointRegistry.getListenerContainer(id).start();
        });

        log.info("All Kafka listeners started successfully");
    }
}
