package com.reslife.api.domain.integration;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByIntegrationKeyIdOrderByCreatedAtDesc(UUID integrationKeyId);

    /**
     * Returns all active endpoints whose {@code eventTypes} JSON array contains
     * the given event type string.  Eagerly loads {@code integrationKey} to avoid
     * lazy-load issues when the session closes after dispatch.
     *
     * <p>Uses {@code LIKE %eventType%} — callers pass a quoted form such as
     * {@code "\"resident.updated\""} to prevent partial matches against
     * similar event names stored in the JSON array.
     */
    @EntityGraph(attributePaths = "integrationKey")
    List<WebhookEndpoint> findByActiveTrueAndIntegrationKeyActiveTrueAndEventTypesContaining(String eventType);
}
