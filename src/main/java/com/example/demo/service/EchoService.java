package com.example.demo.service;

import com.example.demo.config.KafkaTopicConfig;
import com.example.demo.entity.Message;
import com.example.demo.kafka.NonRetryableBusinessException;
import com.example.demo.kafka.RetryableProcessingException;
import com.example.demo.producer.EchoProducer;
import com.example.demo.repository.MessageRepository;
import com.example.demo.spec.MessageSpecs;
import com.example.model.EchoRequest;
import com.example.model.EchoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EchoService {

    private final EchoProducer echoProducer;
    private final MessageRepository messageRepository;

    public EchoResponse sendToKafka(EchoRequest request) {
        log.debug("Processing echo request: id={}", request.getId());

        echoProducer.publishEchoRequest(request);

        return new EchoResponse(request.getId())
          .message(request.getMessage());
    }

    public void processConsumedMessage(EchoRequest request) {
        log.debug("Processing consumed message: id={}, text={}", request.getId(), request.getMessage());

        String text = request.getMessage();

        // 👉 1) Simulate a NON-recoverable (business) error
        if ("BAD".equalsIgnoreCase(text)) {
            log.warn("Simulating NON-retryable error for message id={}", request.getId());
            throw new NonRetryableBusinessException("Invalid echo message content: " + text);
        }

        // 👉 2) Simulate a RECOVERABLE (transient) error
        if ("RETRY".equalsIgnoreCase(text)) {
            log.warn("Simulating RETRYABLE (transient) error for message id={}", request.getId());
            throw new RetryableProcessingException("Simulated transient failure for testing");
        }

        Message message = new Message();
        message.setTopic(KafkaTopicConfig.TOPIC_1);
        message.setMessage(request.getMessage());

        messageRepository.save(message);

        log.info("Saved message to database: topic={}, message={}", message.getTopic(), request.getMessage());
    }

    public List<EchoResponse> getMessages(String topic, String text) {
        Specification<Message> spec = Specification.where(null);

        if ( topic != null && !topic.isBlank()) {
            spec = spec.and(MessageSpecs.hasTopic(topic));
        }

        if (text != null && !text.isBlank()) {
            spec = spec.and(MessageSpecs.messageContains(text));
        }

        List<Message> response = messageRepository.findAll(spec);

        return response.stream().map(item -> new EchoResponse(item.getId())
          .message(item.getMessage())
          .topic(item.getTopic())
        ).toList();
    }
}
