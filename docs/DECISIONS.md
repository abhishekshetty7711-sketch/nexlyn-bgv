# Decisions log

Every decision Claude makes on the owner's behalf is written here so it can be reviewed and
reversed later. Newest entries go at the bottom. Format: **ID, date, phase/step, decision,
alternatives considered, why, how to reverse.**

Status legend: **confirmed** = the owner explicitly approved it; **auto** = Claude decided under
the autonomy rule in `CLAUDE.md` section 0 (review when convenient).

---

## Before autonomy mode (owner approved or chose)

### D-001 — Build the Spring Boot platform, not the Next.js portal — *confirmed, 2026-09-24*
- **Context:** two BGV implementations existed: `Projects/futurenexlyn-bgv-portal` (Next.js) and `Documents/nexlyn-bgv` (Spring Modulith + React).
- **Decision:** continue with `Documents/nexlyn-bgv`. The Next.js portal is shelved, not deleted.
- **Reverse:** resume the portal; nothing here depends on it.

### D-002 — MinIO is off by default (compose profile `storage`) — *auto, 2026-09-24, Phase 1*
- **Context:** `minio/minio` was removed from Docker Hub and Quay returns 401, so the stack could not start.
- **Decision:** put `minio` and `minio-init` behind the compose profile `storage`. The stack starts without them.
- **Alternatives:** SeaweedFS/Garage (S3-compatible), build MinIO from source (too heavy for 5.8 GB RAM).
- **Follow-up:** Phase 5 (Documents) needs an S3-compatible store. Decide then (see D-0xx when made).
- **Reverse:** remove `profiles: ["storage"]` once a working image exists.

### D-003 — Testcontainers 1.21.4 — *auto, 2026-09-24, Phase 2a*
- Spring Boot 3.3.4 ships an older Testcontainers whose Docker client cannot talk to Docker Engine 29 on this PC.
- Set `testcontainers.version` in `backend/pom.xml`. Reverse: remove the property (tests will fail on Docker 29).

### D-004 — Tests run in UTC — *auto, 2026-09-24, Phase 2a*
- This PC reports the legacy timezone `Asia/Calcutta`, which PostgreSQL 16 rejects. Surefire runs with `-Duser.timezone=UTC`; also makes tests identical on every machine.

### D-005 — Three extra auth columns — *auto, 2026-09-24, Phase 2a*
- `refresh_tokens.family_started_at` (12 h absolute session limit), `admins.lockout_count` (escalating lockout), `admins.password_changed_at`. Small additions to CLAUDE.md section 10; needed to implement section 11.4.

### D-006 — Bootstrap admin password comes from `BOOTSTRAP_SUPERADMIN_PASSWORD` — *confirmed, 2026-09-24, Phase 2b*
- The spec only lists the email variable, but an initial password is needed. Used once, only when no admin exists, checked against the password policy, never logged. Prod could later use an invite link instead.

### D-007 — Password complexity = 3 of 4 character classes — *confirmed, 2026-09-24, Phase 2b*
- Rules: minimum **12** characters (a test fails if this is lowered), at least 3 of upper/lower/digit/symbol, not equal to the email, not in the common-password list.

### D-008 — Bundled common-password list is a starter only — *confirmed, 2026-09-24, Phase 2b*
- ~150 entries. **Must be replaced with a full breached-password list before production** (CLAUDE.md section 17 item 7, Phase 8).

### D-009 — `bgv-common` is an OPEN Spring Modulith module — *confirmed, 2026-09-24, Phase 2b*
- Otherwise `ModularityTests` rejects other modules using `common.error`, `common.crypto`, etc. Business logic and entities must still never go in `bgv-common`.

---

## Autonomy mode (Claude decides, owner reviews later)

### D-010 — Account lock is time-based, `status` stays ACTIVE — *auto, 2026-09-24, Phase 2b*
- Lockout uses `admins.locked_until` and unlocks by itself. `status = LOCKED` is reserved for a manual lock by a super admin (Phase 2e).

### D-011 — A correct password alone does NOT reset lockout counters — *auto, 2026-09-24, Phase 2c*
- Only a completed login (password + second factor) resets them. Otherwise someone holding a stolen password could guess 2FA codes indefinitely by re-entering the password.
- Wrong 2FA codes count toward the same lockout.

### D-012 — Argon2id parameters — *auto, 2026-09-24, Phase 2b*
- 19 MiB memory, 2 iterations, 1 lane, 16-byte salt, 32-byte hash (OWASP minimum). Raise later if the server has headroom.

### D-013 — Rate-limit defaults — *auto, 2026-09-24, Phase 2b*
- Per IP: 30 requests/minute on `/api/auth/**`. Per email: 10 attempts / 15 min (login) and a separate 10 / 15 min budget for 2FA. In-memory (single instance). Configurable under `nexlyn.auth.rate-limit.*`.
- Behind the production reverse proxy, the real client IP must be forwarded and trusted (Phase 8).

