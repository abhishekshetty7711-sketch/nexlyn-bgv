# UI/UX audit of the admin dashboard

Date: 2026-09-25. Scope: the whole admin dashboard (`frontend/`), after the scope-A UI work (D-037). **No code was changed by this audit.**
Governed by CLAUDE.md 16.1: the design skill is advice only, the report (PDF) is out of scope, and nothing below may change security behaviour.

**Owner's notes (Step 1): not supplied.** The request contained the placeholder "[paste your notes from Step 1 here]". When the notes arrive they get
their own section here and are ranked with the rest.

## How it was done

- The real app was started on a throw-away stack (its own database, removed afterwards) with the five demo cases in different workflow states
  (finalized, in review, changes requested, two drafts), four extra users (reviewer, analyst, auditor and an invited person) and real Chrome.
- **76 screens and states were captured** (`docs/ui/audit-2026-09-25/` holds the 24 cited here; the rest were reviewed and not kept): sign-in, wrong password,
  two-step code, backup-code mode, invitation, two-step set-up (QR), dashboard, case list (filtered, empty), new-case dialog, clients, admins (+ invite,
  edit, confirm dialogs), roles (+ new role), audit log, change password, not-found, the case workspace sections 1 to 8, the check editor (Aadhaar, court),
  add-check, document editor, report preview, generate, the four review states, reviewer, auditor and analyst views, loading and error states, and a phone (390 px).
- **Automated WCAG scan** of every state with axe-core (WCAG 2.0/2.1/2.2 A and AA plus best practices). 65 signed-in states scanned; colour contrast passed
  everywhere axe can measure it.
- **Manual tests:** keyboard-only use (tab order, visible focus, skip link, dialog focus trap), page titles, heading structure, size of clickable targets,
  sideways scrolling at 720, 390 and 320 px, form validation messages, loading and error behaviour (requests delayed or made to fail).
- Findings are checked against the skill's guidelines (the row number in `data/ux-guidelines.csv` is given as "UX-n").
- The skill's Python search tool was not run (Python is not installed); its guideline files were read directly.

## Verdict in short

The dashboard is in good shape: **no problem that blocks a task or endangers data was found**. Keyboard use works end to end, dialogs trap and manage focus, errors
are written next to the field in plain words, empty / loading / forbidden states exist, and the automated scan is clean on the main screens.
What remains is a short list of accessibility conformance gaps (2 Critical, quick to fix), usability and consistency problems that a busy analyst
would feel (13 Important), and polish (10 Nice-to-have).

**"Critical" here means a proven failure of WCAG 2.2 level A or AA. None of them stops anyone from working; both are small fixes. Nothing found is a security problem.**

---

## Critical (WCAG A / AA failures)

**C1. Every page has the same title, "Nexlyn BGV Platform"** (WCAG 2.4.2 Page Titled, level A). Measured on `/login`, `/`, `/cases`, `/clients`, `/admin-users`, `/roles`, `/audit`,
`/account/password`. A screen-reader user or someone with many tabs cannot tell pages apart, and the browser history is useless. *Fix:* set the title per screen ("Cases - Nexlyn BGV",
"DEMO-2026-0004 - Nexlyn BGV") and say it changes on navigation. Effort: small. (UX-39, UX-5)

**C2. `aria-label` on a plain `<div>` in the remarks preview** ("Remarks for this check preview", in the check editor and its dialogs). axe: *serious*, "aria-label cannot be used on a div with no valid role"
(WCAG 4.1.2). Seen on 4 screens (the check editor and the document editor: 52, 53, 56 and the court-check editor). *Fix:* give it `role="region"` (or drop the label and use a visible caption). Effort: small.

---

## Important

**I1. A failed request looks like "loading" for about 7 seconds before the error appears** (screens 75, 76: the error screenshot still shows the loading placeholder 1.5 s after the server failed).
The app uses the data library's default of three retries with growing delays, for every request, including "not allowed" and "not found" answers that can never succeed on retry.
*Fix:* do not retry client errors (4xx); retry a server or network error once; show the error after 2 s at most. Effort: small. (UX-78, UX-83)

**I2. Case list: the Report ID and some status badges wrap onto two lines** (screen 13: `DEMO-2026-` / `0001`, "Changes requested" in two lines). The Report ID is the main way people recognise a case.
Introduced by the scope-A restyle (more padding, narrower first column). *Fix:* no-wrap on the ID and the badge, let the Candidate and Client columns give way. Effort: small. (UX-113)

**I3. Admins page on a phone: Status, 2FA, last sign-in and every action (Edit, Disable, Sign out) are off screen** (screen 83). The page itself scrolls sideways at 390 and 320 px (567 px wide).
There is no hint that the table scrolls. *Fix:* on narrow screens show each admin as a card with the actions visible, or at least a visible scroll cue. Effort: medium. (UX-71)

