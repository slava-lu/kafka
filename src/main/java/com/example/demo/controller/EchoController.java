package com.example.demo.controller;

import com.example.api.EchoApi;
import com.example.model.EchoRequest;
import com.example.model.EchoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

@Component
public class EchoController implements EchoApi {

    @Override
    public ResponseEntity<EchoResponse> sendString(EchoRequest body) {

        EchoResponse response = new EchoResponse();

        response.setId(body.getId());
        response.setMessage(body.getMessage());

        return ResponseEntity.ok(response);
    }
}
