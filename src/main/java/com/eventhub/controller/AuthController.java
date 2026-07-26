package com.eventhub.controller;

import com.eventhub.controller.dto.LoginRequest;
import com.eventhub.controller.dto.LoginResponse;
import com.eventhub.controller.dto.ParticipantResponse;
import com.eventhub.controller.dto.RegisterUserRequest;
import com.eventhub.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ParticipantResponse> register(@RequestBody @Valid RegisterUserRequest request) {
        ParticipantResponse response = authService.register(request);
        URI location = URI.create("/api/participants/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
