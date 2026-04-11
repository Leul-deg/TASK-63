package com.reslife.api.domain.resident;

/**
 * Thrown when a create-resident request matches one or more existing residents
 * and the caller has not passed {@code ?force=true}.
 */
public class DuplicateResidentException extends RuntimeException {

    private final DuplicateCheckResponse duplicates;

    public DuplicateResidentException(DuplicateCheckResponse duplicates) {
        super("Possible duplicate residents found");
        this.duplicates = duplicates;
    }

    public DuplicateCheckResponse getDuplicates() {
        return duplicates;
    }
}