### D-014 — Phase 2c token design — *auto, 2026-09-24, Phase 2c*
- **JWT library:** Nimbus JOSE directly, not `spring-boot-starter-oauth2-resource-server`, so Spring Security auto-configuration does not lock every endpoint before step 2d builds the filter chain.
- **Challenge token:** a short-lived (5 min) signed JWT proving the password step passed, with purpose `VERIFY` or `SETUP`.
- **Refresh tokens:** opaque, stored as SHA-256 hash, httpOnly + SameSite=Strict cookie on `/api/auth`, rotated on every use. TTL = 30 min idle, capped at 12 h from login. Reusing a rotated token revokes the whole session family; a second presentation within 10 s of rotation is treated as a harmless double-refresh (two browser tabs) and does not kill the session.
- **CSRF:** double-submit cookie `csrf_token` (readable by JS) must equal header `X-CSRF-Token` on refresh/logout.
- **Logout:** authenticated by the refresh cookie + CSRF (works even after the access token expired), instead of requiring an access token.
- **Locked account response:** HTTP 423 `ACCOUNT_LOCKED` with `Retry-After`. This reveals that an email exists; chosen for usability with a handful of internal admins. Flip to a generic 401 in `AuthController` if preferred.
- **Backup codes:** 10 codes of 10 characters (no look-alike characters), stored as Argon2id hashes, single use.
- **TOTP:** RFC 6238 (SHA-1, 6 digits, 30 s, +-1 step) with replay protection (`totp_secrets.last_used_step`, migration V3). Secrets encrypted with AES-256-GCM.
- **Keys:** `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` / `JWT_KEY_ID`, `TOTP_ENCRYPTION_KEY` from the environment. Prod refuses to start without them; other profiles generate temporary keys with a warning. `scripts/local-up.sh` creates persistent local keys in the git-ignored `infra/local/.env`.

### D-015 — Host Maven trusts the Windows certificate store while Avast HTTPS scanning is on — *auto, 2026-09-24, Phase 2c*
- **Context:** Avast re-signs HTTPS traffic with its own root certificate, which the JDK does not trust, so Maven cannot download dependencies. Docker image builds have the same problem.
- **Decision:** for host builds only, run Maven with `MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"` (uses the Windows certificate store, which already contains the Avast root). It is **not** committed to the repo, because it would break Linux CI.
- **Docker builds still need Avast HTTPS scanning switched off** (owner action, see PROGRESS.md), or a root-certificate step added to the Dockerfiles.
- **Reverse:** stop setting `MAVEN_OPTS`.

### D-016 — Extra dependency versions pinned — *auto, 2026-09-24, Phase 2b/2c*
- Spring Boot does not manage these, so they are pinned in `backend/pom.xml`: Nimbus JOSE JWT 9.40, Bucket4j 8.14.0 (`bucket4j_jdk17-core`), BouncyCastle 1.78.1 (needed by Spring Security Argon2). Caffeine and commons-codec use Boot-managed versions. Revisit during dependency scanning (Phase 8).

### D-017 — Full-application smoke test — *auto, 2026-09-24, Phase 2c*
- `BgvApplicationSmokeTest` boots every module against a Testcontainers PostgreSQL initialised with the real `infra/local/postgres/init/01-create-schemas.sql`. It checks health, that every module has its own Flyway history, and that the bootstrap admin can start logging in. It stands in for `docker compose up` while image builds are blocked by Avast, and stays useful afterwards.

### D-018 — Bulk queries must not clear the Hibernate session — *auto, 2026-09-24, Phase 2c*
- `@Modifying(clearAutomatically = true)` detached the already-loaded admin and caused a `LazyInitializationException` (found by the flow tests). The auth repositories use `flushAutomatically = true` only. Remember this for every future bulk update or delete.

### D-019 — Phase 2d authorization design — *auto, 2026-09-24, Phase 2d*
- **Filter chain:** everything under `/api/**` needs a valid bearer token, except `/api/auth/{login,refresh,logout,2fa/**}` (they authenticate themselves) and `/actuator/health`. Other actuator endpoints need `SETTINGS_MANAGE`. Any path outside `/api` and `/actuator` is denied. Anonymous callers get 401 even for paths that do not exist, so route names are not leaked.
- **Session check on every request:** the access-token filter also verifies the session is still alive (`SessionService.isSessionActive`, one small query). That is what makes logout, admin disable and theft detection take effect immediately instead of after the 15-minute token lifetime. Fine for one instance; add a short cache if load ever requires it.
- **CSRF protection is off for `/api/**`** because it is stateless and authenticated by the `Authorization` header, not a cookie. The cookie endpoints under `/api/auth` do their own double-submit check (D-014).
- **Security headers:** CSP `default-src 'none'; frame-ancestors 'none'` (the API only returns JSON), `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`, HSTS 1 year (only sent over HTTPS), and no-cache defaults. The frontend's own CSP will come from Nginx (section 11.5).
- **CORS:** exactly one allowed origin from `nexlyn.cors.allowed-origin` (with credentials, for the refresh cookie). If it is blank, no cross-origin access at all.
- **`CaseAccessPolicy`:** public interface in the auth root package. Rule: the admin needs the permission for the action, and unless they hold `CASE_READ_ALL` the case must be assigned to them. Assignments live in the `cases` schema, so the `cases` module (Phase 3) must implement the small SPI `CaseAssignmentLookup`. Until it exists nobody counts as assigned. A nonexistent case behaves like an unassigned one (403), so guessing ids reveals nothing.
- **`Permission` enum** in `bgv-common` mirrors the seeded permissions; a test fails if the enum and the database drift apart (a typo in `hasAuthority(...)` would otherwise silently lock everyone out).
- `JwtAuthenticationFilter` is created inside `SecurityConfig` and is deliberately not a Spring bean (a bean would be registered twice).

