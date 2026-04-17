package com.reslife.api.API_TESTS;

import com.reslife.api.admin.AnalyticsAdminController;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.analytics.AnalyticsComputeService;
import com.reslife.api.domain.analytics.AnalyticsSnapshotRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer tests for {@link AnalyticsAdminController}.
 *
 * <ul>
 *   <li>{@code GET /api/admin/analytics} and {@code POST /api/admin/analytics/refresh}
 *       require {@code ADMIN} or {@code HOUSING_ADMINISTRATOR}.</li>
 *   <li>Non-admin users receive 403; unauthenticated requests receive 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = AnalyticsAdminController.class)
@Import(SecurityConfig.class)
class AnalyticsAdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private AnalyticsSnapshotRepository snapshotRepo;
    @MockBean private AnalyticsComputeService     computeService;
    @MockBean private UserDetailsServiceImpl      userDetailsService;
    @MockBean private IntegrationAuthFilter       integrationAuthFilter;
    @MockBean private UserRepository              userRepository;

    private static final UUID ADMIN_ID   = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        when(snapshotRepo.findAll()).thenReturn(List.of());
        doNothing().when(computeService).refresh();
    }

    // ── GET /api/admin/analytics ───────────────────────────────────────────────

    @Test
    void admin_canGetDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/analytics")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk());
    }

    @Test
    void housingAdmin_canGetDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/analytics")
                        .with(asUser(ADMIN_ID, RoleName.HOUSING_ADMINISTRATOR)))
               .andExpect(status().isOk());
    }

    @Test
    void student_cannotGetDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/analytics")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotGetDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/analytics"))
               .andExpect(status().isUnauthorized());
    }

    // ── POST /api/admin/analytics/refresh ─────────────────────────────────────

    @Test
    void admin_canTriggerRefresh() throws Exception {
        mockMvc.perform(post("/api/admin/analytics/refresh")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isNoContent());

        verify(computeService).refresh();
    }

    @Test
    void student_cannotTriggerRefresh() throws Exception {
        mockMvc.perform(post("/api/admin/analytics/refresh")
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
