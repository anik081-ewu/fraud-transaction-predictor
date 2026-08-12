package com.ftd.fraud_transaction_detector.auth.dto;

public record UserResponse(Long id, String username, String fullName, String roleName, boolean active) {
}
