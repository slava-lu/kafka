package com.example.demo.producer;

import com.example.demo.config.KafkaTopicConfig;
import com.example.model.EchoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer responsible for publishing echo messages.
 * Encapsulates all Kafka publishing logic for the echo domain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EchoProducer {

    private final KafkaTemplate<String, EchoRequest> kafkaTemplate;

    public void publishEchoRequest(EchoRequest request) {
        log.debug("Publishing EchoRequest to topic {}: id={}, message={}",
                KafkaTopicConfig.TOPIC_ECHO, request.getId(), request.getMessage());

        kafkaTemplate.send(KafkaTopicConfig.TOPIC_ECHO, request.getId(), request)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish EchoRequest: id={}", request.getId(), ex);
                    } else {
                        log.debug("Successfully published EchoRequest: id={}, partition={}, offset={}",
                                request.getId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
