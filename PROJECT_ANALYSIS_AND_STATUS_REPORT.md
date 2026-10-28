# SAGE (SIH 26262) — Comprehensive Workspace & Implementation Status Report

**Generated On**: October 27, 2026  
**Platform Name**: SAGE — Institutional Approval & Compliance Platform  
**Hackathon Problem Statement**: AICTE Smart India Hackathon (SIH 26262)  
**Target Architecture**: Spring Boot 3.4.3 (Java 21 LTS) + PostgreSQL 18.6 + Dual React 19.3.1 Frontend + MinIO  

---

## 1. Executive Summary

This repository contains the complete implementation of **SAGE (Institutional Approval & Compliance Platform)**, developed for AICTE (Ministry of Education) under Problem Statement **SIH 26262**.

The platform orchestrates the technical education approval cycle governed by the AICTE **Approval Process Handbook (APH)**:
- **Approval Lifecycles**: Extension of Approval (EoA), New Campus Setup, Intake Variations, and Closure Approvals.
- **Scrutiny State Machine**: Documentary Scrutiny, Formal Deficiency Notices, Return to College, Institutional Rectification, and Re-scrutiny.
- **EVC Inspections**: Expert Visit Committee (EVC) scheduling, panel roster allocation, visit execution, and 48-hour report submission.
- **Statutory Compliance**: Ongoing compliance undertaking verification, affidavit tracking, and expiring instrument tracking (fire NOCs, 30-year land deeds).
- **Administrative Governance**: Dedicated administrative portal for user management, account status controls (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`), and immutable audit log exploration.

---

## 2. Platform Architecture & Service Topology

The system enforces a strict architectural boundary: **the backend owns business logic, state machines, regulatory validation, and authorization**.

| Component | Port | Directory | Technology | Role |
| :--- | :---: | :--- | :--- | :--- |
| **SAGE User Portal** | `5173` | `frontend/` | React 19.3.1 + Vite 8.3.4 | 7 Role-Adaptive Dashboards & Scrutiny Dossiers |
| **SAGE Admin Panel** | `5174` | `admin/` | React 19.3.1 + Vite (`strictPort`) | Administrative User Directory & Audit Log Explorer |
| **Spring Boot REST API** | `8080` | `src/` | Spring Boot 3.4.3 (Java 21) | Stateless REST API, State Machine & Security Perimeter |
| **PostgreSQL Database** | `5432` | `compose.yaml` | PostgreSQL 18.6 | Persistent Relational Store (Flyway V1 to V6) |
| **MinIO Object Store** | `9000/9001` | `compose.yaml` | MinIO Server | S3-Compatible Statutory Document Attachment Store |

---

## 3. Work Completed Till Now

### 3.1 Database Persistence (Flyway Migrations V1 – V6)
All database schema changes are managed via Flyway versioned migrations (`src/main/resources/db/migration/`):
1. **`V1__initial_schema.sql`**: Core `institutions`, `applications` (with optimistic locking `@Version`), and `audit_events` tables.
2. **`V2__workflow_and_application_extensions.sql`**: State machine attributes (`current_stage`, `owner_name`, `owner_id`, `due_date`, `observations`, `deficiencies_json`) and `workflow_transitions` history table.
3. **`V3__evc_operations.sql`**: `evc_visits` table with auto-backfill for applications in `EVC scheduling`.
4. **`V4__admin_and_user_management.sql`**: Persistent `users` directory, BCrypt hashed passwords, account status tracking, audit event metadata extensions (`outcome`, `request_id`), and seed identities.
5. **`V5__application_submission_workflow.sql`**: Dynamic form data JSON, snapshot freezing (`submitted_snapshot_json`), and `application_documents` registry.
6. **`V6__application_drafts_and_safe_promotion.sql`**: Staging table `application_drafts` for un-promoted drafts, MinIO document association, and safe atomic promotion to official applications.

---

### 3.2 Backend Implementation (`src/main/java/in/gov/aicte/platform/`)
- **Workflow Engine (`WorkflowService.java`)**:
  - Enforces atomic state transitions: `RETURN_FOR_CORRECTION`, `SUBMIT_CORRECTIONS`, `COMPLETE_SCRUTINY`.
  - Atomic transactions (`@Transactional`) updating application status, appending to `workflow_transitions`, and logging to `audit_events`.
  - Concurrency safety with JPA Optimistic Locking (`@Version`), preventing concurrent overwrites.
- **EVC Operations (`EvcService.java`, `EvcController.java`)**:
  - Full REST lifecycle for EVC visits: `scheduleVisit`, `completeVisit`, `submitReport`, `rescheduleVisit`.
  - Automatically advances linked application to `Committee review` upon report submission.
- **Admin Management (`AdminService.java`, `AdminController.java`)**:
  - User search with pagination, filtering by role/status/region, user creation, profile update, and account status updates (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`) with mandatory audit reasoning.
  - Privilege escalation safeguards and root administrator (`USR-ADM-01`) lockout protection.
- **Drafts & Promotion (`ApplicationDraftService.java`, `ApplicationDraftController.java`)**:
  - Persistent draft auto-save, statutory checklist validation, MinIO document upload/download/deletion, and promotion to official applications table.
- **Security & Authorization (`SecurityConfig.java`, `DevTokenService.java`)**:
  - Stateless Bearer authentication with `@EnableMethodSecurity`.
  - Server-managed Dev Token Bridge for rapid role-switching during evaluations.

---

