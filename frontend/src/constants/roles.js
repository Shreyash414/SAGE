export const ROLES = {
  INSTITUTION_REP: 'Institution Representative',
  REGIONAL_OFFICER: 'Regional Officer',
  SCRUTINY_OFFICER: 'Scrutiny Officer',
  EVC_COORDINATOR: 'EVC Coordinator',
  EVC_MEMBER: 'EVC Member',
  COMPLIANCE_REVIEWER: 'Compliance Reviewer',
  SENIOR_MANAGEMENT: 'Senior Management',
  ADMINISTRATOR: 'Administrator',
};

// Roles permitted to sign into the regular user-facing portal (port 5173).
// Administrator accounts are strictly restricted to the dedicated SAGE Admin Panel (port 5174).
export const USER_PORTAL_ROLES = [
  ROLES.INSTITUTION_REP,
  ROLES.REGIONAL_OFFICER,
  ROLES.SCRUTINY_OFFICER,
  ROLES.EVC_COORDINATOR,
  ROLES.EVC_MEMBER,
  ROLES.COMPLIANCE_REVIEWER,
  ROLES.SENIOR_MANAGEMENT,
];

