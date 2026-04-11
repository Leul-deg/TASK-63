package com.reslife.api.domain.integration;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of every API call handled by the integration layer
 * (both inbound events received from local systems and outbound webhook attempts).
 * Rows are never modified after insert.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "integration_audit_logs")
@EntityListeners(AuditingEntityListener.class)
public class IntegrationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_key_id")
    private IntegrationKey integrationKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Direction direction;

    @Column(name = "event_type", length = 100)
    private String eventType;

    /** Inbound: request path. Outbound: webhook target URL. */
    @Column(name = "target_url", length = 500)
    private String targetUrl;

    /** Inbound requests only — the caller's IP address. */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Value of the {@code X-Request-ID} header, if present. */
    @Column(name = "request_id", length = 100)
    private String requestId;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public enum Direction { INBOUND, OUTBOUND }
}
