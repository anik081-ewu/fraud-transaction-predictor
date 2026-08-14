package com.ftd.fraud_transaction_detector.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.common.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] ADMIN_ROLES = {"ADMIN", "AML_ADMIN"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/health",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/uploads/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers("/api/v1/ml/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.POST, "/api/v1/aml/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/aml/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/aml/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.POST, "/api/v1/anomaly-model-comparisons/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/anomaly-model-comparisons/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/anomaly-model-comparisons/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response, request.getRequestURI(), HttpStatus.UNAUTHORIZED,
                                "Authentication is required"
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response, request.getRequestURI(), HttpStatus.FORBIDDEN,
                                "Your role is not authorized for this operation"
                        ))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeError(
            HttpServletResponse response,
            String path,
            HttpStatus status,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, path
        ));
    }
}
