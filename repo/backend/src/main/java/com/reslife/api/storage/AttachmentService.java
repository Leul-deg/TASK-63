package com.reslife.api.storage;

import com.reslife.api.domain.housing.AgreementAttachment;
import com.reslife.api.domain.housing.AgreementAttachmentRepository;
import com.reslife.api.domain.housing.HousingAgreement;
import com.reslife.api.domain.housing.HousingAgreementRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates file-upload validation, physical storage, and metadata persistence
 * for housing-agreement attachments.
 *
 * <h3>Validation chain (backend)</h3>
 * <ol>
 *   <li>File must not be empty.</li>
 *   <li>File size ≤ {@value #MAX_SIZE_BYTES} bytes (15 MB).</li>
 *   <li>Filename extension must be one of: {@code pdf}, {@code jpg}, {@code jpeg}, {@code png}.</li>
 *   <li>Declared MIME type must agree with the extension.</li>
 *   <li>First bytes of the file (magic bytes) must match the declared type — prevents
 *       disguised executables uploaded with a PDF extension.</li>
 * </ol>
 */
@Service
public class AttachmentService {

    static final long MAX_SIZE_BYTES = 15L * 1024 * 1024; // 15 MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    /** Maps each allowed extension to its canonical MIME type. */
    private static final Map<String, String> EXT_TO_MIME = Map.of(
            "pdf",  "application/pdf",
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "png",  "image/png"
    );

    private final HousingAgreementRepository  agreementRepository;
    private final AgreementAttachmentRepository attachmentRepository;
    private final StorageService               storageService;

    public AttachmentService(HousingAgreementRepository agreementRepository,
                             AgreementAttachmentRepository attachmentRepository,
                             StorageService storageService) {
        this.agreementRepository  = agreementRepository;
        this.attachmentRepository = attachmentRepository;
        this.storageService       = storageService;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<AgreementAttachment> findByAgreement(UUID agreementId) {
        requireAgreement(agreementId);
        return attachmentRepository.findByAgreementIdOrderByCreatedAtAsc(agreementId);
    }

    public AgreementAttachment findById(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Attachment not found: " + attachmentId));
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Validates, stores, and records an uploaded file.
     *
     * @param agreementId  the agreement this file belongs to
     * @param file         the multipart upload
     * @param uploadedBy   username of the authenticated uploader
     * @return the persisted metadata record
     * @throws IllegalArgumentException if any validation check fails
     */
    @Transactional
    public AgreementAttachment upload(UUID agreementId, MultipartFile file, String uploadedBy) {
        HousingAgreement agreement = requireAgreement(agreementId);

        // 1 — size
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "File exceeds the 15 MB size limit (" +
                    toMb(file.getSize()) + " MB uploaded)");
        }

        // 2 — extension
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "File type not allowed. Accepted formats: PDF, JPG, PNG");
        }

        // 3 — MIME type cross-check
        String resolvedMime = EXT_TO_MIME.get(extension);
        String declaredMime = file.getContentType();
        if (declaredMime != null && !declaredMime.isEmpty()
                && !resolvedMime.equals(declaredMime)
                // image/jpg is a common non-standard alias
                && !(resolvedMime.equals("image/jpeg") && declaredMime.equals("image/jpg"))) {
            throw new IllegalArgumentException(
                    "File extension and content type do not match");
        }

        // 4 — magic bytes (read first 8 bytes only)
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(8);
            if (!hasValidMagicBytes(header, resolvedMime)) {
                throw new IllegalArgumentException(
                        "File content does not match the declared type. " +
                        "Make sure you are uploading a valid PDF, JPEG, or PNG.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file for validation", e);
        }

        // 5 — persist to disk
        String storedFilename = UUID.randomUUID() + "." + extension;
        storageService.store(agreementId, storedFilename, file);

        // 6 — persist metadata
        AgreementAttachment attachment = new AgreementAttachment();
        attachment.setAgreement(agreement);
        attachment.setOriginalFilename(originalFilename);
        attachment.setStoredFilename(storedFilename);
        attachment.setContentType(resolvedMime);
        attachment.setFileSizeBytes(file.getSize());
        attachment.setUploadedBy(uploadedBy);
        return attachmentRepository.save(attachment);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes the metadata record and the physical file.
     *
     * <p>The DB row is removed in the current transaction.  Disk deletion
     * happens after the method returns (best-effort — a failure is logged
     * but not re-thrown because the DB record is the source of truth).
     */
    @Transactional
    public void delete(UUID attachmentId) {
        AgreementAttachment att = findById(attachmentId);
        UUID agreementId    = att.getAgreement().getId();
        String storedFilename = att.getStoredFilename();

        attachmentRepository.delete(att);
        // Flush so the DB delete commits before we touch the filesystem
        attachmentRepository.flush();

        storageService.delete(agreementId, storedFilename);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HousingAgreement requireAgreement(UUID id) {
        return agreementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Agreement not found: " + id));
    }

    /** Strips leading path components and null-checks. */
    private static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "upload";
        return StringUtils.cleanPath(raw).replaceAll("[^\\w.\\-]", "_");
    }

    /** Returns the lowercase extension, or empty string if none. */
    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    /**
     * Checks the leading bytes of the file against known magic byte sequences.
     *
     * <ul>
     *   <li>PDF  : {@code %PDF} (25 50 44 46)</li>
     *   <li>JPEG : {@code FF D8 FF}</li>
     *   <li>PNG  : {@code 89 50 4E 47 0D 0A 1A 0A}</li>
     * </ul>
     */
    static boolean hasValidMagicBytes(byte[] header, String mimeType) {
        if (header.length < 4) return false;
        return switch (mimeType) {
            case "application/pdf" ->
                    header[0] == 0x25   // '%'
                 && header[1] == 0x50   // 'P'
                 && header[2] == 0x44   // 'D'
                 && header[3] == 0x46;  // 'F'
            case "image/jpeg" ->
                    (header[0] & 0xFF) == 0xFF
                 && (header[1] & 0xFF) == 0xD8
                 && (header[2] & 0xFF) == 0xFF;
            case "image/png" ->
                    header.length >= 8
                 && (header[0] & 0xFF) == 0x89
                 && header[1] == 0x50   // 'P'
                 && header[2] == 0x4E   // 'N'
                 && header[3] == 0x47   // 'G'
                 && header[4] == 0x0D
                 && header[5] == 0x0A
                 && header[6] == 0x1A
                 && header[7] == 0x0A;
            default -> false;
        };
    }

    private static String toMb(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024.0));
    }
}
