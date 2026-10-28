# SAGE (SIH 26262) — EVC Member Role & Dedicated Dashboard Specification

## 1. Overview & Objective

In accordance with AICTE approval regulations and SIH Problem Statement SIH26262, the **EVC Member** (`EVC_MEMBER` / `ROLE_EVC_MEMBER`) role represents an external academic or industrial subject expert appointed to an Expert Visit Committee (EVC). 

An EVC Member participates in on-site or hybrid evaluation visits, examines physical and academic infrastructure, scrutinizes institutional compliance documents, and contributes to the joint EVC inspection report.

This implementation provides:
1. **Normalized Database Schema** via Flyway migration `V10__evc_member_role_and_management.sql` for member profiles and committee assignments.
2. **Strict Spring Security Authorization** ensuring members only access inspections, institutions, and documents to which they are assigned.
3. **Coordinator Operation Denial**: Privileged operations (visit scheduling, rescheduling, finalizing completion, drafting official reports) remain exclusive to `EVC_COORDINATOR` and return `403 Forbidden` if attempted by an `EVC_MEMBER`.
4. **Portal Isolation**: Member accounts belong to `USER_PORTAL_ROLES` on port 5173. Administrative token endpoints reject `EVC_MEMBER` with `403 Forbidden`.
5. **Admin Member Management**: Admin endpoints (`/api/v1/admin/evc-members/**`) and UI dropdowns to register, update, and manage committee assignments.
6. **Dedicated React 19 EVC Member Dashboard** with responsive tabs: Overview, My Inspections, Visit Schedule, Institute Details, Scrutiny & Deficiency Reports, and Profile (with self-service phone and availability updates).

---

## 2. Role Model and Authorization Rules

### 2.1 Role Identity
- **Display Name**: `EVC Member`
- **Internal Enum / Canonical Name**: `EVC_MEMBER`
- **Spring Security Granted Authority**: `ROLE_EVC_MEMBER`
- **Prefix in Admin ID Generation**: `EVCM` (e.g., `USR-EVCM-01`)
- **Portal Assignment**: User Portal (`http://localhost:5173`)

### 2.2 Security Matrix & Permission Boundaries

| Operation / Endpoint | `EVC_MEMBER` | `EVC_COORDINATOR` | `ADMINISTRATOR` | `REGIONAL_OFFICER` |
| :--- | :--- | :--- | :--- | :--- |
| **Authenticate via User Portal (`POST /api/v1/auth/token`)** | Allowed (200) | Allowed (200) | Blocked (403) | Allowed (200) |
| **Authenticate via Admin Portal (`POST /api/v1/auth/admin/token`)** | Blocked (403) | Blocked (403) | Allowed (200) | Blocked (403) |
| **Access Member Profile (`GET /api/v1/evc/member/profile`)** | Self Only (200) | Allowed (200) | Allowed (200) | Blocked (403) |
| **Update Member Profile (`PUT /api/v1/evc/member/profile`)** | Self Only (200) | Blocked (403) | Allowed via Admin | Blocked (403) |
| **List Assigned Inspections (`GET /api/v1/evc/member/inspections`)** | Assigned Only | All Visits | All Visits | Regional Visits |
| **View Member Dashboard (`GET /api/v1/evc/member/dashboard`)** | Scoped Metrics | N/A | N/A | N/A |
| **View Inspection Documents (`GET /api/v1/evc/member/inspections/{id}/documents`)** | Assigned Only (403 if unassigned) | All Visits | All Visits | Regional Visits |
| **Schedule Visit (`POST /api/v1/evc/visits/{id}/schedule`)** | **Denied (403)** | Allowed (200) | Allowed (200) | Allowed (200) |
| **Complete Visit (`POST /api/v1/evc/visits/{id}/complete`)** | **Denied (403)** | Allowed (200) | Allowed (200) | Blocked (403) |
| **Submit Report (`POST /api/v1/evc/visits/{id}/report`)** | **Denied (403)** | Allowed (200) | Allowed (200) | Blocked (403) |
| **Admin Member CRUD (`/api/v1/admin/evc-members/**`)** | **Denied (403)** | **Denied (403)** | Allowed (200) | **Denied (403)** |

---

## 3. Database Schema (Flyway V10)

