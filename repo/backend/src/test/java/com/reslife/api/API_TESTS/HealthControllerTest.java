package com.reslife.api.API_TESTS;

import com.reslife.api.controller.HealthController;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.security.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer test for {@link HealthController}.
 *
 * <p>{@code GET /api/health} is permit-all — no authentication required.
 */
@WebMvcTest(controllers = HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());
    }

    @Test
    void health_returnsOk_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("ok"));
    }
}
