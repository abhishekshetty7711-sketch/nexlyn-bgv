# Progress

Read this file and `docs/DECISIONS.md` at the start of every session, then continue from
"Next step". Update it (and commit) after every step.

_Last updated: 2026-09-25_

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
| 8. Hardening & deploy | **Done as far as it can be done without the owner** (8a-8c, D-034): NCSC password list, correlation ids, JSON logs, proxy-aware prod config, nightly purge of retired documents, lazy routes, Dockerfiles, prod compose + nginx (TLS, rate limits), CI / security / dependabot / manual deploy workflows, backup script, runbooks, PDF load test (about 10 s per heavy report). NOT verified here: building the images, TLS, the AWS side and running the workflows (owner-only items below). |

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

All eight phases of CLAUDE.md section 15 are built. What is left needs the owner (see below) or is in the "Later" row of section 15 (third-party verification vendors, roadmap polish, module extraction), which must not start until the owner asks. Suggested first use: bring the local stack up (`./scripts/local-up.sh -d`, needs Avast HTTPS scanning off), create the admin, and try a real case end to end; then review the open items of CLAUDE.md section 17.

## Open issues and things only the owner can do

- **Avast HTTPS scanning** breaks Maven/npm inside Docker builds (certificate error). It must be turned off while images are built, then can be turned back on. Claude cannot change it. Check with `openssl s_client -connect repo.maven.apache.org:443` (issuer must not be Avast). Host builds work regardless (D-015).
- **The running Docker stack is still on the Phase 2b image** (it also lacks the new SeaweedFS `storage` service, so file uploads cannot be tried in the browser until it is rebuilt). Rebuilding it (`./scripts/local-up.sh -d`) with the 2c code needs Avast HTTPS scanning off. Until then the full-app smoke test is the end-to-end check. The first rebuild also generates local JWT and 2FA keys into `infra/local/.env`.
- **`docs/reference/` now holds the reference tool** (sanitised copy, D-032). Nothing needed from the owner.
- **Password list:** replaced by the NCSC top-100k list (D-034), so CLAUDE.md section 17 item 7 is done. The local file store is SeaweedFS (D-031); review that MinIO deviation.
- **Going live (owner only, all listed in `infra/prod/README.md`):** a Linux server with Docker; a domain and a TLS certificate; AWS RDS (PostgreSQL 16) and an S3 bucket in ap-south-1; real secrets in `infra/prod/.env` (`JWT_PRIVATE_KEY`, `PII_ENCRYPTION_KEY`, `TOTP_ENCRYPTION_KEY`, `DB_PASSWORD`, bootstrap admin) and an **offline backup of `PII_ENCRYPTION_KEY`** (losing it makes stored Aadhaar / PAN / UAN unreadable); the real host name in `infra/prod/nginx/nexlyn.conf`; the daily backup cron job.
- **GitHub (owner only):** pushing the repository, the secret `NVD_API_KEY`, the AWS role `AWS_DEPLOY_ROLE_ARN` and variable `AWS_ACCOUNT_ID` for the `deploy` workflow. The workflows are written but have never run.
- **Images not built yet:** the backend and web Docker images (Chromium, fonts, non-root, health checks) need Avast HTTPS scanning off to build. Their compose and nginx configs are validated; the first real build may need small fixes.
- **Open questions for the owner (CLAUDE.md section 17):** full or masked Aadhaar / PAN on the PDF (masked is built), the provisional check-type fields, POLICE, Report ID format, RDS.
- **Local admin:** set `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` in `infra/local/.env` to create the first admin (the owner picks the password).
