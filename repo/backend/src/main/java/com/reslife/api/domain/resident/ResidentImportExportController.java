package com.reslife.api.domain.resident;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * REST endpoints for bulk CSV import and export of resident data.
 *
 * <pre>
 * POST /api/residents/import/preview   multipart — parse + validate, no DB writes
 * POST /api/residents/import/commit    JSON       — apply operator decisions
 * GET  /api/residents/export.csv                 — download all residents as CSV
 * </pre>
 *
 * <p>All endpoints require staff or admin role.
 */
@RestController
@RequestMapping("/api/residents")
public class ResidentImportExportController {

    private static final String STAFF_ROLES =
            "hasAnyRole('ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR'," +
            "'RESIDENT_DIRECTOR','RESIDENT_ASSISTANT','RESIDENCE_STAFF','STAFF')";

    private final ResidentImportExportService importExportService;

    public ResidentImportExportController(ResidentImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    // ── Import: preview ───────────────────────────────────────────────────────

    /**
     * Parses a CSV upload and returns per-row validation and duplicate results.
     *
     * <p>No records are created or modified. The response is used to populate
     * the preview table in the UI.
     *
     * @param file  a UTF-8 CSV with the header row:
     *              {@code studentId, firstName, lastName, email, phone, dateOfBirth,
     *              enrollmentStatus, department, classYear, roomNumber, buildingName}
     */
    @PostMapping(value = "/import/preview",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(STAFF_ROLES)
    public ImportPreviewResponse preview(
            @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
        String contentType = file.getContentType();
        // Accept text/csv, text/plain, application/vnd.ms-excel (Excel CSV export), or blank
        if (contentType != null && !contentType.isBlank()
                && !contentType.startsWith("text/")
                && !contentType.equals("application/vnd.ms-excel")
                && !contentType.equals("application/octet-stream")) {
            throw new IllegalArgumentException(
                    "Expected a CSV file (text/csv), got: " + contentType);
        }
        return importExportService.preview(file);
    }

    // ── Import: commit ────────────────────────────────────────────────────────

    /**
     * Commits the operator's per-row decisions.
     *
     * <p>Each row is processed independently; failures are returned in the
     * response rather than aborting the whole batch.
     */
    @PostMapping("/import/commit")
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.OK)
    public ImportCommitResponse commit(
            @Valid @RequestBody ImportCommitRequest req) {
        return importExportService.commit(req);
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Streams all active residents as a UTF-8 CSV attachment.
     *
     * <p>Date-of-birth is included in plain text (decrypted by JPA).
     * The endpoint is restricted to staff/admin roles, so sensitivity
     * concerns are handled at the access-control layer.
     */
    @GetMapping("/export.csv")
    @PreAuthorize(STAFF_ROLES)
    public void exportCsv(HttpServletResponse response) throws IOException {
        String filename = "residents-" + LocalDate.now() + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        // UTF-8 BOM so Excel opens the file correctly without re-encoding
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        try (Writer writer = new OutputStreamWriter(
                response.getOutputStream(), StandardCharsets.UTF_8)) {
            importExportService.exportToCsv(writer);
        }
    }
}
