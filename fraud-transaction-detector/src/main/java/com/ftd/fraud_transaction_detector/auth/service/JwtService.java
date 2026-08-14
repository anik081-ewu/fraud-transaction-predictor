package com.ftd.fraud_transaction_detector.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.auth.entity.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private final ObjectMapper objectMapper;
    private final String secret;
    private final long expirySeconds;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${auth.jwt.secret:change-me-demo-secret}") String secret,
            @Value("${auth.jwt.expiry-seconds:3600}") long expirySeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.expirySeconds = expirySeconds;
    }

    public String createToken(AppUser user) {
        try {
            String header = base64Url(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            long now = Instant.now().getEpochSecond();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", user.getUsername());
            payload.put("role", user.getRoleName());
            payload.put("fullName", user.getFullName());
            payload.put("iat", now);
            payload.put("exp", now + expirySeconds);
            String payloadEncoded = base64Url(objectMapper.writeValueAsBytes(payload));
            return header + "." + payloadEncoded + "." + sign(header + "." + payloadEncoded);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create token", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token format");
            }
            Map<String, Object> header = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            if (!"HS256".equals(header.get("alg"))) {
                throw new IllegalArgumentException("Unsupported token algorithm");
            }
            String expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                throw new IllegalArgumentException("Invalid token signature");
            }
            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            if (payload.get("sub") == null || String.valueOf(payload.get("sub")).isBlank()) {
                throw new IllegalArgumentException("Token subject is missing");
            }
            Number expiry = (Number) payload.get("exp");
            if (expiry == null || Instant.now().getEpochSecond() > expiry.longValue()) {
                throw new IllegalArgumentException("Token expired");
            }
            return payload;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Token validation failed", exception);
        }
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
