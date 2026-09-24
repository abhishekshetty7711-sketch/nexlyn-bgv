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
| 6. Reports | **Done** (6a-6d). Done-when met: a fixture case renders to a PDF whose layout matches the reference tool (printed both and compared page by page); the browser's page count equals the plan; cut-off pages are detected. 69 reports + 43 documents + 92 cases + 23 common + 106 auth + 5 app backend tests; 247 frontend tests. Finalize (protected final PDF) belongs to Phase 7. |
| 7. Workflow | **Done** (7a-7d). Done-when met: maker-checker is enforced by tests (preparer, creator, submitter, later-added preparer, super admin). 14 workflow + 7 dashboard + 12 finalize tests among 81 reports, 43 documents, 113+ cases, 23 common, 106+ auth and 5 app backend tests; 269 frontend tests. |
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

Start **Phase 8: Hardening & deploy** (CLAUDE.md section 15, with sections 3.3, 11.4, 14, 17): production Docker Compose and Nginx with TLS (`infra/prod`), the backend Dockerfile with Chromium and fonts for the report PDFs (Noto for Indian scripts) and a non-root user, env config and `.env.example` for prod (JWT keys, PII / TOTP keys, S3, RDS, bootstrap admin), CORS and CSP from Nginx, backups (database dump, S3 versioning) and runbooks, dependency scanning (OWASP Dependency-Check, `npm audit`, Dependabot) in CI, the load test of PDF generation, the audit-log viewer polish, **replace the starter common-password list (CLAUDE.md 17 item 7)**, a purge job for retired documents (D-030), lazy-route code splitting (bundle over 600 kB), correlation-id filter and trusted-proxy client IPs, GitHub Actions workflows (`ci-backend`, `ci-frontend`, `deploy`; anything that pushes or deploys is owner-only). Owner-only items (secrets, DNS and certificates, AWS accounts, spending) must be listed, not attempted.

## Open issues and things only the owner can do

- **Avast HTTPS scanning** breaks Maven/npm inside Docker builds (certificate error). It must be turned off while images are built, then can be turned back on. Claude cannot change it. Check with `openssl s_client -connect repo.maven.apache.org:443` (issuer must not be Avast). Host builds work regardless (D-015).
- **The running Docker stack is still on the Phase 2b image** (it also lacks the new SeaweedFS `storage` service, so file uploads cannot be tried in the browser until it is rebuilt). Rebuilding it (`./scripts/local-up.sh -d`) with the 2c code needs Avast HTTPS scanning off. Until then the full-app smoke test is the end-to-end check. The first rebuild also generates local JWT and 2FA keys into `infra/local/.env`.
- **`docs/reference/` now holds the reference tool** (sanitised copy, D-032). Nothing needed from the owner.
- **Before production:** replace the starter common-password list (CLAUDE.md section 17 item 7). The local file store is now SeaweedFS (D-031); review that MinIO deviation.
- **Local admin:** set `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` in `infra/local/.env` to create the first admin (the owner picks the password).
