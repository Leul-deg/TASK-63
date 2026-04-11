package com.reslife.api.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.domain.system.ConfigurationVersion;
import com.reslife.api.domain.system.ConfigurationVersionRepository;
import com.reslife.api.domain.system.SystemService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages the booking/visit policy, stored as a versioned JSON blob under
 * the configuration key {@value #POLICY_KEY}.
 *
 * <h3>Versioning model</h3>
 * <p>Every call to {@link #update} or {@link #activateVersion} creates a new
 * row in {@code configuration_versions}.  Old rows are never mutated (except
 * to flip {@code is_active = false}).  This gives a full, append-only audit
 * trail of every policy change.
 *
 * <h3>Canary rollout</h3>
 * <p>When {@link BookingPolicy#isCanaryEnabled()} is {@code true}, calling
 * code (future booking endpoints) should check
 * {@link #isInCanaryCohort(String buildingId)} before applying the policy.
 */
@Service
@Transactional(readOnly = true)
public class BookingPolicyService {

    static final String POLICY_KEY = "booking.policy";
    private static final Logger log = LoggerFactory.getLogger(BookingPolicyService.class);

    private final SystemService                  systemService;
    private final ConfigurationVersionRepository configRepo;
    private final ObjectMapper                   objectMapper;

    public BookingPolicyService(SystemService systemService,
                                ConfigurationVersionRepository configRepo,
                                ObjectMapper objectMapper) {
        this.systemService = systemService;
        this.configRepo    = configRepo;
        this.objectMapper  = objectMapper;
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /**
     * Returns the currently active policy version.
     *
     * @throws EntityNotFoundException if no policy has been seeded yet
     */
    public PolicyVersionResponse getCurrent() {
        ConfigurationVersion cv = configRepo.findByKeyAndActiveTrue(POLICY_KEY)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active booking policy found. Run migrations to seed the default."));
        return PolicyVersionResponse.from(cv, parse(cv.getValue()));
    }

    /**
     * Returns the parsed policy without version metadata.
     * Suitable for use by other services that need to enforce the rules.
     */
    public BookingPolicy getPolicy() {
        String json = systemService.getConfigValue(POLICY_KEY,
                "{}"); // fallback to empty obj → all defaults
        return parse(json);
    }

    /**
     * Returns all historical versions, newest first.
     */
    public List<PolicyVersionResponse> getHistory() {
        return configRepo.findByKeyOrderByVersionDesc(POLICY_KEY).stream()
                .map(cv -> PolicyVersionResponse.from(cv, parse(cv.getValue())))
                .toList();
    }

    // ── Write ──────────────────────────────────────────────────────────────────

    /**
     * Validates and persists a new policy version.
     *
     * <p>Validation is done via Bean Validation on the {@link BookingPolicy}
     * object itself (in the controller via {@code @Valid}).  This method
     * additionally validates date formats in the blackout list.
     *
     * @param policy      the new policy to save
     * @param description human-readable reason for the change
     * @param actorId     UUID of the admin who made the change
     * @return the newly created version
     */
    @Transactional
    public PolicyVersionResponse update(BookingPolicy policy, String description, UUID actorId) {
        validateBlackoutDates(policy);

        String json = serialize(policy);
        String oldJson = systemService.getConfigValue(POLICY_KEY, null);

        ConfigurationVersion cv = systemService.setConfigValue(
                POLICY_KEY, json, description, actorId);

        systemService.log("UPDATE", "BookingPolicy", cv.getId(),
                oldJson, json,
                actorId, null, null);

        log.info("Booking policy updated to v{} by {}", cv.getVersion(), actorId);
        return PolicyVersionResponse.from(cv, policy);
    }

    /**
     * Activates a specific historical version by creating a new version record
     * with the historical value.  History is never mutated.
     *
     * @param targetVersion version number to restore
     * @param actorId       admin performing the action
     */
    @Transactional
    public PolicyVersionResponse activateVersion(int targetVersion, UUID actorId) {
        ConfigurationVersion historical = configRepo
                .findByKeyAndVersion(POLICY_KEY, targetVersion)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Booking policy version " + targetVersion + " not found."));

        if (historical.isActive()) {
            // Already active — return it as-is
            return PolicyVersionResponse.from(historical, parse(historical.getValue()));
        }

        String description = "Restored from v" + targetVersion
                + (historical.getDescription() != null
                   ? " (" + historical.getDescription() + ")"
                   : "");

        String oldJson = systemService.getConfigValue(POLICY_KEY, null);
        ConfigurationVersion cv = systemService.setConfigValue(
                POLICY_KEY, historical.getValue(), description, actorId);

        systemService.log("ROLLBACK", "BookingPolicy", cv.getId(),
                oldJson, historical.getValue(),
                actorId, null, null);

        log.info("Booking policy rolled back to v{} (new v{}) by {}", targetVersion, cv.getVersion(), actorId);
        return PolicyVersionResponse.from(cv, parse(cv.getValue()));
    }

    // ── Canary check ───────────────────────────────────────────────────────────

    /**
     * Returns true if the given building should receive the new policy.
     *
     * <p>Logic:
     * <ol>
     *   <li>If canary is disabled → true (all buildings get the policy).</li>
     *   <li>If buildingId is in the explicit {@code canaryBuildingIds} list → true.</li>
     *   <li>Otherwise, uses a stable hash of the buildingId to determine if it
     *       falls within {@code canaryRolloutPercent} of buildings.</li>
     * </ol>
     */
    public boolean isInCanaryCohort(String buildingId) {
        BookingPolicy policy = getPolicy();
        if (!policy.isCanaryEnabled()) return true;
        if (policy.getCanaryBuildingIds().contains(buildingId)) return true;
        int hash = Math.abs(buildingId.hashCode()) % 100;
        return hash < policy.getCanaryRolloutPercent();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private BookingPolicy parse(String json) {
        if (json == null || json.isBlank()) return new BookingPolicy();
        try {
            return objectMapper.readValue(json, BookingPolicy.class);
        } catch (JsonProcessingException e) {
            log.warn("Could not parse booking policy JSON — returning defaults: {}", e.getMessage());
            return new BookingPolicy();
        }
    }

    private String serialize(BookingPolicy policy) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise booking policy", e);
        }
    }

    private void validateBlackoutDates(BookingPolicy policy) {
        if (policy.getHolidayBlackoutDates() == null) return;
        for (BookingPolicy.HolidayBlackout b : policy.getHolidayBlackoutDates()) {
            try {
                java.time.LocalDate.parse(b.getDate());
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid holiday blackout date: '" + b.getDate() + "'. Expected YYYY-MM-DD.");
            }
        }
        // Check for duplicate dates
        long distinct = policy.getHolidayBlackoutDates().stream()
                .map(BookingPolicy.HolidayBlackout::getDate)
                .distinct().count();
        if (distinct < policy.getHolidayBlackoutDates().size()) {
            throw new IllegalArgumentException("Holiday blackout dates must be unique.");
        }
    }
}
