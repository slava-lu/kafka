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

    public static final String TOPIC_1 = "topic-1";
    public static final String TOPIC_2 = "topic-2";

    @Bean
    public NewTopic topic1() {
        return TopicBuilder.name(TOPIC_1)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic topic2() {
        return TopicBuilder.name(TOPIC_2)
          .partitions(2)
          .replicas(1)
          .build();
    }
}
