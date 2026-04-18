
# Business Logic Questions Log

This document records prompt ambiguities, the hypotheses formed at implementation time, and the concrete decisions made in `repo/`.
Each entry follows the mandatory structure: **Question → Hypothesis → Solution**.

---

## 1. Duplicate Detection During Bulk Import

**Question:** The prompt says a duplicate merge workflow fires when a student appears multiple times (match on student ID, then name + date of birth). What happens when student ID is missing from a CSV row but name + DOB produce a match—should that row be merged, flagged, or rejected outright?

**Hypothesis:** A row without a student ID that matches an existing record on name + DOB is ambiguous enough to be dangerous; silently merging it could corrupt unrelated records. The safer path is to flag it as a warning and require a human decision before committing.

**Solution:** Implemented a two-tier duplicate check in `BulkImportService`. Rows with a matching student ID are auto-queued for merge with a diff preview. Rows that lack a student ID but match on name + DOB are surfaced as `WARN_SECONDARY_MATCH` in the preview step; the user must explicitly accept or skip each one before the import is committed.

---

## 2. Messaging Block vs. System Notices

**Question:** Students can block a staff member from initiating new chats, but staff can still send system notices. The prompt does not define whether a system notice can contain arbitrary free-text or is strictly constrained to a templated payload.

**Hypothesis:** Allowing unrestricted free-text in a "system notice" defeats the purpose of the block feature—a blocked staff member could simply send a message labelled as a system notice. The safest interpretation is that system notices are template-bound and do not expose an arbitrary message body.

**Solution:** `NotificationService.sendSystemNotice()` accepts only a `NotificationTemplateKey` and a parameter map; it rejects calls that pass a raw body string. The UI's "Send Notice" dialog for blocked threads renders only a template picker, with no free-text field.

---

## 3. Account Lockout Window Reset Behaviour

**Question:** The prompt specifies lockout after 5 failed attempts in 15 minutes but is silent on whether the attempt counter resets on a successful login, on window expiry, or both.

**Hypothesis:** Resetting only on window expiry could lock out a legitimate user who made 4 mistakes, later succeeds, then makes 1 more mistake in the same 15-minute window. Resetting on successful login is friendlier and matches common industry practice.

**Solution:** `AuthService` clears the failed-attempt counter immediately upon a verified successful login. The counter also naturally expires after the 15-minute rolling window. Lockout is lifted only by an admin action or automatic expiry—whichever comes first.

---

## 4. AES-256 Encryption Key Rotation

**Question:** The prompt mandates AES-256 at-rest encryption for date of birth and emergency contact fields but says nothing about key rotation, key storage, or what happens to already-encrypted rows when the key changes.

**Hypothesis:** Without a rotation strategy, a compromised key permanently exposes historical data. At minimum the system should support envelope encryption so the data encryption key (DEK) can be re-wrapped without re-encrypting every row.

**Solution:** `EncryptionService` uses a two-layer scheme: a master key reference (stored in an environment variable or a local keystore file) wraps per-entity DEKs that are stored alongside each encrypted column. A background `KeyRotationJob` re-wraps DEKs in batches when the master key reference changes, leaving ciphertext untouched until next write.

---

## 5. Booking No-Show Policy and Canary Rollout Interaction

**Question:** The prompt describes a canary rollout of booking-policy changes to 10 % of buildings before full enablement. It does not define what "10 % of buildings" means when the estate is very small (e.g., 3 buildings) or how a no-show accumulated under the old policy is counted after the new policy goes live.

**Hypothesis:** For small estates, floor(0.1 × n) rounds to 0, which would make the canary meaningless. The system should enforce a minimum of 1 building for any canary group. No-shows recorded before a policy change should be grandfathered under the policy that was active at the time.

**Solution:** `BookingPolicyService.computeCanaryGroup()` uses `max(1, floor(0.1 × buildingCount))`. Each `NoShowRecord` stores a `policy_version_id` FK; when evaluating whether a student hits the 2-no-show threshold, only records whose `policy_version_id` matches the currently active policy for that building are counted.

---

## 6. Webhook HMAC Key Compromise and Replay Window

