package com.reslife.api.domain.housing;

import com.reslife.api.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * File metadata for a document attached to a {@link HousingAgreement}.
 *
 * <p>Only metadata is stored here. The actual bytes live on disk at
 * {@code {upload-dir}/{agreementId}/{storedFilename}}.
 *
 * <p>{@code storedFilename} is always server-generated ({@code UUID.ext}),
 * never derived from user input, eliminating path-traversal risk.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agreement_attachments")
public class AgreementAttachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_id", nullable = false)
    private HousingAgreement agreement;

    /** Original filename as supplied by the client (display only). */
    @Column(name = "original_filename", nullable = false, columnDefinition = "TEXT")
    private String originalFilename;

    /** Server-generated {@code UUID.ext} — safe to use in filesystem paths. */
    @Column(name = "stored_filename", nullable = false, columnDefinition = "TEXT")
    private String storedFilename;

    /** Resolved MIME type: {@code application/pdf}, {@code image/jpeg}, or {@code image/png}. */
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    /** Username of the staff member who uploaded the file. */
    @Column(name = "uploaded_by", nullable = false, columnDefinition = "TEXT")
    private String uploadedBy;
}
