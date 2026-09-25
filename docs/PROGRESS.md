# Progress

Read this file and `docs/DECISIONS.md` at the start of every session, then continue from
"Next step". Update it (and commit) after every step.

_Last updated: 2026-09-25 (after the brand and footer font fix, D-036)_

## Phase status (CLAUDE.md section 15)

| Phase | Status |
|---|---|
| 1. Foundation | **Done** (commit `db0689b`). Stack starts with `./scripts/local-up.sh -d`; MinIO off by default (D-002). |
| 2. Auth & RBAC | **Done** (steps 2a-2f). Done-when met: security tests cover unauthenticated, wrong permission, refresh-token reuse and lockout. 106 auth + 6 common + 5 app backend tests, 97 frontend tests. The Docker stack was rebuilt with this code on 2026-09-25 and answers correctly (see "Docker stack" below). |
| 3. Clients & Case workspace core | **Done** (3a-3c). Done-when met: every section saves and loads, validation follows section 7.1. 41 cases + 16 common + 106 auth + 5 app backend tests; 155 frontend tests. |
| 4. Checks | **Done** (4a-4c). Done-when met: all 18 types can be added and saved (backend tests add and save every type; frontend renders any definition). 92 cases + 23 common + 106 auth + 5 app backend tests; 197 frontend tests. |
| 5. Documents | **Done** (5a-5c). Done-when met: files are stored in an S3-compatible bucket and never public (tests against S3Mock and SeaweedFS). 43 documents + 92 cases + 23 common + 106 auth + 5 app backend tests; 233 frontend tests. The Docker stack was rebuilt with this code on 2026-09-25 (SeaweedFS storage service healthy). |
| 6. Reports | **Done** (6a-6d). Done-when met: a fixture case renders to a PDF whose layout matches the reference tool (printed both and compared page by page); the browser's page count equals the plan; cut-off pages are detected. 69 reports + 43 documents + 92 cases + 23 common + 106 auth + 5 app backend tests; 247 frontend tests. Finalize (protected final PDF) belongs to Phase 7. |
| 7. Workflow | **Done** (7a-7d). Done-when met: maker-checker is enforced by tests (preparer, creator, submitter, later-added preparer, super admin). 14 workflow + 7 dashboard + 12 finalize tests among 81 reports, 43 documents, 113+ cases, 23 common, 106+ auth and 5 app backend tests; 269 frontend tests. |
| 8. Hardening & deploy | **Done as far as it can be done without the owner** (8a-8c, D-034): NCSC password list, correlation ids, JSON logs, proxy-aware prod config, nightly purge of retired documents, lazy routes, Dockerfiles, prod compose + nginx (TLS, rate limits), CI / security / dependabot / manual deploy workflows, backup script, runbooks, PDF load test (about 10 s per heavy report). The images now build and the local stack runs (2026-09-25); NOT verified here: TLS, the AWS side and running the GitHub workflows (owner-only items below). |

## Final state after Phase 8 (2026-09-25)

