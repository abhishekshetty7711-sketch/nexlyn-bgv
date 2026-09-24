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

### D-022 — Overview numbers worked out from the checks — *auto, 2026-09-24, Phase 3b*
- The spec says Total, Completed and Overall Status are automatic with manual override but not how. Chosen rules (`OverviewCalculator`): **Total** = number of checks. **Completed** = checks with an outcome (everything except Pending and In Progress). **Overall status**, first match wins: no checks = "Pending"; any Discrepancy = "Discrepancy"; any Unable to Verify = "Unable to Verify"; any Pending / In Progress = "In Progress"; all Closed = "Closed"; otherwise "Completed". Check against the reference HTML tool in Phase 6 and adjust if it differs.

### D-023 — Case model choices — *auto, 2026-09-24, Phase 3b*
- **Report IDs** `NX-YYYY-NNNN`: one atomic counter per year (year in Indian time). Editable, unique ignoring case, **and never reused even after a case is deleted**. A generated ID that clashes with a hand-typed one is skipped.
- **`cases.company_display_name`** (nullable) added: overrides the client's printed name for one report ("Company name ... prefilled" in section 1). Empty = use the client's name.
- **The creator of a case becomes its PREPARER**, whatever their permissions (so an analyst can see their own case, and the maker of a report is always on record for the maker-checker rule).
- **Assignments:** any role (preparer or reviewer) grants visibility; one admin cannot hold both roles on one case; only active admins can be assigned; finalized cases cannot be reassigned. Assignment changes do not change the case version.
- **Soft delete only**, needs `CASE_DELETE`, and a finalized report can never be deleted.
- **Client** is required on every case and must be active when chosen (an inactive client already on a case may stay).

