package com.reslife.api.domain.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceTest {

    private final WebhookEndpointRepository endpointRepo = mock(WebhookEndpointRepository.class);
    private final WebhookDeliveryRepository deliveryRepo = mock(WebhookDeliveryRepository.class);
    private final IntegrationAuditLogRepository auditRepo = mock(IntegrationAuditLogRepository.class);
    private final HmacService hmacService = new HmacService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final LocalNetworkValidator localNetworkValidator = mock(LocalNetworkValidator.class);

    private WebhookService service;
    private WebhookEndpoint endpoint;

    @BeforeEach
    void setUp() {
        service = new WebhookService(
                endpointRepo,
                deliveryRepo,
                auditRepo,
                hmacService,
                objectMapper,
                restTemplate,
                localNetworkValidator);

        IntegrationKey key = new IntegrationKey();
        key.setId(UUID.randomUUID());
        key.setKeyId("rk_test");
        key.setSecret("integration-secret");

        endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setIntegrationKey(key);
        endpoint.setName("Local board");
        endpoint.setTargetUrl("http://board.local/webhook");
        endpoint.setEventTypes("[\"resident.updated\"]");
        endpoint.setSigningSecret("webhook-secret");
        endpoint.setSigningSecretPrefix("webhook");
        endpoint.setActive(true);

        when(endpointRepo.findByActiveTrueAndIntegrationKeyActiveTrueAndEventTypesContaining(
                contains("\"resident.updated\"")))
                .thenReturn(List.of(endpoint));
        when(restTemplate.postForEntity(any(String.class), any(), any(Class.class)))
                .thenReturn(ResponseEntity.ok("ok"));
        when(deliveryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dispatch_revalidatesTargetBeforeSending() {
        assertDoesNotThrow(() -> service.dispatch("resident.updated", java.util.Map.of("id", "resident-1")));

        verify(localNetworkValidator).requireLocalTarget("http://board.local/webhook");
        verify(restTemplate).postForEntity(any(String.class), any(), any(Class.class));
    }

    @Test
    void dispatch_doesNotSendWhenTargetFailsLocalValidation() {
        doThrow(new IllegalArgumentException("public address"))
                .when(localNetworkValidator).requireLocalTarget("http://board.local/webhook");

        assertDoesNotThrow(() -> service.dispatch("resident.updated", java.util.Map.of("id", "resident-1")));

        verify(localNetworkValidator).requireLocalTarget("http://board.local/webhook");
        verify(restTemplate, never()).postForEntity(any(String.class), any(), any(Class.class));
    }
}