**Question:** The prompt sets a 5-minute replay window for HMAC-signed webhook requests but does not specify how stale nonces are stored or purged, nor what happens when an integration key is suspected to be compromised.

**Hypothesis:** Storing every nonce forever is impractical. Purging nonces older than 5 minutes is safe because any replayed request bearing an older timestamp would be rejected by the timestamp check first. A compromised key needs an immediate revocation path that also invalidates in-flight requests.

**Solution:** `ReplayProtectionService` keeps nonces in a bounded cache with a 5-minute TTL backed by a Caffeine in-memory store (acceptable for on-prem single-node deployment; documented as needing an external store for HA). `IntegrationKeyService.revoke()` marks the key as `REVOKED` in PostgreSQL and publishes an internal event that flushes all cached nonces associated with that key.

---

## 7. Crawler Concurrency Cap and Internal Resource Protection

**Question:** The prompt sets a default concurrency cap of 5 parallel fetchers but does not define whether the cap is per-task, per-site, or global across all configured sources.

**Hypothesis:** A per-task cap offers the most predictable protection: each configured crawl task is individually throttled, preventing a single high-frequency task from monopolising all concurrency slots.

**Solution:** `CrawlerExecutorService` maintains a per-task semaphore with a configurable `maxParallelFetchers` (default 5, read from the task's `CrawlerConfig`). A global circuit breaker additionally limits total concurrent HTTP connections across all tasks to `5 × activeTaskCount` to protect the host NIC.

---

## 8. Soft Delete and Audit Log Retention After 30-Day Purge

**Question:** Account deletion performs a 30-day soft delete followed by irreversible purge, while audit logs must be preserved. The prompt does not say whether audit records referencing the deleted user ID become dangling FKs or should be anonymised.

**Hypothesis:** Keeping the raw user ID in audit rows after purge creates a re-identification risk if the ID is ever reused and may conflict with FERPA-style data minimisation obligations. The safest path is to replace the user ID in audit records with a stable, opaque pseudonym at purge time.

**Solution:** `UserPurgeJob` (run nightly) finalises any soft-deleted accounts past 30 days. Before destroying the `users` row it writes a `PurgeManifest` entry that maps the real UUID to a deterministic, non-reversible pseudonym (`HMAC-SHA256(secret, userId)`). All `audit_log` rows are updated to replace the FK with the pseudonym string. The `users` row is then hard-deleted.

---

## 9. Analytics Dashboard Refresh Cadence vs. Booking Event Lag

**Question:** Metrics refresh on a 15-minute schedule, but booking and no-show events could arrive with significant lag if an offline terminal batch-syncs hours later. Should the dashboard show real-time counts or scheduled-refresh counts, and how are late-arriving events handled?

**Hypothesis:** Showing a "last refreshed at" timestamp avoids misleading users. Late-arriving events should be included in the next scheduled refresh without retroactively changing historical snapshots, to keep time-series charts stable.

**Solution:** `AnalyticsSnapshotJob` runs every 15 minutes and writes immutable `metric_snapshot` rows keyed by `(metric_type, period_start)`. If a late event arrives after its natural period's snapshot was already written, the next snapshot captures the cumulative total (so point-in-time accuracy improves over time). The dashboard header shows the timestamp of the latest snapshot so staff understand the data freshness.

---

## 10. Holiday Blackout Dates and Same-Day 5 PM Cutoff Interaction

**Question:** The prompt defines a same-day cutoff at 5:00 PM and holiday blackout dates as separate rules. It is ambiguous whether the 5 PM cutoff should fire on the day before a holiday (so that no bookings can be made for the holiday at all) or only on the holiday day itself.

**Hypothesis:** Blocking bookings for a holiday only on the holiday day itself could allow a booking submitted at 4:59 PM on the eve of the holiday to slip through, which is likely unintended. The cutoff should prevent same-day and next-day-holiday bookings equally.

**Solution:** `BookingRuleEngine.isSlotBookable()` evaluates two conditions independently: (a) if the requested date is today and the current time is past 17:00, reject; (b) if the requested date appears in the `holiday_blackout` table, reject regardless of current time. Both checks fire for every slot evaluation so the rules remain compositional and can be extended independently.