Migration file: `src/main/resources/db/migration/V10__evc_member_role_and_management.sql`

### 3.1 `evc_member_profiles`
Stores domain-specific academic and professional profiles linked to user accounts.
```sql
CREATE TABLE evc_member_profiles (
    id VARCHAR(64) PRIMARY KEY,                         -- e.g. MEM-EVCM-01
    user_id VARCHAR(64) NOT NULL UNIQUE,                -- FK to users(user_id)
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(32),
    designation VARCHAR(255),                           -- e.g. Professor & Dean (Faculty of Engineering)
    organization VARCHAR(255),                          -- e.g. Indian Institute of Technology Roorkee
    specialization VARCHAR(255),                        -- e.g. Computer Science & Artificial Intelligence
    highest_qualification VARCHAR(64),                  -- e.g. Ph.D.
    total_experience_years INT DEFAULT 0,
    verification_status VARCHAR(64) NOT NULL DEFAULT 'VERIFIED',
    account_status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    availability_status VARCHAR(64) NOT NULL DEFAULT 'AVAILABLE',
    preferred_regions VARCHAR(255) DEFAULT 'ALL',
    max_visits_per_cycle INT DEFAULT 5,
    assigned_visits_count INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evc_member_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
```

### 3.2 `evc_member_assignments`
Maps members to scheduled or conducted EVC inspections.
```sql
CREATE TABLE evc_member_assignments (
    id VARCHAR(64) PRIMARY KEY,                         -- e.g. ASG-EVCM-01
    visit_id VARCHAR(64) NOT NULL,                      -- references evc_inspection_visits(id)
    member_id VARCHAR(64) NOT NULL,                     -- FK to evc_member_profiles(id)
    committee_role VARCHAR(64) NOT NULL DEFAULT 'MEMBER',-- CHAIR, MEMBER, CONVENOR, SPECIAL_INVITEE
    attendance_status VARCHAR(64) NOT NULL DEFAULT 'CONFIRMED',
    assigned_by VARCHAR(64),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    CONSTRAINT fk_evc_asg_member FOREIGN KEY (member_id) REFERENCES evc_member_profiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_evc_asg_visit_member UNIQUE (visit_id, member_id)
);
```

### 3.3 Seeded Baseline Demo Account
- **User ID**: `USR-EVCM-01`
- **Member ID**: `MEM-EVCM-01`
- **Name**: `Prof. Sunita Raman`
- **Email**: `member.evc@aicte-india.gov.in`
- **Role**: `EVC Member`
- **Designation**: `Professor & Head (Computer Science)`
- **Organization**: `Indian Institute of Technology Delhi`
- **Specialization**: `Computer Science & Engineering (AI/ML)`
- **Initial Assignments**: Seeded to `EVC-201` (`APP-2026-1042`, ABC Institute of Technology) and `EVC-195` (`APP-2026-1033`, Global Institute of Management).

---

## 4. API Endpoints Reference

### 4.1 EVC Member Endpoints (`/api/v1/evc/member/**`)

#### `GET /api/v1/evc/member/profile`
- **Description**: Returns the member profile of the authenticated user.
- **Authorization**: `ROLE_EVC_MEMBER`, `ROLE_ADMINISTRATOR`, `ROLE_EVC_COORDINATOR`
- **Response**: `200 OK`
```json
{
  "id": "MEM-EVCM-01",
  "userId": "USR-EVCM-01",
  "fullName": "Prof. Sunita Raman",
  "email": "member.evc@aicte-india.gov.in",
  "phone": "+91-9876543210",
  "designation": "Professor & Head (Computer Science)",
  "organization": "Indian Institute of Technology Delhi",
  "specialization": "Computer Science & Engineering (AI/ML)",
  "highestQualification": "Ph.D.",
  "totalExperienceYears": 22,
  "verificationStatus": "VERIFIED",
  "accountStatus": "ACTIVE",
  "availabilityStatus": "AVAILABLE",
  "preferredRegions": "Northern Region, Western Region",
  "maxVisitsPerCycle": 5,
  "assignedVisitsCount": 2
}
```