- **Tests:** backend `./mvnw verify` is green: common 27, auth 108, cases 113, documents 48, reports 99, app 7 (402). Demo-data script: 6 Node tests (`node --test scripts/demo-data/*.test.mjs`). Frontend: 269 tests, type check, lint and build clean, `npm audit` reports 0 vulnerabilities. The PDF load test (tag `load`) is run by hand.
- **Never run from here:** the GitHub workflows, TLS with a real certificate, RDS / S3 in AWS, the production compose file (`infra/prod`, only syntax-checked) and a full sign-in + PDF through the running stack (needs the owner's admin password). Everything else is covered by tests.
- **Not built (by design, "Later" in CLAUDE.md section 15):** third-party verification vendors, notifications (e-mail / in-app), risk score and other roadmap polish, module extraction, browser end-to-end tests, an antivirus scan of uploads, the OpenAPI-generated client.
- **Only the owner can do:** see "Open issues and things only the owner can do" below.

## Docker stack (rebuilt 2026-09-25, Avast HTTPS scanning was off)

The first real image build found two bugs, both fixed and committed:

1. `backend/bgv-app/Dockerfile`: the step that copies the Playwright jars ran `mvn package` and then a second `mvn` on one module, which could not see its sibling modules. The first step is now `install`.
2. `infra/local/docker-compose.yml`: the SeaweedFS health check used `nc -z localhost`, but the S3 gateway listens on IPv4 only and `localhost` tried IPv6 first, so the service stayed "unhealthy" and blocked the backend. It now checks `127.0.0.1`.

Checked on the running stack (all containers healthy: postgres, storage, backend, frontend):

- Web app on http://localhost:5173 returns 200 with the Content-Security-Policy, `nosniff`, no-framing and referrer headers; `/actuator/health` is UP through the web container; `/api/me` without a token gives 401 with an `X-Correlation-Id` header and the same id in the error body.
- The backend container runs as user `nexlyn` (uid 10001), has Chromium (`/opt/ms-playwright/chromium-1140`) and 269 Noto font faces, and that Chromium printed a PDF (English, Devanagari and Kannada text) as that user with `--no-sandbox`.
- The database volume was kept; no data was deleted. An unrelated container named `priceless_bose` is also running on this machine and was not touched.

**Not yet checked:** signing in and generating a report through the browser. That needs the first admin (see Next step 1), because the password is the owner's to choose.

Avast HTTPS scanning can be turned back on now. It has to be turned off again for any later image rebuild that downloads dependencies (Docker layers already cached may not need it).

## Demo data and the report fixes it found (2026-09-25, D-035)

`scripts/demo-data/` fills a LOCAL stack with five fake cases (Aadhaar, PAN, Court, Employment, Education, Address, UAN, Credit; photos, image and PDF documents; Kannada and Hindi text) and prints a PDF for each into `scripts/demo-data/output/` (git-ignored). See its README. It cannot run against a real installation (loopback address only, and a local-profile check before any write) and adds nothing to the backend or the images. **The owner has not run it yet**: it needs the owner's own admin (Next step 1) and their authenticator code.

It was run here against a separate throw-away stack (removed afterwards; the owner's stack and volumes were not touched) and all five reports printed with no layout warnings. Looking at the printed pages found three real bugs, fixed with tests:

1. a report job **hung forever** in the Docker image (Playwright tried to download Firefox and WebKit); nothing is downloaded at run time any more, and a render stopped after 3 minutes fails cleanly;
2. with a **watermark on, every footer floated up** under the content (a rule inherited from the reference tool);
3. **documents were cut off** on full pages (a court check always is): the job now moves the last document on such a page to its own page and prints again.

**The owner's running Docker stack was rebuilt with these fixes** (2026-09-25, `./scripts/local-up.sh -d`, data kept; all containers healthy). Not done: the reference tool was not re-driven with the five new cases (its data entry is a browser UI); the HTML preview does not auto-move documents (only the PDF does).

## Report fonts (2026-09-25, D-036)

The owner found that the PDF's brand header looked different from the preview. Cause: the reference tool gives the brand block, the title and the footer **no font file**, only the system stack ('Segoe UI', Arial, ...), so the preview used Segoe UI (Windows) while the Docker container printed Liberation Sans. Fixed: the bundled open-licence **Selawik** (Microsoft's metric-compatible stand-in for Segoe UI, SIL OFL, licence and README in `static/report/fonts/`) is loaded with `@font-face` and used for the brand, title and footer; the `|` separators are drawn bars (their colour was already the reference's navy `#0c2d6b`; the blue/red/orange was screen sub-pixel fringing of a one-pixel glyph). Checked by printing a real report from the rebuilt container: the header matches the preview and the PDF embeds only Selawik and Inter.

Guards added: `ReportFontAudit` (reads the fonts of a finished PDF) with tests that fail if any font is not bundled or not embedded or if the brand/footer words are not Selawik, and a **start-up self-check in the Docker image** (`NEXLYN_REPORTS_FONT_CHECK=fail`): a container whose browser would print in another font does not start. After a deploy, `docker compose logs backend | grep "font check"` should say `Report font check passed`. Known difference: Selawik has no black weight, so NEXLYN prints in Bold (a bit lighter than Segoe UI Black on Windows). The owner's stack was rebuilt with this; the old PDFs in `scripts/demo-data/output/` still show the old header until the reports are generated again in the app.

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

All eight phases of CLAUDE.md section 15 are built, committed and running locally in Docker. Nothing in the "Later" row of section 15 (third-party verification vendors, notifications, roadmap polish, module extraction, browser end-to-end tests) may be started until the owner asks. Exact next steps, in order:

1. ~~**Owner: create the first admin.**~~ **Done 2026-09-25:** admin created, two-step login enabled, bootstrap lines removed from `infra/local/.env`, backend restarted. (Original instructions kept for reference:) Edit `infra/local/.env` and set `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` (12+ characters, 3 of 4 character kinds, not a common password). Then `cd infra/local && docker compose up -d backend`. Open http://localhost:5173, sign in, scan the QR code with an authenticator app, save the backup codes, then **remove both lines from `.env`** and run `docker compose up -d backend` again.
2. ~~**Optional: look at sample reports first.**~~ **Done 2026-09-25:** the demo script ran on the owner's stack: DEMO-2026-0001 to 0005 exist (all DRAFT, one PDF each, 0 layout warnings) and the PDFs are in `scripts/demo-data/output/`. (Original instructions:) With your admin from step 1: `node scripts/demo-data/seed-demo.mjs --email <your admin e-mail>` (asks for your password and authenticator code; about 4 minutes; PDFs land in `scripts/demo-data/output/`). Then continue with step 3.
3. **Owner, NEXT: look at the PDFs and try one case end to end** in the browser: add a client, create a case, fill the sections, add checks, upload a photo and documents, generate a draft PDF, submit for review, approve as a second admin (invite one; the person who prepared a case can never approve it), generate a fresh draft, finalize. Write down anything that looks wrong; that is the real acceptance test of Phases 3 to 7.
4. **Owner: answer the open items in CLAUDE.md section 17** (full or masked Aadhaar / PAN on the PDF, review the provisional check-type fields, confirm POLICE, Report ID format, RDS vs self-hosted) and review D-031 (SeaweedFS instead of MinIO).
5. **Owner: go live**, following `infra/prod/README.md` (server, domain and certificate, AWS RDS and S3, secrets, offline backup of `PII_ENCRYPTION_KEY`, backup cron job). Then build the images with the `deploy` workflow or on the server (`docs/runbooks/deploy-update.md`).
6. **Owner: GitHub.** Push the repository and add the secret `NVD_API_KEY`, the secret `AWS_DEPLOY_ROLE_ARN` and the variable `AWS_ACCOUNT_ID`. The workflows have never run: expect to fix small things on their first run.
7. **Claude, once real usage shows problems:** fix what step 3 finds; validate `infra/prod` on a real server (the first production start may need small changes); then ask the owner which "Later" item to build first.

## Open issues and things only the owner can do

- **Avast HTTPS scanning** breaks Maven/npm downloads inside Docker builds (certificate error). It was off for the 2026-09-25 rebuild and can be turned back on; turn it off again before rebuilding images that need new downloads. Claude cannot change it. Check with `openssl s_client -connect repo.maven.apache.org:443` (issuer must not be Avast). Host builds work regardless (D-015).
- **The running Docker stack is current** (rebuilt 2026-09-25, see "Docker stack"). Local JWT, 2FA and PII keys were generated into `infra/local/.env` (git-ignored); back up `PII_ENCRYPTION_KEY` from that file if you will keep any data you care about.
- **`docs/reference/` now holds the reference tool** (sanitised copy, D-032). Nothing needed from the owner.
- **Password list:** replaced by the NCSC top-100k list (D-034), so CLAUDE.md section 17 item 7 is done. The local file store is SeaweedFS (D-031); review that MinIO deviation.
- **Going live (owner only, all listed in `infra/prod/README.md`):** a Linux server with Docker; a domain and a TLS certificate; AWS RDS (PostgreSQL 16) and an S3 bucket in ap-south-1; real secrets in `infra/prod/.env` (`JWT_PRIVATE_KEY`, `PII_ENCRYPTION_KEY`, `TOTP_ENCRYPTION_KEY`, `DB_PASSWORD`, bootstrap admin) and an **offline backup of `PII_ENCRYPTION_KEY`** (losing it makes stored Aadhaar / PAN / UAN unreadable); the real host name in `infra/prod/nginx/nexlyn.conf`; the daily backup cron job.
- **GitHub (owner only):** pushing the repository, the secret `NVD_API_KEY`, the AWS role `AWS_DEPLOY_ROLE_ARN` and variable `AWS_ACCOUNT_ID` for the `deploy` workflow. The workflows are written but have never run.
- **Production images:** the same Dockerfiles build and run locally. The production compose file (`infra/prod`) has only been syntax-checked, never started on a real server.
- **Open questions for the owner (CLAUDE.md section 17):** full or masked Aadhaar / PAN on the PDF (masked is built), the provisional check-type fields, POLICE, Report ID format, RDS.
- **Local admin:** set `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` in `infra/local/.env` to create the first admin (the owner picks the password).
