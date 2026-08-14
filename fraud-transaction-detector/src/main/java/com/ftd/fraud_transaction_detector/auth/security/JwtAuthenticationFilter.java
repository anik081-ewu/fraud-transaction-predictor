package com.ftd.fraud_transaction_detector.auth.security;

import com.ftd.fraud_transaction_detector.auth.entity.AppUser;
import com.ftd.fraud_transaction_detector.auth.repo.AppUserRepository;
import com.ftd.fraud_transaction_detector.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository appUserRepository) {
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            Map<String, Object> claims = jwtService.validate(token);
            String username = String.valueOf(claims.get("sub"));
            AppUser user = appUserRepository.findByUsernameIgnoreCase(username)
                    .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                    .orElse(null);
            if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String role = normalizeRole(user.getRoleName());
                var authentication = new UsernamePasswordAuthenticationToken(
                        user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String normalizeRole(String roleName) {
        if (roleName == null || roleName.isBlank()) return "REVIEWER";
        String role = roleName.trim().toUpperCase(Locale.ROOT);
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }
}
