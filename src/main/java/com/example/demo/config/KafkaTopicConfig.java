package com.example.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Centralized configuration for all Kafka topics.
 * Define topic names as constants and topic beans for auto-creation.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_ECHO = "topic-echo";

    @Bean
    public NewTopic echoTopic() {
        return TopicBuilder.name(TOPIC_ECHO)
                .partitions(10)
                .replicas(1)
                .build();
    }
}
