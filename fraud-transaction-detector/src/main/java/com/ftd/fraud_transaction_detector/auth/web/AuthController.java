package com.ftd.fraud_transaction_detector.auth.web;

import com.ftd.fraud_transaction_detector.auth.dto.*;
import com.ftd.fraud_transaction_detector.auth.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public UserResponse register(@RequestBody RegisterRequest request) { return authService.register(request); }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) { return authService.login(request); }

    @GetMapping("/me")
    public UserResponse me(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.getCurrentUser(authorizationHeader);
    }

    @GetMapping("/validate")
    public Map<String, Object> validate(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.validateToken(authorizationHeader);
    }

    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("module", "auth", "status", "UP"); }
}
