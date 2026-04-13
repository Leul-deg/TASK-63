package com.reslife.api.API_TESTS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.Role;
import com.reslife.api.domain.user.RoleName;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.domain.user.UserRole;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import com.reslife.api.admin.AdminUserController;
import com.reslife.api.admin.AdminUserService;
import com.reslife.api.admin.UpdateStatusRequest;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that Housing Administrator has the same user-management authority
 * as Admin (Medium #7).
 *
 * <ul>
 *   <li>HOUSING_ADMINISTRATOR may call status-change endpoints — 200.</li>
 *   <li>ADMIN continues to work — 200.</li>
 *   <li>Lower-privilege roles (e.g. RESIDENCE_STAFF) remain blocked — 403.</li>
 * </ul>
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import(SecurityConfig.class)
class AdminRolePolicyTest {

    @Autowired private MockMvc         mockMvc;
    @Autowired private ObjectMapper    objectMapper;

    @MockBean private AdminUserService     adminUserService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        // Service stub: return a minimal User so LoginResponse.from() serialises cleanly
        User updated = mock(User.class);
        when(updated.getId()).thenReturn(TARGET_ID);
        when(updated.getUsername()).thenReturn("target");
        when(updated.getEmail()).thenReturn("target@reslife.local");
        when(updated.getFirstName()).thenReturn("Target");
        when(updated.getLastName()).thenReturn("User");
        when(updated.getUserRoles()).thenReturn(Set.of());
        when(adminUserService.updateAccountStatus(any(), any(), any(), any(), any()))
                .thenReturn(updated);
    }

    // ── Housing Administrator has full user-management authority ──────────────

    @Test
    void housingAdministrator_canUpdateUserStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", TARGET_ID)
                        .with(asUser(ACTOR_ID, RoleName.HOUSING_ADMINISTRATOR))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody()))
               .andExpect(status().isOk());
    }

    // ── Admin still has access ────────────────────────────────────────────────

    @Test
    void admin_canUpdateUserStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", TARGET_ID)
                        .with(asUser(ACTOR_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody()))
               .andExpect(status().isOk());
    }

    // ── Other roles are denied ────────────────────────────────────────────────

    @Test
    void residenceStaff_cannotUpdateUserStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", TARGET_ID)
                        .with(asUser(ACTOR_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody()))
               .andExpect(status().isForbidden());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String statusBody() throws Exception {
        return objectMapper.writeValueAsString(new UpdateStatusRequest(AccountStatus.FROZEN, "test"));
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
