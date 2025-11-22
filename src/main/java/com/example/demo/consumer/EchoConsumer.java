package com.example.demo.consumer;

import com.example.demo.config.KafkaTopicConfig;
import com.example.demo.service.EchoService;
import com.example.model.EchoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EchoConsumer {

    private final EchoService echoService;

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_ECHO, groupId = "${spring.kafka.consumer.group-id}")
    public void consumeEchoRequest(EchoRequest request) {
        log.info("Received EchoRequest: id={}, message='{}'", request.getId(), request.getMessage());
        echoService.processConsumedMessage(request);
    }
}
