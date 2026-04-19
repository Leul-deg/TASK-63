package com.reslife.api.API_TESTS;

import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.messaging.*;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
 * HTTP-layer tests for {@link MessagingController}.
 *
 * <ul>
 *   <li>Any authenticated user may access thread/message/block/quick-reply endpoints.</li>
 *   <li>{@code POST /api/messages/notices} is restricted to staff roles only.</li>
 *   <li>Unauthenticated requests are rejected with 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = MessagingController.class)
@Import(SecurityConfig.class)
class MessagingControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private MessagingService      messagingService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID THREAD_ID = UUID.randomUUID();
    private static final UUID MSG_ID    = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    private ThreadSummaryResponse stubThread;
    private ThreadDetailResponse  stubDetail;
    private MessageResponse       stubMsg;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        stubMsg = new MessageResponse(
                MSG_ID, USER_ID, "Alice", "hello", "TEXT", null, null, false, Instant.now(), "SENT");
        stubThread = new ThreadSummaryResponse(
                THREAD_ID, "Subject", "DIRECT", List.of(), stubMsg, 0, Instant.now());
        stubDetail = new ThreadDetailResponse(
                THREAD_ID, "Subject", "DIRECT", List.of(), List.of(stubMsg), Instant.now());

        when(messagingService.listThreads(any(), any())).thenReturn(List.of(stubThread));
        when(messagingService.createThread(any(), any())).thenReturn(stubThread);
        when(messagingService.getThread(any(), any(), any())).thenReturn(stubDetail);
        when(messagingService.sendMessage(any(), any(), any())).thenReturn(stubMsg);
        when(messagingService.sendImageMessage(any(), any(), any())).thenReturn(stubMsg);
        when(messagingService.pollMessages(any(), any(), any(), any())).thenReturn(List.of(stubMsg));
        doNothing().when(messagingService).deleteMessage(any(), any());
        when(messagingService.listQuickReplies()).thenReturn(
                List.of(new QuickReplyResponse(UUID.randomUUID(), "gr.hello", "Hello", "Hello!")));
        when(messagingService.searchUsers(any(), any())).thenReturn(
                List.of(new UserSummaryResponse(TARGET_ID, "alice", "Alice Smith")));
        when(messagingService.listBlocks(any())).thenReturn(
                List.of(new BlockedStaffResponse(TARGET_ID, "Staff Bob", Instant.now())));
        doNothing().when(messagingService).blockStaff(any(), any());
        doNothing().when(messagingService).unblockStaff(any(), any());
        doNothing().when(messagingService).sendSystemNotice(any(), any());
    }

    // ── Inbox ─────────────────────────────────────────────────────────────────

    @Test
    void authenticated_canListThreads() throws Exception {
        mockMvc.perform(get("/api/messages/threads")
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(THREAD_ID.toString()));
    }

    @Test
    void unauthenticated_cannotListThreads() throws Exception {
        mockMvc.perform(get("/api/messages/threads"))
               .andExpect(status().isUnauthorized());
    }

    // ── Create thread ──────────────────────────────────────────────────────────

    @Test
    void authenticated_canCreateThread() throws Exception {
        mockMvc.perform(post("/api/messages/threads")
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Hello","recipientIds":["%s"],"body":"Hi there"}
                                """.formatted(TARGET_ID)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(THREAD_ID.toString()));
    }

    // ── Thread detail ──────────────────────────────────────────────────────────

    @Test
    void authenticated_canGetThread() throws Exception {
        mockMvc.perform(get("/api/messages/threads/{id}", THREAD_ID)
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(THREAD_ID.toString()));
    }

    @Test
    void getThread_notFound_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        when(messagingService.getThread(eq(missing), any(), any()))
                .thenThrow(new EntityNotFoundException("Thread not found"));

        mockMvc.perform(get("/api/messages/threads/{id}", missing)
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isNotFound());
    }

    // ── Send message ───────────────────────────────────────────────────────────

    @Test
    void authenticated_canSendMessage() throws Exception {
        mockMvc.perform(post("/api/messages/threads/{id}/messages", THREAD_ID)
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Hello"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(MSG_ID.toString()));
    }

    // ── Send image ─────────────────────────────────────────────────────────────

    @Test
    void authenticated_canSendImageMessage() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-jpg-bytes".getBytes());

        mockMvc.perform(multipart("/api/messages/threads/{id}/messages/image", THREAD_ID)
                        .file(image)
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(MSG_ID.toString()));
    }

    @Test
    void unauthenticated_cannotSendImageMessage() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-jpg-bytes".getBytes());

        mockMvc.perform(multipart("/api/messages/threads/{id}/messages/image", THREAD_ID)
                        .file(image)
                        .with(csrf()))
               .andExpect(status().isUnauthorized());
    }

    // ── Poll messages ──────────────────────────────────────────────────────────

    @Test
    void authenticated_canPollMessages() throws Exception {
        mockMvc.perform(get("/api/messages/threads/{id}/poll", THREAD_ID)
                        .param("after", Instant.now().minusSeconds(60).toString())
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(MSG_ID.toString()));
    }

    // ── Delete message ─────────────────────────────────────────────────────────

    @Test
    void authenticated_canDeleteOwnMessage() throws Exception {
        mockMvc.perform(delete("/api/messages/threads/{id}/messages/{msgId}", THREAD_ID, MSG_ID)
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    // ── Quick replies ──────────────────────────────────────────────────────────

    @Test
    void authenticated_canListQuickReplies() throws Exception {
        mockMvc.perform(get("/api/messages/quick-replies")
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].replyKey").value("gr.hello"));
    }

    // ── User search ────────────────────────────────────────────────────────────

    @Test
    void authenticated_canSearchUsers() throws Exception {
        mockMvc.perform(get("/api/messages/users")
                        .param("q", "alice")
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].username").value("alice"));
    }

    // ── Blocks ─────────────────────────────────────────────────────────────────

    @Test
    void authenticated_canListBlocks() throws Exception {
        mockMvc.perform(get("/api/messages/blocks")
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].staffUserId").value(TARGET_ID.toString()));
    }

    @Test
    void authenticated_canBlockStaff() throws Exception {
        mockMvc.perform(post("/api/messages/blocks/{staffUserId}", TARGET_ID)
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    @Test
    void authenticated_canUnblockStaff() throws Exception {
        mockMvc.perform(delete("/api/messages/blocks/{staffUserId}", TARGET_ID)
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    // ── System notices ─────────────────────────────────────────────────────────

    @Test
    void staff_canSendNotice() throws Exception {
        mockMvc.perform(post("/api/messages/notices")
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Heads up","body":"Read me","recipientIds":["%s"]}
                                """.formatted(USER_ID)))
               .andExpect(status().isNoContent());
    }

    @Test
    void student_cannotSendNotice() throws Exception {
        mockMvc.perform(post("/api/messages/notices")
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Heads up","body":"Read me","recipientIds":["%s"]}
                                """.formatted(TARGET_ID)))
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
