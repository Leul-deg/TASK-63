package com.reslife.api.domain.housing;

import com.reslife.api.domain.resident.ResidentService;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.storage.AttachmentService;
import com.reslife.api.storage.StorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for housing agreements and their file attachments.
 *
 * <p>All endpoints are scoped under a resident for URL clarity, though the
 * service layer enforces ownership independently.
 *
 * <pre>
 * GET    /api/residents/{rId}/agreements
 * POST   /api/residents/{rId}/agreements
 * GET    /api/residents/{rId}/agreements/{aId}/attachments
 * POST   /api/residents/{rId}/agreements/{aId}/attachments       (multipart/form-data)
 * GET    /api/residents/{rId}/agreements/{aId}/attachments/{id}/content
 * DELETE /api/residents/{rId}/agreements/{aId}/attachments/{id}
 * </pre>
 */
@RestController
@RequestMapping("/api/residents/{residentId}/agreements")
public class HousingController {

    private static final String STAFF_ROLES =
            "hasAnyRole('ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR'," +
            "'RESIDENT_DIRECTOR','RESIDENT_ASSISTANT','RESIDENCE_STAFF','STAFF')";

    private final HousingService               housingService;
    private final ResidentService              residentService;
    private final AgreementAttachmentRepository attachmentRepository;
    private final AttachmentService            attachmentService;
    private final StorageService               storageService;

    public HousingController(HousingService housingService,
                             ResidentService residentService,
                             AgreementAttachmentRepository attachmentRepository,
                             AttachmentService attachmentService,
                             StorageService storageService) {
        this.housingService        = housingService;
        this.residentService       = residentService;
        this.attachmentRepository  = attachmentRepository;
        this.attachmentService     = attachmentService;
        this.storageService        = storageService;
    }

    // ── Agreements ────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize(STAFF_ROLES)
    public List<HousingAgreementResponse> listAgreements(@PathVariable UUID residentId) {
        residentService.findById(residentId); // 404 if not found
        return housingService.findAgreementsByResident(residentId).stream()
                .map(a -> HousingAgreementResponse.from(a,
                        attachmentRepository.countByAgreementId(a.getId())))
                .toList();
    }

    @PostMapping
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public HousingAgreementResponse createAgreement(
            @PathVariable UUID residentId,
            @Valid @RequestBody HousingAgreementRequest req) {
        HousingAgreement agreement = new HousingAgreement();
        agreement.setAgreementType(req.agreementType());
        agreement.setSignedDate(req.signedDate());
        agreement.setExpiresDate(req.expiresDate());
        agreement.setStatus(req.status() != null ? req.status() : AgreementStatus.PENDING);
        agreement.setVersion(req.version());
        agreement.setNotes(req.notes());
        HousingAgreement saved = housingService.createAgreement(residentId, agreement);
        return HousingAgreementResponse.from(saved, 0);
    }

    // ── Attachments — list ────────────────────────────────────────────────────

    @GetMapping("/{agreementId}/attachments")
    @PreAuthorize(STAFF_ROLES)
    public List<AttachmentResponse> listAttachments(
            @PathVariable UUID residentId,
            @PathVariable UUID agreementId) {
        verifyOwnership(residentId, agreementId);
        return attachmentService.findByAgreement(agreementId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    // ── Attachments — upload ──────────────────────────────────────────────────

    /**
     * Accepts a {@code multipart/form-data} POST with a single file part named {@code file}.
     *
     * <p>Validation (backend):
     * <ul>
     *   <li>MIME types allowed: {@code application/pdf}, {@code image/jpeg}, {@code image/png}</li>
     *   <li>Max size: 15 MB</li>
     *   <li>Magic-byte check: file header must match the declared type</li>
     * </ul>
     */
    @PostMapping(value = "/{agreementId}/attachments",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(
            @PathVariable UUID residentId,
            @PathVariable UUID agreementId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        verifyOwnership(residentId, agreementId);
        AgreementAttachment saved =
                attachmentService.upload(agreementId, file, principal.getUsername());
        return AttachmentResponse.from(saved);
    }

    // ── Attachments — download ────────────────────────────────────────────────

    /**
     * Streams the stored file with the original filename as the
     * {@code Content-Disposition} attachment header.
     */
    @GetMapping("/{agreementId}/attachments/{attachmentId}/content")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Resource> download(
            @PathVariable UUID residentId,
            @PathVariable UUID agreementId,
            @PathVariable UUID attachmentId) {
        verifyOwnership(residentId, agreementId);

        AgreementAttachment att = attachmentService.findById(attachmentId);
        if (!att.getAgreement().getId().equals(agreementId)) {
            throw new jakarta.persistence.EntityNotFoundException(
                    "Attachment not found for this agreement");
        }

        Resource resource = storageService.load(agreementId, att.getStoredFilename());

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(att.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(att.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    // ── Attachments — delete ──────────────────────────────────────────────────

    @DeleteMapping("/{agreementId}/attachments/{attachmentId}")
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(
            @PathVariable UUID residentId,
            @PathVariable UUID agreementId,
            @PathVariable UUID attachmentId) {
        verifyOwnership(residentId, agreementId);
        AgreementAttachment att = attachmentService.findById(attachmentId);
        if (!att.getAgreement().getId().equals(agreementId)) {
            throw new jakarta.persistence.EntityNotFoundException(
                    "Attachment not found for this agreement");
        }
        attachmentService.delete(attachmentId);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Ensures the agreement belongs to the resident in the URL path. */
    private void verifyOwnership(UUID residentId, UUID agreementId) {
        boolean owned = housingService.findAgreementsByResident(residentId).stream()
                .anyMatch(a -> a.getId().equals(agreementId));
        if (!owned) {
            throw new jakarta.persistence.EntityNotFoundException(
                    "Agreement not found for resident " + residentId);
        }
    }
}