### D-020 — Phase 2e admin management design — *auto, 2026-09-24, Phase 2e*
- **Invite-only, no "create admin with a password" endpoint.** The spec lists `POST /api/admins`, but the same spec says there is no public sign-up and admins are invited (section 11.4), so `POST /api/admins/invitations` is the only way in. Extra endpoints added because the UI needs them: `GET /api/admins/{id}`, `POST /api/admins/{id}/enable`, `POST /api/admins/{id}/unlock`, `GET` and `DELETE /api/admins/invitations`, `GET /api/roles/{id}`.
- **No email sending yet.** The invitation link token is returned once in the API response (`inviteToken`, hash-only in the database, 24 h, single use). The frontend builds the link (`/accept-invite?token=...`) for the inviter to pass on. Inviting the same email again replaces the earlier link. Every problem with a link (unknown, used, expired, revoked) gets the same answer.
- **Accepting an invitation** sets name and password (policy enforced), then continues as a first login: 2FA setup and confirm. A weak password does not burn the link.
- **No escalation:** you can only grant roles or permissions you hold yourself, and only manage admins who hold no more than you do (`PrivilegeGuard`). Matters if a custom role ever carries `USER_MANAGE` or `ROLE_MANAGE` without everything else.
- **No lock-out:** after any change, at least one active admin must still hold `USER_MANAGE` and one `ROLE_MANAGE`, otherwise the whole change is rolled back (409). You cannot disable yourself.
- **Sessions end immediately** when an admin is disabled, their roles change, a role's permissions change, or they change their password (tokens carry permissions for up to 15 minutes otherwise).
- **Built-in roles are fixed:** their permissions cannot change and they cannot be deleted; name and description can. Anything else needs a custom role. Custom roles cannot be deleted while assigned.
- **Own password change** re-checks the current password (a stolen access token alone is not enough), counts a wrong current password toward lockout, and ends all sessions including the current one, so the admin signs in again.
- **Audit log:** persisted synchronously in the publisher's transaction (an action and its audit row succeed or fail together). Actor, IP and user agent are filled in from the current request when the publisher did not set them. Before/after snapshots contain no secrets (no hashes, no tokens); a test scans every row for that. `GET /api/audit-log` filters: `actor` (id or email), `action`, `entity`, `entityId`, `caseId`, `from`, `to`; newest first; page size capped at 200. Correlation ids are taken from the logging MDC once a correlation filter exists (Phase 8).

### D-021 — Phase 2f frontend design — *auto, 2026-09-24, Phase 2f*
- **Session handling (CLAUDE.md 11.5):** the access token lives only in a module variable, never in storage or the URL. On page load the httpOnly refresh cookie restores the session (skipped entirely when there is no `csrf_token` cookie, so a visitor causes no needless requests). Renewals from several places share one request so a rotated refresh token is never sent twice. An expired token is renewed once and the request retried once; if that fails the session ends with an explanation on the login page.
- **Idle logout:** warning at 29 minutes, sign-out at 30. Once the warning shows, only the "Stay signed in" button counts (a stray mouse move does not silently cancel it). Activity and sign-out are shared between browser tabs (BroadcastChannel). Signing out clears all cached query data so nothing from the old session stays in memory.
- **First-time 2FA:** QR code is drawn in the browser (`qrcode.react`), so the secret never goes to a third party. Nothing is signed in until the admin has ticked "I have saved these codes" (backup codes are shown only once).
- **Invitation link:** the token is read once and removed from the address bar (no browser history or Referer leak).
- **Password change** ends every session, this one included, and the login page says why.
- **Backend tweak:** `GET /api/roles` (list only) is also allowed for `USER_MANAGE`, because someone who invites or edits admins must see the roles to assign. Everything else about roles still needs `ROLE_MANAGE`.
- **Cases and Clients** menu entries exist but show a "coming soon" page until Phase 3.
- **Tooling:** `qrcode.react` and `@testing-library/user-event` added; `tsconfig.app.json` sets `strict` explicitly; the dev server proxies `/api` and `/actuator/health` to the backend (same origin, like production); Vitest is limited to 2 workers because this PC (6 GB shared with Docker and an IDE) runs out of memory otherwise; on this PC npm needs `NODE_OPTIONS=--use-system-ca` while Avast HTTPS scanning is on (same idea as D-015).
- **Known, deferred:** the production bundle is 556 kB (one chunk). Split the admin pages with lazy routes when it starts to matter (Phase 8).
