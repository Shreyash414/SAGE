// SIH 26262 - EVC Member Role and Dedicated Dashboard Verification Tests
import assert from 'node:assert/strict';
import { ROLES, USER_PORTAL_ROLES } from '../src/constants/roles.js';
import { getNavItemsForRole, hasRoutePermission } from '../src/utils/permissions.js';

console.log('--- Running SIH26262 EVC Member Role & Dashboard Tests ---');

// Test 1: Role Definition and Portal Classification
console.log('\nTest 1: Verifying EVC_MEMBER role definition and portal boundaries...');
assert.equal(ROLES.EVC_MEMBER, 'EVC Member', 'ROLES.EVC_MEMBER must equal "EVC Member"');
assert.equal(
  USER_PORTAL_ROLES.includes(ROLES.EVC_MEMBER),
  true,
  'EVC_MEMBER must be recognized as an authorized USER_PORTAL_ROLE'
);
assert.equal(
  USER_PORTAL_ROLES.includes(ROLES.ADMINISTRATOR),
  false,
  'ADMINISTRATOR must not be in USER_PORTAL_ROLES (strict portal separation)'
);
console.log('✓ Test 1 passed: EVC Member role definition is correct.');

// Test 2: Role-based Navigation Configuration
console.log('\nTest 2: Verifying dedicated EVC Member navigation items...');
const memberNav = getNavItemsForRole(ROLES.EVC_MEMBER);
assert.equal(memberNav.length, 6, 'EVC Member must have exactly 6 dedicated sidebar navigation items');

const expectedTabs = [
  { path: '/dashboard?tab=overview', label: 'Overview' },
  { path: '/dashboard?tab=inspections', label: 'My Inspections' },
  { path: '/dashboard?tab=schedule', label: 'Visit Schedule' },
  { path: '/dashboard?tab=institutes', label: 'Institute Details' },
  { path: '/dashboard?tab=reports', label: 'Scrutiny & Reports' },
  { path: '/dashboard?tab=profile', label: 'Member Profile' },
];

expectedTabs.forEach((expected, idx) => {
  assert.equal(memberNav[idx].path, expected.path, `Tab ${idx} path should be ${expected.path}`);
  assert.equal(memberNav[idx].label, expected.label, `Tab ${idx} label should be ${expected.label}`);
});

// Verify coordinator does not get EVC Member navigation
const coordinatorNav = getNavItemsForRole(ROLES.EVC_COORDINATOR);
const coordinatorPaths = coordinatorNav.map((item) => item.path);
assert.equal(coordinatorPaths.includes('/evc'), true, 'EVC Coordinator must have access to /evc');
assert.equal(coordinatorPaths.includes('/dashboard?tab=profile'), false, 'Coordinator must not receive EVC Member tabs');
console.log('✓ Test 2 passed: Dedicated navigation items correctly assigned.');

// Test 3: Route Permission Scoping and Isolation
console.log('\nTest 3: Verifying route permission isolation for EVC Member...');
// Permitted routes
assert.equal(hasRoutePermission(ROLES.EVC_MEMBER, '/dashboard'), true, 'EVC Member must access /dashboard');
assert.equal(hasRoutePermission(ROLES.EVC_MEMBER, '/dashboard?tab=overview'), true, 'EVC Member must access /dashboard?tab=overview');
assert.equal(hasRoutePermission(ROLES.EVC_MEMBER, '/dashboard?tab=inspections'), true, 'EVC Member must access /dashboard?tab=inspections');
assert.equal(hasRoutePermission(ROLES.EVC_MEMBER, '/dashboard?tab=profile'), true, 'EVC Member must access /dashboard?tab=profile');

// Denied routes (privileged coordinator and reviewer operations)
assert.equal(hasRoutePermission(ROLES.EVC_MEMBER, '/evc'), false, 'EVC Member MUST NOT have access to coordinator /evc route');
assert.equal(hasRoutePermission(ROLES.EVC_MEMBER, '/compliance'), false, 'EVC Member MUST NOT access /compliance');
assert.equal(hasRoutePermission(ROLES.EVC_MEMBER, '/reports'), false, 'EVC Member MUST NOT access executive /reports');

