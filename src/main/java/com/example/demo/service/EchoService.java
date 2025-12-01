package com.example.demo.service;

import com.example.demo.config.KafkaTopicConfig;
import com.example.demo.entity.Message;
import com.example.demo.producer.EchoProducer;
import com.example.demo.repository.MessageRepository;
import com.example.model.EchoRequest;
import com.example.model.EchoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EchoService {

    private final EchoProducer echoProducer;
    private final MessageRepository messageRepository;

    public EchoResponse sendToKafka(EchoRequest request) {
        log.debug("Processing echo request: id={}", request.getId());

        echoProducer.publishEchoRequest(request);

        EchoResponse response = new EchoResponse();
        response.setId(request.getId());
        response.setMessage(request.getMessage());

        return response;
    }

    public void processConsumedMessage(EchoRequest request) {
        log.debug("Processing consumed message: id={}", request.getId());

        Message message = new Message();
        message.setTopic(KafkaTopicConfig.TOPIC_1);
        message.setMessage(request.getMessage());

        messageRepository.save(message);

        log.info("Saved message to database: topic={}, message={}", message.getTopic(), request.getMessage());
    }
}
