package com.reslife.api.API_TESTS;

import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.notification.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
 * HTTP-layer tests for the four notification inbox endpoints not previously covered:
 * {@code GET /api/notifications}, {@code GET /api/notifications/count},
 * {@code POST /api/notifications/{id}/read}, {@code POST /api/notifications/read-all}.
 */
@WebMvcTest(controllers = NotificationController.class)
@Import(SecurityConfig.class)
class NotificationInboxControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private NotificationService   notificationService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final UUID USER_ID           = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        when(notificationService.findInbox(any(), anyBoolean(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationService.counts(any()))
                .thenReturn(new NotificationCountResponse(3L, 1L));
        when(notificationService.markRead(eq(NOTIFICATION_ID), any()))
                .thenReturn(stubNotificationResponse());
        when(notificationService.markAllRead(any())).thenReturn(5);
    }

    // ── GET /api/notifications ────────────────────────────────────────────────

    @Test
    void authenticated_canGetInbox() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void authenticated_canGetInboxWithFilter() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .param("unreadOnly", "true")
                        .param("category", "GENERAL")
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_cannotGetInbox() throws Exception {
        mockMvc.perform(get("/api/notifications"))
               .andExpect(status().isUnauthorized());
    }

    // ── GET /api/notifications/count ──────────────────────────────────────────

    @Test
    void authenticated_canGetCounts() throws Exception {
        mockMvc.perform(get("/api/notifications/count")
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.unread").value(3))
               .andExpect(jsonPath("$.pendingAcknowledgment").value(1));
    }

    @Test
    void unauthenticated_cannotGetCounts() throws Exception {
        mockMvc.perform(get("/api/notifications/count"))
               .andExpect(status().isUnauthorized());
    }

    // ── POST /api/notifications/{id}/read ─────────────────────────────────────

    @Test
    void authenticated_canMarkOneRead() throws Exception {
        mockMvc.perform(post("/api/notifications/{id}/read", NOTIFICATION_ID)
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(NOTIFICATION_ID.toString()));
    }

    @Test
    void unauthenticated_cannotMarkRead() throws Exception {
        mockMvc.perform(post("/api/notifications/{id}/read", NOTIFICATION_ID)
                        .with(csrf()))
               .andExpect(status().isUnauthorized());
    }

    // ── POST /api/notifications/read-all ──────────────────────────────────────

    @Test
    void authenticated_canMarkAllRead() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all")
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.marked").value(5));
    }

    @Test
    void unauthenticated_cannotMarkAllRead() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all")
                        .with(csrf()))
               .andExpect(status().isUnauthorized());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private NotificationResponse stubNotificationResponse() {
        try {
            Notification n = new Notification();
            java.lang.reflect.Field idField =
                    com.reslife.api.common.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(n, NOTIFICATION_ID);

            java.lang.reflect.Field createdAtField =
                    com.reslife.api.common.BaseEntity.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(n, java.time.Instant.now());

            User recipient = new User();
            n.setRecipient(recipient);
            n.setTitle("Test");
            n.setBody("Body");
            n.setType(NotificationType.INFO);
            n.setPriority(NotificationPriority.NORMAL);
            n.setCategory(NotificationCategory.GENERAL);
            return NotificationResponse.from(n);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

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
