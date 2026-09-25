# Demo data

Fills a LOCAL stack with five FAKE cases (candidates, checks, photos, documents) and prints a PDF report for each into
`scripts/demo-data/output/` (git-ignored), so you can look at real-looking reports without typing anything.

## Run it

1. Start the local stack: `./scripts/local-up.sh -d` and create your administrator (see `docs/PROGRESS.md`, "Next step").
2. `node scripts/demo-data/seed-demo.mjs --email you@example.com`
   It asks for your password and the current 6-digit code of your authenticator app. Needs Node 18+ and Chrome, Edge or
   Chromium on this computer (it uses it to draw the fake pictures; set `CHROME_PATH` if it cannot find one).
   Your administrator must be a SUPER_ADMIN with two-step login already set up.
3. Open http://localhost:5173 to see the cases, and `scripts/demo-data/output/` for the PDFs (about 3 to 4 minutes in all).

Options: `--only DEMO-2026-0004`, `--no-pdf`, `--out <folder>`, `--url http://localhost:8080`. Running it again does not
duplicate anything (a report ID that exists is skipped; a demo case that has no report yet gets one).

## What you get

| Report ID | Shows |
|---|---|
| DEMO-2026-0001 | Everything verified. Aadhaar, PAN, Court (attestation), Employment (PDF letter), Education. Remarks on page 2 |
| DEMO-2026-0002 | Court hit and employment discrepancy, UAN, Credit. Overflow summary page, text dates, watermark |
| DEMO-2026-0003 | Two checks only: remarks stay on page 1, 6-card layout, guardian, verification period hidden, "Unable to verify" |
| DEMO-2026-0004 | Eight checks: Kannada and Hindi text, a small phone-photo document, larger-box and own-page documents, an image block |
| DEMO-2026-0005 | Unfinished draft: no photo, no documents, no remarks (the warnings path) |

## It cannot touch a real installation

- It only talks to `http://localhost`, `127.0.0.1` or `[::1]` and refuses anything else BEFORE asking for a password.
- After signing in and BEFORE writing anything it reads `/actuator` and refuses unless the backend also exposes `info`,
  which only the `local` profile does (the `prod` profile exposes `health` alone). So even a tunnel to a real server on
  `localhost` is refused.
- Nothing from this folder is in the backend jar or in any Docker image.
- Everything is invented and marked: report IDs start with `DEMO-`, every picture says SPECIMEN / FAKE DEMO DATA. The
  Aadhaar numbers pass the checksum but come from patterned prefixes, the PANs are `ZZZP?000?Z`, the UANs `1000000000nn`,
  the phone numbers the `98765 4321x` example range.

`node --test scripts/demo-data/*.test.mjs` checks the guards and the number formats.

## Removing the demo cases

There is deliberately no "delete" here. Reset the local database (`./scripts/db-reset-local.sh`; it removes ALL local cases, and the
uploaded files stay behind in the storage volume), or delete the DEMO cases one by one in the web app (CASE_DELETE).