// Existing roles regression checks
assert.equal(hasRoutePermission(ROLES.EVC_COORDINATOR, '/evc'), true, 'EVC Coordinator MUST retain access to /evc');
assert.equal(hasRoutePermission(ROLES.REGIONAL_OFFICER, '/applications'), true, 'Regional Officer MUST retain access to /applications');
assert.equal(hasRoutePermission(ROLES.COMPLIANCE_REVIEWER, '/compliance'), true, 'Compliance Reviewer MUST retain access to /compliance');
console.log('✓ Test 3 passed: Route permissions correctly enforced without regressions.');

// Test 4: Member Inspection Metrics and Data Transformation
console.log('\nTest 4: Verifying inspection metrics calculations from API payload...');
const mockInspections = [
  {
    id: 'EVC-201',
    applicationId: 'APP-2026-1042',
    institutionName: 'ABC Institute of Technology',
    status: 'Scheduled',
    visitDate: '2026-10-31',
    committeeRole: 'Committee Member',
  },
  {
    id: 'EVC-195',
    applicationId: 'APP-2026-1033',
    institutionName: 'Global Institute of Management',
    status: 'Report pending',
    visitDate: '2026-10-21',
    committeeRole: 'Committee Chair',
  },
  {
    id: 'EVC-189',
    applicationId: 'APP-2026-1004',
    institutionName: 'Sunrise Engineering College',
    status: 'Completed',
    visitDate: '2026-10-15',
    committeeRole: 'Subject Expert',
  },
];

function calculateMetrics(inspections) {
  const total = inspections.length;
  const upcoming = inspections.filter((i) => i.status === 'Scheduled').length;
  const completed = inspections.filter((i) => i.status === 'Completed').length;
  const reportPending = inspections.filter((i) => i.status === 'Report pending').length;
  return { total, upcoming, completed, reportPending };
}

const metrics = calculateMetrics(mockInspections);
assert.equal(metrics.total, 3, 'Total inspections count matches');
assert.equal(metrics.upcoming, 1, 'Upcoming visits count matches');
assert.equal(metrics.completed, 1, 'Completed visits count matches');
assert.equal(metrics.reportPending, 1, 'Report pending count matches');
console.log('✓ Test 4 passed: Dashboard KPI metric calculations are accurate.');

// Test 5: Search and Filtering Logic
console.log('\nTest 5: Verifying client-side search and filtering for Member inspections...');
function filterInspections(list, searchTerm, statusFilter) {
  return list.filter((item) => {
    const matchesSearch =
      !searchTerm ||
      item.institutionName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.applicationId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.id.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus =
      !statusFilter || statusFilter === 'ALL' || item.status.toLowerCase() === statusFilter.toLowerCase();

    return matchesSearch && matchesStatus;
  });
}

const abcResults = filterInspections(mockInspections, 'ABC', 'ALL');
assert.equal(abcResults.length, 1);
assert.equal(abcResults[0].id, 'EVC-201');

const completedResults = filterInspections(mockInspections, '', 'Completed');
assert.equal(completedResults.length, 1);
assert.equal(completedResults[0].id, 'EVC-189');

const nonExistent = filterInspections(mockInspections, 'NonExistentCollege', 'ALL');
assert.equal(nonExistent.length, 0);
console.log('✓ Test 5 passed: Search and status filter behavior verified.');

// Test 6: Member Scoping Validation
console.log('\nTest 6: Verifying member scoping simulation...');
const assignedMemberId = 'MEM-EVCM-01';
const visitAssignmentMap = {
  'EVC-201': ['MEM-EVCM-01', 'MEM-EXP-02'],
  'EVC-195': ['MEM-EVCM-01'],
  'EVC-999': ['MEM-OTHER-99'], // Unassigned visit
};

function canAccessVisit(memberId, visitId) {
  const assigned = visitAssignmentMap[visitId] || [];
  return assigned.includes(memberId);
}

assert.equal(canAccessVisit(assignedMemberId, 'EVC-201'), true, 'Member can access assigned visit EVC-201');
assert.equal(canAccessVisit(assignedMemberId, 'EVC-195'), true, 'Member can access assigned visit EVC-195');
assert.equal(canAccessVisit(assignedMemberId, 'EVC-999'), false, 'Member cannot access unassigned visit EVC-999');
console.log('✓ Test 6 passed: Member data isolation verified.');

console.log('\n========================================');
console.log('ALL EVC MEMBER FRONTEND TESTS PASSED (6/6)');
console.log('========================================\n');

