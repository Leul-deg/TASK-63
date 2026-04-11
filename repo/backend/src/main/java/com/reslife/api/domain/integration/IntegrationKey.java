package com.reslife.api.domain.integration;

import com.reslife.api.common.BaseEntity;
import com.reslife.api.domain.user.User;
import com.reslife.api.encryption.StringEncryptionConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An API key issued to a local on-prem system for integration access.
 *
 * <p>The HMAC secret is encrypted at rest via {@link StringEncryptionConverter}.
 * The plaintext secret is shown to the admin exactly once (at creation) and
 * never stored in plaintext form.  The {@link #secretPrefix} (first 8 chars)
 * is kept in plaintext for UI identification.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "integration_keys",
    uniqueConstraints = @UniqueConstraint(name = "uq_integration_keys_key_id", columnNames = "key_id")
)
public class IntegrationKey extends BaseEntity {

    /** Public identifier sent in the {@code X-Integration-Key-ID} header. */
    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Encrypted HMAC secret. Use {@link StringEncryptionConverter} to decrypt for signing. */
    @Convert(converter = StringEncryptionConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String secret;

    /** First 8 plaintext characters of the secret — safe to display, useless for attacks. */
    @Column(name = "secret_prefix", nullable = false, length = 10)
    private String secretPrefix;

    /**
     * JSON array of allowed event types, e.g. {@code ["resident.created","booking.cancelled"]}.
     * {@code null} means the key is unrestricted.
     */
    @Column(name = "allowed_events", columnDefinition = "TEXT")
    private String allowedEvents;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", columnDefinition = "TEXT")
    private String revokedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @OneToMany(mappedBy = "integrationKey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WebhookEndpoint> webhookEndpoints = new ArrayList<>();

    /** Deactivates this key and records who revoked it and why. */
    public void revoke(String reason) {
        this.active        = false;
        this.revokedAt     = Instant.now();
        this.revokedReason = reason;
    }
}