#### `PUT /api/v1/evc/member/profile`
- **Description**: Self-service profile updates (phone, preferred regions, availability status). Sensitive fields (name, email, verification status) are immutable by member.
- **Authorization**: `ROLE_EVC_MEMBER`
- **Request Body**:
```json
{
  "phone": "+91-9876543211",
  "availabilityStatus": "UNAVAILABLE",
  "preferredRegions": "Northern Region"
}
```
- **Response**: `200 OK`

#### `GET /api/v1/evc/member/dashboard`
- **Description**: Returns aggregated metrics, member profile summary, upcoming visit, and assigned inspections list in a single call.
- **Authorization**: `ROLE_EVC_MEMBER`
- **Response**: `200 OK`

#### `GET /api/v1/evc/member/inspections`
- **Description**: Returns all inspection visits explicitly assigned to the authenticated member.
- **Authorization**: `ROLE_EVC_MEMBER`
- **Response**: `200 OK` (Array of `EvcVisitResponse`)

#### `GET /api/v1/evc/member/inspections/{idOrReference}/documents`
- **Description**: Scrutiny reports, deficiency reports, and application documents for an assigned visit.
- **Authorization**: `ROLE_EVC_MEMBER` (Checks assignment; returns `403 Forbidden` if unassigned)
- **Response**: `200 OK`

---

### 4.2 Administrator Member Management (`/api/v1/admin/evc-members/**`)

#### `GET /api/v1/admin/evc-members`
- **Description**: List all EVC members with optional search, verification status, and availability status filters.
- **Authorization**: `ROLE_ADMINISTRATOR`

#### `POST /api/v1/admin/evc-members`
- **Description**: Provision a new EVC member account and profile.
- **Authorization**: `ROLE_ADMINISTRATOR`

#### `GET /api/v1/admin/evc-members/{id}`
- **Description**: Retrieve detailed profile and visit assignments for a member.
- **Authorization**: `ROLE_ADMINISTRATOR`

#### `PUT /api/v1/admin/evc-members/{id}`
- **Description**: Update member verification status, organization, max visits, etc.
- **Authorization**: `ROLE_ADMINISTRATOR`

#### `POST /api/v1/admin/evc-members/assignments`
- **Description**: Assign an EVC member to a visit committee.
- **Authorization**: `ROLE_ADMINISTRATOR`

#### `DELETE /api/v1/admin/evc-members/assignments/{visitId}/{memberId}`
- **Description**: Remove an EVC member from a visit committee.
- **Authorization**: `ROLE_ADMINISTRATOR`

---

## 5. Frontend Architecture & EVC Member Dashboard

### 5.1 Route & Tab Navigation
- **Route**: `http://localhost:5173/dashboard`
- **Query Parameter Tabs**:
  - `?tab=overview`: Summary KPIs, active inspection alerts, next upcoming visit, profile badge.
  - `?tab=inspections`: Table/cards of assigned inspections with search and status filtering.
  - `?tab=schedule`: Chronological timeline of upcoming and concluded physical visits.
  - `?tab=institutes`: Institute summaries, program details, addresses, and regions for assigned visits.
  - `?tab=reports`: Scrutiny recommendations, deficiency summaries, and authorized document access modal.
  - `?tab=profile`: Full profile details, credentials, and self-service availability/contact updater.

### 5.2 Security Enforcements
- Direct navigation to `/evc` (coordinator operations) is blocked by `hasRoutePermission(ROLES.EVC_MEMBER, '/evc') === false` and redirects to `/dashboard`.
- Token issuance for administrator accounts redirects to port 5174; EVC Member logins reject admin portal authentication with `403 Forbidden`.

---

## 6. How to Provision a New EVC Member Account

### Option A: Via SAGE Admin Console (Port 5174)
1. Sign in as Administrator (`admin@aicte-india.gov.in`).
2. Navigate to **User Management** -> click **Add User**.
3. Fill in details:
   - **Full Name**: e.g., `Dr. Anita Sharma`
   - **Email**: `anita.sharma@institution.edu.in`
   - **Role**: Select `EVC Member` from the role dropdown.
   - **Designation**: `Professor, Electrical Engineering`
   - **Region**: Select region.
4. Submit. The backend will automatically create the user account in `users` and provision a matching record in `evc_member_profiles`.

