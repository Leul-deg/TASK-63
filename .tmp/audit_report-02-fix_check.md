# Audit Report 02 Fix Check

## Issues Fixed

1. Self-service resident lookup tied to mutable email
- Fixed by adding an explicit resident-to-user link on the resident record.
- Added a migration to backfill the link for existing student accounts and updated student self-service endpoints to resolve through `linkedUserId` instead of email equality.

2. Webhook local-network enforcement gap
- Fixed by revalidating webhook targets with the local-network validator at dispatch time, not only at registration time.
- Added regression tests to confirm outbound delivery is blocked when send-time locality validation fails.

3. Student self-service date-of-birth exposure
- Fixed by returning the student self-service resident response with restricted sensitive access instead of full sensitive access.
- Updated the student profile UI to show date of birth as `Restricted` rather than rendering raw DOB.
- Added backend and frontend tests to verify DOB stays hidden in self-service.

4. Messaging coverage gap for block semantics and delivery/read status
- Fixed by adding direct service-level tests for blocked direct-thread initiation, system-notice bypass behavior, and the `SENT` / `DELIVERED` / `READ` status lifecycle.

5. Attachment validation coverage gap
- Fixed by adding direct `AttachmentService` tests for valid uploads, oversize rejection, magic-byte mismatch rejection, and MIME/extension mismatch rejection.

## Verification

- Verified by static inspection of the current codebase and test sources against the resolved items explicitly mentioned in `audit_report-02.md`.
