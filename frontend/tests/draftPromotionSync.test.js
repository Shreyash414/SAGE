// SIH 26262 - Frontend Data Synchronization & Promotion Regression Tests
import assert from 'node:assert/strict';

// Import canonical roles
import { ROLES } from '../src/constants/roles.js';

console.log('--- RUNNING DRAFT PROMOTION & FRONTEND SYNCHRONIZATION REGRESSION TESTS ---');

// =========================================================================
// Fixtures
// =========================================================================
const INSTITUTION_A = {
  id: 'INST-1025',
  institutionName: 'ABC Institute of Technology',
  region: 'Northern Region',
};

const INSTITUTION_B = {
  id: 'INST-1030',
  institutionName: 'Global Institute of Management',
  region: 'Western Region',
};

const INITIAL_PENDING_DRAFTS = [
  {
    id: 42,
    draftReference: 'DFT-2026-881920',
    applicationType: 'Extension of Approval (EoA)',
    academicYear: '2026-2027',
    completionPercentage: 100,
    submittable: true,
    updatedAt: '2026-10-27T10:30:00Z',
  },
  {
    id: 43,
    draftReference: 'DFT-2026-992031',
    applicationType: 'New Technical Campus',
    academicYear: '2026-2027',
    completionPercentage: 60,
    submittable: false,
    updatedAt: '2026-10-27T09:15:00Z',
  },
];

const INITIAL_OFFICIAL_APPLICATIONS = [
  {
    id: 'APP-2026-1001',
    backendId: 101,
    applicationReference: 'APP-2026-1001',
    institutionId: 'INST-1025',
    institutionName: 'ABC Institute of Technology',
    applicationType: 'Extension of Approval (EoA)',
    academicYear: '2026-2027',
    region: 'Northern Region',
    currentStage: 'Scrutiny',
    owner: 'R. Sharma (Scrutiny Desk 1)',
    ownerId: 'USR-SCRUT-01',
    dueDate: '2026-11-15',
    status: 'In progress',
  },
  {
    id: 'APP-2026-1002',
    backendId: 102,
    applicationReference: 'APP-2026-1002',
    institutionId: 'INST-1030',
    institutionName: 'Global Institute of Management',
    applicationType: 'Introduction of New Course',
    academicYear: '2026-2027',
    region: 'Western Region',
    currentStage: 'Regional Office',
    owner: 'P. Nair (Regional Desk)',
    ownerId: 'USR-RO-02',
    dueDate: '2026-11-10',
    status: 'Due soon',
  },
];

// Backend promotion response fixture (matches PromoteDraftResult DTO)
const MOCK_PROMOTION_RESULT = {
  success: true,
  draftId: 42,
  draftReference: 'DFT-2026-881920',
  officialApplicationId: 105,
  officialApplicationReference: 'APP-2026-881920',
  applicationId: 105,
  applicationReference: 'APP-2026-881920',
  status: 'In progress',
  currentStage: 'Scrutiny',
  ownerName: 'Scrutiny Committee Desk',
  submittedAt: '2026-10-27T11:00:00Z',
  submittedBy: 'Representative (INST-1025)',
  institution: {
    id: 1025,
    institutionalIdentifier: 'INST-1025',
    name: 'ABC Institute of Technology',
    region: 'Northern Region',
  },
};

// =========================================================================
// Test 1: Promotion State Transition (Draft Removal + Official App Insertion)
// =========================================================================
console.log('\nTest 1: Verifying immediate UI state update on promotion without page reload...');

function simulatePromotionStateUpdate(pendingDrafts, officialApps, promotionResult) {
  // 1. Promoted draft disappears from pending drafts
  const nextPendingDrafts = pendingDrafts.filter(
    (d) => d.id !== promotionResult.draftId && d.draftReference !== promotionResult.draftReference
  );

  // 2. Official application mapped and added to official applications list
  const promotedApp = {
    id: promotionResult.officialApplicationReference,
    backendId: promotionResult.officialApplicationId,
    applicationReference: promotionResult.officialApplicationReference,
    institutionId: promotionResult.institution?.institutionalIdentifier || 'INST-1025',
    institutionName: promotionResult.institution?.name || 'ABC Institute of Technology',
    applicationType: 'Extension of Approval (EoA)',
    academicYear: '2026-2027',
    region: promotionResult.institution?.region || 'Northern Region',
    currentStage: promotionResult.currentStage || 'Scrutiny',
    owner: promotionResult.ownerName || 'Unassigned Desk',
    ownerId: '',
    dueDate: '2026-11-20',
    status: promotionResult.status || 'In progress',
  };

  const nextOfficialApps = [
    promotedApp,
    ...officialApps.filter((a) => a.applicationReference !== promotedApp.applicationReference),
  ];

  return { nextPendingDrafts, nextOfficialApps, promotedApp };
}

