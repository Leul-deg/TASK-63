package com.reslife.api.API_TESTS;

import com.reslife.api.config.IntegrationConfig;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.HmacService;
import com.reslife.api.domain.integration.InboundController;
import com.reslife.api.domain.integration.IntegrationAuditLogRepository;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.integration.IntegrationKey;
import com.reslife.api.domain.integration.IntegrationKeyRepository;
import com.reslife.api.domain.integration.IntegrationRateLimiter;
import com.reslife.api.security.AccountStatusFilter;
import com.reslife.api.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InboundController.class)
@Import({SecurityConfig.class, IntegrationConfig.class, HmacService.class, IntegrationRateLimiter.class})
class InboundIntegrationAuthWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private HmacService hmacService;

    @MockBean private IntegrationKeyRepository keyRepository;
    @MockBean private IntegrationAuditLogRepository auditRepository;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private AccountStatusFilter accountStatusFilter;

    private IntegrationKey activeKey;

    @BeforeEach
    void setUp() {
        activeKey = new IntegrationKey();
        activeKey.setKeyId("rk_test");
        activeKey.setName("Test key");
        activeKey.setSecret("integration-secret");
        activeKey.setActive(true);
        activeKey.setAllowedEvents(null);

        when(keyRepository.findByKeyIdAndActiveTrue(anyString())).thenAnswer(inv -> {
            IntegrationKey key = new IntegrationKey();
            key.setKeyId(inv.getArgument(0));
            key.setName("Test key");
            key.setSecret("integration-secret");
            key.setActive(true);
            key.setAllowedEvents(null);
            return Optional.of(key);
        });
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void missingHeaders_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/integrations/events/door.unlocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"door-1\"}"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void badSignature_returnsUnauthorized() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        mockMvc.perform(post("/api/integrations/events/door.unlocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IntegrationAuthFilter.KEY_ID_HDR, "rk_bad")
                        .header(IntegrationAuthFilter.TS_HDR, String.valueOf(timestamp))
                        .header(IntegrationAuthFilter.SIG_HDR, "sha256=deadbeef")
                        .content("{\"deviceId\":\"door-1\"}"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void validSignedRequest_isAccepted() throws Exception {
        String body = "{\"deviceId\":\"door-1\"}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = hmacService.sign(activeKey.getSecret(), timestamp, body.getBytes());

        mockMvc.perform(post("/api/integrations/events/door.unlocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IntegrationAuthFilter.KEY_ID_HDR, "rk_valid")
                        .header(IntegrationAuthFilter.TS_HDR, String.valueOf(timestamp))
                        .header(IntegrationAuthFilter.SIG_HDR, signature)
                        .header("X-Request-ID", "req-123")
                        .content(body))
               .andExpect(status().isOk());
    }

    @Test
    void disallowedEventType_returnsForbidden() throws Exception {
        IntegrationKey limitedKey = new IntegrationKey();
        limitedKey.setKeyId("rk_limited");
        limitedKey.setName("Limited key");
        limitedKey.setSecret("integration-secret");
        limitedKey.setActive(true);
        limitedKey.setAllowedEvents("[\"door.locked\"]");
        when(keyRepository.findByKeyIdAndActiveTrue("rk_limited")).thenReturn(Optional.of(limitedKey));

        String body = "{\"deviceId\":\"door-1\"}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = hmacService.sign(limitedKey.getSecret(), timestamp, body.getBytes());

        mockMvc.perform(post("/api/integrations/events/door.unlocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IntegrationAuthFilter.KEY_ID_HDR, "rk_limited")
                        .header(IntegrationAuthFilter.TS_HDR, String.valueOf(timestamp))
                        .header(IntegrationAuthFilter.SIG_HDR, signature)
                        .content(body))
               .andExpect(status().isForbidden());
    }

    @Test
    void requestPastRateLimit_returnsTooManyRequests() throws Exception {
        String body = "{\"deviceId\":\"door-1\"}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = hmacService.sign(activeKey.getSecret(), timestamp, body.getBytes());

        for (int i = 0; i < IntegrationRateLimiter.LIMIT; i++) {
            mockMvc.perform(post("/api/integrations/events/door.unlocked")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IntegrationAuthFilter.KEY_ID_HDR, "rk_limit")
                            .header(IntegrationAuthFilter.TS_HDR, String.valueOf(timestamp))
                            .header(IntegrationAuthFilter.SIG_HDR, signature)
                            .content(body))
                   .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/integrations/events/door.unlocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IntegrationAuthFilter.KEY_ID_HDR, "rk_limit")
                        .header(IntegrationAuthFilter.TS_HDR, String.valueOf(timestamp))
                        .header(IntegrationAuthFilter.SIG_HDR, signature)
                        .content(body))
               .andExpect(status().isTooManyRequests());
    }
}