### 3.3 Dual Frontend Portals (`frontend/` & `admin/`)
- **User Portal (`frontend/`, Port 5173)**:
  - Role switcher header supporting 7 roles.
  - 7 Role-Adaptive Dashboards (`Institution Rep`, `Scrutiny Officer`, `Regional Officer`, `EVC Coordinator`, `Compliance Reviewer`, `Senior Management`, `Administrator`).
  - Applications Explorer (`/applications`) with multi-attribute filtering.
  - 7-Tab Application Detail Dossier (`/applications/:id`) with real-time state machine action buttons.
  - 5-Step Application Wizard (`/applications/new`) with catalogue selection, auto-save drafts, document upload checklist, and submission.
  - EVC Inspection Workbench (`/evc`) with scheduling and report filing.
- **Dedicated SAGE Admin Panel (`admin/`, Port 5174)**:
  - System Overview (`/overview`) with user breakdown and role metrics.
  - User Directory (`/users`) with search, filter, creation modal, edit modal, and status change modal.
  - Immutable Audit Explorer (`/audit-logs`) with multi-attribute filtering.
  - User Activity Timeline (`/activity`).

---

### 3.4 Automated Testing Evidence (68 Backend Tests, 100% Passing)

All 10 test suites passed cleanly with **0 failures and 0 errors** (verified in `target/surefire-reports`):

| Test Suite | Tests Run | Failures | Errors | Result |
| :--- | :---: | :---: | :---: | :---: |
| `AdminSecurityAndManagementIntegrationTest` | 11 | 0 | 0 | PASSED |
| `AdminServiceUnitTest` | 7 | 0 | 0 | PASSED |
| `ApplicationDraftSecurityAndPromotionIntegrationTest` | 7 | 0 | 0 | PASSED |
| `ApplicationSubmissionWorkflowIntegrationTest` | 5 | 0 | 0 | PASSED |
| `EvcOperationsIntegrationTest` | 8 | 0 | 0 | PASSED |
| `HealthEndpointTest` | 2 | 0 | 0 | PASSED |
| `InstitutionAndApplicationApiIntegrationTest` | 9 | 0 | 0 | PASSED |
| `PortalAuthenticationSeparationIntegrationTest` | 8 | 0 | 0 | PASSED |
| `PostgresPersistenceIntegrationTest` | 2 | 0 | 0 | PASSED |
| `WorkflowAndSecurityIntegrationTest` | 9 | 0 | 0 | PASSED |
| **Total Backend Tests** | **68** | **0** | **0** | **100% Passing** |

Frontend tests:
- `evcLifecycle.test.js`: Validates 5 EVC visit status stages and completion guards.
- `draftPromotionSync.test.js`: Validates draft promotion invariants and store synchronization.

---

## 4. What Is Working (Live vs. Demo State)

| Module / Feature | Persistence State | Working Status |
| :--- | :--- | :--- |
| **Backend REST API** | PostgreSQL 18.6 | **100% Live**: Full Spring Boot 3.4.3 API surface with Flyway migrations. |
| **Authentication & RBAC** | Dev Token Bridge | **100% Live**: Server-side role resolution and Bearer token issuance. |
| **Application List & Search** | PostgreSQL 18.6 | **100% Live**: Real database records loaded via `@EntityGraph`. |
| **Scrutiny State Machine** | PostgreSQL 18.6 | **100% Live**: Atomic returns, rectifications, clearances with optimistic locking. |
| **EVC Inspection Workbench** | PostgreSQL 18.6 | **100% Live**: Scheduling, panel assignment, visit completion, report submission. |
| **SAGE Admin Panel** | PostgreSQL 18.6 | **100% Live**: User management, status toggles, BCrypt passwords, audit log querying. |
| **Draft Creation & Promotion** | PostgreSQL + MinIO | **100% Live**: Multi-step wizard, document attachments, and promotion. |
| **Audit Log Trail** | PostgreSQL 18.6 | **100% Live**: `workflow_transitions` and `audit_events` tables with JSON serialization. |
| **Compliance Obligations (`/compliance`)** | React In-Memory | **Simulated**: In-memory UI workflow; backend persistence mapped for Phase 2. |
| **Standalone Document Registry (`/documents`)** | React In-Memory | **Simulated**: Document preview for expiring certificates; draft uploads use MinIO, but standalone registry is in-memory. |
| **Alerts & Escalations (`/alerts`)** | React In-Memory | **Simulated**: UI simulates handbook SLA breach notices and gateway retries. |
| **Executive Dashboards** | Hybrid | **Live/Calculated**: Real counts derived from backend applications and EVC data. |

---

## 5. Existing Technical Reports in the Workspace

The following comprehensive reports already exist in the repository:
1. `SIH26262_Complete_Technical_Report.md` (and `.pdf`): 86 KB comprehensive architecture reference manual.
2. `SIH26262_Database_Architecture_Report.md` (and `.pdf`): 33 KB database manual, ER diagrams, Flyway migrations, and SQL verification cookbook.
3. `SIH26262_API_Documentation.md` (and `.pdf`): 17 KB complete REST API specification and cURL examples.
4. `SIH26262_Team_Handover_Guide.md` (and `.pdf`): 20 KB presentation guide, fast-start guide, and 18 judge Q&As.
5. `docs/FRONTEND_COMPLETE_DOCUMENTATION.md`: 75 KB complete React frontend architecture and component mapping.
6. `docs/SIH26262_Backend_Connected_UI_Testing_Guide.md` (and `.pdf`): Step-by-step UI testing guide with PostgreSQL verification.
