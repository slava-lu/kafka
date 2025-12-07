package com.example.demo.controller;

import com.example.api.EchoApi;
import com.example.demo.service.EchoService;
import com.example.model.EchoRequest;
import com.example.model.EchoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EchoController implements EchoApi {

    public final EchoService echoService;

    @Override
    public ResponseEntity<List<EchoResponse>> getMessages(String topic, String message) {
        List<EchoResponse> response = echoService.getMessages(topic, message);
        return ResponseEntity.ok(response);

    }

    @Override
    public ResponseEntity<EchoResponse> sendString(EchoRequest body) {
        EchoResponse response = echoService.sendToKafka(body);
        return ResponseEntity.ok(response);
    }

}
