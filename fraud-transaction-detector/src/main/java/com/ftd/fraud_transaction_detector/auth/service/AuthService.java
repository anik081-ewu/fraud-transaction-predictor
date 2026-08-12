package com.ftd.fraud_transaction_detector.auth.service;

import com.ftd.fraud_transaction_detector.auth.dto.*;
import com.ftd.fraud_transaction_detector.auth.entity.AppUser;
import com.ftd.fraud_transaction_detector.auth.repo.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {
    private final AppUserRepository appUserRepository;
    private final PasswordHashService passwordHashService;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository, PasswordHashService passwordHashService, JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordHashService = passwordHashService;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        validateRegister(request);
        String username = request.username().trim();
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordHashService.hash(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRoleName("REVIEWER");
        user.setIsActive(Boolean.TRUE);
        user.setCreatedAt(Instant.now());
        return toResponse(appUserRepository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new IllegalArgumentException("Username and password are required");
        }
        AppUser user = appUserRepository.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!Boolean.TRUE.equals(user.getIsActive())
                || !passwordHashService.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        if (passwordHashService.needsUpgrade(user.getPasswordHash())) {
            user.setPasswordHash(passwordHashService.hash(request.password()));
            appUserRepository.save(user);
        }
        return new AuthResponse(jwtService.createToken(user), "Bearer", jwtService.getExpirySeconds(), toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String authorizationHeader) {
        Map<String, Object> claims = jwtService.validate(extractToken(authorizationHeader));
        AppUser user = appUserRepository.findByUsernameIgnoreCase(String.valueOf(claims.get("sub")))
                .orElseThrow(() -> new IllegalArgumentException("User not found for token"));
        return toResponse(user);
    }

    public Map<String, Object> validateToken(String authorizationHeader) {
        return jwtService.validate(extractToken(authorizationHeader));
    }

    private void validateRegister(RegisterRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
    }

    private static String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header must use Bearer token");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private static UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRoleName(), Boolean.TRUE.equals(user.getIsActive()));
    }
}
