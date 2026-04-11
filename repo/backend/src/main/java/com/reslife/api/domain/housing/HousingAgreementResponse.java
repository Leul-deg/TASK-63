package com.reslife.api.domain.housing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Safe API representation of a {@link HousingAgreement}.
 * Includes a pre-computed attachment count so the list view
 * doesn't need a separate request per agreement.
 */
public record HousingAgreementResponse(
        UUID            id,
        UUID            residentId,
        String          agreementType,
        LocalDate       signedDate,
        LocalDate       expiresDate,
        AgreementStatus status,
        String          version,
        String          notes,
        long            attachmentCount
) {
    public static HousingAgreementResponse from(HousingAgreement a, long attachmentCount) {
        return new HousingAgreementResponse(
                a.getId(),
                a.getResident().getId(),
                a.getAgreementType(),
                a.getSignedDate(),
                a.getExpiresDate(),
                a.getStatus(),
                a.getVersion(),
                a.getNotes(),
                attachmentCount
        );
    }
}
