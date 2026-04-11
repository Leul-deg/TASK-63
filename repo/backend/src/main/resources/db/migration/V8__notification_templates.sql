-- ============================================================
-- V8__notification_templates.sql
-- Extends the notifications table with priority, category,
-- required-acknowledgment, and template metadata.
-- Adds database-driven notification_templates and an
-- append-only notification_acknowledgments audit table.
-- ============================================================

-- ── Extend notifications ──────────────────────────────────────────────────────
ALTER TABLE notifications
    ADD COLUMN priority                 VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN category                 VARCHAR(50)  NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN requires_acknowledgment  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN acknowledged_at          TIMESTAMPTZ,
    ADD COLUMN template_key             VARCHAR(100),
    ADD COLUMN variables                TEXT;         -- JSON map of template variable values

CREATE INDEX idx_notif_recipient_ack
    ON notifications(recipient_id, requires_acknowledgment, acknowledged_at)
    WHERE requires_acknowledgment = TRUE AND acknowledged_at IS NULL;

CREATE INDEX idx_notif_category ON notifications(recipient_id, category);

-- ── Notification templates ────────────────────────────────────────────────────
CREATE TABLE notification_templates (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key            VARCHAR(100) NOT NULL UNIQUE,
    category                VARCHAR(50)  NOT NULL,
    title_pattern           TEXT         NOT NULL,
    body_pattern            TEXT         NOT NULL,
    default_priority        VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    requires_acknowledgment BOOLEAN      NOT NULL DEFAULT FALSE,
    description             TEXT,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Acknowledgment audit log ──────────────────────────────────────────────────
-- Append-only: each row records one "I acknowledge" action.
-- UNIQUE(notification_id, user_id) enforces one acknowledgment per user per notification.
CREATE TABLE notification_acknowledgments (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID        NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id),
    session_id      VARCHAR(255),
    ip_address      VARCHAR(45),
    acknowledged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (notification_id, user_id)
);

CREATE INDEX idx_notif_ack_user ON notification_acknowledgments(user_id);

-- ── Template seeds ────────────────────────────────────────────────────────────

-- ONBOARDING / HIRE templates
INSERT INTO notification_templates
    (template_key, category, title_pattern, body_pattern, default_priority, requires_acknowledgment, description)
VALUES
    (
        'onboarding.welcome',
        'ONBOARDING',
        'Welcome to ResLife, {{recipientName}}!',
        'Your ResLife staff account is now active. Please review the onboarding checklist and complete all assigned tasks before your start date.{{#tasks}}

Assigned tasks:
{{tasks}}{{/tasks}}',
        'NORMAL',
        FALSE,
        'Sent to new hires when their account is activated'
    ),
    (
        'onboarding.task_assigned',
        'ONBOARDING',
        'New Onboarding Task: {{taskName}}',
        'You have been assigned the following onboarding task:

  Task:        {{taskName}}
  Due date:    {{dueDate}}
  Assigned by: {{assignedBy}}

{{description}}

Please complete this task by the due date.',
        'HIGH',
        FALSE,
        'Sent when a new hire is assigned a specific onboarding task'
    ),
    (
        'onboarding.task_overdue',
        'ONBOARDING',
        'Overdue Task: {{taskName}}',
        'The following onboarding task has passed its due date and remains incomplete:

  Task:     {{taskName}}
  Was due:  {{dueDate}}

Please complete this task immediately or contact your supervisor.',
        'HIGH',
        TRUE,
        'Sent when an onboarding task is overdue — acknowledgment required'
    );

-- APPOINTMENT templates
INSERT INTO notification_templates
    (template_key, category, title_pattern, body_pattern, default_priority, requires_acknowledgment, description)
VALUES
    (
        'appointment.rescheduled',
        'APPOINTMENT',
        'Appointment Rescheduled: {{appointmentType}}',
        'Your appointment has been rescheduled.

  Type:          {{appointmentType}}
  Original time: {{originalDate}}
  New time:      {{newDate}}
  Location:      {{location}}

Reason: {{reason}}

If you cannot attend at the new time, please contact the scheduling office.',
        'HIGH',
        TRUE,
        'Sent when an appointment is moved to a new date/time'
    ),
    (
        'appointment.cancelled',
        'APPOINTMENT',
        'Appointment Cancelled: {{appointmentType}}',
        'Your appointment has been cancelled.

  Type:          {{appointmentType}}
  Was scheduled: {{originalDate}}

Reason: {{reason}}

Please contact the office to reschedule if needed.',
        'HIGH',
        TRUE,
        'Sent when an appointment is cancelled outright'
    );

-- SETTLEMENT templates
INSERT INTO notification_templates
    (template_key, category, title_pattern, body_pattern, default_priority, requires_acknowledgment, description)
VALUES
    (
        'settlement.outcome',
        'SETTLEMENT',
        'Settlement Outcome — Case {{caseReference}}',
        'A settlement has been reached for the following case:

  Case reference: {{caseReference}}
  Outcome:        {{outcome}}
  Effective date: {{effectiveDate}}
  Decided by:     {{decidedBy}}

{{details}}

You are required to acknowledge receipt of this settlement outcome.',
        'HIGH',
        TRUE,
        'Sent when a settlement case reaches a final outcome'
    );

-- ARBITRATION templates
INSERT INTO notification_templates
    (template_key, category, title_pattern, body_pattern, default_priority, requires_acknowledgment, description)
VALUES
    (
        'arbitration.decision',
        'ARBITRATION',
        'Arbitration Decision — Case {{caseReference}}',
        'An arbitration decision has been issued for the following case:

  Case reference: {{caseReference}}
  Decision:       {{decision}}
  Arbitrator:     {{arbitrator}}
  Date issued:    {{decisionDate}}

{{details}}

This decision is binding. You are required to acknowledge receipt.',
        'CRITICAL',
        TRUE,
        'Sent when an arbitration panel issues a binding decision'
    );

-- GENERAL templates
INSERT INTO notification_templates
    (template_key, category, title_pattern, body_pattern, default_priority, requires_acknowledgment, description)
VALUES
    (
        'general.info',
        'GENERAL',
        '{{title}}',
        '{{body}}',
        'NORMAL',
        FALSE,
        'Generic informational notification'
    ),
    (
        'general.announcement',
        'GENERAL',
        'Announcement: {{title}}',
        '{{body}}',
        'NORMAL',
        FALSE,
        'Staff-wide announcement'
    );
