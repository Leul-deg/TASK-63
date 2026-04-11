package com.reslife.api.domain.resident;

/**
 * All fields from a single CSV row, kept as raw strings.
 *
 * <p>Strings are used throughout (rather than typed fields) so that the
 * same record can represent both the input from CSV and the payload sent
 * back from the frontend at commit time. Type conversion and validation
 * happen in {@link ResidentImportExportService}.
 */
public record ImportRowData(
        String studentId,
        String firstName,
        String lastName,
        String email,
        String phone,
        /** ISO-8601 {@code YYYY-MM-DD}, or blank/null if not provided. */
        String dateOfBirth,
        String enrollmentStatus,
        String department,
        /** Integer year as a string, or blank/null. */
        String classYear,
        String roomNumber,
        String buildingName
) {
    /** Returns a trimmed copy; null input becomes empty string. */
    public static String clean(String s) {
        return s == null ? "" : s.strip();
    }
}
