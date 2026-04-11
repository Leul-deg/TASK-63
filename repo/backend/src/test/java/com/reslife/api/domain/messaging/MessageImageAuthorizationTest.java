package com.reslife.api.domain.messaging;

import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.Role;
import com.reslife.api.domain.user.RoleName;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.domain.user.UserRole;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies participant-level authorization for image serving introduced by High #4.
 *
 * <ul>
 *   <li>A thread participant may fetch an image — 200.</li>
 *   <li>A non-participant is denied — 403.</li>
 *   <li>An unknown filename returns 404.</li>
 * </ul>
 */
@WebMvcTest(controllers = MessagingController.class)
class MessageImageAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private MessagingService       messagingService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final String FILENAME         = "a1b2c3d4-0000-0000-0000-000000000001.jpg";
    private static final UUID   PARTICIPANT_ID   = UUID.randomUUID();
    private static final UUID   NON_PARTICIPANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        // IntegrationAuthFilter is mocked — let the filter chain pass through
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        // AccountStatusFilter needs a user row for any UUID it encounters
        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));
    }

    // ── Participant can fetch the image ────────────────────────────────────────

    @Test
    void participant_canFetchImage() throws Exception {
        when(messagingService.serveImageAsParticipant(eq(FILENAME), eq(PARTICIPANT_ID)))
                .thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        mockMvc.perform(get("/api/messages/images/{filename}", FILENAME)
                        .with(asUser(PARTICIPANT_ID, RoleName.STUDENT)))
               .andExpect(status().isOk());
    }

    // ── Non-participant is denied ──────────────────────────────────────────────

    @Test
    void nonParticipant_isDenied() throws Exception {
        when(messagingService.serveImageAsParticipant(eq(FILENAME), eq(NON_PARTICIPANT_ID)))
                .thenThrow(new BlockedException("You are not a participant in this thread."));

        mockMvc.perform(get("/api/messages/images/{filename}", FILENAME)
                        .with(asUser(NON_PARTICIPANT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    // ── Unknown filename returns 404 ───────────────────────────────────────────

    @Test
    void unknownFilename_returnsNotFound() throws Exception {
        String missing = "00000000-0000-0000-0000-000000000000.jpg";
        when(messagingService.serveImageAsParticipant(eq(missing), any(UUID.class)))
                .thenThrow(new EntityNotFoundException("Image not found: " + missing));

        mockMvc.perform(get("/api/messages/images/{filename}", missing)
                        .with(asUser(PARTICIPANT_ID, RoleName.STUDENT)))
               .andExpect(status().isNotFound());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