### D-024 — Saving sections safely — *auto, 2026-09-24, Phase 3b*
- Every section save carries the `version` the caller last saw; a stale version gets 409 "changed by someone else", and two truly simultaneous saves are caught by the database lock (also 409). Each save moves the version (even if only one section changed, so a colleague's save to any other section also invalidates a stale editor: the whole case is one unit) and answers with the whole updated case so the screen never drifts.
- Cases are editable only while DRAFT or CHANGES_REQUESTED; otherwise 409 with an explanation. Reading always works.
- Audit: one `CASE_SECTION_SAVED:<section>` event per save with before/after, and the case id in the audit row. Snapshots deliberately leave out phone numbers and remark text (lengths only).
- Remarks are sanitised on the server to bold-only HTML (`BoldOnlyHtml`); running it twice changes nothing, so repeated edits never pile up entities.

### D-025 — Progress and validation — *auto, 2026-09-24, Phase 3b*
- Validation follows section 7.1 exactly. Until Phase 4 exists, every case shows the error "Add at least one verification check", and a missing candidate photo stays a warning until Phase 5 (this is correct, not a bug).
- Progress percentage = data sections that have been saved (report info, candidate, period, checks, overview, remarks, settings) out of 7. A saved section that still has validation issues shows a warning mark. "Generate Report" is an action, not counted.
- The checks source is a small interface (`ChecksSummary`) that returns nothing until Phase 4 replaces it.

### D-026 — Case list and visibility — *auto, 2026-09-24, Phase 3b*
- Visibility is enforced inside the database query (admins without `CASE_READ_ALL` only ever get cases assigned to them), on top of the per-case `CaseAccessPolicy` check. Search (`q`) matches Report ID, candidate name and employee ID, ignores case, and treats `%` and `_` as ordinary characters. Page size capped at 100; newest change first.
### D-027 — Phase 3c frontend design — *auto, 2026-09-24, Phase 3c*
- **One workspace page per case** at `/cases/:id`, the chosen section kept in the address (`?section=candidate`) so a link or reload lands on the same section. Sections 1, 2, 3, 5, 6, 7 are editable; 4 (Checks) and 8 (Generate) are placeholders until Phases 4 and 6-7. Section 8 already lists the validation errors and warnings with "Go to section" links; its Preview / Generate / Submit buttons stay disabled.
- **Each section saves on its own** with the case version it was loaded with; the answer (the whole case) replaces the cached case, so the navigator, header and every other screen update at once. A stale version or a locked case shows the server's message with a "Reload the case" button. Read-only whenever the case is locked or the admin lacks `CASE_UPDATE`.
- **Unsaved changes:** switching section, leaving the case for another page (router blocker), or closing the tab asks first. A successful save clears the flag.
- **Remarks editor:** stored as bold-only HTML source in a plain textarea with a Bold button and a live preview that runs through DOMPurify allowing only `<strong>`/`<b>` (the one place the app renders HTML). The server sanitises again. A rich-text editor was deliberately not added (extra dependency, more attack surface).
- **Status presets:** choosing one fills in its standard title and subtitle (still editable). A bug found while testing: React Hook Form keeps only the last `onChange` of radios that share a field, so the choice is read from the event.
- **Phone / PIN / dates** are validated in the browser with the same rules as the server; phone is shown as `+91 XXXXX XXXXX`. The candidate photo is not uploadable yet (Phase 5); the screen says so.
- **Case list** search matches Report ID, candidate name and employee ID; the New case dialog only offers active clients and opens the workspace on creation. Clients page is read-only unless `CLIENT_MANAGE`; "usual checks" is typed as comma-separated codes until Phase 4 provides the check-type picker.
- **Assignments** live in the workspace sidebar (visible to everyone on the case; changing them needs `CASE_ASSIGN` and is hidden for finalized cases).

### D-028 — Phase 4 checks: backend design — *auto, 2026-09-24, Phase 4a/4b*
- **No 18 hand-written payload records.** The spec mentions a Java payload record per type "for validation". Validation is instead driven by the YAML field types (`FieldValues`: text, textarea, date, number, pin, phone, aadhaar with Verhoeff checksum, pan, uan, boolean, select, repeatable rows), so adding a type or a field is a YAML change with no Java. All 18 types load from `check-types/*.yml`; a mistake in a file stops the app at startup with a message naming the file and field (`CheckTypeRegistry`, tested).
- **Sensitive values (Aadhaar, PAN, UAN):** validated, then stored only as AES-256-GCM ciphertext (`PII_ENCRYPTION_KEY`; prod refuses to start without it, other profiles use a temporary key with a warning; `local-up.sh` generates a persistent local key, **back it up: losing it makes stored numbers unreadable**). The plain `value` column holds only the masked form (`XXXX XXXX 1234`, `ABXXXXX34F`); `value_last4` is kept. Responses always carry the masked form. `GET .../fields/{key}/reveal` needs `PII_UNMASK` plus access to the case, is audited (who, case, field; never the value) and is never cached. A save that omits or blanks a sensitive field leaves it unchanged (the screen cannot send the real value back); `clear: true` removes it. Sensitive fields are never prefilled.
- **Audit never holds field values:** check saves record the card values and only the *keys* of changed fields (`aadhaar_number (sensitive)`); a test scans every audit row for full identity numbers.
- **Prefill:** fields with `prefill` are filled from the candidate at creation and on every candidate save, unless marked manual. Typing a value marks it manual (source MANUAL, sticky); sending `manual: false` makes the field follow the candidate again. The Father's-name label reads "Guardian's Name" when the candidate has a guardian.
- **Date master:** the first check (by order) owns the dates; later checks copy them (AUTO) until someone types different dates (MANUAL, sticky); typing the master's dates again returns a check to AUTO. Reordering changes which check is the master. Saving the master changes the other checks too, so **their versions move**: the UI must refresh the whole checks list after any check save.
- **Attestation:** applying, changing or removing one needs `ATTESTATION_APPLY` (audited). A new court check starts with attestation on (Bar Council `KAR/670/06` and the standard disclaimer) only when its creator holds that permission; otherwise it starts off. Saves that leave the attestation unchanged need no permission.
- **Versions:** each check has its own version (a fields-only edit moves it, so stale editors are caught). Editing checks does NOT change the case's version, so an open case section is not made stale.
- **Limits:** 50 checks per case, 30 extra detail rows, 50 repeatable rows, free text 5000 characters. Image free sections are refused until documents exist (Phase 5).
- **Validation and overview now use real checks:** the "at least one check" error clears once a check exists; per check, warnings for a missing requested or completed date, a status still Pending / In Progress, and each missing required field (Aadhaar / PAN / UAN number). "At least one document per check" waits for Phase 5.

- Cases module tests forge access tokens with the auth module's public test-visible classes (`JwtService`, `SessionService`); this is a test-only shortcut, the production code only uses auth's published API (`AuthApi`, `CaseAccessPolicy`, `AdminDirectory`, `AuditEvent`).

### D-029 — Phase 4c checks: frontend design — *auto, 2026-09-24, Phase 4c*
- **Forms are drawn from the type definitions** (`GET /api/check-types`, cached for the session), so a new field or type in YAML needs no frontend change. The browser mirrors the server's value rules (`checks/validation.ts`, including the Aadhaar Verhoeff check digit) and the schema for each check is built from its definition (`checkForm.ts`); the server still checks everything again.
- **Workspace:** the chosen check is kept in the address (`?section=checks&check=<id>`) and listed under "4 Checks" in the navigator. Switching to another check, section or page with unsaved typing asks first (same guard as sections). The check list (add, move up/down, remove with confirmation) sits above the editor; "Add check" offers the client's usual checks first.
- **One Save per check** (card info, fields, extra details, remarks, attestation). After a save the form is reset from the server's answer and the whole checks list is refreshed, because saving the master check moves the other checks' dates and versions (D-028). Free text blocks save on their own (outside the form); because they move the check's version, the editor always sends the latest version and is not reset by them, so typing is never lost.
- **Prefilled fields:** untouched ones are sent as "keep following the candidate"; an edited one is sent as manual; "Use candidate value" (shown on manual fields) makes it follow the candidate again after the next save.
- **Sensitive numbers:** only the masked form is ever loaded into the page. "Reveal" (only with `PII_UNMASK`) calls the audited reveal endpoint, keeps the number in component state only (never in the query cache), and hides it again after 30 s or on "Hide". Typing in the box replaces the stored number; a checkbox removes it. The old number is never put back into the form.
- **Attestation:** the checkbox, Bar Council number and disclaimer are disabled without `ATTESTATION_APPLY`.
- **Clients' "usual checks"** is now a tick list of the real check types (a stored code the server no longer lists is kept, not silently dropped), replacing the comma-separated text field (D-027).
- **Not yet:** image free sections and per-check documents wait for Phase 5; the "at least one document per check" warning likewise.

### D-030 — Documents module design — *auto, 2026-09-24, Phase 5*
- **Dependency direction:** `documents` depends on `auth` and `cases`; `cases` does not depend on `documents` (Spring Modulith forbids cycles). Cases publishes what documents needs in its root package: `CaseApi` (which case owns a check, is it editable, set / clear the candidate photo), the `CaseDocumentLookup` interface that documents implements (document counts per check for validation, "is this picture a free image of this check"), and two events (`CheckDeletedEvent`, `FreeImageRemovedEvent`) that documents handles in the same transaction so files are retired together with the check or block. When nothing implements `CaseDocumentLookup` the cases module skips the document warning and refuses image blocks.
- **Endpoints** follow CLAUDE.md 9.3 plus three additions: `GET /api/checks/{id}/documents` (list), `DELETE /api/cases/{id}/candidate/photo`, and `?kind=FREE_IMAGE` on the upload for pictures used in image blocks. Uploads are refused unless the case is Draft or Changes requested.
- **Files are accepted by content, not by name or claimed type:** JPEG, PNG or PDF recognised from the first bytes; anything else is 415. Max 10 MB (`nexlyn.documents.max-bytes`), max 25 megapixels (checked before decoding, so a small file cannot expand into gigabytes), max 30 files per kind per check. Pictures are **decoded and re-encoded** (JPEG quality 92, PNG lossless) which drops EXIF (location, camera), embedded thumbnails and anything appended after the image; the EXIF *orientation* is applied to the pixels first so phone photos are not left sideways (all eight orientations are tested). PDFs are stored as uploaded (no processing yet). ClamAV scanning (CLAUDE.md 11.4, "optional, feature flag") is **not built**; add it in Phase 8 if wanted.
- **Access:** the bucket is never exposed. Every read goes through `GET /api/documents/{id}/content` after the permission and case checks and is streamed (the spec allows "signed URL or streamed"; streaming keeps the store private and every access auditable). Pictures are `inline`, PDFs `attachment`, always with `nosniff`, `Content-Security-Policy: sandbox` and `no-store`. **Every content read is audited** (`DOCUMENT_VIEWED`); the frontend keeps a picture in memory for 5 minutes so the log records people looking, not scrolling. Audit rows hold ids, kind, type, size and hash, never file names (they can contain names).
- **Storage keys** are `cases/{caseId}/{random uuid}`, never derived from the file name. Deleting is a **soft delete** (row retired, bytes kept). A retention purge (delete stored bytes of retired documents after N days) is deferred to Phase 8; until then nothing is ever removed from the store.
- **Labels:** "Original Document" for the first supporting document, then "Additional Document 1, 2, ..." by position (recomputed after every reorder), unless the admin gave a custom label. Free-block pictures and the photo do not take part in the numbering.
- **Crop** is stored as fractions of the picture (`{x, y, width, height}`, at least 5% each side, inside the picture), so it does not depend on pixel size and the original is untouched. The report (Phase 6) applies it. "Use larger box" and crop are for pictures only; "new page" also works for PDFs.
- **Candidate photo:** one per case; a new upload replaces (retires) the old one. It is set through `CaseApi` without changing the case's version, so an open candidate form does not become stale. Uploading the photo saves immediately and is separate from the Save button of the details form.
- **Image blocks on a check** (free sections of kind IMAGE): upload the picture as `FREE_IMAGE`, then create the block with its `documentId`. The picture cannot be deleted on its own while it is a block (409); removing the block retires it.
- **Validation:** new warning per check "no supporting document is attached" (CLAUDE.md 7.1); pictures used in blocks do not count.
- **Frontend:** the crop editor works by dragging on the picture and by four sliders (keyboard-accessible); pictures are fetched with the sign-in token and shown from a data URL held by TanStack Query for 5 minutes (a plain image tag cannot send the token); PDFs download instead of opening inside the site; the validation list's "Go to section" now opens the exact check.
- **Not done here:** drag-and-drop upload, upload progress bars (files upload one after another with a busy button), client logo (CLIENT_LOGO kind).

### D-031 — Local file store is SeaweedFS, not MinIO — *auto, 2026-09-24, Phase 5*
- **Deviation to review:** CLAUDE.md section 2 says "MinIO locally". MinIO's public images are unavailable (D-002), and Phase 5 needs a store that actually runs. The application only speaks the **S3 API** (AWS SDK v2), so the store is a deployment detail: local Docker Compose now runs `chrislusf/seaweedfs` (`server -s3`, path-style, no server-side encryption, bucket created on first use, one throw-away local identity in `infra/local/seaweedfs/s3.json`; SeaweedFS refuses signed requests without one). Production is AWS S3 in ap-south-1 (bucket private, SSE on) exactly as specified.
- **Tests:** the S3 adapter is tested against two real servers in Testcontainers: S3Mock (strict S3 stand-in) and SeaweedFS started with the very same `s3.json` the Compose stack mounts. The MinIO services stay in the compose file under the `storage` profile.
- **Reverse:** point `S3_ENDPOINT` / keys at any other S3-compatible store; no code change.

### D-032 — Reports: reference tool, layout port and design — *auto, 2026-09-25, Phase 6*
- **The reference tool is now in the repo** (`docs/reference/nexlyn-bgv-report-v3.2.html` and its documentation), copied from the shelved portal project. It was checked for real candidate data first: every input starts empty; the only personal-looking things were three *example* numbers in code comments (an Aadhaar, and a phone number), which were replaced with obvious fakes (`1234 5678 9012`, `+91 98765 43210`). The business contact details on its Services page (the company phone and e-mail, the advocate's name and address on the seal) are printed on every report and were kept. Nothing else was changed.
- **Ported, not re-imagined:** the report CSS is sliced straight from the reference (`static/report/report.css`, with a comment naming the reference line ranges); only the editor sidebar, toasts and image editor were left out. Page markup follows the reference exactly (cover, dedicated remarks page, overflow pages, detail pages, documents on their own pages, services page). Verified by printing the reference and this report to PDF and comparing them page by page: the layouts match.
- **Page plan (`Pagination`)** is the reference's logic on **icon groups**, never on the number of checks or the words of a title: two Identity and two Court checks are two groups (remarks stay on page 1); 3 to 4 (or 6) groups give the remarks their own page 2; more overflow onto extra summary pages (caps 12/14 with/without remarks in the 4-layout, 14/16 in the 6-layout) with the remarks on the last one. One summary card stands for a group and **shows the most serious status of its checks** (the reference showed only the first check's status, which could hide a discrepancy; deviation, tested).
- **Other deliberate differences from the reference:** the status pill's subtitle is the one the admin wrote (the reference overwrote it with "All N Requested Verifications Completed" on every redraw); the automatic Overall Status is "Clear" when everything is verified or closed (the word the reference prints; D-022 said "Completed"); totals print with two digits (02); the status icons and ticks are drawn as SVG, not as text symbols (a font without them would print empty boxes); a long title wraps inside its bar instead of pushing the status badge off the page; the watermark shrinks for long texts so it is not cut off; a document that cannot be shown (missing file, damaged picture or PDF) becomes a note in its frame instead of failing the report; a PDF supporting document is shown as its first page; pictures are cropped as saved (D-030) and scaled down to at most 2400 px on the long side (`nexlyn.reports.max-image-side`), untouched when no crop is needed and they are small enough. The reference's "split remarks / split document to a second page" flags are not ported (moving a document to its own page covers the need).
- **Sensitive numbers print masked** (`XXXX XXXX 1234`, `ABXXXXX34F`), as CLAUDE.md 13 recommends; the report model never receives a full number. CLAUDE.md 17 item 3 (full or masked) stays open for the owner; masked is the safe default and the only mode built.
- **Engine:** Thymeleaf renders one self-contained HTML page (Inter bundled as WOFF2 in the HTML, pictures as data URIs, nothing fetched at print time; every text is escaped, only the sanitised bold remarks and the report's own icons are raw HTML). Playwright for Java prints it with Chromium as A4 with backgrounds. Each print starts its own browser (Playwright objects are not thread-safe) and retries once with a fresh one if printing fails. `nexlyn.reports.chromium-path` / `CHROMIUM_PATH` picks the browser; when unset the app looks for Chrome, Chromium or Edge, then uses the one Playwright installs (the Docker image must contain Chromium, Phase 8). PDF tests are skipped on a machine with no browser.
- **Cut-off detection:** pages have a fixed A4 size, so content that is too tall would be clipped or run into the footer. After layout the browser measures every page; pages that do not fit are listed as warnings on the job ("Page 4: the content does not fit ... Move a document to its own page ..."). A test also asserts the browser's page count equals the plan.
- **Jobs and versions:** `POST /api/cases/{id}/reports` returns 202 and a job; a bounded executor (2 renders at once, 20 waiting, then "busy") runs it. One active job per case. Errors of the checklist (7.1) block a draft; warnings need `acknowledgeWarnings: true`. Jobs cut off by a restart are marked failed at start-up. Each finished PDF is a **version** (1, 2, 3...) stored under `reports/{caseId}/v{n}.pdf` with its SHA-256, page count, warnings and a **snapshot of the case data used** (JSON, numbers masked). Downloads recheck the hash (a changed file is refused with 503) and are audited. Drafts need only read access to the case; a final report needs `REPORT_DOWNLOAD_FINAL` (final reports are created by the workflow phase).
- **PDF protection** (`PdfEncryptionService`, PDFBox): AES-256, random owner password nobody sees, optional open password, print (also high resolution) and accessibility allowed, modify / copy / assemble denied; any failure fails the call (never an unprotected file passed off as protected). It is built and tested now and used by finalize in Phase 7.
- **Preview** is HTML served with `default-src 'none'; sandbox`, shown by the frontend in an iframe with an empty `sandbox` attribute (no scripts, no network).
- **Known limits:** Inter is the Latin subset; a candidate name in an Indian script would fall back to a system font (install Noto fonts in the Docker image, Phase 8). The brand block and footer keep the reference's system font stack, so on Linux they use the default sans (Liberation Sans) instead of Segoe UI.

### D-033 — Workflow, maker-checker, finalize and dashboard — *auto, 2026-09-25, Phase 7*
- **Steps** (`WorkflowService`, cases module): submit (DRAFT or CHANGES_REQUESTED to IN_REVIEW, needs `REPORT_SUBMIT_FOR_REVIEW`), approve (IN_REVIEW to APPROVED, `REPORT_APPROVE`), request changes (IN_REVIEW to CHANGES_REQUESTED, `REPORT_APPROVE`, a reason is required and is what the preparer sees), finalize (APPROVED to FINALIZED, driven by the reports module, `REPORT_FINALIZE`), **reopen** (FINALIZED to DRAFT, `REPORT_FINALIZE`, a reason is required; the CLAUDE.md rule "changes require reopening into a new version, old versions kept"). Each step also needs access to the case (`CaseAccessPolicy`) and the right starting state (409 otherwise). Endpoints: `POST /api/cases/{id}/submit-review | approve | request-changes | reopen` (each answers with the whole case) and `GET /api/cases/{id}/history`. `REOPEN` was added to `CaseAction`.
- **Submitting** follows CLAUDE.md 7.1: errors of the checklist block it; warnings need `acknowledgeWarnings: true`. From IN_REVIEW on, every module already refuses edits (only DRAFT / CHANGES_REQUESTED are editable); assignments of the PREPARER role cannot change while the case is in review or approved.
- **Maker-checker (`WorkflowRules`), enforced in one place:** whoever **prepared** a case can never approve, send back or finalize it, whatever permissions or roles they hold, super admin included. "Prepared" = assigned as its preparer, **or** created it, **or** sent it for review. (Creator and submitter were added to the spec's "preparer" so that unassigning oneself, or submitting on someone's behalf, cannot be used to approve one's own work.) The same function decides what the case screen offers (`workflow.actions` in the case view), so the UI and the server cannot disagree. Tests cover: the preparer, a super admin who created the case, a submitter who was never a preparer, a preparer added later, and the review buttons shown per person.
- **History:** every step is written to `case_status_history` (from, to, who, comment, report version for finalize), append-only (a database trigger refuses UPDATE and DELETE, like the audit log; tests empty it with TRUNCATE) and also raises an audit event (`CASE_SUBMITTED_FOR_REVIEW`, `CASE_APPROVED`, `CASE_CHANGES_REQUESTED`, `CASE_FINALIZED`, `CASE_REOPENED`, `REPORT_FINALIZED`).
- **Finalize** (`POST /api/cases/{id}/reports/{version}/finalize {openPassword?}`): the case must be APPROVED and the caller not a maker; the draft must have been **made after the approval** (so the final report shows exactly what was approved; otherwise "generate a fresh draft of the approved case, check it, then finalize that one"); the stored draft is hash-checked; the PDF is protected (AES-256, optional open password of 8 to 128 characters that is never stored or logged, printing allowed, editing and copying not); the result is stored as a **new** version marked FINAL (the draft stays untouched) and the case becomes FINALIZED, **all or nothing** (if anything fails the new file is removed and the case stays APPROVED; a concurrent change gives a 409). Drafts cannot be generated for a FINALIZED case (reopen first). Downloading a FINAL needs `REPORT_DOWNLOAD_FINAL` (built in Phase 6).
- **Dashboard** (`GET /api/dashboard`): counts per stage, "my cases" (assigned to me, not finalized), "waiting for my review" (IN_REVIEW, I hold `REPORT_APPROVE` and am not a maker of it), "due soon and overdue" (due today up to 3 days ahead, not finalized; overdue counted separately; dates in India time), lists capped at 8. All of it follows the case-list visibility rule (without `CASE_READ_ALL` only assigned cases exist). The stage tiles link to the case list filtered by status.
- **Frontend:** Section 8 now shows the review panel (status, who submitted / approved / finalized, the reviewer's comment, only the buttons the server allows, a collapsible history) above the report panel. Finalize asks which eligible draft to use and offers an optional password (typed twice; the dialog says it cannot be recovered).
- **Not built (not asked for):** notifications (e-mail or in-app) when a case is submitted or sent back; a "reviewer" auto-assignment; TAT alert thresholds other than the fixed 3 days.
