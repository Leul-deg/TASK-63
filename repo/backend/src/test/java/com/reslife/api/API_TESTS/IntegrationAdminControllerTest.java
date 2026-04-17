package com.reslife.api.API_TESTS;

import com.reslife.api.admin.IntegrationAdminController;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.*;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests for {@link IntegrationAdminController}.
 *
 * <ul>
 *   <li>All eleven endpoints require {@code ADMIN} or {@code HOUSING_ADMINISTRATOR}.</li>
 *   <li>Non-admin users receive 403.</li>
 *   <li>Unauthenticated requests receive 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = IntegrationAdminController.class)
@Import(SecurityConfig.class)
class IntegrationAdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private IntegrationKeyService  keyService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final UUID ADMIN_ID   = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID KEY_ID     = UUID.randomUUID();
    private static final UUID WEBHOOK_ID = UUID.randomUUID();

    private KeyResponse stubKey;
    private WebhookResponse stubWebhook;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        stubKey = new KeyResponse(
                KEY_ID, "rk_abc123def456ghi7", "Test Key", "desc",
                "rk_abc1", null, true, null, null, null, "admin", Instant.now());
        stubWebhook = new WebhookResponse(
                WEBHOOK_ID, "Test Webhook", "http://internal/hook",
                "[\"resident.created\"]", "wh_abc1", true, Instant.now());

        CreateKeyResponse createKeyResp = new CreateKeyResponse(
                KEY_ID, "rk_abc123def456ghi7", "Test Key", "desc",
                "plaintextSecret", "rk_abc1", null, true, Instant.now());
        CreateWebhookResponse createWebhookResp = new CreateWebhookResponse(
                WEBHOOK_ID, "Test Webhook", "http://internal/hook",
                "[\"resident.created\"]", "signingSecret", "wh_abc1", true, Instant.now());

        when(keyService.listKeys(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(stubKey)));
        when(keyService.createKey(any(), any())).thenReturn(createKeyResp);
        when(keyService.getKey(eq(KEY_ID))).thenReturn(stubKey);
        when(keyService.updateKey(eq(KEY_ID), any())).thenReturn(stubKey);
        when(keyService.revokeKey(eq(KEY_ID), any())).thenReturn(stubKey);
        when(keyService.listWebhooks(eq(KEY_ID))).thenReturn(List.of(stubWebhook));
        when(keyService.addWebhook(eq(KEY_ID), any())).thenReturn(createWebhookResp);
        when(keyService.toggleWebhook(eq(KEY_ID), eq(WEBHOOK_ID), anyBoolean())).thenReturn(stubWebhook);
        doNothing().when(keyService).deleteWebhook(eq(KEY_ID), eq(WEBHOOK_ID));
        when(keyService.getAuditLogs(eq(KEY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(keyService.getAllAuditLogs(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
    }

    // ── GET /api/admin/integration-keys ───────────────────────────────────────

    @Test
    void admin_canListKeys() throws Exception {
        mockMvc.perform(get("/api/admin/integration-keys")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content[0].id").value(KEY_ID.toString()));
    }

    @Test
    void student_cannotListKeys() throws Exception {
        mockMvc.perform(get("/api/admin/integration-keys")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotListKeys() throws Exception {
        mockMvc.perform(get("/api/admin/integration-keys"))
               .andExpect(status().isUnauthorized());
    }

    // ── POST /api/admin/integration-keys ──────────────────────────────────────

    @Test
    void admin_canCreateKey() throws Exception {
        mockMvc.perform(post("/api/admin/integration-keys")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My Integration","description":"desc","allowedEvents":null}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(KEY_ID.toString()));
    }

    // ── GET /api/admin/integration-keys/{id} ──────────────────────────────────

    @Test
    void admin_canGetKey() throws Exception {
        mockMvc.perform(get("/api/admin/integration-keys/{id}", KEY_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.name").value("Test Key"));
    }

    // ── PUT /api/admin/integration-keys/{id} ──────────────────────────────────

    @Test
    void admin_canUpdateKey() throws Exception {
        mockMvc.perform(put("/api/admin/integration-keys/{id}", KEY_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Name","description":"new desc","allowedEvents":null}
                                """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.name").value("Test Key"));
    }

    // ── POST /api/admin/integration-keys/{id}/revoke ──────────────────────────

    @Test
    void admin_canRevokeKey() throws Exception {
        mockMvc.perform(post("/api/admin/integration-keys/{id}/revoke", KEY_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Compromised"}
                                """))
               .andExpect(status().isOk());
    }

    // ── GET /api/admin/integration-keys/{id}/webhooks ─────────────────────────

    @Test
    void admin_canListWebhooks() throws Exception {
        mockMvc.perform(get("/api/admin/integration-keys/{id}/webhooks", KEY_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(WEBHOOK_ID.toString()));
    }

    // ── POST /api/admin/integration-keys/{id}/webhooks ────────────────────────

    @Test
    void admin_canAddWebhook() throws Exception {
        mockMvc.perform(post("/api/admin/integration-keys/{id}/webhooks", KEY_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My Hook","targetUrl":"http://internal/hook","eventTypes":"[\\"resident.created\\"]"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(WEBHOOK_ID.toString()));
    }

    // ── POST /api/admin/integration-keys/{id}/webhooks/{wid}/toggle ───────────

    @Test
    void admin_canToggleWebhook() throws Exception {
        mockMvc.perform(post("/api/admin/integration-keys/{id}/webhooks/{wid}/toggle", KEY_ID, WEBHOOK_ID)
                        .param("active", "false")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isOk());
    }

    // ── DELETE /api/admin/integration-keys/{id}/webhooks/{wid} ───────────────

    @Test
    void admin_canDeleteWebhook() throws Exception {
        mockMvc.perform(delete("/api/admin/integration-keys/{id}/webhooks/{wid}", KEY_ID, WEBHOOK_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    // ── GET /api/admin/integration-keys/{id}/audit ────────────────────────────

    @Test
    void admin_canGetAuditForKey() throws Exception {
        mockMvc.perform(get("/api/admin/integration-keys/{id}/audit", KEY_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").isArray());
    }

    // ── GET /api/admin/integrations/audit ─────────────────────────────────────

    @Test
    void admin_canGetAllAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/integrations/audit")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void student_cannotGetAllAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/integrations/audit")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
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