**I4. Case workspace on a phone: the section list (about 800 px tall) sits above the form**, so the first screen shows only navigation (screen 87). *Fix:* on narrow screens collapse the list into a
"Section 4 of 8: Checks" control that opens the list; keep the progress bar. Effort: medium.

**I5. "Generate draft PDF" is offered on a finalized case** (screen 61), where the server refuses it (D-033: reopen first). The person meets an error at the end of a path the screen invited.
*Fix:* hide or disable it on a finalized case with a one-line reason ("Reopen the case to make a new draft"). Effort: small. (The refusal itself is by design; the click was not tried in this audit.)

**I6. Dates and times are written in four different ways.** The case list shows `11/06/2026` next to `9/25/2026, 4:53:29 PM` (month first, English-US); the admin and audit tables use the same US style;
the workspace's review box shows `25 Sept 2026, 4:53 pm`, and the header of a case whose report uses text dates shows `11-Jun-2026` while the others show `11/06/2026`. CLAUDE.md fixes dd/mm/yyyy for people in India; month-first dates are easy to misread
(screens 13, 21, 29, 61, 66). *Fix:* one date format (dd/mm/yyyy) and one time format (24-hour or `en-IN`) for the whole dashboard; the per-case report format should only affect the report preview. Effort: small to medium. (UX-85)

**I7. Technical language where an owner or analyst reads the screen.** The Roles page lists raw permission codes (`CASE_READ_ASSIGNED`, `PII_UNMASK`, `REPORT_SUBMIT_FOR_REVIEW`, about 20 for the Super Admin role);
the Audit log shows codes (`LOGIN_FAILED:BAD_PASSWORD`, `DOCUMENT_VIEWED`) and bare UUIDs as entities, with no link to the case, although the server records the case (screens 26, 29). *Fix:* plain-language names
and short descriptions for permissions, grouped by area; readable audit sentences ("Anitha viewed a document of DEMO-2026-0004") with a link to the case. Effort: medium to large.

**I8. The check editor is 3,100 px long and its Save button is only at the top** (screen 52), while the other sections keep Save in a sticky bar. Someone editing the last field must scroll back up to save.
*Fix:* the same sticky Save bar for the check editor. Effort: small.

**I9. The invitation and two-step set-up screens do not look like the sign-in screen** (screens 10, 11 against 01): no logo, a different background and title style. The invitation form also states the password rule only after the mistake
("Use at least 12 characters"); the policy is 12 characters, three of four kinds, not on a common list. *Fix:* the same brand header, and the rule shown as a hint under the password field before typing. Effort: small.

**I10. Page structure for assistive technology** (axe, moderate, best practice):
(a) the sign-in, invitation and set-up pages have no `<main>` landmark (11 screens); (b) the sidebar and the workspace's section panel are both unnamed "complementary" regions (28 screens; the sidebar became an `<aside>` in scope A);
(c) the heading order jumps from the page title to "Assigned to" (h1 to h3, 30 screens); (d) the versions table has an empty column header (10 screens); (e) the "case not allowed" page has no heading (1 screen).
*Fix:* `<main>` on those pages, a label on the sidebar (or make it a plain container), an h2 for "Assigned to", a visually hidden "Actions" header, a heading on the not-allowed page. Effort: small. (UX-39, UX-45)

**I11. "Sign out" on each row of the Admins table means "end this person's sessions"**, next to the person's own "Sign out" in the sidebar (screen 21). It is confirmed in a dialog, but the label is easy to misread. *Fix:* "End sessions". Effort: small. (UX-35)

**I12. Required fields are not marked** (client name, new case client, report ID) and the forms rely on the error after a failed save to say so (screens 17, 20). *Fix:* "(required)" or an asterisk with a legend. Effort: small. (UX-59)

**I13. Small click targets** (WCAG 2.2 target size, 24 px): the eight check names in the section list are 20 px high, "Remove ... as preparer" and the sidebar's "Change password" are 16 px high, the native checkboxes and radios are 13 px (their text labels are clickable, which helps) (measured on the workspace and the audit log's "Before / after" toggles).
*Fix:* at least 24 px of height and spacing for these controls. Effort: small.

*Also noted (not ranked separately):* the photo box is a blank grey square for one to two seconds before the picture appears, with no loading cue (screens 41 against 49); in the keyboard test the date-of-birth field showed no visible focus ring once (needs a manual check in a real
browser; all other controls showed one).

---

## Nice to have

