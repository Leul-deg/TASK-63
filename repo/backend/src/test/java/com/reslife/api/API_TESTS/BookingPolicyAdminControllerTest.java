package com.reslife.api.API_TESTS;

import com.reslife.api.admin.BookingPolicy;
import com.reslife.api.admin.BookingPolicyController;
import com.reslife.api.admin.BookingPolicyService;
import com.reslife.api.admin.PolicyVersionResponse;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.user.*;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests for {@link BookingPolicyController}.
 *
 * <ul>
 *   <li>All four endpoints require {@code ADMIN} or {@code HOUSING_ADMINISTRATOR}.</li>
 *   <li>Non-admin users receive 403; unauthenticated requests receive 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = BookingPolicyController.class)
@Import(SecurityConfig.class)
class BookingPolicyAdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private BookingPolicyService   bookingPolicyService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final UUID ADMIN_ID   = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();

    private PolicyVersionResponse stubPolicy;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        stubPolicy = new PolicyVersionResponse(
                UUID.randomUUID(), 1, true, "Initial policy", "admin", Instant.now(), new BookingPolicy());

        when(bookingPolicyService.getCurrent()).thenReturn(stubPolicy);
        when(bookingPolicyService.update(any(), anyString(), any())).thenReturn(stubPolicy);
        when(bookingPolicyService.getHistory()).thenReturn(List.of(stubPolicy));
        when(bookingPolicyService.activateVersion(anyInt(), any())).thenReturn(stubPolicy);
    }

    // ── GET /api/admin/booking-policy ─────────────────────────────────────────

    @Test
    void admin_canGetCurrentPolicy() throws Exception {
        mockMvc.perform(get("/api/admin/booking-policy")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void student_cannotGetCurrentPolicy() throws Exception {
        mockMvc.perform(get("/api/admin/booking-policy")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotGetCurrentPolicy() throws Exception {
        mockMvc.perform(get("/api/admin/booking-policy"))
               .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/admin/booking-policy ─────────────────────────────────────────

    @Test
    void admin_canUpdatePolicy() throws Exception {
        mockMvc.perform(put("/api/admin/booking-policy")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policy":{"windowDays":14,"sameDayCutoffHour":17,
                                 "sameDayCutoffMinute":0,"noShowThreshold":2,
                                 "noShowWindowDays":30,"canaryEnabled":false,
                                 "canaryRolloutPercent":10,"canaryBuildingIds":[],
                                 "holidayBlackoutDates":[]},"description":"Updated"}
                                """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void student_cannotUpdatePolicy() throws Exception {
        mockMvc.perform(put("/api/admin/booking-policy")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policy":{"windowDays":14,"sameDayCutoffHour":17,
                                 "sameDayCutoffMinute":0,"noShowThreshold":2,
                                 "noShowWindowDays":30,"canaryEnabled":false,
                                 "canaryRolloutPercent":10,"canaryBuildingIds":[],
                                 "holidayBlackoutDates":[]},"description":"Nope"}
                                """))
               .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/booking-policy/history ─────────────────────────────────

    @Test
    void admin_canGetPolicyHistory() throws Exception {
        mockMvc.perform(get("/api/admin/booking-policy/history")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].version").value(1));
    }

    @Test
    void student_cannotGetPolicyHistory() throws Exception {
        mockMvc.perform(get("/api/admin/booking-policy/history")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    // ── POST /api/admin/booking-policy/activate/{version} ────────────────────

    @Test
    void admin_canActivateVersion() throws Exception {
        mockMvc.perform(post("/api/admin/booking-policy/activate/1")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void student_cannotActivateVersion() throws Exception {
        mockMvc.perform(post("/api/admin/booking-policy/activate/1")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isForbidden());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static RequestPostProcessor asUser(UUID userId, RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("user");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));

        ReslifeUserDetails details = ReslifeUserDetails.from(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities()));
    }
}