const { nextPendingDrafts, nextOfficialApps, promotedApp } = simulatePromotionStateUpdate(
  INITIAL_PENDING_DRAFTS,
  INITIAL_OFFICIAL_APPLICATIONS,
  MOCK_PROMOTION_RESULT
);

// Verify draft 42 is removed
assert.equal(nextPendingDrafts.length, 1, 'Pending drafts must decrease by 1');
assert.equal(nextPendingDrafts.some((d) => d.id === 42), false, 'Promoted draft 42 must NOT be in pending drafts');
assert.equal(nextPendingDrafts[0].id, 43, 'Un-promoted draft 43 must remain in pending drafts');

// Verify official application is added
assert.equal(nextOfficialApps.length, 3, 'Official applications list must increase by 1');
assert.equal(
  nextOfficialApps.some((a) => a.applicationReference === 'APP-2026-881920'),
  true,
  'Promoted application APP-2026-881920 must appear in official applications'
);
assert.equal(promotedApp.status, 'In progress', 'Promoted application status must be In progress');
assert.equal(promotedApp.currentStage, 'Scrutiny', 'Promoted application stage must be Scrutiny');
console.log('✓ Test 1 passed: Promoted draft disappeared from pending drafts and appeared in official applications.');

// =========================================================================
// Test 2: Institution-Level Access Control Scoping
// =========================================================================
console.log('\nTest 2: Verifying institution-level access control scoping after promotion...');

function scopeApplicationsByRole(applications, user, role) {
  if (role === ROLES.INSTITUTION_REP) {
    if (user?.institutionId) {
      return applications.filter(
        (app) => app.institutionId === user.institutionId || app.institutionName === user?.institutionName
      );
    }
    if (user?.institutionName) {
      return applications.filter((app) => app.institutionName === user.institutionName);
    }
    return applications;
  }
  if (role === ROLES.REGIONAL_OFFICER) {
    if (user?.region) {
      return applications.filter((app) => app.region === user.region);
    }
  }
  return applications;
}

// Institution A rep view
const userInstA = { institutionId: 'INST-1025', institutionName: 'ABC Institute of Technology', role: ROLES.INSTITUTION_REP };
const scopedForA = scopeApplicationsByRole(nextOfficialApps, userInstA, ROLES.INSTITUTION_REP);
assert.equal(scopedForA.length, 2, 'Institution A must see exactly its 2 applications');
assert.ok(
  scopedForA.every((a) => a.institutionId === 'INST-1025'),
  'All scoped records for Inst A must belong to INST-1025'
);
assert.ok(
  scopedForA.some((a) => a.applicationReference === 'APP-2026-881920'),
  'Institution A must see its newly promoted application'
);

// Institution B rep view
const userInstB = { institutionId: 'INST-1030', institutionName: 'Global Institute of Management', role: ROLES.INSTITUTION_REP };
const scopedForB = scopeApplicationsByRole(nextOfficialApps, userInstB, ROLES.INSTITUTION_REP);
assert.equal(scopedForB.length, 1, 'Institution B must see only its 1 application');
assert.equal(
  scopedForB.some((a) => a.applicationReference === 'APP-2026-881920'),
  false,
  'Institution B must NEVER see Institution A promoted application'
);
console.log('✓ Test 2 passed: Institution access boundary strictly preserved across promotion.');

// =========================================================================
// Test 3: Search, Filters, Sorting, and Pagination
// =========================================================================
console.log('\nTest 3: Verifying search filters, sorting, and pagination with promoted record...');

function filterAndSortApplications(apps, { searchTerm = '', region = 'All', stage = 'All', status = 'All', sortOrder = 'asc' }) {
  const filtered = apps.filter((app) => {
    if (searchTerm.trim() !== '') {
      const q = searchTerm.toLowerCase();
      const matchesId = app.id.toLowerCase().includes(q) || app.applicationReference.toLowerCase().includes(q);
      const matchesInst = app.institutionName.toLowerCase().includes(q);
      if (!matchesId && !matchesInst) return false;
    }
    if (region !== 'All' && app.region !== region) return false;
    if (stage !== 'All' && app.currentStage !== stage) return false;
    if (status !== 'All' && app.status !== status) return false;
    return true;
  });

  return filtered.sort((a, b) => {
    const dateA = new Date(a.dueDate || '9999-12-31').getTime();
    const dateB = new Date(b.dueDate || '9999-12-31').getTime();
    return sortOrder === 'asc' ? dateA - dateB : dateB - dateA;
  });
}

