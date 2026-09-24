# Progress

Read this file and `docs/DECISIONS.md` at the start of every session, then continue from
"Next step". Update it (and commit) after every step.

_Last updated: 2026-09-24_

## Phase status (CLAUDE.md section 15)

| Phase | Status |
|---|---|
| 1. Foundation | **Done** (commit `db0689b`). Stack starts with `./scripts/local-up.sh -d`; MinIO off by default (D-002). |
| 2. Auth & RBAC | **Done** (steps 2a-2f). Done-when met: security tests cover unauthenticated, wrong permission, refresh-token reuse and lockout. 106 auth + 6 common + 5 app backend tests, 97 frontend tests. Only the Docker-image rebuild check is pending (see open issues). |
| 3. Clients & Case workspace core | **In progress:** 3a done (clients, cases schema, shared validators); 3b done (cases, sections 1-3 and 5-7, report ID generator, assignments, progress, validation; 41 cases tests); 3c (frontend: case list, workspace, clients, assignments) to do |
| 4. Checks | Not started |
| 5. Documents | Not started |
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

Start **Phase 3: Clients & Case workspace core** (CLAUDE.md section 15, with sections 7, 9.2 and 10 "schema cases"): `cases` module with clients, cases, candidate, verification period, overview, remarks, settings, Report ID generator, assignments, validation and progress; frontend case list and workspace sections 1, 2, 3, 5, 6, 7. The `cases` module must implement `CaseAssignmentLookup` (D-019) and call `CaseAccessPolicy.check(...)` in every case-scoped service method.

## Open issues and things only the owner can do

- **Avast HTTPS scanning** breaks Maven/npm inside Docker builds (certificate error). It must be turned off while images are built, then can be turned back on. Claude cannot change it. Check with `openssl s_client -connect repo.maven.apache.org:443` (issuer must not be Avast). Host builds work regardless (D-015).
- **The running Docker stack is still on the Phase 2b image.** Rebuilding it (`./scripts/local-up.sh -d`) with the 2c code needs Avast HTTPS scanning off. Until then the full-app smoke test is the end-to-end check. The first rebuild also generates local JWT and 2FA keys into `infra/local/.env`.
- **`docs/reference/` is empty.** Phase 6 must port the report layout from the original HTML tool. Copies exist at `C:\Users\abhis\Desktop\Nexlyn BGV.html` and `C:\Users\abhis\Projects\futurenexlyn-bgv-portal\New_nexlyn-bgv-report.html`. Before copying one into the repo, check it contains no real candidate data.
- **Before production:** replace the starter common-password list (CLAUDE.md section 17 item 7); choose the S3-compatible store for local development (D-002).
- **Local admin:** set `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` in `infra/local/.env` to create the first admin (the owner picks the password).
