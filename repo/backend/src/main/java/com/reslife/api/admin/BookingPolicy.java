package com.reslife.api.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Value object representing the complete booking/visit policy.
 *
 * <p>Stored as a JSON blob in the {@code configuration_versions} table under
 * the key {@code booking.policy}.  Every change creates a new version row so
 * the full history is preserved.
 *
 * <h3>Rules encoded here</h3>
 * <ul>
 *   <li><b>windowDays</b> — how many days into the future a visitor may book</li>
 *   <li><b>sameDayCutoff</b> — same-day bookings blocked after HH:MM</li>
 *   <li><b>noShowThreshold / noShowWindowDays</b> — after N no-shows within
 *       D days the visitor cannot make new bookings</li>
 *   <li><b>canary rollout</b> — new policy activates for canaryRolloutPercent %
 *       of buildings (or the explicit canaryBuildingIds list) before it goes
 *       system-wide</li>
 *   <li><b>holidayBlackoutDates</b> — dates on which no bookings are accepted</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingPolicy {

    // ── Booking window ────────────────────────────────────────────────────────

    /** How many days ahead a booking can be made (rolling window). */
    @Min(value = 1,  message = "windowDays must be at least 1")
    @Max(value = 365, message = "windowDays cannot exceed 365")
    private int windowDays = 14;

    // ── Same-day cutoff ───────────────────────────────────────────────────────

    /** Hour of day (0–23) after which same-day bookings are blocked. */
    @Min(value = 0,  message = "sameDayCutoffHour must be 0–23")
    @Max(value = 23, message = "sameDayCutoffHour must be 0–23")
    private int sameDayCutoffHour = 17;

    /** Minute (0–59) of the cutoff time. */
    @Min(value = 0,  message = "sameDayCutoffMinute must be 0–59")
    @Max(value = 59, message = "sameDayCutoffMinute must be 0–59")
    private int sameDayCutoffMinute = 0;

    // ── No-show policy ────────────────────────────────────────────────────────

    /** Number of no-shows within noShowWindowDays that triggers the booking restriction. */
    @Min(value = 1,  message = "noShowThreshold must be at least 1")
    @Max(value = 20, message = "noShowThreshold cannot exceed 20")
    private int noShowThreshold = 2;

    /** Rolling window (days) over which no-shows are counted. */
    @Min(value = 1,  message = "noShowWindowDays must be at least 1")
    @Max(value = 365, message = "noShowWindowDays cannot exceed 365")
    private int noShowWindowDays = 30;

    // ── Canary rollout ────────────────────────────────────────────────────────

    /** When true, the policy is only applied to the canary cohort. */
    private boolean canaryEnabled = false;

    /** Percentage of buildings (by hash) included in the canary cohort (0–100). */
    @Min(value = 0,   message = "canaryRolloutPercent must be 0–100")
    @Max(value = 100, message = "canaryRolloutPercent must be 0–100")
    private int canaryRolloutPercent = 10;

    /**
     * Explicit list of building identifiers always in the canary cohort,
     * regardless of the percentage-based hash.  Empty list means use the
     * hash-based selection only.
     */
    @NotNull
    private List<String> canaryBuildingIds = new ArrayList<>();

    // ── Holiday blackouts ─────────────────────────────────────────────────────

    @NotNull
    @Valid
    private List<HolidayBlackout> holidayBlackoutDates = new ArrayList<>();

    // ── Nested types ──────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HolidayBlackout {

        /** ISO-8601 date, e.g. {@code 2026-12-25}. */
        @NotBlank(message = "Holiday date is required")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}",
                 message = "Holiday date must be in YYYY-MM-DD format")
        private String date;

        /** Human-readable name, e.g. "Christmas Day". */
        @NotBlank(message = "Holiday label is required")
        @Size(max = 100, message = "Holiday label must be 100 characters or fewer")
        private String label;

        public HolidayBlackout() {}
        public HolidayBlackout(String date, String label) { this.date = date; this.label = label; }

        public String getDate()  { return date; }
        public String getLabel() { return label; }
        public void setDate(String date)   { this.date = date; }
        public void setLabel(String label) { this.label = label; }
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public int  getWindowDays()             { return windowDays; }
    public void setWindowDays(int v)        { this.windowDays = v; }

    public int  getSameDayCutoffHour()      { return sameDayCutoffHour; }
    public void setSameDayCutoffHour(int v) { this.sameDayCutoffHour = v; }

    public int  getSameDayCutoffMinute()      { return sameDayCutoffMinute; }
    public void setSameDayCutoffMinute(int v) { this.sameDayCutoffMinute = v; }

    public int  getNoShowThreshold()         { return noShowThreshold; }
    public void setNoShowThreshold(int v)    { this.noShowThreshold = v; }

    public int  getNoShowWindowDays()        { return noShowWindowDays; }
    public void setNoShowWindowDays(int v)   { this.noShowWindowDays = v; }

    public boolean isCanaryEnabled()              { return canaryEnabled; }
    public void    setCanaryEnabled(boolean v)    { this.canaryEnabled = v; }

    public int  getCanaryRolloutPercent()         { return canaryRolloutPercent; }
    public void setCanaryRolloutPercent(int v)    { this.canaryRolloutPercent = v; }

    public List<String> getCanaryBuildingIds()            { return canaryBuildingIds; }
    public void setCanaryBuildingIds(List<String> ids)    { this.canaryBuildingIds = ids; }

    public List<HolidayBlackout> getHolidayBlackoutDates()             { return holidayBlackoutDates; }
    public void setHolidayBlackoutDates(List<HolidayBlackout> dates)   { this.holidayBlackoutDates = dates; }
}
