package com.reslife.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private UserRepository userRepository;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter integrationAuthFilter;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = buildUser(USER_ID, AccountStatus.ACTIVE);
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(active));
        doNothing().when(authService).recordLogout(any(), any());
    }

    @Test
    void login_returnsCurrentUserPayload() throws Exception {
        when(authService.login(any(), any())).thenReturn(buildDetails(USER_ID, RoleName.STUDENT));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("student", "password"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(USER_ID.toString()))
               .andExpect(jsonPath("$.username").value("student"))
               .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
    }

    @Test
    void me_returnsAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(asUser(USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.username").value("student"));
    }

    @Test
    void logout_invalidatesAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(asUser(USER_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    private static RequestPostProcessor asUser(UUID userId, RoleName roleName) {
        ReslifeUserDetails details = buildDetails(userId, roleName);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(
                        details, null, details.getAuthorities());
        return authentication(token);
    }

    private static ReslifeUserDetails buildDetails(UUID userId, RoleName roleName) {
        return ReslifeUserDetails.from(buildUser(userId, AccountStatus.ACTIVE, roleName));
    }

    private static User buildUser(UUID userId, AccountStatus status) {
        return buildUser(userId, status, RoleName.STUDENT);
    }

    private static User buildUser(UUID userId, AccountStatus status, RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("student");
        when(user.getEmail()).thenReturn("student@reslife.local");
        when(user.getFirstName()).thenReturn("Student");
        when(user.getLastName()).thenReturn("User");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getAccountStatus()).thenReturn(status);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));
        return user;
    }
}
