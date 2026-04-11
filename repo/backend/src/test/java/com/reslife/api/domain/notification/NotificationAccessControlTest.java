package com.reslife.api.domain.notification;

import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.user.*;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import jakarta.persistence.EntityNotFoundException;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import(SecurityConfig.class)
class NotificationAccessControlTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private NotificationService notificationService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter integrationAuthFilter;
    @MockBean private UserRepository userRepository;

    private static final UUID RECIPIENT_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        Notification recipientNotification = notificationFor(RECIPIENT_ID, true);
        NotificationResponse recipientResponse = NotificationResponse.from(recipientNotification);
        when(notificationService.findById(eq(NOTIFICATION_ID), eq(RECIPIENT_ID)))
                .thenReturn(recipientNotification);
        when(notificationService.findById(eq(NOTIFICATION_ID), eq(OTHER_USER_ID)))
                .thenThrow(new EntityNotFoundException("Notification not found"));
        when(notificationService.acknowledge(eq(NOTIFICATION_ID), eq(RECIPIENT_ID), any(), any()))
                .thenReturn(recipientResponse);
        when(notificationService.listTemplates())
                .thenReturn(List.of(new TemplateResponse(
                        UUID.randomUUID(),
                        "general.info",
                        NotificationCategory.GENERAL.name(),
                        "{{title}}",
                        "{{body}}",
                        "NORMAL",
                        false,
                        "General information"
                )));
        when(notificationService.sendFromTemplate(eq("general.info"), any(), any(), any(), any(), any()))
                .thenReturn(List.of(notificationFor(RECIPIENT_ID, false)));
    }

    @Test
    void recipient_canFetchOwnNotification() throws Exception {
        mockMvc.perform(get("/api/notifications/{id}", NOTIFICATION_ID)
                        .with(asUser(RECIPIENT_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(NOTIFICATION_ID.toString()));
    }

    @Test
    void otherUser_cannotFetchSomeoneElsesNotification() throws Exception {
        mockMvc.perform(get("/api/notifications/{id}", NOTIFICATION_ID)
                        .with(asUser(OTHER_USER_ID, RoleName.STUDENT)))
               .andExpect(status().isNotFound());
    }

    @Test
    void recipient_canAcknowledgeOwnNotification() throws Exception {
        mockMvc.perform(post("/api/notifications/{id}/acknowledge", NOTIFICATION_ID)
                        .with(asUser(RECIPIENT_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.acknowledged").value(true));
    }

    @Test
    void staff_canListTemplates() throws Exception {
        mockMvc.perform(get("/api/notifications/templates")
                        .with(asUser(RECIPIENT_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].templateKey").value("general.info"));
    }

    @Test
    void student_cannotListTemplates() throws Exception {
        mockMvc.perform(get("/api/notifications/templates")
                        .with(asUser(RECIPIENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    @Test
    void staff_canSendTemplateNotification() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .with(asUser(RECIPIENT_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"templateKey":"general.info","recipientIds":["%s"],"variables":{"title":"Heads up","body":"Read me"}}
                                """.formatted(RECIPIENT_ID)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.sent").value(1));
    }

    private static Notification notificationFor(UUID recipientId, boolean acknowledged) {
        User recipient = new User();
        assignBaseEntityId(recipient, recipientId);

        Notification n = new Notification();
        assignBaseEntityId(n, NOTIFICATION_ID);
        assignCreatedAt(n, Instant.now());
        n.setRecipient(recipient);
        n.setTitle("Important");
        n.setBody("Read me");
        n.setType(NotificationType.INFO);
        n.setPriority(NotificationPriority.HIGH);
        n.setCategory(NotificationCategory.SETTLEMENT);
        n.setRequiresAcknowledgment(true);
        if (acknowledged) {
            n.markAcknowledged();
        }
        return n;
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
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
        return authentication(token);
    }

    private static void assignBaseEntityId(Object entity, UUID id) {
        try {
            java.lang.reflect.Field idField =
                    com.reslife.api.common.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assignCreatedAt(Object entity, Instant createdAt) {
        try {
            java.lang.reflect.Field createdAtField =
                    com.reslife.api.common.BaseEntity.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(entity, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