- **N1.** The checks appear twice on the checks section (the section list and the main list with arrows) (screen 43). One list would do.
- **N2.** The "Page not found" and "Not allowed" pages are bare text; add an icon, a clear button back, and the same layout and spacing as other pages (screens 32, 70).
- **N3.** The progress bar reads 100% while "8 Generate report" still shows the "not started" mark (screens 44, 47). The mark is right (nothing to save there) but reads as a contradiction.
- **N4.** A reviewer's "changes requested" comment is shown twice on the same screen, in the banner and in the review box (screens 63, 64).
- **N5.** The Overview section shows the automatic totals as grey placeholder text in the override boxes (screen 44); an "Automatic: 8" hint would be clearer than a value that looks like a placeholder.
- **N6.** The "Usual checks" list in the client dialog cuts a row in half where it scrolls (screen 20); show a scroll cue or a taller box.
- **N7.** Date inputs use the browser's own date control, so the format and the empty hint (`dd-----yyyy`) depend on the computer's language. The reference tool used plain dd/mm/yyyy text; a text field with a format hint would match it (screens 16, 29).
- **N8.** Audit log filters: no quick ranges (today, last 7 days), no way to filter by case. Roles: permissions could be grouped and searchable.
- **N9.** A dark theme is not offered (deliberately out of scope so far).
- **N10.** No "show password" toggle (deliberately left out: it changes how a secret is displayed, so it needs an owner decision under rule 4).

---

## What already works well (keep it)

- **Keyboard:** the skip link is the first stop, the tab order follows the screen, every menu item and control is reachable, and 44 of 45 focus stops in the candidate form showed a visible focus ring.
- **Dialogs:** focus moves into the dialog, Tab stays inside it (12 of 12 tabs), Escape closes it. Leaving a section with unsaved changes is guarded twice (in-app dialog and the browser's own prompt).
- **Messages:** validation errors sit next to the field with an icon and text, in plain words with an example ("Enter a valid Indian mobile number, for example 98765 43210"); the first invalid field takes focus.
- **States:** skeleton placeholders while loading, a helpful empty state in the case list, a clear "not allowed" for a restricted role, a locked-case banner, and the maker-checker rule explained on screen ("Whoever prepared or submitted a case cannot approve it").
- **Permissions in the interface:** the auditor and the analyst see exactly the menu and cases their role allows; the reviewer gets Approve / Request changes only where allowed.
- **Contrast:** no colour-contrast failure anywhere axe measures it, on all 65 signed-in states; the sign-in and dashboard use icons and words next to colour.
- **Security-related behaviour** was exercised and unaffected by the redesign: silent sign-in through the refresh cookie on new pages, the login rate limit (it blocked this audit's own repeated sign-ins, which is the intended behaviour), the strict content policy (it appeared to stop one of the test tool's in-page scripts, as intended).

## Not covered (be aware)

The idle-timeout warning dialog (its behaviour is covered by unit tests), session expiry screens, the audited 30-second reveal of Aadhaar / PAN numbers, upload errors (file too big or wrong type), the expired-invitation page, other browsers (Firefox, Safari), a
real screen reader (only the automated scan and the keyboard were used), and slow-network behaviour beyond the delayed requests described above.

## Suggested order of work

1. **One short batch (about a day):** C1, C2, I1, I2, I5, I8, I10, I11, I12 (all small, high value).
2. **Consistency batch:** I6 (dates), I9 (invitation and set-up screens), I13 (target sizes).
3. **Phone batch:** I3 and I4, only if the team will really use phones for these screens.
4. **Content batch:** I7 (plain-language permissions and audit log), the biggest change in wording, best done together with the owner's notes.
5. Nice-to-have items as time allows.

Every fix stays inside `frontend/src`, changes nothing about sign-in, tokens, permissions, masking or the report, and would be one commit per screen with the tests kept green (CLAUDE.md 16.1).

## Screenshots kept in `docs/ui/audit-2026-09-25/`

`10-accept-invite-weak-password`, `11-2fa-setup-qr`, `13-cases-list`, `17-new-case-dialog-validation`, `20-client-dialog-validation`, `21-admins`, `26-roles`, `29-audit-log`, `32-not-found`,
`41-ws-2-candidate`, `44-ws-5-overview`, `47-ws-8-generate-draft`, `49-ws-2-validation-candidate`, `52-check-editor-aadhaar`, `56-document-editor-dialog`, `57-report-preview-dialog`,
`61-finalized-generate`, `63-changes-requested-banner`, `66-in-review-as-reviewer`, `70-auditor-admins-forbidden`, `72-analyst-case-not-allowed`, `75-error-cases-list`, `83-mobile-admins`, `87-mobile-check-editor`.
(Some captures show the demo case as `DEMO-2026-000`: the audit's own typing test shortened its Report ID on the throw-away stack. It is not a defect.)
