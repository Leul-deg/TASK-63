Audit Report 02 Fix Check

This file tracks the issues listed in audit_report-02.md and how each one was resolved during the second fix cycle.

Issue-by-issue resolution
1. Self-service resident lookup tied to mutable email
Issue from report 02: student self-service resolved resident records using email equality, which is mutable and could allow identity confusion or unauthorized access.
Resolution: introduced an explicit resident.user_id (linkedUserId) relationship, added a migration to backfill existing records, and updated self-service endpoints to resolve residents via this stable foreign key instead of email.
Status: Resolved
2. Webhook local-network enforcement gap
Issue from report 02: webhook endpoints were validated as local-only at registration time but not revalidated at dispatch time, allowing potential bypass if targets changed.
Resolution: added send-time revalidation using the local network validator before dispatching any webhook, and introduced regression tests to ensure delivery is blocked when validation fails.
Status: Resolved
3. Student self-service date-of-birth exposure
Issue from report 02: student self-service endpoints exposed raw date of birth, violating sensitive data access restrictions.
Resolution: changed backend responses to return SensitiveAccessLevel.NONE for self-service, updated frontend to render DOB as Restricted, and added backend/frontend tests to enforce the restriction.
Status: Resolved
4. Messaging coverage gap for block semantics and delivery/read status
Issue from report 02: messaging system lacked direct test coverage for blocked-user behavior and message lifecycle states (SENT, DELIVERED, READ).
Resolution: added service-level tests covering blocked direct-thread initiation, system-message bypass behavior, and full message status lifecycle transitions.
Status: Resolved
5. Attachment validation coverage gap
Issue from report 02: attachment handling lacked sufficient validation tests for file integrity and constraints.
Resolution: added dedicated AttachmentService tests for valid uploads, oversize rejection, magic-byte validation, and MIME/extension mismatch handling.
Status: Resolved
6. Crawler engine coverage gap (checkpoint/resume and concurrency)
Issue from report 02: crawler subsystem lacked sufficient test coverage for resumable checkpoints and concurrency enforcement.
Resolution: added targeted tests for checkpoint persistence and resume behavior, along with tests validating concurrency limits (e.g., semaphore-based execution caps) under multiple job submissions.
Status: Resolved
Verification
Verified by static inspection of updated backend, frontend, and test sources
Regression tests added for:
webhook locality enforcement
student self-service restrictions
messaging lifecycle and block semantics
attachment validation
crawler checkpoint/resume and concurrency enforcement
No previously identified issues remain unaddressed in code