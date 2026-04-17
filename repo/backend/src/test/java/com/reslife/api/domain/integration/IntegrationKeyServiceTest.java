package com.reslife.api.domain.integration;

import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationKeyServiceTest {

    private final IntegrationKeyRepository      keyRepo   = mock(IntegrationKeyRepository.class);
    private final WebhookEndpointRepository     webhookRepo = mock(WebhookEndpointRepository.class);
    private final IntegrationAuditLogRepository auditRepo   = mock(IntegrationAuditLogRepository.class);
    private final LocalNetworkValidator         localNetworkValidator = mock(LocalNetworkValidator.class);
    private final UserService                   userService = mock(UserService.class);

    private IntegrationKeyService service;

    private static final UUID KEY_ID     = UUID.randomUUID();
    private static final UUID WEBHOOK_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new IntegrationKeyService(
                keyRepo, webhookRepo, auditRepo, localNetworkValidator, userService);
        when(keyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(webhookRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private IntegrationKey mockKey() {
        IntegrationKey k = mock(IntegrationKey.class);
        when(k.getId()).thenReturn(KEY_ID);
        when(k.getKeyId()).thenReturn("rk_abc123");
        when(k.getName()).thenReturn("Test Key");
        when(k.getDescription()).thenReturn("desc");
        when(k.getSecret()).thenReturn("plaintextsecret=");
        when(k.getSecretPrefix()).thenReturn("plaintex");
        when(k.getAllowedEvents()).thenReturn(List.of());
        when(k.isActive()).thenReturn(true);
        return k;
    }

    // ── getKey ────────────────────────────────────────────────────────────────

    @Test
    void getKey_returnsKeyResponse_whenFound() {
        when(keyRepo.findById(KEY_ID)).thenReturn(Optional.of(mockKey()));

        KeyResponse response = service.getKey(KEY_ID);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test Key");
    }

    @Test
    void getKey_throwsEntityNotFoundException_whenNotFound() {
        when(keyRepo.findById(KEY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getKey(KEY_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(KEY_ID.toString());
    }

    // ── createKey ─────────────────────────────────────────────────────────────

    @Test
    void createKey_returnsPlaintextSecretExactlyOnce() {
        when(keyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateKeyRequest req = new CreateKeyRequest("My Key", "desc", List.of("RESIDENT_CREATED"));
        CreateKeyResponse response = service.createKey(req, null);

        assertThat(response.name()).isEqualTo("My Key");
        assertThat(response.plaintext()).isNotBlank();
        assertThat(response.plaintext().length()).isGreaterThan(10);
        assertThat(response.keyId()).startsWith("rk_");
    }

    @Test
    void createKey_linksActorUser_whenActorIdProvided() {
        User actor = new User();
        when(userService.findById(ACTOR_ID)).thenReturn(actor);
        when(keyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createKey(new CreateKeyRequest("Key", null, List.of()), ACTOR_ID);

        verify(userService).findById(ACTOR_ID);
    }

    @Test
    void createKey_skipsActorLookup_whenActorIdIsNull() {
        when(keyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createKey(new CreateKeyRequest("Key", null, List.of()), null);

        verify(userService, never()).findById(any());
    }

    // ── updateKey ────────────────────────────────────────────────────────────

    @Test
    void updateKey_updatesNameAndDescriptionOnExistingKey() {
        IntegrationKey k = mockKey();
        when(keyRepo.findById(KEY_ID)).thenReturn(Optional.of(k));
        when(keyRepo.save(k)).thenReturn(k);

        service.updateKey(KEY_ID, new CreateKeyRequest("Updated Name", "new desc", List.of()));

        verify(k).setName("Updated Name");
        verify(k).setDescription("new desc");
        verify(keyRepo).save(k);
    }

    // ── revokeKey ────────────────────────────────────────────────────────────

    @Test
    void revokeKey_callsRevokeAndPersists() {
        IntegrationKey k = mockKey();
        when(keyRepo.findById(KEY_ID)).thenReturn(Optional.of(k));
        when(keyRepo.save(k)).thenReturn(k);

        service.revokeKey(KEY_ID, "No longer needed");

        verify(k).revoke("No longer needed");
        verify(keyRepo).save(k);
    }

    // ── addWebhook ────────────────────────────────────────────────────────────

    @Test
    void addWebhook_throwsWhenTargetUrlIsNotLocalNetwork() {
        when(keyRepo.findById(KEY_ID)).thenReturn(Optional.of(mockKey()));
        doThrow(new IllegalArgumentException("Public IP not allowed"))
                .when(localNetworkValidator).requireLocalTarget(any());

        assertThatThrownBy(() -> service.addWebhook(KEY_ID,
                new CreateWebhookRequest("wh", "http://evil.example.com", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void addWebhook_returnsSigningSecret_whenLocalUrl() {
        when(keyRepo.findById(KEY_ID)).thenReturn(Optional.of(mockKey()));
        doNothing().when(localNetworkValidator).requireLocalTarget(any());

        CreateWebhookResponse response = service.addWebhook(KEY_ID,
                new CreateWebhookRequest("Internal Hook", "http://10.0.0.5:9000/hook", List.of()));

        assertThat(response.signingSecret()).isNotBlank();
        assertThat(response.name()).isEqualTo("Internal Hook");
    }

    // ── deleteWebhook ─────────────────────────────────────────────────────────

    @Test
    void deleteWebhook_removesEndpointFromRepository() {
        IntegrationKey k = mockKey();
        WebhookEndpoint endpoint = mock(WebhookEndpoint.class);
        when(endpoint.getIntegrationKey()).thenReturn(k);
        when(webhookRepo.findById(WEBHOOK_ID)).thenReturn(Optional.of(endpoint));

        service.deleteWebhook(KEY_ID, WEBHOOK_ID);

        verify(webhookRepo).delete(endpoint);
    }

    @Test
    void deleteWebhook_throwsWhenWebhookBelongsToDifferentKey() {
        IntegrationKey otherKey = mock(IntegrationKey.class);
        when(otherKey.getId()).thenReturn(UUID.randomUUID());

        WebhookEndpoint endpoint = mock(WebhookEndpoint.class);
        when(endpoint.getIntegrationKey()).thenReturn(otherKey);
        when(webhookRepo.findById(WEBHOOK_ID)).thenReturn(Optional.of(endpoint));

        assertThatThrownBy(() -> service.deleteWebhook(KEY_ID, WEBHOOK_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
