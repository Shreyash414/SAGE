# SAGE — Institutional Approval & Compliance Platform

**Official Name**: SAGE  
**Full Name**: Institutional Approval & Compliance Platform  
**Architecture**: Spring Boot 3.4.3 REST API + PostgreSQL 18 + Dual React 19 Frontend (User Portal & Dedicated Admin Panel)

---

## 1. Platform Overview

**SAGE — Institutional Approval & Compliance Platform** is an enterprise-grade governance system designed to orchestrate technical institution approval lifecycles, deficiency rectifications, statutory compliance monitoring, Expert Visit Committee (EVC) operations, and administrative access control.

The platform enforces the architectural mandate that **the backend owns business logic, state machines, regulatory validation, and authorization**:
- **User Portal (Port 5173)**: Serves institutional applicants, scrutiny officers, regional officers, compliance reviewers, and committee coordinators.
- **SAGE Admin Panel (Port 5174)**: Dedicated administrative entry point with `strictPort: true` providing authorized administrators (`ROLE_ADMIN` / `ROLE_ADMINISTRATOR`) with full user-management directory, status lifecycle controls, and immutable audit log exploration.
- **Spring Boot REST API (Port 8080)**: Central application server with stateless Bearer authentication, method security, BCrypt credential verification, and OpenAPI documentation.
- **PostgreSQL Database (Port 5432)**: Relational persistent store managed via Flyway migrations (V1 through V4).

> **Important Security Clarification**:  
> A different port provides architectural and network separation, **not** a security boundary. All privileged administrative operations are strictly protected by backend authentication and persisted role authorization (`ROLE_ADMIN` / `ROLE_ADMINISTRATOR`). Non-admin users are rejected with `403 Forbidden` at the API layer.

---

## 2. Port Topology & Service Configuration

All services use designated default ports configured explicitly without silent fallbacks:

| Service | Port | Directory | Configuration / Command |
| :--- | :--- | :--- | :--- |
| **SAGE User Portal** | `5173` | `frontend/` | `npm run dev -- --host 0.0.0.0 --port 5173` |
| **SAGE Admin Panel** | `5174` | `admin/` | `npm run dev -- --host 0.0.0.0 --port 5174 --strictPort` |
| **Backend REST API** | `8080` | Root | `./mvnw spring-boot:run` (Swagger at `/swagger-ui.html`) |
| **PostgreSQL Database** | `5432` | Root | `compose.yaml` (Container: `aicte-postgres`) |

---

## 3. Technology Stack & Component Architecture

| Component | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Language** | Java (OpenJDK) | `21` LTS | Backend runtime and compiler |
| **Framework** | Spring Boot | `3.4.3` | Application framework (Web, Data JPA, Security) |
| **Build Tool** | Apache Maven | `3.10.0` | Build automation via Maven Wrapper (`./mvnw`) |
| **Database** | PostgreSQL | `18.6` | Relational persistent store (`aicte_platform_db`) |
| **Migrations** | Flyway | `10.20.1` | Automated schema migrations (`V1` to `V4`) |
| **ORM / JPA** | Hibernate | `6.6.8.Final` | Persistence provider with entity validation |
| **Security** | Spring Security | `6.4.3` | Bearer token auth, `@EnableMethodSecurity`, BCrypt hashing |
| **API Docs** | springdoc-openapi | `2.8.5` | OpenAPI 3.0 specification & Swagger UI |
| **User Frontend** | React 19 + Vite | React `19.3.1`, Vite `8.3.5` | User-facing portal (located in `frontend/`, port 5173) |
| **Admin Panel** | React 19 + Vite | React `19.3.1`, Vite `8.3.5` | Dedicated Admin Console (located in `admin/`, port 5174) |

---

## 4. Repository Layout

```text
SIH2/
├── admin/                             # Dedicated SAGE Admin Frontend (Port 5174)
│   ├── src/                           # Pages, layout, user management, audit explorer
│   ├── index.html                     # SAGE Admin Console title & icons
│   ├── package.json                   # Dependencies, build, preview scripts
│   └── vite.config.js                 # Port 5174 configuration with strictPort: true
├── frontend/                          # User-facing SAGE Portal (Port 5173)
│   ├── src/                           # Workspaces, application dossiers, EVC operations
│   ├── tests/                         # EVC lifecycle automated tests
│   ├── package.json                   # Dependencies and build scripts
│   └── vite.config.js                 # Port 5173 configuration
├── src/                               # Spring Boot 3.4.3 backend
│   ├── main/java/in/gov/aicte/platform/
│   │   ├── admin/                     # AdminController, AdminService, DTOs
│   │   ├── audit/                     # AuditEvent JPA entity and repository
│   │   ├── auth/                      # DevBearerAuthenticationFilter, DevTokenService
│   │   ├── config/                    # SecurityConfig (roles, CORS, BCrypt)
│   │   ├── user/                      # User JPA entity and UserRepository
│   │   ├── application/               # Application dossiers & approval entities
│   │   ├── workflow/                  # Workflow state machine & transition history
│   │   └── evc/                       # Expert Visit Committee scheduling
│   └── main/resources/db/migration/   # Flyway migrations V1, V2, V3, and V4
├── compose.yaml                       # Docker Compose PostgreSQL service
└── run.sh                             # Master service runner (DB -> API -> User -> Admin)
```

---

## 5. Master Startup Workflow (`run.sh`)

The updated `run.sh` script starts all platform services in topological order with process tracking and graceful cleanup:

