package com.ftd.fraud_transaction_detector.auth.dto;

public record RegisterRequest(String username, String password, String fullName, String roleName) {
}
