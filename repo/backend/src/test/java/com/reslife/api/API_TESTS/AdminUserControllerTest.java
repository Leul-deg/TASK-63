package com.reslife.api.API_TESTS;

import com.reslife.api.admin.AdminUserController;
import com.reslife.api.admin.AdminUserService;
import com.reslife.api.admin.UserSummaryDto;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests for {@link AdminUserController}.
 *
 * <ul>
 *   <li>All endpoints require {@code ADMIN} or {@code HOUSING_ADMINISTRATOR}.</li>
 *   <li>Non-admin users receive 403.</li>
 *   <li>Unauthenticated requests receive 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private AdminUserService       adminUserService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final UUID ADMIN_ID   = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(new User()));

        UserSummaryDto stubUser = new UserSummaryDto(
                TARGET_ID, "jdoe", "jdoe@example.com", "John", "Doe",
                AccountStatus.ACTIVE, null, null, false, Instant.now(), Set.of("STUDENT"));

        when(adminUserService.listUsers(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(stubUser)));
        doNothing().when(adminUserService).deleteAccount(eq(TARGET_ID), any(), any());
    }

    // ── GET /api/admin/users ──────────────────────────────────────────────────

    @Test
    void admin_canListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content[0].id").value(TARGET_ID.toString()));
    }

    @Test
    void admin_canListUsersWithFilter() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .param("q", "john")
                        .param("status", "ACTIVE")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk());
    }

    @Test
    void student_cannotListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
               .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/admin/users/{id} ──────────────────────────────────────────

    @Test
    void admin_canDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", TARGET_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    @Test
    void student_cannotDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", TARGET_ID)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", TARGET_ID)
                        .with(csrf()))
               .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/admin/users/{id}/status ────────────────────────────────────

    @Test
    void admin_canUpdateStatus() throws Exception {
        User updated = mock(User.class);
        when(updated.getId()).thenReturn(TARGET_ID);
        when(updated.getUsername()).thenReturn("jdoe");
        when(updated.getEmail()).thenReturn("jdoe@example.com");
        when(updated.getAccountStatus()).thenReturn(AccountStatus.DISABLED);
        when(updated.getUserRoles()).thenReturn(Set.of());
        when(adminUserService.updateAccountStatus(eq(TARGET_ID), any(), any(), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/admin/users/{id}/status", TARGET_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","reason":"Policy violation"}
                                """))
               .andExpect(status().isOk());
    }

    @Test
    void student_cannotUpdateStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", TARGET_ID)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","reason":"test"}
                                """))
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
