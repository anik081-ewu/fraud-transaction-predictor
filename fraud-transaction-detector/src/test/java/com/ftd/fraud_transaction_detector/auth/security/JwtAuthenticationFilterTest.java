package com.ftd.fraud_transaction_detector.auth.security;

import com.ftd.fraud_transaction_detector.auth.entity.AppUser;
import com.ftd.fraud_transaction_detector.auth.repo.AppUserRepository;
import com.ftd.fraud_transaction_detector.auth.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAnActiveUserUsingTheDatabaseRole() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        AppUserRepository repository = mock(AppUserRepository.class);
        AppUser user = user(true, "AML_ADMIN");
        when(jwtService.validate("valid-token")).thenReturn(Map.of("sub", "operator", "role", "REVIEWER"));
        when(repository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.addHeader("Authorization", "Bearer valid-token");

        new JwtAuthenticationFilter(jwtService, repository).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain()
        );

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("operator", authentication.getName());
        assertEquals("ROLE_AML_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void rejectsAValidTokenWhenTheUserHasBeenDeactivated() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        AppUserRepository repository = mock(AppUserRepository.class);
        when(jwtService.validate("valid-token")).thenReturn(Map.of("sub", "operator"));
        when(repository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.of(user(false, "ADMIN")));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.addHeader("Authorization", "Bearer valid-token");

        new JwtAuthenticationFilter(jwtService, repository).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain()
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private AppUser user(boolean active, String role) {
        AppUser user = new AppUser();
        user.setUsername("operator");
        user.setRoleName(role);
        user.setIsActive(active);
        return user;
    }
}
