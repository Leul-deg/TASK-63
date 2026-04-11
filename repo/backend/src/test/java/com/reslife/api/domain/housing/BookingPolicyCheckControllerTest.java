package com.reslife.api.domain.housing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.resident.ResidentController;
import com.reslife.api.domain.resident.ResidentService;
import com.reslife.api.domain.housing.ResidentBookingService;
import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.Role;
import com.reslife.api.domain.user.RoleName;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.domain.user.UserRole;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level authorization and validation tests for
 * {@code POST /api/residents/{id}/booking-policy-check}.
 *
 * <p>The service-layer policy logic is covered by {@link BookingPolicyEnforcementServiceTest}.
 * These tests focus on the HTTP boundary:
 * <ul>
 *   <li>STUDENT role is denied (403) — endpoint is staff-only.</li>
 *   <li>Staff role is allowed (200) and the service result is returned.</li>
 *   <li>Missing required field returns 400 (bean validation).</li>
 * </ul>
 */
@WebMvcTest(controllers = ResidentController.class)
@Import(SecurityConfig.class)
class BookingPolicyCheckControllerTest {

    @Autowired private MockMvc      mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ResidentService                  residentService;
    @MockBean private BookingPolicyEnforcementService  enforcementService;
    @MockBean private ResidentBookingService           residentBookingService;
    @MockBean private UserDetailsServiceImpl           userDetailsService;
    @MockBean private IntegrationAuthFilter            integrationAuthFilter;
    @MockBean private UserRepository                   userRepository;

    private static final UUID RESIDENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        when(enforcementService.evaluate(eq(RESIDENT_ID), any()))
                .thenReturn(new BookingPolicyCheckResponse(
                        true, true, "Maple Hall",
                        LocalDate.of(2026, 4, 20), LocalDate.of(2026, 5, 31),
                        "12:00", 0, false, List.of()));
    }

    // ── Student is denied ─────────────────────────────────────────────────────

    @Test
    void student_isDenied() throws Exception {
        mockMvc.perform(post("/api/residents/{id}/booking-policy-check", RESIDENT_ID)
                        .with(asUser(UUID.randomUUID(), RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
               .andExpect(status().isForbidden());
    }

    // ── Staff is allowed and service result is returned ───────────────────────

    @Test
    void staff_isAllowed_andResponseReturned() throws Exception {
        mockMvc.perform(post("/api/residents/{id}/booking-policy-check", RESIDENT_ID)
                        .with(asUser(UUID.randomUUID(), RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.allowed").value(true))
               .andExpect(jsonPath("$.buildingName").value("Maple Hall"));
    }

    // ── Missing required field → 400 ──────────────────────────────────────────

    @Test
    void missingRequiredDate_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/residents/{id}/booking-policy-check", RESIDENT_ID)
                        .with(asUser(UUID.randomUUID(), RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
               .andExpect(status().isBadRequest());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String validBody() throws Exception {
        return objectMapper.writeValueAsString(
                new BookingPolicyCheckRequest(LocalDate.of(2026, 4, 20), "Maple Hall"));
    }

    private static RequestPostProcessor asUser(UUID userId, RoleName roleName) {
        ReslifeUserDetails details = buildDetails(userId, roleName);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(
                        details, null, details.getAuthorities());
        return authentication(token);
    }

    private static ReslifeUserDetails buildDetails(UUID userId, RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);

        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("testuser");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));

        return ReslifeUserDetails.from(user);
    }
}