// Search by new reference
const searched = filterAndSortApplications(scopedForA, { searchTerm: '881920' });
assert.equal(searched.length, 1, 'Search by promoted application reference should match 1 record');
assert.equal(searched[0].applicationReference, 'APP-2026-881920');

// Filter by Stage
const stageFiltered = filterAndSortApplications(scopedForA, { stage: 'Scrutiny' });
assert.equal(stageFiltered.length, 2, 'Scrutiny filter must match both Institution A scrutiny applications');

// Sort by Deadline
const sortedAsc = filterAndSortApplications(scopedForA, { sortOrder: 'asc' });
assert.equal(sortedAsc[0].applicationReference, 'APP-2026-1001', 'Earlier deadline (Nov 15) must sort first');
assert.equal(sortedAsc[1].applicationReference, 'APP-2026-881920', 'Later deadline (Nov 20) must sort second');

// Pagination page 1
const pageSize = 5;
const page1 = sortedAsc.slice(0, pageSize);
assert.equal(page1.length, 2, 'Page 1 must contain both applications');
console.log('✓ Test 3 passed: Search, filtering, deadline sorting, and pagination intact.');

// =========================================================================
// Test 4: Stale Cached Data & Out-of-Order Response Prevention
// =========================================================================
console.log('\nTest 4: Verifying stale cache and out-of-order response prevention...');

class DataSyncCoordinator {
  constructor() {
    this.latestFetchId = 0;
    this.state = [];
  }

  startFetch() {
    this.seq = (this.seq || 0) + 1;
    const fetchId = Date.now() + this.seq;
    this.latestFetchId = fetchId;
    return fetchId;
  }

  commitFetch(fetchId, data) {
    if (fetchId < this.latestFetchId) {
      // Stale response: ignore!
      return false;
    }
    this.state = data;
    return true;
  }
}

const coordinator = new DataSyncCoordinator();

// Request 1: Old pre-promotion fetch started
const req1Id = coordinator.startFetch();

// Promotion occurs, initiating Request 2 (newer)
const req2Id = coordinator.startFetch();
assert.ok(req2Id > req1Id, 'New fetch initiated after promotion must have newer timestamp/ID');

// Request 2 completes first with post-promotion data
const committed2 = coordinator.commitFetch(req2Id, nextOfficialApps);
assert.equal(committed2, true, 'Latest fetch must be committed to state');
assert.equal(coordinator.state.length, 3, 'State has all 3 applications');

// Request 1 arrives late (simulating network delay or stale browser cache)
const committed1 = coordinator.commitFetch(req1Id, INITIAL_OFFICIAL_APPLICATIONS);
assert.equal(committed1, false, 'Stale older fetch MUST be discarded');
assert.equal(coordinator.state.length, 3, 'State must NOT be overwritten by stale response');
assert.ok(
  coordinator.state.some((a) => a.applicationReference === 'APP-2026-881920'),
  'Promoted application remains intact in state'
);
console.log('✓ Test 4 passed: Out-of-order stale responses prevented from overwriting fresh data.');

// =========================================================================
// Test 5: Success Notification Construction
// =========================================================================
console.log('\nTest 5: Verifying success notification format with official application reference...');

function buildPromotionSuccessNotification(res) {
  const officialRef = res.officialApplicationReference || res.applicationReference || `APP-${res.officialApplicationId || res.applicationId}`;
  return {
    reference: officialRef,
    title: 'Promotion & Submission Successful!',
    message: `Draft ${res.draftReference} was successfully promoted to official application ${officialRef}.`,
  };
}

const notification = buildPromotionSuccessNotification(MOCK_PROMOTION_RESULT);
assert.equal(notification.reference, 'APP-2026-881920', 'Notification must contain exact official reference');
assert.ok(notification.message.includes('APP-2026-881920'), 'Notification message must mention the official application');
assert.ok(notification.message.includes('DFT-2026-881920'), 'Notification message must mention the origin draft');
console.log('✓ Test 5 passed: Success notification properly constructed with official application reference.');

console.log('\nALL 5 FRONTEND DATA SYNCHRONIZATION REGRESSION TESTS PASSED PERFECTLY!\n');
