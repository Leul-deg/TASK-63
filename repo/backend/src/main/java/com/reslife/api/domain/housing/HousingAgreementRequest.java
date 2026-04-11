package com.reslife.api.domain.housing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Input for creating or updating a {@link HousingAgreement}.
 */
public record HousingAgreementRequest(

        @NotBlank(message = "Agreement type is required")
        @Size(max = 100, message = "Agreement type must be 100 characters or fewer")
        String agreementType,

        LocalDate signedDate,

        LocalDate expiresDate,

        AgreementStatus status,

        @Size(max = 20)
        String version,

        String notes

) {}
