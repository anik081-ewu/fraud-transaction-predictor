package com.ftd.fraud_transaction_detector.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class PasswordHashService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (storedHash != null && storedHash.startsWith("$2")) {
            return encoder.matches(rawPassword, storedHash);
        }
        return legacyHash(rawPassword).equals(storedHash);
    }

    public boolean needsUpgrade(String storedHash) {
        return storedHash == null || !storedHash.startsWith("$2");
    }

    private String legacyHash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash password", exception);
        }
    }
}
