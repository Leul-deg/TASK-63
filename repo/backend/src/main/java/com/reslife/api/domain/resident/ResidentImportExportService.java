package com.reslife.api.domain.resident;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles bulk CSV import (parse → preview → commit) and export for
 * resident records.
 *
 * <h3>Import duplicate rules</h3>
 * <ol>
 *   <li><b>Primary</b>: exact studentId match → definitive match, shown as
 *       {@link ImportRowPreview.RowStatus#MERGE_CANDIDATE}</li>
 *   <li><b>Secondary</b>: case-insensitive first+last name AND matching DOB
 *       (both must be non-blank) → probable match, shown as
 *       {@link ImportRowPreview.RowStatus#MERGE_CANDIDATE}</li>
 * </ol>
 *
 * <h3>Cross-row conflict detection</h3>
 * <ul>
 *   <li>Two rows with the same email → both marked {@link ImportRowPreview.RowStatus#INVALID}</li>
 *   <li>Two rows with the same studentId → second marked INVALID</li>
 * </ul>
 */
@Service
public class ResidentImportExportService {

    private static final Logger log = LoggerFactory.getLogger(ResidentImportExportService.class);

    /** CSV column headers — same for import and export. */
    static final String[] CSV_HEADERS = {
        "studentId", "firstName", "lastName", "email", "phone",
        "dateOfBirth", "enrollmentStatus", "department",
        "classYear", "roomNumber", "buildingName"
    };

    private static final Pattern PHONE_RE = Pattern.compile("^\\d{3}-\\d{3}-\\d{4}$");
    private static final int MAX_ROWS = 5_000;

    private final ResidentRepository residentRepository;
    private final ResidentService    residentService;

    public ResidentImportExportService(ResidentRepository residentRepository,
                                       ResidentService residentService) {
        this.residentRepository = residentRepository;
        this.residentService    = residentService;
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Writes all active residents to the given writer as CSV.
     *
     * <p>The output uses Unix line endings and is UTF-8 encoded.
     * Date-of-birth is included in plain text (endpoint is staff-only).
     */
    public void exportToCsv(Writer writer) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(CSV_HEADERS)
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Resident r : residentRepository.findAll()) {
                printer.printRecord(
                        r.getStudentId(),
                        r.getFirstName(),
                        r.getLastName(),
                        r.getEmail(),
                        r.getPhone(),
                        r.getDateOfBirth() != null ? r.getDateOfBirth().toString() : "",
                        r.getEnrollmentStatus(),
                        r.getDepartment(),
                        r.getClassYear() != null ? r.getClassYear().toString() : "",
                        r.getRoomNumber(),
                        r.getBuildingName()
                );
            }
        }
    }

    // ── Import — preview ──────────────────────────────────────────────────────

    /**
     * Parses and validates a CSV file without writing anything to the database.
     *
     * @param csvFile  the uploaded file (must be UTF-8 or ASCII CSV)
     * @return a preview that the frontend displays for operator review
     */
    public ImportPreviewResponse preview(MultipartFile csvFile) throws IOException {
        List<ImportRowData> rawRows = parseCsv(csvFile);
        List<ImportRowPreview> previews = buildPreviews(rawRows);

        long newCount    = previews.stream().filter(r -> r.status() == ImportRowPreview.RowStatus.NEW).count();
        long mergeCount  = previews.stream().filter(r -> r.status() == ImportRowPreview.RowStatus.MERGE_CANDIDATE).count();
        long invalidCount= previews.stream().filter(r -> r.status() == ImportRowPreview.RowStatus.INVALID).count();

        return new ImportPreviewResponse(
                rawRows.size(),
                (int) newCount,
                (int) mergeCount,
                (int) invalidCount,
                previews
        );
    }

    /** Parses the CSV file and returns raw row data; throws on structural errors. */
    private List<ImportRowData> parseCsv(MultipartFile csvFile) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(CSV_HEADERS)
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();

        List<ImportRowData> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {

            for (CSVRecord rec : parser) {
                if (rows.size() >= MAX_ROWS) {
                    throw new IllegalArgumentException(
                            "CSV exceeds the " + MAX_ROWS + "-row limit. " +
                            "Split the file into smaller batches.");
                }
                rows.add(new ImportRowData(
                        col(rec, "studentId"),
                        col(rec, "firstName"),
                        col(rec, "lastName"),
                        col(rec, "email"),
                        col(rec, "phone"),
                        col(rec, "dateOfBirth"),
                        col(rec, "enrollmentStatus"),
                        col(rec, "department"),
                        col(rec, "classYear"),
                        col(rec, "roomNumber"),
                        col(rec, "buildingName")
                ));
            }
        } catch (IllegalArgumentException e) {
            throw e; // re-throw our limit error
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not parse CSV: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV file has no data rows.");
        }
        return rows;
    }

    private static String col(CSVRecord rec, String name) {
        try {
            String v = rec.get(name);
            return v == null ? "" : v.strip();
        } catch (IllegalArgumentException e) {
            return ""; // column missing → treat as blank
        }
    }

    /**
     * Validates all rows, detects cross-row conflicts, and resolves
     * duplicate matches against the database.
     */
    private List<ImportRowPreview> buildPreviews(List<ImportRowData> rows) {
        // 1 — per-row field validation
        List<List<String>> rowErrors = rows.stream()
                .map(this::validateRow)
                .collect(Collectors.toList());

        // 2 — cross-row email conflicts for rows that are not the same student
        detectCrossRowConflicts(rows, rowErrors);

        // 3 — build previews (duplicate detection happens per row)
        List<ImportRowPreview> previews = new ArrayList<>(rows.size());

        for (int i = 0; i < rows.size(); i++) {
            ImportRowData data = rows.get(i);
            List<String> errors = rowErrors.get(i);

            ImportRowPreview.ExistingMatch match = null;
            if (errors.isEmpty()) {
                match = findDuplicateInBatch(rows, rowErrors, i);
                if (match == null) {
                    match = findDuplicateInDb(data);
                }
            }

            ImportRowPreview.RowStatus status;
            if (!errors.isEmpty()) {
                status = ImportRowPreview.RowStatus.INVALID;
            } else if (match != null) {
                status = ImportRowPreview.RowStatus.MERGE_CANDIDATE;
            } else {
                status = ImportRowPreview.RowStatus.NEW;
            }

            previews.add(new ImportRowPreview(i + 1, status, data, List.copyOf(errors), match));
        }
        return previews;
    }

    /**
     * Field-level validation for a single row.
     * Returns a mutable list of error messages (empty = valid).
     */
    private List<String> validateRow(ImportRowData d) {
        List<String> errors = new ArrayList<>();

        if (d.firstName().isBlank())  errors.add("firstName is required.");
        else if (d.firstName().length() > 100) errors.add("firstName must be 100 characters or fewer.");

        if (d.lastName().isBlank())   errors.add("lastName is required.");
        else if (d.lastName().length() > 100) errors.add("lastName must be 100 characters or fewer.");

        if (d.email().isBlank()) {
            errors.add("email is required.");
        } else if (!d.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            errors.add("email '" + d.email() + "' is not a valid address.");
        } else if (d.email().length() > 255) {
            errors.add("email must be 255 characters or fewer.");
        }

        if (!d.phone().isBlank() && !PHONE_RE.matcher(d.phone()).matches()) {
            errors.add("phone must use format 555-123-4567 (got '" + d.phone() + "').");
        }

        if (!d.studentId().isBlank() && d.studentId().length() > 50) {
            errors.add("studentId must be 50 characters or fewer.");
        }

        if (!d.dateOfBirth().isBlank()) {
            try {
                LocalDate dob = LocalDate.parse(d.dateOfBirth());
                if (!dob.isBefore(LocalDate.now())) {
                    errors.add("dateOfBirth must be in the past.");
                }
            } catch (DateTimeParseException e) {
                errors.add("dateOfBirth '" + d.dateOfBirth() + "' is not a valid date (expected YYYY-MM-DD).");
            }
        }

        if (!d.classYear().isBlank()) {
            try {
                int cy = Integer.parseInt(d.classYear());
                if (cy < 1900 || cy > 2100) errors.add("classYear must be between 1900 and 2100.");
            } catch (NumberFormatException e) {
                errors.add("classYear '" + d.classYear() + "' is not a valid integer.");
            }
        }

        return errors;
    }

    /** Marks conflicting duplicate emails as INVALID in place (both rows get an error). */
    private void detectCrossRowConflicts(List<ImportRowData> rows, List<List<String>> rowErrors) {
        // email → first rowIndex that used it (among rows without other errors yet)
        Map<String, Integer> emailIndex = new LinkedHashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            String email = rows.get(i).email().toLowerCase();
            if (email.isBlank()) continue;

            if (emailIndex.containsKey(email)) {
                int first = emailIndex.get(email);
                if (isSameBatchDuplicate(rows.get(first), rows.get(i))) {
                    continue;
                }
                String msg = "Duplicate email '" + rows.get(i).email() + "' also appears in row " +
                             (first + 1) + " of this file.";
                rowErrors.get(i).add(msg);
                if (!rowErrors.get(first).contains(msg.replace("row " + (first + 1), "row " + (i + 1)))) {
                    rowErrors.get(first).add("Duplicate email '" + email + "' also appears in row " + (i + 1) + " of this file.");
                }
            } else {
                emailIndex.put(email, i);
            }
        }
    }

    /**
     * Checks earlier rows in this upload for the same student using the prompt's
     * matching hierarchy: primary studentId, secondary name + DOB.
     */
    private ImportRowPreview.ExistingMatch findDuplicateInBatch(
            List<ImportRowData> rows,
            List<List<String>> rowErrors,
            int rowIndex) {
        ImportRowData current = rows.get(rowIndex);
        for (int i = 0; i < rowIndex; i++) {
            if (!rowErrors.get(i).isEmpty()) continue;
            ImportRowData candidate = rows.get(i);
            String matchReason = batchMatchReason(candidate, current);
            if (matchReason != null) {
                return toBatchMatch(candidate, i + 1, matchReason);
            }
        }
        return null;
    }

    /**
     * Checks the database for a duplicate match for the given row.
     *
     * <ul>
     *   <li>Primary: exact studentId match</li>
     *   <li>Secondary: case-insensitive name + DOB (both must be non-blank)</li>
     * </ul>
     *
     * @return the first match found, or {@code null}
     */
    private ImportRowPreview.ExistingMatch findDuplicateInDb(ImportRowData d) {
        // Primary: studentId
        if (!d.studentId().isBlank()) {
            Optional<Resident> byId = residentRepository.findByStudentId(d.studentId());
            if (byId.isPresent()) {
                return toMatch(byId.get(), "studentId");
            }
        }

        // Secondary: name + DOB (both must be present)
        if (!d.firstName().isBlank() && !d.lastName().isBlank() && !d.dateOfBirth().isBlank()) {
            LocalDate csvDob;
            try { csvDob = LocalDate.parse(d.dateOfBirth()); }
            catch (DateTimeParseException e) { return null; }

            List<Resident> namePeers = residentRepository
                    .findByFirstNameIgnoreCaseAndLastNameIgnoreCase(d.firstName(), d.lastName());
            for (Resident r : namePeers) {
                if (csvDob.equals(r.getDateOfBirth())) {
                    return toMatch(r, "name+dob");
                }
            }
        }

        return null;
    }

    private static ImportRowPreview.ExistingMatch toMatch(Resident r, String reason) {
        return new ImportRowPreview.ExistingMatch(
                r.getId(), r.getStudentId(), r.getFirstName(), r.getLastName(),
                r.getEmail(), reason, null);
    }

    private static ImportRowPreview.ExistingMatch toBatchMatch(
            ImportRowData row, int sourceRowNumber, String reason) {
        return new ImportRowPreview.ExistingMatch(
                null,
                row.studentId().isBlank() ? null : row.studentId(),
                row.firstName(),
                row.lastName(),
                row.email(),
                reason,
                sourceRowNumber
        );
    }

    private static boolean isSameBatchDuplicate(ImportRowData left, ImportRowData right) {
        return batchMatchReason(left, right) != null;
    }

    private static String batchMatchReason(ImportRowData left, ImportRowData right) {
        if (!left.studentId().isBlank()
                && !right.studentId().isBlank()
                && left.studentId().equalsIgnoreCase(right.studentId())) {
            return "studentId";
        }

        if (!left.firstName().isBlank()
                && !left.lastName().isBlank()
                && !left.dateOfBirth().isBlank()
                && !right.firstName().isBlank()
                && !right.lastName().isBlank()
                && !right.dateOfBirth().isBlank()
                && left.firstName().equalsIgnoreCase(right.firstName())
                && left.lastName().equalsIgnoreCase(right.lastName())
                && left.dateOfBirth().equals(right.dateOfBirth())) {
            return "name+dob";
        }

        return null;
    }

    // ── Import — commit ───────────────────────────────────────────────────────

    /**
     * Commits the operator's decisions to the database.
     *
     * <p>Each row is processed independently — one failure does not abort
     * the rest. The backend re-validates all CREATE and MERGE rows to guard
     * against race conditions between preview and commit.
     */
    public ImportCommitResponse commit(ImportCommitRequest req) {
        int created = 0, merged = 0, skipped = 0;
        List<ImportCommitResponse.RowFailure> failures = new ArrayList<>();
        Map<Integer, UUID> resolvedRows = new HashMap<>();

        List<ImportCommitRequest.CommitDecision> decisions = new ArrayList<>(req.rows());
        decisions.sort(Comparator.comparingInt(ImportCommitRequest.CommitDecision::rowNumber));

        for (ImportCommitRequest.CommitDecision decision : decisions) {
            try {
                switch (decision.action()) {
                    case SKIP -> skipped++;
                    case CREATE -> {
                        ResidentRequest resReq = toResidentRequest(decision.data());
                        Resident createdResident = residentService.create(resReq, true); // force=true: preview already checked
                        resolvedRows.put(decision.rowNumber(), createdResident.getId());
                        created++;
                    }
                    case MERGE -> {
                        UUID targetId = resolveMergeTargetId(decision, resolvedRows);
                        ResidentRequest resReq = toResidentRequest(decision.data());
                        residentService.update(targetId, resReq);
                        resolvedRows.put(decision.rowNumber(), targetId);
                        merged++;
                    }
                }
            } catch (Exception e) {
                log.warn("Import commit failed for row {}: {}", decision.rowNumber(), e.getMessage());
                failures.add(new ImportCommitResponse.RowFailure(decision.rowNumber(), e.getMessage()));
            }
        }

        return new ImportCommitResponse(created, merged, skipped, failures.size(), failures);
    }

    private static UUID resolveMergeTargetId(
            ImportCommitRequest.CommitDecision decision,
            Map<Integer, UUID> resolvedRows) {
        if (decision.mergeTargetId() != null) {
            return decision.mergeTargetId();
        }
        if (decision.mergeTargetRowNumber() != null) {
            UUID resolved = resolvedRows.get(decision.mergeTargetRowNumber());
            if (resolved == null) {
                throw new IllegalArgumentException(
                        "Cannot merge row " + decision.rowNumber()
                                + " because target row " + decision.mergeTargetRowNumber()
                                + " did not resolve to a resident.");
            }
            return resolved;
        }
        throw new IllegalArgumentException(
                "mergeTargetId or mergeTargetRowNumber is required for MERGE action");
    }

    /**
     * Converts a raw string-based {@link ImportRowData} to a typed
     * {@link ResidentRequest}, performing lenient type coercion.
     */
    private static ResidentRequest toResidentRequest(ImportRowData d) {
        LocalDate dob = null;
        if (!d.dateOfBirth().isBlank()) {
            try { dob = LocalDate.parse(d.dateOfBirth()); } catch (DateTimeParseException ignored) {}
        }
        Integer cy = null;
        if (!d.classYear().isBlank()) {
            try { cy = Integer.parseInt(d.classYear()); } catch (NumberFormatException ignored) {}
        }
        return new ResidentRequest(
                d.firstName(),
                d.lastName(),
                d.email(),
                d.phone().isBlank()            ? null : d.phone(),
                d.studentId().isBlank()        ? null : d.studentId(),
                dob,
                d.enrollmentStatus().isBlank() ? null : d.enrollmentStatus(),
                d.department().isBlank()       ? null : d.department(),
                cy,
                d.roomNumber().isBlank()       ? null : d.roomNumber(),
                d.buildingName().isBlank()     ? null : d.buildingName()
        );
    }
}
