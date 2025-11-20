package com.example.demo.controller;

import com.example.api.EchoApi;
import com.example.demo.service.EchoService;
import com.example.model.EchoRequest;
import com.example.model.EchoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EchoController implements EchoApi {

    public final EchoService echoService;

    @Override
    public ResponseEntity<EchoResponse> sendString(EchoRequest body) {
        EchoResponse response = echoService.sendToKafka(body);
        return ResponseEntity.ok(response);
    }
}
