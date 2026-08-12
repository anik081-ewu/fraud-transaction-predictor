package com.ftd.fraud_transaction_detector.auth.dto;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) {
}
