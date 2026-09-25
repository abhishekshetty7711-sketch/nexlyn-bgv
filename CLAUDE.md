# CLAUDE.md — Nexlyn BGV Platform

> This file is the single source of truth for building the Nexlyn BGV Platform.
> Read it fully at the start of every session. Follow the locked decisions exactly.
> If something here conflicts with a request, ask the user before deviating.
> Work through the phases in order under the autonomy rules in section 0.

---

## 0. How to work on this project

- **Autonomous mode (authorized by the owner, 2026-09-24).** Work through the phases and steps in §15 **in order, without stopping to ask for approval** between steps or phases.
  - **Decide for yourself.** Where a choice is needed, pick the recommended option and keep going. Write every such decision in `docs/DECISIONS.md` (ID, date, step, decision, alternatives, why, how to reverse) so the owner can review it later.
  - **Keep records.** Read `docs/PROGRESS.md` and `docs/DECISIONS.md` at the start of every session and continue from "Next step". Update `docs/PROGRESS.md` after every step.
  - **Commit after each step** (Conventional Commits, `Co-Authored-By` line) once `./mvnw verify` (and, for frontend work, `npm run build && npm test`) pass. Never commit a red build.
  - **Stop and ask ONLY when the owner personally has to act or the action cannot be undone**, for example: changing antivirus or other security-software settings; installing system software or anything needing admin rights or a reboot; entering, choosing or supplying real passwords, keys or credentials; anything that would delete or overwrite data (dropping databases or Docker volumes, deleting files outside build output); pushing to a remote, deploying, or spending money.
  - **Never weaken a locked decision (§2) or a security rule (§11) to avoid asking.** If a step seems to need that, log a compliant alternative in `docs/DECISIONS.md`; if none exists, stop and ask.
  - When stopped or done, give a short summary of what was built, what was decided, and what (if anything) needs the owner.
- Before writing code in an area, read the relevant section of this file and the existing code in that module.
- The original tool is at `docs/reference/nexlyn-bgv-report-v3.2.html` (and its notes at `docs/reference/ALL_data_of_bgv.md`). Port report layout, CSS, pagination and formatting rules **from that source**, not from memory.
- Every phase must compile, pass tests (`./mvnw verify`, `npm run build && npm test`) and start with `docker compose up` before it is called done.
- Never commit secrets. Never weaken a security rule in §11 to make something work — ask instead.
- Keep this file updated when the user approves a change to a locked decision.
- Admin-dashboard UI work may use the design skill in `.claude/skills/ui-ux-pro-max`, but only under the six rules of §16.1 (dashboard only, never the PDF report, this file wins, no security changes, no hand-edited generated client, tests green and a commit per screen).

---

## 1. Project overview

**Nexlyn Services** produces Background Verification (BGV) reports for client companies.

**Today:** reports are made manually with a single-file browser tool (`nexlyn-bgv-report-v3.2.html`, ~6,100 lines, vanilla JS, localStorage). One person, one report at a time, then "Print → Save as PDF".

**Goal:** an internal, admin-only platform where admins enter all candidate and verification data through a dashboard, everything is stored in PostgreSQL, and the system automatically generates the same Nexlyn-branded PDF report.

**Future (design for it now, do not build yet):** PAN, Aadhaar, address and other verification data will be fetched from third-party verification APIs instead of manual entry.

**Users:** Nexlyn admins only (multiple admins with different access levels). No client logins, no candidate logins.

---

## 2. Locked decisions

| Topic | Decision |
|---|---|
| Frontend | **React** + TypeScript + Vite |
| Backend | **Java 21** + **Spring Boot 3.x** |
| Architecture | **Modular monolith** using **Spring Modulith** — one deployable backend, strict modules, each module extractable to its own service later |
| Database | **PostgreSQL 16**, one database, **one schema per module** |
| Deployment now | **One machine** (Docker). Local dev also Docker Compose |
| Deployment later | Extract modules to separate instances as the business grows (see §4.4) |
| Security | **Spring Security 6**, permission-based **RBAC**, mandatory **TOTP 2FA**, maker-checker workflow (see §11) |
| PDF | Server-side: Thymeleaf HTML → **Playwright for Java** (headless Chromium) → PDF; encryption with **Apache PDFBox** (AES-256) |
| Files | S3-compatible object storage — **AWS S3 (ap-south-1)** in prod; locally an S3-compatible store (MinIO was intended, but its images were withdrawn; SeaweedFS is used instead, see D-031 in `docs/DECISIONS.md`) |
| Data residency | India (AWS Mumbai, ap-south-1) |
| Build | Maven multi-module (with wrapper) |
| Check type field sets | Aadhaar, PAN, Court are from the HTML tool. **All other types are PROVISIONAL** — user will review later. Keep them config-driven so changes need no DB migration |

---

## 3. Tech stack (detailed)

### 3.1 Backend
| Area | Choice |
|---|---|
| Language / framework | Java 21, Spring Boot 3.x (3.2+) |
| Concurrency | Virtual threads enabled (`spring.threads.virtual.enabled=true`) |
| Modularity | Spring Modulith (module verification test, event publication registry, later `@Externalized` events) |
| Web | Spring Web MVC, REST, JSON |
| API docs | springdoc-openapi (Swagger UI in local only) |
| Persistence | Spring Data JPA + Hibernate |
| Migrations | Flyway — one migration folder + history table per module/schema |
| DTOs | Java **records**; MapStruct for entity↔DTO mapping |
| Validation | Jakarta Bean Validation + custom validators (PAN, Aadhaar Verhoeff, PIN, Indian phone) |
| Polymorphic check payloads | `sealed interface CheckPayload` + one record per check type; Jackson `@JsonTypeInfo` on `type` |
| Security | Spring Security 6, JWT (RS256) via `spring-boot-starter-oauth2-resource-server` + custom token issuer, Argon2id, TOTP |
| Rate limiting | Bucket4j |
| Async / events | Spring Modulith application events (persisted, transactional outbox). RabbitMQ only when a module is extracted |
| Background jobs | Bounded executor for PDF rendering (`ReportJobExecutor`) |
| HTTP client (future vendors) | Spring `RestClient` + Resilience4j (retry, timeout, circuit breaker) |
| PDF | Thymeleaf templates, Playwright for Java (Chromium), Apache PDFBox for encryption |
| Storage | AWS SDK for Java v2 (S3 API — works with MinIO and S3) |
| Observability | Spring Boot Actuator, Micrometer, structured JSON logs (logstash-logback-encoder), correlation IDs, Sentry (optional) |
| Testing | JUnit 5, Mockito, AssertJ, **Testcontainers** (PostgreSQL, MinIO), Spring Modulith test support |

### 3.2 Frontend
| Area | Choice |
|---|---|
| Framework | React 18 + TypeScript, Vite |
| Routing | React Router |
| UI | Tailwind CSS + shadcn/ui |
| Forms | React Hook Form + Zod |
| Server state | TanStack Query |
| API client | Generated from backend OpenAPI (`frontend/src/api/generated`, never edit by hand) |
| Rich text (remarks) | Minimal editor, bold only; sanitize with DOMPurify (allow `<strong>` only) |
| Testing | Vitest + React Testing Library; Playwright for end-to-end |
| Serving | Static build served by Nginx |

