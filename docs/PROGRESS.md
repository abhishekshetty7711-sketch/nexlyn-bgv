# Progress

Read this file and `docs/DECISIONS.md` at the start of every session, then continue from
"Next step". Update it (and commit) after every step.

_Last updated: 2026-09-24_

## Phase status (CLAUDE.md section 15)

| Phase | Status |
|---|---|
| 1. Foundation | **Done** (commit `db0689b`). Stack starts with `./scripts/local-up.sh -d`; MinIO off by default (D-002). |
| 2. Auth & RBAC | **In progress** |
| 3. Clients & Case workspace core | Not started |
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
| 2c. 2FA and tokens (TOTP, backup codes, JWT, refresh rotation, CSRF, `/api/auth/*`) | **In progress** (encryption helper + V3 migration + dependencies done) |
| 2d. Authorization (`SecurityFilterChain`, `@PreAuthorize`, `CaseAccessPolicy`, headers, CORS, `GET /api/me`) | Not started |
| 2e. Admin management and audit (invites, admin/role CRUD, session revocation, audit persistence) | Not started |
| 2f. Frontend (login/2FA, AuthProvider, ProtectedRoute, Can, idle logout, admin/role screens) | Not started |

## Next step

Finish 2c, run `./mvnw verify`, commit, then start 2d.

## Open issues and things only the owner can do

- **Avast HTTPS scanning** breaks Maven/npm inside Docker builds (certificate error). It must be turned off while images are built, then can be turned back on. Claude cannot change it. Check with `openssl s_client -connect repo.maven.apache.org:443` (issuer must not be Avast).
- **`docs/reference/` is empty.** Phase 6 must port the report layout from the original HTML tool. Copies exist at `C:\Users\abhis\Desktop\Nexlyn BGV.html` and `C:\Users\abhis\Projects\futurenexlyn-bgv-portal\New_nexlyn-bgv-report.html`. Before copying one into the repo, check it contains no real candidate data.
- **Before production:** replace the starter common-password list (CLAUDE.md section 17 item 7); choose the S3-compatible store for local development (D-002).
- **Local admin:** set `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` in `infra/local/.env` to create the first admin (the owner picks the password).