```bash
# Execute master startup script
./run.sh
```

### Startup Sequence
1. **[1/4] PostgreSQL Readiness**: Checks port 5432 and attempts automated startup if inactive.
2. **[2/4] Spring Boot Backend**: Frees port 8080 if lingering, launches backend in background, and polls `/api/v1/health` until UP.
3. **[3/4] User Frontend**: Ensures `node_modules` exists and launches on port 5173.
4. **[4/4] SAGE Admin Panel**: Ensures dependencies are present and launches on port 5174 with `--strictPort`.
5. **Clean Shutdown**: Traps `SIGINT`, `SIGTERM`, and `EXIT` to gracefully terminate all child processes on `Ctrl+C`.

---

## 6. SAGE Administrative Subsystem

### 6.1 Administrator Provisioning
The platform is initialized with canonical identities via Flyway migration `V4__admin_and_user_management.sql`:

- **Primary Administrator**:
  - **User ID**: `USR-ADM-01`
  - **Email**: `admin@aicte-india.gov.in`
  - **Initial Password**: `SAGE@Admin2026!`
  - **Role**: `Administrator` (`ROLE_ADMIN`, `ROLE_ADMINISTRATOR`)
  - **Status**: `ACTIVE`

> **Production Recommendation**:  
> Upon initial deployment, the administrator password must be updated immediately via an authenticated administrative request or integrated enterprise OpenID Connect (OIDC) identity provider.

### 6.2 Administrative API Endpoints

All administrative endpoints reside under `/api/v1/admin/**` and require `ROLE_ADMIN` or `ROLE_ADMINISTRATOR`:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/admin/users` | Paginated search and filtering (by search query, role, status, region) |
| `GET` | `/api/v1/admin/users/{userId}` | Retrieve detailed user profile and recent target audit trail |
| `POST` | `/api/v1/admin/users` | Provision user account with validated unique email, permitted role, and BCrypt password |
| `PUT` | `/api/v1/admin/users/{userId}` | Update user attributes (server-side validation, self-demotion protection) |
| `PATCH` | `/api/v1/admin/users/{userId}/status` | Update status (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`) with mandatory audit reason |
| `GET` | `/api/v1/admin/audit-logs` | Paginated audit logs with criteria filtering (eventType, actorId, targetEntity) |
| `GET` | `/api/v1/admin/metrics` | Aggregate counts: total users, active, suspended, deactivated, role breakdown |
| `GET` | `/api/v1/admin/activity` | Recent user authentication timestamps and administrative changes |

### 6.3 Security Controls & Invariants
1. **Server-Side Authorization**: Hiding frontend routes or using a separate port is not treated as security. Every administrative endpoint is guarded with `@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")` and `SecurityConfig` rules.
2. **Actor Identity Derivation**: The acting administrator's ID is derived directly from the authenticated `SecurityContext`, never accepted from client-supplied request bodies.
3. **Privilege Escalation Protection**: Users cannot grant themselves administrator roles. Administrators are prevented from demoting their own account to prevent lockout.
4. **Root Administrator Protection**: The canonical system account (`USR-ADM-01`) cannot be suspended or deactivated.
5. **Immediate Token Invalidation**: When an account is deactivated or suspended, active tokens are revoked and subsequent requests are rejected.
6. **No Secret Leakage**: Password hashes and tokens are `@JsonIgnore` annotated and never returned in API responses or logged in audit records.

---

## 7. Database Migrations

- **`V1__initial_schema.sql`**: Core canonical institutions, applications, and audit_events tables.
- **`V2__workflow_and_application_extensions.sql`**: Workflow engine attributes and workflow_transitions table.
- **`V3__evc_operations.sql`**: Expert Visit Committee (EVC) operations schema and inspection backfills.
- **`V4__admin_and_user_management.sql`**: Persistent `users` directory, unique constraints, status tracking (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`), audit event extensions (`outcome`, `request_id`), and canonical identity seed.

---

## 8. Verification & Test Execution

### 8.1 Backend Automated Tests (100% Passing)
Execute backend test suites:
```bash
# Run Admin security and authorization tests
./mvnw test -Dtest=AdminSecurityAndManagementIntegrationTest

# Run AdminService business logic and invariant tests
./mvnw test -Dtest=AdminServiceUnitTest

# Run Public health endpoint tests
./mvnw test -Dtest=HealthEndpointTest
```

### 8.2 Frontend Builds & Verification
```bash
# Verify SAGE Admin Panel (Port 5174)
cd admin
npm run build

# Verify SAGE User Portal (Port 5173)
cd ../frontend
npm test
npm run build
```

### 8.3 Script Validation
```bash
bash -n run.sh
```

---

## 9. Manual Rehearsal Walkthrough

1. **Start Services**: Run `./run.sh`.
2. **Access Admin Panel**: Open `http://localhost:5174` in your browser.
3. **Sign In**: Enter `admin@aicte-india.gov.in` and `SAGE@Admin2026!`.
4. **User Management**:
   - Navigate to **User Management**.
   - Search for `Singh` or filter by role `Scrutiny Officer`.
   - Click **Provision User** to add a new officer. Verify uniqueness constraints and role restrictions.
   - Click **Status** on an existing user to change status to `SUSPENDED` with a documented reason.
   - Verify that the status modification is logged to **Audit Logs**.
5. **User Portal**: Open `http://localhost:5173` to verify that normal user workflows operate independently.
