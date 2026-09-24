# Progress

Read this file and `docs/DECISIONS.md` at the start of every session, then continue from
"Next step". Update it (and commit) after every step.

_Last updated: 2026-09-24_

## Phase status (CLAUDE.md section 15)

| Phase | Status |
|---|---|
| 1. Foundation | **Done** (commit `db0689b`). Stack starts with `./scripts/local-up.sh -d`; MinIO off by default (D-002). |
| 2. Auth & RBAC | **Done** (steps 2a-2f). Done-when met: security tests cover unauthenticated, wrong permission, refresh-token reuse and lockout. 106 auth + 6 common + 5 app backend tests, 97 frontend tests. Only the Docker-image rebuild check is pending (see open issues). |
| 3. Clients & Case workspace core | **Done** (3a-3c). Done-when met: every section saves and loads, validation follows section 7.1. 41 cases + 16 common + 106 auth + 5 app backend tests; 155 frontend tests. |
| 4. Checks | **Done** (4a-4c). Done-when met: all 18 types can be added and saved (backend tests add and save every type; frontend renders any definition). 92 cases + 23 common + 106 auth + 5 app backend tests; 197 frontend tests. |
| 5. Documents | **Done** (5a-5c). Done-when met: files are stored in an S3-compatible bucket and never public (tests against S3Mock and SeaweedFS). 43 documents + 92 cases + 23 common + 106 auth + 5 app backend tests; 233 frontend tests. The Docker image has not been rebuilt with this code yet (needs Avast HTTPS scanning off, see open issues). |
| 6. Reports | Not started |
| 7. Workflow | Not started |
| 8. Hardening & deploy | Not started |

## Phase 2 steps

| Step | Status |
|---|---|
| 2a. Database (tables + seeded roles/permissions) | Done (`b07a34c`) |
| 2b. Passwords, lockout, rate limiting, bootstrap admin | Done (`bae6348`, `faffc6c`) |
| 2c. 2FA and tokens (TOTP, backup codes, JWT, refresh rotation, CSRF, `/api/auth/*`) | **Done.** 71 auth tests + 6 common tests + full-app smoke test pass. |
| 2d. Authorization (`SecurityFilterChain`, `@PreAuthorize`, `CaseAccessPolicy`, headers, CORS, `GET /api/me`) | **Done.** 86 auth tests + 5 app tests pass. Covers unauthenticated, wrong permission, expired/tampered/revoked tokens, per-case rule, CORS, headers. |
| 2e. Admin management and audit (invites, admin/role CRUD, session revocation, audit persistence) | **Done.** 106 auth tests + 6 common + 5 app tests pass. |
| 2f. Frontend (login/2FA, AuthProvider, ProtectedRoute, Can, idle logout, admin/role screens) | **Done.** Login with 2FA, first-time setup (QR + backup codes), invitation accept, change password, admins, roles, audit log. 97 tests, lint and build clean. |

## Next step

Start **Phase 6: Reports** (CLAUDE.md section 15, with sections 6, 9.4, 13 and 10 tables `report_jobs`, `report_versions`): port the layout, CSS, pagination and formatting rules from the original HTML tool into Thymeleaf templates, `ReportModelAssembler` (uses `CaseApi` and documents), `PaginationService`, Playwright Chromium render, watermark, PDFBox AES-256 encryption, background jobs, versions, preview endpoint; frontend Generate section (preview, generate, version list). **The reference file is not in the repo:** `docs/reference/` is empty (see open issues). Build everything that does not depend on it first (assembler, model, job executor, versions, encryption, endpoints), then port the layout from the copies named below once the owner has confirmed they hold no real candidate data. Cases and Documents also need a public read API for the assembler (`CaseApi` / a new `DocumentApi` in the root package): add it in this phase.

## Open issues and things only the owner can do

- **Avast HTTPS scanning** breaks Maven/npm inside Docker builds (certificate error). It must be turned off while images are built, then can be turned back on. Claude cannot change it. Check with `openssl s_client -connect repo.maven.apache.org:443` (issuer must not be Avast). Host builds work regardless (D-015).
- **The running Docker stack is still on the Phase 2b image** (it also lacks the new SeaweedFS `storage` service, so file uploads cannot be tried in the browser until it is rebuilt). Rebuilding it (`./scripts/local-up.sh -d`) with the 2c code needs Avast HTTPS scanning off. Until then the full-app smoke test is the end-to-end check. The first rebuild also generates local JWT and 2FA keys into `infra/local/.env`.
- **`docs/reference/` is empty.** Phase 6 must port the report layout from the original HTML tool. Copies exist at `C:\Users\abhis\Desktop\Nexlyn BGV.html` and `C:\Users\abhis\Projects\futurenexlyn-bgv-portal\New_nexlyn-bgv-report.html`. Before copying one into the repo, check it contains no real candidate data.
- **Before production:** replace the starter common-password list (CLAUDE.md section 17 item 7). The local file store is now SeaweedFS (D-031); review that MinIO deviation.
- **Local admin:** set `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` in `infra/local/.env` to create the first admin (the owner picks the password).
