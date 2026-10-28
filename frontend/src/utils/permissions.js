import { ROLES } from '../constants/roles.js';

export const NAVIGATION_ITEMS = [
  {
    path: '/dashboard',
    label: 'Dashboard',
    icon: 'LayoutDashboard',
    allowedRoles: [
      ROLES.INSTITUTION_REP,
      ROLES.REGIONAL_OFFICER,
      ROLES.SCRUTINY_OFFICER,
      ROLES.EVC_COORDINATOR,
      ROLES.EVC_MEMBER,
      ROLES.COMPLIANCE_REVIEWER,
      ROLES.SENIOR_MANAGEMENT,
      ROLES.ADMINISTRATOR,
    ],
  },
  {
    path: '/applications',
    label: 'Applications',
    icon: 'FileText',
    allowedRoles: [
      ROLES.INSTITUTION_REP,
      ROLES.REGIONAL_OFFICER,
      ROLES.SCRUTINY_OFFICER,
      ROLES.EVC_COORDINATOR,
      ROLES.COMPLIANCE_REVIEWER,
      ROLES.SENIOR_MANAGEMENT,
      ROLES.ADMINISTRATOR,
    ],
  },
  {
    path: '/evc',
    label: 'EVC Operations',
    icon: 'CalendarClock',
    allowedRoles: [
      ROLES.REGIONAL_OFFICER,
      ROLES.EVC_COORDINATOR,
      ROLES.SENIOR_MANAGEMENT,
      ROLES.ADMINISTRATOR,
    ],
  },
  {
    path: '/compliance',
    label: 'Compliance Review',
    icon: 'CheckSquare',
    allowedRoles: [
      ROLES.INSTITUTION_REP,
      ROLES.REGIONAL_OFFICER,
      ROLES.COMPLIANCE_REVIEWER,
      ROLES.SENIOR_MANAGEMENT,
      ROLES.ADMINISTRATOR,
    ],
  },
  {
    path: '/documents',
    label: 'Document Registry',
    icon: 'FolderArchive',
    allowedRoles: [
      ROLES.INSTITUTION_REP,
      ROLES.REGIONAL_OFFICER,
      ROLES.SCRUTINY_OFFICER,
      ROLES.COMPLIANCE_REVIEWER,
      ROLES.SENIOR_MANAGEMENT,
      ROLES.ADMINISTRATOR,
    ],
  },
  {
    path: '/alerts',
    label: 'Alerts & Escalations',
    icon: 'AlertTriangle',
    allowedRoles: [
      ROLES.REGIONAL_OFFICER,
      ROLES.SCRUTINY_OFFICER,
      ROLES.EVC_COORDINATOR,
      ROLES.COMPLIANCE_REVIEWER,
      ROLES.SENIOR_MANAGEMENT,
      ROLES.ADMINISTRATOR,
    ],
  },
  {
    path: '/reports',
    label: 'Reports & Analytics',
    icon: 'BarChart3',
    allowedRoles: [
      ROLES.REGIONAL_OFFICER,
      ROLES.SENIOR_MANAGEMENT,
      ROLES.ADMINISTRATOR,
    ],
  },
];

export const getNavItemsForRole = (role) => {
  if (role === ROLES.EVC_MEMBER) {
    return [
      { path: '/dashboard?tab=overview', label: 'Overview', icon: 'LayoutDashboard' },
      { path: '/dashboard?tab=inspections', label: 'My Inspections', icon: 'ClipboardCheck' },
      { path: '/dashboard?tab=schedule', label: 'Visit Schedule', icon: 'CalendarDays' },
      { path: '/dashboard?tab=institutes', label: 'Institute Details', icon: 'Building2' },
      { path: '/dashboard?tab=reports', label: 'Scrutiny & Reports', icon: 'FileCheck' },
      { path: '/dashboard?tab=profile', label: 'Member Profile', icon: 'UserCheck' },
    ];
  }
  return NAVIGATION_ITEMS.filter((item) => item.allowedRoles.includes(role));
};

export const hasRoutePermission = (role, path) => {
  // Allow all dashboard sub-paths and queries
  if (path === '/dashboard' || path.startsWith('/dashboard?') || path.startsWith('/dashboard/')) {
    return true;
  }
  // Application detail route shares permission with /applications
  if (path.startsWith('/applications/')) {
    return true;
  }
  if (role === ROLES.EVC_MEMBER) {
    // EVC Member access is strictly scoped to member dashboard and assigned applications
    return false;
  }
  const item = NAVIGATION_ITEMS.find((nav) => nav.path === path);
  if (!item) return false;
  return item.allowedRoles.includes(role);
};
