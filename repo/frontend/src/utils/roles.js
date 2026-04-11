/**
 * Role constants and permission helpers.
 *
 * Roles match the backend RoleName enum (without the ROLE_ prefix used
 * internally by Spring Security).
 */

export const ROLES = {
  ADMIN:                 'ADMIN',
  DIRECTOR:              'DIRECTOR',
  HOUSING_ADMINISTRATOR: 'HOUSING_ADMINISTRATOR',
  RESIDENT_DIRECTOR:     'RESIDENT_DIRECTOR',
  RESIDENT_ASSISTANT:    'RESIDENT_ASSISTANT',
  RESIDENCE_STAFF:       'RESIDENCE_STAFF',
  STAFF:                 'STAFF',
  STUDENT:               'STUDENT',
  READ_ONLY:             'READ_ONLY',
};

export const ADMIN_PORTAL_ROLES = [
  ROLES.ADMIN,
  ROLES.HOUSING_ADMINISTRATOR,
];

/**
 * Roles whose holders may see sensitive resident data (DOB, emergency contacts).
 * Must mirror SensitiveAccessLevel.FULL_ACCESS_ROLES on the backend.
 */
const SENSITIVE_ACCESS_ROLES = new Set([
  ROLES.ADMIN,
  ROLES.HOUSING_ADMINISTRATOR,
  ROLES.DIRECTOR,
  ROLES.RESIDENT_DIRECTOR,
  ROLES.RESIDENT_ASSISTANT,
  ROLES.RESIDENCE_STAFF,
  ROLES.STAFF,
]);

/** Returns true if the user has the specified role. */
export function hasRole(user, role) {
  return Array.isArray(user?.roles) && user.roles.includes(role);
}

/** Returns true if the user has at least one of the specified roles. */
export function hasAnyRole(user, roles) {
  return roles.some(r => hasRole(user, r));
}

/**
 * Returns true if the user's role grants access to sensitive fields.
 * When true, the frontend shows a per-field "Reveal" button.
 * When false, the field is shown as "Restricted" (backend returned null).
 */
export function canRevealSensitive(user) {
  return Array.isArray(user?.roles) &&
    user.roles.some(r => SENSITIVE_ACCESS_ROLES.has(r));
}

/** Returns true if the user can access admin configuration screens. */
export function canAccessAdmin(user) {
  return hasAnyRole(user, ADMIN_PORTAL_ROLES);
}

/** Returns true if the user has the STUDENT role. */
export function isStudent(user) {
  return hasRole(user, ROLES.STUDENT);
}

/**
 * Returns true if the user may browse the resident directory.
 * Mirrors STAFF_ROLES on the backend (ResidentController).
 * Students are excluded — they access their own record via /me.
 */
export function canViewResidentDirectory(user) {
  return hasAnyRole(user, [
    ROLES.ADMIN,
    ROLES.HOUSING_ADMINISTRATOR,
    ROLES.DIRECTOR,
    ROLES.RESIDENT_DIRECTOR,
    ROLES.RESIDENT_ASSISTANT,
    ROLES.RESIDENCE_STAFF,
    ROLES.STAFF,
  ]);
}

/** Returns a human-readable label for a role string. */
export function roleLabel(role) {
  const labels = {
    [ROLES.ADMIN]:                 'Administrator',
    [ROLES.DIRECTOR]:              'Director',
    [ROLES.HOUSING_ADMINISTRATOR]: 'Housing Administrator',
    [ROLES.RESIDENT_DIRECTOR]:     'Resident Director',
    [ROLES.RESIDENT_ASSISTANT]:    'Resident Assistant',
    [ROLES.RESIDENCE_STAFF]:       'Residence Staff',
    [ROLES.STAFF]:                 'Staff',
    [ROLES.STUDENT]:               'Student',
    [ROLES.READ_ONLY]:             'Read Only',
  };
  return labels[role] ?? role;
}