### 3.3 Infrastructure
| Area | Local | Prod (now) |
|---|---|---|
| Runtime | Docker Compose | Docker Compose on one machine (≈4 GB RAM minimum) |
| Database | `postgres:16` container | AWS RDS PostgreSQL 16 (recommended) |
| Files | MinIO container | AWS S3 ap-south-1 |
| Reverse proxy / TLS | — (Vite dev server or Nginx) | Nginx + TLS (Let's Encrypt or ACM) |
| CI/CD | GitHub Actions | GitHub Actions → image registry (ECR) → deploy |
| Secrets | `.env` (git-ignored) | Env vars / AWS Secrets Manager |

---

## 4. Architecture

### 4.1 Shape
```
Browser (React SPA)
      │ HTTPS
   Nginx  ── /        → frontend static files
          └─ /api/**  → bgv-app (Spring Boot, single process)
                          ├── auth          (admins, roles, permissions, 2FA, audit)
                          ├── cases         (clients, cases, candidates, checks, workflow)
                          ├── verification  (check execution, provider adapters)
                          ├── documents     (uploads, storage, image quality)
                          └── reports       (HTML → PDF, encryption, versions)
                                │
                     PostgreSQL (schemas: auth, cases, verification, documents, reports)
                     S3 / MinIO (photos, documents, generated PDFs)
```

### 4.2 Module boundary rules (enforced — do not break)
1. **Public API only.** A module's public types live in its **root package** (e.g. `com.nexlyn.bgv.documents.DocumentApi`). Everything under `…/internal/**` is private. Other modules may use only root-package types. `ModularityTests` (`ApplicationModules.of(BgvApplication.class).verify()`) must pass on every build.
2. **Synchronous calls** go through a Java interface in the root package (`CaseApi`, `DocumentApi`, `ReportApi`, `VerificationApi`, `AuthApi`). Implementations (`*ApiImpl`) live in `internal`.
3. **Asynchronous work** uses events (records in the root package, e.g. `CheckSubmittedEvent`), published with `ApplicationEventPublisher`, consumed with `@ApplicationModuleListener`. Event publication registry is enabled (JDBC) so events survive restarts.
4. **Own schema.** Each module owns exactly one PostgreSQL schema. No cross-schema joins, no cross-schema foreign keys, no JPA relationships across modules. Reference other modules' data **by ID only** (`UUID caseId`).
5. **Own migrations.** Each module has its own Flyway location (`db/migration/<module>`) and its own history table (`flyway_schema_history` inside its schema). Configure one Flyway bean per module.
6. **No shared mutable state** in memory between modules.
7. **Heavy work is isolated.** PDF rendering runs on a bounded executor so it cannot starve web threads.
8. `bgv-common` contains only generic building blocks (security utils, crypto, validation, errors, enums, logging). No business logic, no entities.

### 4.3 Package naming
`com.nexlyn.bgv.<module>` for public API, `com.nexlyn.bgv.<module>.internal.<layer>` for internals.
Layers inside `internal`: `web`, `domain`, `repository`, `service`, `mapper`, `listener`, `config`, plus module-specific ones (`storage`, `provider`, `render`, …).

### 4.4 Extraction path (future — do not build now)
To move a module (e.g. `reports`) to its own instance:
1. Create `reports-app/` Spring Boot app containing only `modules/reports` + `bgv-common`.
2. In the main app, replace `ReportApiImpl` with `ReportApiHttpClient implements ReportApi` (same interface → callers unchanged).
3. Mark its events `@Externalized` → published to RabbitMQ via Spring Modulith.
4. Point it at the same DB (its schema) or move the schema to its own DB.
5. Add Nginx / gateway route for `/api/reports/**`.
6. Services then authenticate each other with an internal service token (see §11).

---

## 5. Repository structure

```
nexlyn-bgv/
├── CLAUDE.md
├── README.md
├── .gitignore  .editorconfig
│
├── backend/
│   ├── pom.xml                                   # parent: Java 21, Spring Boot 3.x, Spring Modulith BOM
│   ├── mvnw  mvnw.cmd  .mvn/
│   │
│   ├── bgv-app/                                  # the ONE runnable application
│   │   ├── pom.xml                               # depends on bgv-common + all modules
│   │   ├── Dockerfile                            # multi-stage; runtime includes Chromium for Playwright
│   │   └── src/
│   │       ├── main/java/com/nexlyn/bgv/BgvApplication.java
│   │       ├── main/resources/application.yml
│   │       ├── main/resources/application-local.yml
│   │       ├── main/resources/application-prod.yml   # everything from env vars
│   │       └── test/java/com/nexlyn/bgv/ModularityTests.java
│   │
│   ├── bgv-common/
│   │   └── src/main/java/com/nexlyn/bgv/common/
│   │       ├── security/     JwtTokenValidator, Permission (enum), CurrentAdmin,
│   │       │                 SecurityHeadersConfig, InternalServiceAuthFilter (future)
│   │       ├── crypto/       AesGcmEncryptor, EncryptedStringConverter
│   │       ├── masking/      PiiMasker
│   │       ├── validation/   ValidPan/PanValidator, ValidAadhaar/AadhaarValidator (Verhoeff),
│   │       │                 ValidPinCode/PinCodeValidator, ValidIndianPhone
│   │       ├── error/        ApiError, ErrorCode, GlobalExceptionHandler
│   │       ├── enums/        CheckType, CheckStatus, CaseLifecycle
│   │       └── logging/      CorrelationIdFilter
│   │
│   └── modules/
│       ├── auth/
│       │   └── src/main/java/com/nexlyn/bgv/auth/
│       │       ├── AuthApi.java  AdminDto.java  AuditEvent.java
│       │       └── internal/
│       │           ├── web/       AuthController, MeController, AdminController,
│       │           │              RoleController, AuditController
│       │           ├── domain/    Admin, Role, PermissionEntity, RefreshToken, TotpSecret,
│       │           │              BackupCode, Invitation, LoginAttempt, AuditLog
│       │           ├── repository/
│       │           ├── service/   AuthService, TokenService, TotpService, PasswordPolicyService,
│       │           │              LockoutService, InvitationService, RoleService, AuditService
│       │           ├── security/  SecurityConfig, CaseAccessPolicy, JwtIssuer
│       │           ├── listener/  AuditEventListener
│       │           └── config/    AuthModuleConfig (Flyway "auth"), RateLimitConfig
│       │       resources/db/migration/auth/
│       │
│       ├── cases/
│       │   └── src/main/java/com/nexlyn/bgv/cases/
│       │       ├── CaseApi.java  CaseDto.java  CheckDto.java
│       │       ├── CheckSubmittedEvent.java  CaseReadyForReportEvent.java
│       │       └── internal/
│       │           ├── web/       ClientController, CaseController, CandidateController,
│       │           │              CheckController, CheckTypeController, WorkflowController,
│       │           │              AssignmentController
│       │           ├── web/dto/   request/response records; payload/ (sealed CheckPayload + records)
│       │           ├── domain/    Client, BgvCase, Candidate, VerificationCheck, CheckField,
│       │           │              CheckDetail, CheckFreeSection, CaseAssignment
│       │           ├── checktypes/ CheckTypeRegistry, CheckTypeDefinition (loaded from
│       │           │              resources/check-types/*.yml)
│       │           ├── service/   CaseService, CandidateService, CheckService,
│       │           │              CandidatePrefillService, DateMasterSyncService,
│       │           │              ProgressService, ValidationService, WorkflowService,
│       │           │              ReportIdGenerator
│       │           ├── mapper/  repository/  listener/ (VerificationCompletedListener)
│       │           └── config/    CasesModuleConfig (Flyway "cases")
│       │       resources/db/migration/cases/
│       │       resources/check-types/            # one YAML per check type (§8)
│       │
│       ├── verification/
│       │   └── src/main/java/com/nexlyn/bgv/verification/
│       │       ├── VerificationApi.java  VerificationCompletedEvent.java
│       │       └── internal/
│       │           ├── provider/  VerificationProvider (interface), ProviderRegistry,
│       │           │              manual/ManualProvider, vendor/README.md (future)
│       │           ├── mapping/   ResultToCheckFieldMapper
│       │           ├── domain/    VerificationRequest, VerificationResult (raw JSONB)
│       │           ├── listener/  CheckSubmittedListener
│       │           └── config/    ResilienceConfig, VerificationModuleConfig (Flyway)
│       │       resources/db/migration/verification/
│       │
│       ├── documents/
│       │   └── src/main/java/com/nexlyn/bgv/documents/
│       │       ├── DocumentApi.java  DocumentDto.java  DocumentUploadedEvent.java
│       │       └── internal/
│       │           ├── web/       DocumentController
│       │           ├── domain/    StoredDocument
│       │           ├── storage/   StorageService, S3StorageService
│       │           ├── image/     ImageQualityService, ImageCropService, FileTypeSniffer
│       │           └── config/    DocumentsModuleConfig (Flyway), S3Config
│       │       resources/db/migration/documents/
│       │
│       └── reports/
│           └── src/main/java/com/nexlyn/bgv/reports/
│               ├── ReportApi.java  ReportGeneratedEvent.java
│               └── internal/
│                   ├── web/        ReportController
│                   ├── assembler/  ReportModelAssembler (uses CaseApi + DocumentApi)
│                   ├── layout/     PaginationService, IconGroupResolver, DocumentNumbering
│                   ├── render/     HtmlRenderer (Thymeleaf), PdfRenderer (Playwright)
│                   ├── security/   PdfEncryptionService (PDFBox AES-256)
│                   ├── job/        ReportJobExecutor
│                   ├── domain/     ReportVersion
│                   └── config/     ReportsModuleConfig (Flyway)
│               resources/templates/report/
│                   report.html
│                   fragments/ cover-page.html, remarks-page.html, detail-page.html,
│                              attestation.html, services-page.html, footer.html
│               resources/static/report/
│                   report.css, fonts/Inter/*, images/ (logo, advocate-seal)
│               resources/db/migration/reports/
│
├── frontend/
│   ├── package.json  vite.config.ts  tsconfig.json
│   ├── Dockerfile  nginx.conf  .env.local.example
│   └── src/
│       ├── main.tsx  App.tsx
│       ├── routes/                  # route table with required permission per route
│       ├── api/  generated/  httpClient.ts  queries/
│       ├── features/
│       │   ├── auth/                # login, 2FA setup/verify, invite acceptance
│       │   ├── dashboard/           # case counts, TAT alerts, my assigned cases
│       │   ├── clients/
│       │   ├── cases/
│       │   │   ├── CaseList.tsx
│       │   │   ├── workspace/       # CaseWorkspace + SectionNav + one component per section (§7)
│       │   │   └── checks/          # CheckSection, per-type field renderer, status presets
│       │   ├── documents/           # uploader, crop/zoom editor, quality badge, reorder
│       │   ├── reports/             # preview, generate, finalize, versions
│       │   ├── workflow/            # submit for review, approve, request changes
│       │   ├── settings/
│       │   ├── admin-users/         # admins, roles, permission editor
│       │   └── audit/
│       ├── components/  ui/ (shadcn)  layout/
│       ├── lib/
│       │   ├── security/            # AuthProvider, ProtectedRoute, Can, idleTimer, apiInterceptor
│       │   ├── validators/          # Zod: PAN, Aadhaar, PIN, phone, dates
│       │   ├── formatters/          # dd/mm/yyyy ↔ dd-Mmm-yyyy, Aadhaar 4-4-4, phone
│       │   └── constants/           # statuses, presets, check types
│       └── styles/
│
├── infra/
│   ├── local/
│   │   ├── docker-compose.yml       # postgres, minio, backend, frontend
│   │   ├── .env.example
│   │   ├── postgres/init/01-create-schemas.sql
│   │   └── minio/init/create-buckets.sh
│   └── prod/
│       ├── docker-compose.yml       # nginx + frontend + backend (DB on RDS, files on S3)
│       ├── .env.example
│       ├── nginx/nexlyn.conf
│       └── README.md
│
├── scripts/  build-all.sh  local-up.sh  local-down.sh  generate-api-client.sh  db-reset-local.sh
├── docs/
│   ├── reference/  nexlyn-bgv-report-v3.2.html  ALL_data_of_bgv.md
│   ├── architecture.md  data-model.md  api/  adr/  runbooks/
└── .github/workflows/  ci-backend.yml  ci-frontend.yml  deploy.yml
```

---

## 6. The reference HTML tool — what to preserve and what to fix

Source: `docs/reference/nexlyn-bgv-report-v3.2.html`. The generated PDF must look the same as this tool's printed output.

### 6.1 Report structure (A4, `@page { size:A4; margin:0 }`)
1. **Page 1 — Cover:** brand header, candidate details + photo, verification period (optional), status pill, report overview, summary grid of verification cards (grouped by icon).
2. **Remarks** (Analyst Remarks + Final Recommendation) — placement rule in §6.3.
3. **Detail pages** — one per verification check: title + "(This Card Verifies)", status, checks table (with ticks), details grid, supporting documents, free sections, card remarks, optional legal attestation block.
4. **Services page** — always the last page (edge-to-edge layout, own header/footer logo).
5. Footer on every page with status legend and page numbers.
6. Optional diagonal **watermark** on every printed page (default text `NEXLYN VERIFIED`, max 40 chars).

### 6.2 Behaviour to port exactly
- **Icon groups:** cards are grouped for the summary grid and pagination. Groups: identity, court, address, employment, education; every other type is its own group. **Fix:** derive the group from `CheckType`, NOT from title text (the HTML tool uses a regex on the title — do not copy that).
- **Page 1 layout:** 4 cards (spacious, default) or 6 cards (compact). Extra groups overflow to the next page.
- **Date formats:** Numeric `dd/mm/yyyy` (e.g. 11/06/2026) or Text `dd-Mmm-yyyy` (e.g. 11-Jun-2026). Stored as ISO dates in DB; formatted only at render time.
- **Date master:** the first check's Requested/Completed dates auto-fill all other checks unless that check's dates were manually set (sticky). UI badges: ★ Master / 🔄 Auto / ✏️ Manual.
- **Field prefill:** in the HTML tool, card 1 (Aadhaar) check values (name, father's name, DOB, address…) auto-fill matching labels on other cards unless `isManual`. **New design:** these values live on the **Candidate** (§7 Section 2) and every check pre-fills from the Candidate. Label matching aliases (e.g. "full name" = "name" = "candidate name") are preserved via `field_key`.
- **Report overview:** Total Verifications, Completed, Overall Status are auto-calculated from checks, with manual override per field (fallback to auto when override is empty).
- **Status pill presets** (title + subtitle + colour, text still editable):

  | Preset | Title | Subtitle | Colours (bg / border / text) |
  |---|---|---|---|
  | completed | Completed | All Requested Verifications Completed | #d1fae5 / #6ee7b7 / #059669 |
  | discrepancy | Discrepancy | Discrepancy Found in Verification | #fee2e2 / #fca5a5 / #dc2626 |
  | unable | Unable to Verify | Unable to Complete Verification | #fef3c7 / #fbbf24 / #b45309 |
  | closed | Closed | Verification Closed / Insufficient Data | #f3f4f6 / #d1d5db / #374151 |

- **Check statuses (6):**

  | Code | Label | Icon |
  |---|---|---|
  | VERIFIED | Verified | ✓ (green) |
  | DISCREPANCY | Discrepancy | ✕ (red) |
  | UNABLE_TO_VERIFY | Unable to Verify | ⓘ (amber) |
  | CLOSED | Closed | − (grey) |
  | PENDING | Pending | ⏱ (yellow) |
  | IN_PROGRESS | In Progress | ↻ (blue) |

- **Documents per check:** multiple; labels "Original Document" / "Additional Document N"; **auto-renumbering** by visual order; **Move to Next Page** (standard box on a new page); **Use Larger Box** (near-full-page image); crop/zoom editor; image quality badge: max(width,height) ≥ 1500 → High, ≥ 800 → Medium, else Low.
- **Free sections per check:** repeatable blocks of type `text` or `image`.
- **Legal attestation (court checks default ON):** advocate seal image + Bar Council Number (default `KAR/670/06`) + disclaimer. Default disclaimer:
  > This report is based on information available in accessible court records and databases at the time of verification. While due care has been taken, the completeness and accuracy of records cannot be guaranteed due to limitations in record availability and updates. This report is issued solely for background verification purposes.
- **Father / Guardian toggle:** same data field, label switches between "Father's Name" and "Guardian's Name".
- **Verification Period toggle:** hide the whole row (icon + content) from Page 1 while keeping the dates.
- **Company name** supports line breaks.
- **Remarks / Final Recommendation** support `<strong>` bold only.
- **Formatters:** Aadhaar `XXXX XXXX XXXX`, PAN uppercase `AAAAA9999A`, PIN 6 digits, phone `+91 XXXXX XXXXX`, uppercase inputs where the tool uses them.
- **Font:** Inter (bundle locally in `static/report/fonts/Inter`, no Google Fonts at runtime). Brand block and footer keep their original fonts (see CSS exclusions in the reference file).

### 6.3 Remarks placement rule (based on icon GROUP count, not card count)
- groups ≤ 2 → Remarks inline on Page 1
- 3 ≤ groups ≤ layout max (4 or 6) → Remarks on a dedicated Page 2
- groups > layout max → Remarks merged into the overflow page (no dedicated page)

### 6.4 Known problems in the HTML tool — do NOT replicate
1. Card type guessed from title regex → use explicit `CheckType`.
2. 14 of 17 card types reuse the Aadhaar field list (e.g. Employment shows "Aadhaar Number") → each type has its own field set (§8).
3. Images stored as base64 in localStorage; autosave fails silently over ~5 MB → files in object storage, explicit saves with visible errors.
4. "Protected PDF" is owner-password only, two-step, and may silently output an unencrypted file when the fallback library loads → server-side PDFBox AES-256, optional user (open) password, fail loudly.
5. Aadhaar never masked → encrypted at rest, masked by default, audited reveal.
6. Advocate seal usable by anyone → requires `ATTESTATION_APPLY`, audited.
7. "Police" listed in docs but missing in code → add `POLICE` type.
8. Master sync depends on card order → Candidate is the master.

---

## 7. Admin dashboard — Case Workspace (one page, section by section)

Case list → open case → **one workspace page**: left section navigator with per-section state (○ not started, ● saved, ⚠ warnings) and overall progress bar; right side shows the selected section with its own **Save** button. Unsaved-changes guard when switching sections. Read-only when the case is IN_REVIEW/APPROVED/FINALIZED (except for permitted workflow actions).

```
┌─────────────────────┬─────────────────────────────────────────────┐
│ CASE: NX-2026-0142  │  ▸ 2. Candidate Details              [Save] │
│ ████████░░ 72%      │  ─────────────────────────────────────────  │
│ 1 Report Info    ●  │  Full Name        [                    ]    │
│ 2 Candidate      ●  │  ( ) Father  ( ) Guardian                   │
│ 3 Verif. Period  ●  │  Father's Name    [                    ]    │
│ 4 Checks            │  Employee ID [      ]  DOB [dd/mm/yyyy]     │
│   ├ Aadhaar      ●  │  Phone [+91        ]   Photo [Upload]       │
│   ├ PAN          ○  │  Address: Street / City / State / PIN / …   │
│   ├ Court        ⚠  │                                             │
│   └ + Add check     │                                             │
│ 5 Overview & Status │                                             │
│ 6 Remarks           │                                             │
│ 7 Settings          │                                             │
│ 8 Generate Report   │                                             │
└─────────────────────┴─────────────────────────────────────────────┘
```

| # | Section | Fields | Save endpoint |
|---|---|---|---|
| 1 | Report Info | Report ID (auto-generated, editable, unique), Issue Date, Client (select; company display name prefilled, multi-line) | `PUT /api/cases/{id}/report-info` |
| 2 | Candidate Details | Full Name, Parent Type (FATHER/GUARDIAN), Father/Guardian Name, Employee ID, DOB, Phone, Photo, Address (Street, City/Town, State, PIN, Country — default India) | `PUT /api/cases/{id}/candidate`; photo `POST /api/cases/{id}/candidate/photo` |
| 3 | Verification Period | Show on report (default ON), Start Date, End Date | `PUT /api/cases/{id}/verification-period` |
| 4 | Checks (one sub-section per check) | A. Card info: Title, Summary Description (Page 1), This Card Verifies (detail page; falls back to document type), Status, Verification Type, Requested Date, Completed Date (Master/Auto/Manual). B. Type-specific fields (§8): value + verified tick + source badge (Candidate/Manual/API). C. Extra details (label/value rows). D. Documents. E. Free sections. F. Remarks. G. Attestation (on/off, Bar Council No., Disclaimer) | `PUT /api/cases/{id}/checks/{checkId}` (A,B,C,F,G together). Documents & free sections save immediately |
| 5 | Overview & Status | Status pill title/subtitle + 4 presets; Total, Completed, Overall Status (auto with manual override) | `PUT /api/cases/{id}/overview` |
| 6 | Remarks & Recommendation | Analyst Remarks, Final Recommendation (bold only) | `PUT /api/cases/{id}/remarks` |
| 7 | Report Settings | Page-1 layout (4/6), Date format (NUMERIC/TEXT), Watermark on/off + text | `PUT /api/cases/{id}/settings` |
| 8 | Generate Report | Validation list with "Go to section" links, Preview, Generate draft PDF, Submit for review, Approve / Request changes, Finalize (optional open password), version history | see §9 reports + workflow |

"+ Add check" opens a picker of all check types (§8). Adding a check creates it with the type's default fields/details, prefilled from the Candidate and date master.

### 7.1 Validation (before generate/submit) — from the HTML tool
- **Errors (block):** Report ID, Issue Date, Full Name, Employee ID, at least 1 check.
- **Warnings (allow with confirmation):** Father/Guardian Name, DOB, Phone, Photo, verification period dates (when shown), check dates, check statuses, at least one document per check, analyst remarks / final recommendation.
- Endpoint: `GET /api/cases/{id}/validation` → `{ errors:[{section, field, message}], warnings:[…] }`.

---

## 8. Check types (config-driven)

Defined in `backend/modules/cases/src/main/resources/check-types/<code>.yml`, loaded by `CheckTypeRegistry`, exposed via `GET /api/check-types`. The frontend renders check forms **from this definition** — no per-type hardcoded forms. Adding/changing a field = edit YAML (no DB migration). Each type also has a Java payload record (`AadhaarPayload`, `PanPayload`, …) implementing `sealed interface CheckPayload` for validation.

**Default details for every type** (unless overridden): Verification Type (`Standard`; `Electronic` for AADHAAR and PAN), Document Type (= the type's document name), Requested Date, Completed Date.

| Code | Display name | Document name | Icon group | Attestation default | Type-specific fields | Status |
|---|---|---|---|---|---|---|
| AADHAAR | Identity Verification (Aadhaar) | Aadhaar Card | identity | off | Aadhaar Number🔒, Full Name, DOB, Father's Name, Street Address, City / Town, State, PIN Code, Country | ★ from HTML |
| PAN | Identity Verification (PAN) | PAN Card | identity | off | PAN Number🔒, Full Name, Father's Name, DOB, Street Address, City / Town, State, PIN Code, Country | ★ from HTML |
| COURT | Court Record (Permanent Address) | Court Document | court | **on** | Full Name, Father's Name, Address, City / Town, State, PIN Code, Country, Search Period. Extra details: Court Type, Jurisdiction (default "Permanent Address") | ★ from HTML |
| POLICE | Police Verification | Police Record | unique | off | Police Station, Jurisdiction, Address, Search Period, Result | provisional (new) |
| ADDRESS | Address Verification | Address Proof | address | off | Street, City / Town, State, PIN Code, Country, Address Type (current/permanent), Period of Stay, Verification Mode (field/postal/digital), Respondent Name, Respondent Relationship | provisional |
| EMPLOYMENT | Employment Verification | Experience Letter | employment | off | Company, Employee ID, Designation, Date of Joining, Date of Leaving, Reason for Leaving, Eligible for Rehire, Verifier Name, Verifier Designation, Verifier Contact | provisional |
| EDUCATION | Education Verification | Degree Certificate | education | off | Institution, University / Board, Degree, Specialization, Year of Passing, Roll / Registration No., Grade / %, Verifier | provisional |
| REFERENCE | Reference Check | Reference Letter | unique | off | Referee Name, Designation, Organisation, Relationship, Contact, Feedback | provisional |
| UAN | UAN Check | EPFO Database | unique | off | UAN Number, Name as per EPFO, Establishment(s), Date of Joining (EPFO), Date of Exit (EPFO) | provisional |
| CREDIT | Credit Check | CIBIL Report | unique | off | CIBIL Score, Report Date, Defaults (yes/no), Summary | provisional |
| DRUG_TEST | Drug Test (5 Panel) | Drug Test Report | unique | off | Sample Date, Lab, Panel 1–5 results, Overall Result | provisional |
| DIRECTORSHIP | Directorship Check | MCA Records | unique | off | DIN, Companies Found, Status | provisional |
| GAP_REVIEW | Gap Review | Employment Gap | unique | off | Gap periods (repeatable: From, To, Reason) | provisional |
| WORLD_CHECK | World Check | Global Database | unique | off | Sources Searched, Search Date, Hits Found (yes/no), Result Summary | provisional |
| OIG | OIG Exclusions | OIG Database | unique | off | Sources Searched, Search Date, Hits Found, Result Summary | provisional |
| ADVERSE_MEDIA | Adverse Media Check | Media Search | unique | off | Sources Searched, Search Date, Hits Found, Result Summary | provisional |
| SOCIAL_MEDIA | Social Media Check | Social Profiles | unique | off | Sources Searched, Search Date, Hits Found, Result Summary | provisional |
| RESUME_REVIEW | Resume Review | Resume Document | unique | off | Sources Searched, Search Date, Hits Found, Result Summary | provisional |

🔒 = sensitive: stored encrypted (`value_encrypted` + `value_last4`), masked in responses by default.

Fields with `prefill: candidate.<property>` are filled from the Candidate on check creation and whenever the Candidate is saved, unless the field is marked manual.

Example YAML:
```yaml
code: AADHAAR
displayName: Identity Verification (Aadhaar)
documentName: Aadhaar Card
iconGroup: identity
attestationDefault: false
verificationTypeDefault: Electronic
fields:
  - { key: aadhaar_number, label: Aadhaar Number, type: aadhaar, sensitive: true, required: true }
  - { key: full_name,      label: Full Name,      type: text, prefill: candidate.fullName }
  - { key: dob,            label: DOB,            type: date, prefill: candidate.dob }
  - { key: father_name,    label: "Father's Name", type: text, prefill: candidate.parentName }
  - { key: street,         label: Street Address, type: text, prefill: candidate.street }
  - { key: city,           label: City / Town,    type: text, prefill: candidate.city }
  - { key: state,          label: State,          type: text, prefill: candidate.state }
  - { key: pin,            label: PIN Code,       type: pin,  prefill: candidate.pin }
  - { key: country,        label: Country,        type: text, prefill: candidate.country }
details: []   # defaults added automatically
```
Field `type` values: `text, textarea, date, number, pin, phone, aadhaar, pan, uan, boolean, select, repeatable`.

---

## 9. REST API

All under `/api`. JSON. Errors use `ApiError { code, message, fieldErrors[], correlationId }`. Every endpoint requires authentication unless marked public, and a permission (§11). Case-scoped endpoints also pass `CaseAccessPolicy`.

### 9.1 Auth & admin (module `auth`)
| Method & path | Permission |
|---|---|
| `POST /api/auth/login` | public, rate-limited → returns `2FA_REQUIRED` + short-lived challenge |
| `POST /api/auth/2fa/verify` | challenge → issues access token + refresh cookie |
| `POST /api/auth/2fa/setup` / `POST /api/auth/2fa/confirm` | during invite onboarding |
| `POST /api/auth/refresh` | refresh cookie + CSRF |
| `POST /api/auth/logout` | authenticated |
| `POST /api/auth/invitations/accept` | invite token (sets password + 2FA) |
| `GET /api/me` | authenticated → admin, roles, permissions |
| `PUT /api/me/password` | authenticated |
| `GET/POST/PUT /api/admins`, `POST /api/admins/{id}/disable`, `POST /api/admins/{id}/revoke-sessions`, `POST /api/admins/invitations` | `USER_MANAGE` |
| `GET/POST/PUT/DELETE /api/roles`, `GET /api/permissions` | `ROLE_MANAGE` |
| `GET /api/audit-log?actor=&entity=&from=&to=` | `AUDIT_READ` |

### 9.2 Clients & cases (module `cases`)
| Method & path | Purpose | Permission |
|---|---|---|
| `GET/POST/PUT /api/clients` | client master | read: any case-read; write: `CLIENT_MANAGE` |
| `POST /api/cases` | create case → id + generated Report ID | `CASE_CREATE` |
| `GET /api/cases?status=&client=&assignee=&q=&page=` | case list (analysts see only assigned) | `CASE_READ_ALL` or `CASE_READ_ASSIGNED` |
| `GET /api/cases/{id}` | full workspace payload (PII masked) | read |
| `DELETE /api/cases/{id}` | soft delete | `CASE_DELETE` |
| `GET /api/cases/{id}/progress` | section states + status counts | read |
| `PUT /api/cases/{id}/report-info` | Section 1 | `CASE_UPDATE` |
| `PUT /api/cases/{id}/candidate` | Section 2 (re-prefills non-manual check fields) | `CASE_UPDATE` |
| `PUT /api/cases/{id}/verification-period` | Section 3 | `CASE_UPDATE` |
| `GET /api/check-types` | type definitions (§8) | authenticated |
| `POST /api/cases/{id}/checks` `{type}` | add check (prefilled) | `CHECK_UPDATE` |
| `GET /api/cases/{id}/checks` | list | read |
| `PUT /api/cases/{id}/checks/{checkId}` | save card info + fields + details + remarks + attestation | `CHECK_UPDATE` (+ `ATTESTATION_APPLY` to turn attestation on) |
| `PATCH /api/cases/{id}/checks/order` | reorder | `CHECK_UPDATE` |
| `DELETE /api/cases/{id}/checks/{checkId}` | remove | `CHECK_UPDATE` |
| `POST/PUT/DELETE /api/cases/{id}/checks/{checkId}/free-sections[/{sectionId}]` | free blocks | `CHECK_UPDATE` |
| `GET /api/cases/{id}/checks/{checkId}/fields/{key}/reveal` | unmasked value (audited) | `PII_UNMASK` |
| `PUT /api/cases/{id}/overview` | Section 5 | `CASE_UPDATE` |
| `PUT /api/cases/{id}/remarks` | Section 6 | `CASE_UPDATE` |
| `PUT /api/cases/{id}/settings` | Section 7 | `CASE_UPDATE` |
| `GET /api/cases/{id}/validation` | errors + warnings | read |
| `POST /api/cases/{id}/assignments` / `DELETE …/{adminId}` | assign preparer/reviewer | `CASE_ASSIGN` |
| `POST /api/cases/{id}/submit-review` | DRAFT → IN_REVIEW | `REPORT_SUBMIT_FOR_REVIEW` |
| `POST /api/cases/{id}/approve` | IN_REVIEW → APPROVED | `REPORT_APPROVE` + separation of duties |
| `POST /api/cases/{id}/request-changes` `{comment}` | IN_REVIEW → CHANGES_REQUESTED | `REPORT_APPROVE` |
| `POST /api/cases/{id}/reopen` `{reason}` · `GET /api/cases/{id}/history` · `GET /api/dashboard` — additions, D-033 | FINALIZED → DRAFT (`REPORT_FINALIZE`) / read / read |

### 9.3 Documents (module `documents`)
| Method & path | Permission |
|---|---|
| `POST /api/cases/{id}/candidate/photo` (multipart) | `DOCUMENT_UPLOAD` |
| `POST /api/checks/{checkId}/documents` (multipart) | `DOCUMENT_UPLOAD` |
| `PUT /api/documents/{docId}` (label, moveToNextPage, useLargerBox, crop) | `DOCUMENT_UPLOAD` |
| `PATCH /api/checks/{checkId}/documents/order` | `DOCUMENT_UPLOAD` |
| `GET /api/documents/{docId}/content` → streamed after permission + case checks (chosen over signed URLs, D-030); every read is audited | read on the case |
| `GET /api/checks/{checkId}/documents` (list) · `DELETE /api/cases/{id}/candidate/photo` · `POST …/documents?kind=FREE_IMAGE` (picture for an image block) — additions, D-030 | read / `DOCUMENT_DELETE` / `DOCUMENT_UPLOAD` |
| `DELETE /api/documents/{docId}` | `DOCUMENT_DELETE` |

### 9.4 Reports (module `reports`)
| Method & path | Permission |
|---|---|
| `GET /api/cases/{id}/reports/preview` (HTML) | read |
| `POST /api/cases/{id}/reports` → job id (draft PDF) | `REPORT_GENERATE` |
| `GET /api/cases/{id}/reports/jobs/{jobId}` | read |
| `GET /api/cases/{id}/reports` (versions) | read |
| `POST /api/cases/{id}/reports/{version}/finalize` `{openPassword?}` (APPROVED only) | `REPORT_FINALIZE` + separation of duties |
| `GET /api/cases/{id}/reports/{version}/download` | drafts: read; final: `REPORT_DOWNLOAD_FINAL` |

---

## 10. Database (PostgreSQL 16)

Conventions: `UUID` primary keys (`gen_random_uuid()`), `created_at`, `updated_at`, `created_by`, `updated_by`, `version` (optimistic locking) on mutable tables; `snake_case`; soft delete via `deleted_at` where noted. No FKs across schemas.

### schema `auth`
- `admins` (id, email unique, full_name, password_hash, status ACTIVE/DISABLED/LOCKED, failed_attempts, locked_until, mfa_enabled, last_login_at, …)
- `roles` (id, code unique, name, description, system_role bool)
- `permissions` (code PK, description)
- `role_permissions` (role_id, permission_code)
- `admin_roles` (admin_id, role_id)
- `refresh_tokens` (id, admin_id, family_id, token_hash, expires_at, revoked_at, replaced_by, ip, user_agent)
- `totp_secrets` (admin_id, secret_encrypted, confirmed_at)
- `backup_codes` (id, admin_id, code_hash, used_at)
- `invitations` (id, email, role_ids, token_hash, expires_at, accepted_at, invited_by)
- `login_attempts` (id, email, ip, success, reason, at)
- `audit_log` (id, at, actor_id, actor_email, action, entity_type, entity_id, case_id, ip, user_agent, before JSONB, after JSONB, correlation_id) — **append-only** (DB role without UPDATE/DELETE; trigger blocks changes)

### schema `cases`
- `clients` (id, name, display_name (multi-line), logo_document_id, default_check_types JSONB, active)
- `cases` (id, report_id unique, client_id, issue_date, period_show, period_start, period_end, status_preset, status_title, status_subtitle, total_override, completed_override, overall_status_override, analyst_remarks, final_recommendation, layout_cards (4|6), date_format (NUMERIC|TEXT), watermark_enabled, watermark_text, lifecycle (DRAFT|IN_REVIEW|CHANGES_REQUESTED|APPROVED|FINALIZED), review_comment, due_date, deleted_at, …)
- `candidates` (id, case_id unique, full_name, parent_type (FATHER|GUARDIAN), parent_name, employee_id, dob, phone, photo_document_id, street, city, state, pin, country)
- `verification_checks` (id, case_id, type, title, summary_description, this_card_verifies, status, verification_type, requested_date, completed_date, dates_manual, remarks, has_attestation, bar_council_no, disclaimer, sort_order)
- `check_fields` (id, check_id, field_key, label, value, value_encrypted, value_last4, verified_tick, is_manual, source (CANDIDATE|MANUAL|API), sort_order)
- `check_details` (id, check_id, label, value, sort_order)
- `check_free_sections` (id, check_id, kind (TEXT|IMAGE), text_value, document_id, sort_order)
- `case_assignments` (case_id, admin_id, role_in_case (PREPARER|REVIEWER), assigned_at, assigned_by)
- `report_id_sequences` (year, next_value) — Report ID format `NX-YYYY-NNNN` (editable)

### schema `verification`
- `verification_requests` (id, check_id, case_id, provider, status, requested_at, completed_at, attempts, error)
- `verification_results` (id, request_id, raw_response JSONB (encrypted fields inside), mapped_fields JSONB)

### schema `documents`
- `documents` (id, case_id, check_id nullable, kind (PHOTO|CHECK_DOC|FREE_IMAGE|CLIENT_LOGO), label, storage_key, original_filename, mime_type, size_bytes, sha256, width, height, quality (HIGH|MEDIUM|LOW), move_to_next_page, use_larger_box, crop JSONB, sort_order, uploaded_by, deleted_at)

### schema `reports`
- `report_jobs` (id, case_id, status (QUEUED|RUNNING|DONE|FAILED), error, requested_by, started_at, finished_at)
- `report_versions` (id, case_id, version, kind (DRAFT|FINAL), pdf_storage_key, sha256, encrypted, generated_by, finalized_by, finalized_at, snapshot JSONB of the data used)

### Spring Modulith
- `event_publication` table (Modulith JDBC registry) in its own schema `modulith`.

---

## 11. Security (Spring Security RBAC — strong on backend AND frontend)

### 11.1 Roles (seeded; stored in DB; SUPER_ADMIN can create new roles from UI)
| Role | Purpose |
|---|---|
| SUPER_ADMIN | Owner — everything incl. admins and roles |
| OPS_MANAGER | Operations — all cases, clients, assignment, finalize |
| QC_REVIEWER | Reviews / approves / finalizes; does not prepare |
| ANALYST | Prepares assigned cases (data entry, documents, drafts) |
| AUDITOR | Read-only, PII masked, audit log access |

### 11.2 Permissions — code checks PERMISSIONS, never role names
| Permission | SUPER_ADMIN | OPS_MANAGER | QC_REVIEWER | ANALYST | AUDITOR |
|---|:-:|:-:|:-:|:-:|:-:|
| CASE_CREATE | ✓ | ✓ | | ✓ | |
| CASE_READ_ALL | ✓ | ✓ | ✓ | | ✓ |
| CASE_READ_ASSIGNED | ✓ | ✓ | ✓ | ✓ | |
| CASE_UPDATE | ✓ | ✓ | | ✓ (assigned) | |
| CASE_DELETE | ✓ | | | | |
| CASE_ASSIGN | ✓ | ✓ | | | |
| CHECK_UPDATE | ✓ | ✓ | | ✓ (assigned) | |
| DOCUMENT_UPLOAD | ✓ | ✓ | | ✓ (assigned) | |
| DOCUMENT_DELETE | ✓ | ✓ | | | |
| PII_UNMASK | ✓ | ✓ | ✓ | ✓ (assigned) | |
| REPORT_GENERATE | ✓ | ✓ | ✓ | ✓ | |
| REPORT_SUBMIT_FOR_REVIEW | ✓ | ✓ | | ✓ | |
| REPORT_APPROVE | ✓ | ✓ | ✓ | | |
| REPORT_FINALIZE | ✓ | ✓ | ✓ | | |
| REPORT_DOWNLOAD_FINAL | ✓ | ✓ | ✓ | | ✓ |
| ATTESTATION_APPLY | ✓ | ✓ | | | |
| CLIENT_MANAGE | ✓ | ✓ | | | |
| SETTINGS_MANAGE | ✓ | | | | |
| USER_MANAGE | ✓ | | | | |
| ROLE_MANAGE | ✓ | | | | |
| AUDIT_READ | ✓ | | | | ✓ |

"(assigned)" = enforced by `CaseAccessPolicy`: admins without `CASE_READ_ALL` can only touch cases where they are in `case_assignments`.

### 11.3 Workflow & separation of duties
```
DRAFT → IN_REVIEW → APPROVED → FINALIZED (locked, versioned)
            ↓
   CHANGES_REQUESTED → DRAFT
```
- The PREPARER of a case can never approve or finalize it (enforced in `WorkflowService`, regardless of permissions).
- Case data is editable only in DRAFT / CHANGES_REQUESTED.
- FINALIZED is immutable; changes require reopening into a new version (old versions kept).

### 11.4 Backend controls
**Authentication**
- Argon2id password hashing; password policy (min 12 chars, complexity, not in breached-password list, not equal to email).
- **TOTP 2FA mandatory** for every admin (RFC 6238, ±1 step), 10 one-time backup codes (hashed).
- Lockout after 5 failed attempts (15 min, escalating); Bucket4j rate limit on `/api/auth/**` per IP and per email.
- No public sign-up — invite-only (SUPER_ADMIN), invite links expire in 24 h, single use.
- First SUPER_ADMIN created by a one-time bootstrap command/env on first start, forced to set 2FA on first login.

**Tokens & sessions**
- Access token: JWT RS256, 15 min, claims: `sub`, `email`, `roles`, `perms`, `sid`, `jti`. Keys from env/secret store; support key rotation (`kid`).
- Refresh token: opaque random, stored **hashed**, in an **httpOnly; Secure; SameSite=Strict; Path=/api/auth** cookie; rotated on every use; reuse of an old token revokes the whole family.
- CSRF protection (double-submit token) on cookie-based auth endpoints.
- Idle timeout 30 min, absolute session 12 h. Disable admin / role change / password change → revoke sessions immediately (session id checked against a revocation set).

**Authorization — three layers**
1. `SecurityFilterChain`: all `/api/**` authenticated except login/2fa/refresh/invite-accept; stateless.
2. Method security: `@EnableMethodSecurity`; every service entry point has `@PreAuthorize("hasAuthority('…')")`.
3. Data level: `CaseAccessPolicy.check(caseId, action)` on every case-scoped operation (prevents ID tampering).

**Data protection**
- Aadhaar/PAN/UAN and other `sensitive` fields: AES-256-GCM via `EncryptedStringConverter`, key from env/secret store; store `value_last4` for display. Masked by default in every response (`XXXX XXXX 1234`, `ABXXXXX12F`). Reveal needs `PII_UNMASK` and is audited.
- Documents never publicly accessible; access via permission check + 5-min signed URL or streamed response. Buckets private, server-side encryption on.
- Upload validation: magic-byte sniffing (JPEG, PNG, PDF only), max size (10 MB default), image re-encoding to strip metadata/EXIF; ClamAV scan optional (feature flag).
- Secrets only from env / AWS Secrets Manager. Nothing sensitive in logs (log masking for PII fields).

**Audit log** (append-only, nobody can edit/delete, not even SUPER_ADMIN): login success/failure, 2FA events, lockouts, session revocations, PII reveal, case create/update (before/after), check changes, document upload/delete/download, status changes, assignment, submit/approve/request-changes, finalize, attestation use, report download, admin/role/permission changes. Modules publish `AuditEvent`; `auth` persists it.

**Hardening**
- Headers: CSP, HSTS (prod), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`.
- CORS: only the frontend origin (from env). Actuator: only `/actuator/health` exposed publicly; the rest internal.
- Errors never leak stack traces or SQL. Request size limits. Bean Validation on every request record.
- OWASP Dependency-Check + Dependabot in CI; fail build on high/critical CVEs.
- Future (module extraction): service-to-service calls carry an internal service token validated by `InternalServiceAuthFilter`.

### 11.5 Frontend controls
- Access token kept **in memory only** (never localStorage/sessionStorage). Silent refresh via cookie on load and before expiry; on 401 → one refresh attempt → logout.
- `ProtectedRoute` with required permission per route; `<Can permission="…">` hides actions the admin lacks (UX only — backend is the gatekeeper).
- UI driven by `GET /api/me` permissions.
- XSS: no `dangerouslySetInnerHTML` except remarks sanitized with DOMPurify (`<strong>` only). Escape everything else.
- Idle timer: warning at 29 min, logout at 30 min; logout broadcast to all tabs (BroadcastChannel).
- PII: masked by default; "Reveal" calls the reveal endpoint, auto-hides after 30 s, never cached by TanStack Query.
- No secrets in the bundle; strict CSP from Nginx; `npm audit` in CI.

---

## 12. Verification module (future third-party APIs)

- `VerificationProvider` interface: `supports(CheckType)`, `verify(VerificationRequest) → VerificationResult`.
- `ProviderRegistry` picks provider per check type from config (`bgv.verification.providers.AADHAAR=manual`).
- **Phase 1: only `ManualProvider`** — admin-entered values are the result.
- Future vendors: one package per vendor under `internal/provider/vendor/<name>`, using `RestClient` + Resilience4j (timeouts, retries with backoff, circuit breaker). Raw responses stored in `verification_results.raw_response` (sensitive parts encrypted); `ResultToCheckFieldMapper` maps into `check_fields` with `source=API`.
- Flow: `cases` publishes `CheckSubmittedEvent` → `verification` processes → publishes `VerificationCompletedEvent` → `cases` updates fields/status.
- Do NOT implement any real vendor until the user asks.

---

## 13. Report generation

1. `ReportModelAssembler` builds an immutable `ReportModel` from `CaseApi` + `DocumentApi` (PII in the PDF follows the user's policy — default: masked Aadhaar; confirm with user before printing full numbers).
2. `PaginationService` applies §6.2/§6.3 rules; `IconGroupResolver` uses `CheckType.iconGroup`; `DocumentNumbering` renumbers documents by order and handles move-to-next-page / larger box.
3. `HtmlRenderer` renders Thymeleaf templates (ported from the reference HTML — same markup/CSS classes where possible); fonts and images are local resources; document images embedded as data URIs or served via internal URLs.
4. `PdfRenderer` uses Playwright Chromium: `page.pdf(A4, printBackground=true, margin 0, preferCSSPageSize=true)`.
5. Watermark applied in HTML/CSS when enabled.
6. `PdfEncryptionService` (PDFBox): AES-256; optional user (open) password; owner password random per file; permissions: print high-res allowed, modify/copy/assemble denied. Encryption failure = job fails (never output an unprotected "protected" file).
7. Stored in S3 under `reports/{caseId}/v{n}.pdf`; `report_versions` stores sha256 and the data snapshot.
8. Runs on `ReportJobExecutor` (max 2 concurrent renders by default, configurable).
9. Visual regression test: render a fixture case and compare against a reference PDF/PNG snapshot.

---

## 14. Configuration & environments

Profiles: `local`, `prod`, `test`. Prod reads everything from env:

```
DB_URL, DB_USERNAME, DB_PASSWORD
S3_ENDPOINT (blank for AWS), S3_REGION=ap-south-1, S3_BUCKET, S3_ACCESS_KEY, S3_SECRET_KEY
JWT_PRIVATE_KEY, JWT_PUBLIC_KEY, JWT_KEY_ID
PII_ENCRYPTION_KEY (base64, 256-bit), TOTP_ENCRYPTION_KEY
FRONTEND_ORIGIN
BOOTSTRAP_SUPERADMIN_EMAIL (first start only)
REPORT_MAX_CONCURRENT_RENDERS=2
SENTRY_DSN (optional)
```

Local: `infra/local/docker-compose.yml` runs `postgres:16`, `minio`, backend (port 8080), frontend (port 5173 dev or 80 via Nginx). `01-create-schemas.sql` creates schemas `auth, cases, verification, documents, reports, modulith`. `create-buckets.sh` creates private bucket `nexlyn-bgv`.

Prod (now): one machine — Nginx (TLS) + frontend + backend containers; PostgreSQL on AWS RDS; files on S3 ap-south-1; daily DB backups + S3 versioning.

---

## 15. Build phases (build in order; autonomous mode per §0)

| Phase | Scope | Done when |
|---|---|---|
| **1. Foundation** | Repo skeleton, parent POM + wrapper, `bgv-common`, `bgv-app`, `ModularityTests`, Flyway-per-module setup, local Docker Compose (postgres, minio), health endpoint, CI for backend + frontend, React app shell (Vite, Tailwind, shadcn, router, layout) | `docker compose up` works; `./mvnw verify` and `npm run build` pass |
| **2. Auth & RBAC** | `auth` module: admins, roles, permissions (seeded §11.2), login + TOTP 2FA, JWT + refresh rotation, lockout, rate limiting, invites, bootstrap super admin, sessions revocation, audit log, `CaseAccessPolicy` skeleton, security headers; frontend login/2FA, AuthProvider, ProtectedRoute, Can, idle logout, admin & role management screens | Security tests pass (unauthenticated, wrong permission, token reuse, lockout) |
| **3. Clients & Case workspace core** | `cases` module: clients, cases, candidate, verification period, overview, remarks, settings, Report ID generator, assignments, validation, progress; frontend case list + workspace Sections 1,2,3,5,6,7 | Each section saves/loads; validation matches §7.1 |
| **4. Checks** | Check type registry + YAML (§8), sealed payload records + validators, add/save/reorder/delete checks, prefill from candidate, date master, free sections, attestation, PII encryption/masking/reveal; frontend Section 4 dynamic forms | All 18 types can be added and saved |
| **5. Documents** | `documents` module: uploads, sniffing, S3/MinIO, signed access, quality badge, crop, reorder, next-page/larger-box; frontend uploader + editor | Files stored in MinIO, never public |
| **6. Reports** | Port templates/CSS from reference HTML, assembler, pagination, Playwright render, watermark, PDFBox encryption, jobs, versions, preview | Generated PDF visually matches the reference tool for a fixture case |
| **7. Workflow** | Submit/approve/request changes/finalize with separation of duties, locking, version history, final download permissions; dashboard widgets (counts, due dates, my cases) | Maker-checker enforced by tests |
| **8. Hardening & deploy** | Prod compose + Nginx TLS, env config, backups, runbooks, dependency scanning, load test of PDF generation, audit log viewer, **replace the starter common-password list with a full breached-password list (§17 #7)** | Deployed on one machine |
| **Later** | Third-party verification vendors (§12); enterprise polish from the v3.2 roadmap (risk score, verification timeline, confidence bars, QR verification code, digital signature block, investigator notes, client logo customization); module extraction (§4.4) | — |

---

## 16. Coding conventions

- Java: records for DTOs/events, constructor injection only, no field injection, `final` where possible, no Lombok on entities (Lombok allowed for simple builders if needed — prefer records). Use `Optional` only as return type.
- Controllers thin; business logic in services; services annotated with `@PreAuthorize` and `@Transactional` at the right boundaries.
- Every mutating service method publishes an `AuditEvent`.
- Use ISO dates (`LocalDate`) in API and DB; formatting to `dd/mm/yyyy` / `dd-Mmm-yyyy` only in UI and report rendering.
- Pagination: `?page=&size=` returning `{ items, page, size, total }`.
- Tests: unit tests for services/validators; `@SpringBootTest` + Testcontainers for integration; security tests for every endpoint (401 without token, 403 without permission, 403 on unassigned case for ANALYST).
- Frontend: feature folders, Zod schemas mirror backend validation, generated API client only (`scripts/generate-api-client.sh`), no `any`.
- Commit messages: Conventional Commits (`feat(cases): …`, `fix(auth): …`).
- Never log PII, tokens, passwords, or document contents.

### 16.1 UI/UX improvement phase and the design skill (`.claude/skills/ui-ux-pro-max`) — rules (owner, 2026-09-25)

A UI/UX improvement phase for the admin dashboard is under way. It uses a third-party design-advice skill (MIT licence, its LICENSE is beside it). The skill only gives **recommendations**; it never overrides this file, the owner, or a decision in `docs/DECISIONS.md`. The rules for the phase:

1. **Only for the admin dashboard UI:** the React + Tailwind + shadcn/ui code in `frontend/`. Nothing else.
2. **Never change the PDF report.** Do not use the skill for, and do not apply its advice to, the report templates, styles, fonts or images (`backend/modules/reports`: `templates/report/**`, `static/report/**`, the Java that builds pages). The report must match the reference HTML (`docs/reference/nexlyn-bgv-report-v3.2.html`, §6) exactly; the only deviations allowed are bug fixes logged in `docs/DECISIONS.md` (for example D-036).
3. **This file always wins.** If the skill disagrees with CLAUDE.md, follow CLAUDE.md. The symbols and colours it defines stay exactly as written: the check-status symbols ✓ ✕ ⓘ − ⏱ ↻ and the status-pill colours of §6.2, the date badges ★ Master / 🔄 Auto / ✏️ Manual, and the section marks of §7 (○ ● ⚠), even though the skill advises against emoji as icons and suggests palettes of its own. The locked decisions of §2 (React + Tailwind + shadcn/ui, and so on) stay too.
4. **Never change security behaviour.** A restyle may change how something looks, never what it does: sign-in and two-step login; the access token kept in memory only (**no tokens in `localStorage`, `sessionStorage`, cookies readable by scripts or the URL**); silent refresh and CSRF; the idle timer (warning at 29 minutes, sign-out at 30); permission checks (`ProtectedRoute`, `<Can>`); PII **masked by default** with the audited 30-second reveal; DOMPurify on remarks; and the Content-Security-Policy of §11.5. Where the skill suggests something that would need to relax any of these (for example loading fonts, icons or scripts from a CDN or Google Fonts), do not apply it: fonts and icons are bundled locally.
5. **Never edit the generated API client by hand** (`frontend/src/api/generated/`, §3.2; it does not exist yet and is git-ignored). UI work does not change how the frontend talks to the backend either: the hand-written typed client (`frontend/src/api/httpClient.ts` and the `features/*/api.ts` files) and its request and response shapes are left alone unless a backend change requires it.
6. **Keep every existing test passing** (`npm run lint`, `npx tsc -b`, `npm test`, `npm run build`), and **commit after each screen** (Conventional Commits, `feat(ui): ...`), so any one screen can be reverted on its own.

Practical notes: the skill is used only when asked to change how the admin UI looks or feels; take its advice as input and check it against §2, §6, §7 and §11 first; do not use `--persist --force` (it overwrites saved design decisions) and do not add files it generates to the project without saying so; before starting a batch of UI work, tell the owner the plan and wait for the go-ahead.

---

## 17. Open items (ask the user when relevant)

1. **Review provisional check-type fields** (§8 rows marked provisional) — user will confirm later; keep config-driven.
2. Confirm adding **POLICE** check type.
3. Whether the final PDF shows **full or masked** Aadhaar/PAN numbers.
4. Report ID format (`NX-YYYY-NNNN` assumed).
5. Services page content — port as-is from reference HTML unless told otherwise.
6. RDS vs self-hosted PostgreSQL on the single machine (RDS recommended).
7. ~~Bigger common-password list~~ **Done 2026-09-25 (D-034):** `common-passwords.txt` is now the NCSC top-100k list (about 97,700 entries). Password rules stay: minimum 12 characters (never lower), at least 3 of 4 character classes, not equal to the email, not in the list.