### Option B: Via Admin REST API
```bash
curl -X POST http://localhost:8080/api/v1/admin/evc-members \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Dr. Ramesh Joshi",
    "email": "r.joshi@nitk.ac.in",
    "phone": "+91-9811223344",
    "designation": "Professor & Dean",
    "organization": "National Institute of Technology Karnataka",
    "specialization": "Mechanical Systems & Automation",
    "highestQualification": "Ph.D.",
    "totalExperienceYears": 18,
    "preferredRegions": "Southern Region, Western Region",
    "maxVisitsPerCycle": 4
  }'
```

---

## 7. Automated Test Verification & Results

### 7.1 Backend Test Suites
Execute command:
```bash
./mvnw test -Dtest=AdminServiceUnitTest,AdminSecurityAndManagementIntegrationTest,HealthEndpointTest,PortalAuthenticationSeparationIntegrationTest,EvcMemberSecurityAndOperationsIntegrationTest
```

**Results:**
- Tests run: **42**
- Failures: **0**
- Errors: **0**
- Skipped: **0**
- Build Status: **BUILD SUCCESS**

#### Tests Included in `EvcMemberSecurityAndOperationsIntegrationTest`:
1. `shouldIssueTokenForEvcMemberAndDenyAdminPortalAccess`: Validates Dev token generation for `USR-EVCM-01` and verifies 403 Forbidden rejection when authenticating to `/api/v1/auth/admin/token`.
2. `shouldRetrieveEvcMemberProfileSuccessfully`: Verifies `/api/v1/evc/member/profile` returns seeded data for `USR-EVCM-01`.
3. `shouldAllowEvcMemberToUpdateOwnProfileContactAndAvailability`: Tests self-service update of phone and availability status.
4. `shouldRetrieveEvcMemberDashboardData`: Verifies `/api/v1/evc/member/dashboard` returns metrics, profile, and assigned visits.
5. `shouldDenyEvcMemberFromPerformingCoordinatorScheduling`: Verifies `POST /api/v1/evc/visits/{id}/schedule` returns `403 Forbidden` for `EVC_MEMBER`.
6. `shouldDenyEvcMemberFromPerformingCoordinatorVisitCompletion`: Verifies `POST /api/v1/evc/visits/{id}/complete` returns `403 Forbidden`.
7. `shouldDenyEvcMemberFromPerformingCoordinatorReportSubmission`: Verifies `POST /api/v1/evc/visits/{id}/report` returns `403 Forbidden`.
8. `shouldAllowEvcMemberToAccessDocumentsForAssignedVisit`: Verifies access to scrutiny documents for assigned visit `EVC-201`.
9. `shouldDenyEvcMemberFromAccessingDocumentsForUnassignedVisit`: Verifies `403 Forbidden` when attempting to access documents for an unassigned visit (`EVC-999`).
10. `shouldAllowAdministratorToManageEvcMembers`: Verifies admin can list, create, and update EVC members.
11. `shouldDenyEvcMemberFromAccessingAdminEvcMemberManagement`: Verifies member is denied from `/api/v1/admin/evc-members`.
12. `shouldDenyCoordinatorFromAccessingAdminEvcMemberManagement`: Verifies coordinator is denied from admin endpoints.
13. `shouldAllowAdministratorToAssignAndRemoveEvcMemberFromVisit`: Verifies committee assignment and removal.
14. `shouldDenyNonAdminFromAssigningEvcMemberToVisit`: Verifies non-admin cannot assign members to visits.

---

### 7.2 Frontend Test Suites
Execute command:
```bash
cd frontend && npm test
```

**Results:**
```
> sih26262_front@0.0.0 test
> node tests/evcLifecycle.test.js && node tests/draftPromotionSync.test.js && node tests/evcMemberRoleAndDashboard.test.js

ALL 6 EVC LIFECYCLE TESTS PASSED PERFECTLY!
ALL 5 FRONTEND DATA SYNCHRONIZATION REGRESSION TESTS PASSED PERFECTLY!
ALL 6 EVC MEMBER FRONTEND TESTS PASSED (6/6)
```

#### Frontend Builds:
- Frontend Vite Build (`cd frontend && npm run build`): **SUCCESS (0 errors, 1.23s)**
- Admin Vite Build (`cd admin && npm run build`): **SUCCESS (0 errors, 500ms)**
- Frontend Linter (`cd frontend && npm run lint`): **SUCCESS (0 errors)**

