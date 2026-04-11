package com.reslife.api.domain.housing;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe API representation of an {@link AgreementAttachment}.
 * The {@code storedFilename} is intentionally excluded from this response.
 */
public record AttachmentResponse(
        UUID    id,
        UUID    agreementId,
        String  originalFilename,
        String  contentType,
        long    fileSizeBytes,
        String  uploadedBy,
        Instant uploadedAt
) {
    public static AttachmentResponse from(AgreementAttachment a) {
        return new AttachmentResponse(
                a.getId(),
                a.getAgreement().getId(),
                a.getOriginalFilename(),
                a.getContentType(),
                a.getFileSizeBytes(),
                a.getUploadedBy(),
                a.getCreatedAt()
        );
    }
}
