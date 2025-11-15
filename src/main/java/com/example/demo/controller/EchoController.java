package com.example.demo.controller;

import com.example.api.EchoApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EchoController implements EchoApi {

    @Override
    public ResponseEntity<String> sendString(String body) {
        return ResponseEntity.ok(body);
    }
}
