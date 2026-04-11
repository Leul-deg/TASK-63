package com.reslife.api.domain.integration;

import com.reslife.api.common.BaseEntity;
import com.reslife.api.encryption.StringEncryptionConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An outgoing webhook target registered under an {@link IntegrationKey}.
 *
 * <p>The {@link #targetUrl} must resolve to a private/local IP address
 * (enforced by {@link LocalNetworkValidator} at creation time).
 *
 * <p>The {@link #signingSecret} is used to sign every outgoing payload so the
 * receiving system can verify the request came from ResLife.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_key_id", nullable = false)
    private IntegrationKey integrationKey;

    @Column(nullable = false, length = 255)
    private String name;

    /** Local-network URL that receives POST requests for subscribed events. */
    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl;

    /**
     * JSON array of event types this endpoint subscribes to,
     * e.g. {@code ["resident.updated","booking.cancelled"]}.
     */
    @Column(name = "event_types", nullable = false, columnDefinition = "TEXT")
    private String eventTypes;

    /** Encrypted secret used to sign outgoing payloads. */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(name = "signing_secret", nullable = false, columnDefinition = "TEXT")
    private String signingSecret;

    /** First 8 plaintext characters — shown in UI for verification. */
    @Column(name = "signing_secret_prefix", nullable = false, length = 10)
    private String signingSecretPrefix;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
