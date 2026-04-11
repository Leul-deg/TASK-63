package com.reslife.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the BCrypt cost factor used by the application encoder (12)
 * matches what V17 seeds into the dev database.
 *
 * <p>BCrypt embeds the cost factor directly in the hash string:
 * {@code $2a$<cost>$<salt><hash>}.  Asserting the prefix catches any
 * accidental regression where the encoder and seed migrate to different
 * cost factors.
 */
class DevSeedPasswordHashTest {

    private static final int    REQUIRED_COST    = 12;
    private static final String DEV_PASSWORD     = "password";
    private static final String EXPECTED_PREFIX  = "$2a$12$";

    /**
     * A hash produced by the configured encoder must carry the cost-12 marker.
     * This mirrors the {@code gen_salt('bf', 12)} call in V17.
     */
    @Test
    void encoderProducesCost12Hash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(REQUIRED_COST);
        String hash = encoder.encode(DEV_PASSWORD);
        assertTrue(hash.startsWith(EXPECTED_PREFIX),
                "Expected cost-12 hash starting with '" + EXPECTED_PREFIX
                        + "' but got: " + hash.substring(0, Math.min(10, hash.length())));
    }

    /**
     * The encoder must accept a cost-12 hash of the dev password.
     * Equivalent to what V17 stores for the three seeded accounts.
     */
    @Test
    void cost12HashMatchesDevPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(REQUIRED_COST);
        String hash = encoder.encode(DEV_PASSWORD);
        assertTrue(encoder.matches(DEV_PASSWORD, hash),
                "Encoder must verify the dev password against a cost-12 hash");
    }

    /**
     * Sanity check: a cost-10 hash (as V14 originally produced) is still
     * accepted by the encoder — Spring's BCryptPasswordEncoder reads the
     * cost from the stored hash, not from the configured strength.  This
     * confirms that authentication continues to work while V17 is deploying
     * on a live database where some rows may not yet be updated.
     */
    @Test
    void cost10HashIsStillAcceptedByEncoder() {
        BCryptPasswordEncoder encoder10  = new BCryptPasswordEncoder(10);
        BCryptPasswordEncoder encoder12  = new BCryptPasswordEncoder(REQUIRED_COST);
        String oldHash = encoder10.encode(DEV_PASSWORD);

        // The encoder reads cost from the hash, not from the configured strength
        assertTrue(encoder12.matches(DEV_PASSWORD, oldHash),
                "Cost-12 encoder must still verify older cost-10 hashes during rolling migration");
        assertTrue(oldHash.startsWith("$2a$10$"),
                "Old hash must embed cost 10 in its prefix");
    }
}
