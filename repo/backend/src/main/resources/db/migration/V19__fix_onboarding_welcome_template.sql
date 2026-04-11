-- The onboarding.welcome body_pattern contains Mustache-style section markers
-- {{#tasks}}...{{/tasks}} which the renderer (NotificationTemplate.render) does
-- not support.  The renderer performs only simple {{key}} substitution via
-- String.replace(), so those markers appear as literal text in every rendered
-- notification.
--
-- Fix: remove the section markers, keep the plain {{tasks}} placeholder.
-- Callers should pre-format the full tasks block (e.g.
--   "Assigned tasks:\n- Review policies\n- Complete training")
-- before passing it as the {{tasks}} variable, or pass an empty string when
-- there are no tasks to list.
UPDATE notification_templates
SET    body_pattern =
'Your ResLife staff account is now active. Please review the onboarding checklist and complete all assigned tasks before your start date.

{{tasks}}'
WHERE  template_key = 'onboarding.welcome';
