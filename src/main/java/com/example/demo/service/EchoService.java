package com.example.demo.service;

import com.example.model.EchoRequest;
import com.example.model.EchoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EchoService {

    public final KafkaTemplate<String, EchoRequest> kafkaTemplate;

    public EchoResponse sendToKafka(EchoRequest body) {
        kafkaTemplate.send("topicEcho", body);

        EchoResponse response = new EchoResponse();
        response.setId(body.getId());
        response.setMessage(body.getMessage());

        return  response;
    }
}
