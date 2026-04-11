package com.reslife.api.encryption;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Determines how much of a resident's sensitive data (date of birth,
 * emergency contact details) a caller may receive in an API response.
 *
 * <h3>Access matrix</h3>
 * <table>
 *   <tr><th>Role</th><th>Level</th></tr>
 *   <tr><td>ADMIN, HOUSING_ADMINISTRATOR, DIRECTOR</td><td>FULL</td></tr>
 *   <tr><td>RESIDENT_DIRECTOR, RESIDENT_ASSISTANT, RESIDENCE_STAFF, STAFF</td><td>FULL</td></tr>
 *   <tr><td>STUDENT, READ_ONLY (or any unrecognised role)</td><td>NONE</td></tr>
 * </table>
 *
 * <p>At the <em>backend</em> level the distinction is binary: either the
 * decrypted value is included in the response ({@code FULL}) or the field
 * is {@code null} ({@code NONE}). The <em>frontend</em> adds a second,
 * UX-layer of masking — showing bullets by default with a per-field reveal
 * button — for all staff roles.
 */
public enum SensitiveAccessLevel {

    /** Decrypted value is returned in the API response. */
    FULL,

    /** Sensitive field is omitted (returned as {@code null}). */
    NONE;

    // -----------------------------------------------------------------------
    // Roles permitted to receive full decrypted data
    // -----------------------------------------------------------------------

    private static final Set<String> FULL_ACCESS_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_HOUSING_ADMINISTRATOR",
            "ROLE_DIRECTOR",
            "ROLE_RESIDENT_DIRECTOR",
            "ROLE_RESIDENT_ASSISTANT",
            "ROLE_RESIDENCE_STAFF",
            "ROLE_STAFF"
    );

    /**
     * Derives the access level from the caller's Spring Security authorities.
     * Any authority in {@link #FULL_ACCESS_ROLES} grants {@link #FULL} access.
     */
    public static SensitiveAccessLevel from(Collection<? extends GrantedAuthority> authorities) {
        Set<String> granted = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return granted.stream().anyMatch(FULL_ACCESS_ROLES::contains) ? FULL : NONE;
    }
}
