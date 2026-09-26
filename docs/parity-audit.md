# Parity audit: old HTML tool (v3.2) vs the new platform

_Date: 2026-09-26. Read-only audit: no application code was changed. The only edits are the two file-name references in
`CLAUDE.md` (§0 and the §5 tree: `ALL_data_of_bgv.md` is now `Nexlyn_BGV_Tool_Documentation_v4.0_PhaseOne.md`) and the
files added under `docs/`._

## 1. Sources and method

| Source | Used for |
|---|---|
| `docs/reference/nexlyn-bgv-report-v3.2.html` (6,087 lines) | The truth. Read completely: layout, sidebar, every script function. Behaviour is quoted from its code, not from its documentation. |
| `docs/reference/Nexlyn_BGV_Tool_Documentation_v4.0_PhaseOne.md` | Cross-reference only (its header says v3.2). Items it lists beyond v3.2 are in section 6, "extra, not required". |
| `CLAUDE.md` §6, §7, §8, §11 | What must be preserved, fixed, or is different on purpose. |
| `docs/DECISIONS.md` | D-032 (report port), D-035, D-036, D-041 and others: differences already logged. |

For each item I found the old behaviour in the HTML, then the new behaviour in code, then a test and (for report items) a
page of a generated PDF.

**What was actually run today (2026-09-26):**

- Backend: `PaginationTest, HtmlRendererTest, ReportModelAssemblerTest, PdfRendererTest, PdfEncryptionServiceTest,
  ReferenceToolComparisonTest, OverviewCalculatorTest, CheckTypeRegistryTest, FieldValuesTest, ImageProcessingTest` =
  **110 tests, 0 failures, 0 skipped** (the browser tests ran; Chrome was found).
- Frontend: `vitest run` = **396 of 396 passed**.
- The full backend `verify` (Testcontainers / Docker tests) was **not** re-run; where I cite a backend integration test
  (`CheckApiIntegrationTest`, `CaseApiIntegrationTest`, `DocumentApiIntegrationTest`) I read it, but did not run it today.

**Status meaning:** ✅ same behaviour, with proof · ⚠️ partial · ❌ missing · 🔁 different on purpose (rule quoted) ·
👤 needs your manual check. A ✅ can still carry a note about a small, harmless difference.

**Limits of this audit (please read):**

1. I cannot sign in, so I never drove the running app in a browser. UI proof is code + the existing frontend tests.
   Things that need eyes are in section 8.
2. The PDF comparison was made with the new app's own report code (the same assembler, template, and renderer the server
   uses; the service classes are called directly) and the old tool driven with its own JavaScript, both printed by the same Chrome
   on this machine. It is **not** a download from the signed-in app. Section 5 says how.

---

## 2. Summary

| Status | Count (of the 53 numbered items) |
|---|---|
| ✅ Complete | **39** |
| ⚠️ Partial | **10** (9, 22, 30, 31, 36, 38, 42, 45, 51, 53) |
| ❌ Missing | **1** (33) |
| 🔁 Different on purpose | **2** (34, 46) |
| 👤 Needs your manual check | **1** (39) |

Item 53 (everything else in the old tool) has 22 sub-items, listed in section 4, with their own statuses. Section 8 has the manual checks.

Three findings you did not ask about but should know:

- **CLAUDE.md §6.2 says the phone format is `+91 XXXXX XXXXX`; the old tool actually prints `+91 XXXXXXXXXX`** (its code comment
  claims 5-5 grouping, its code does not do it). The new app follows CLAUDE.md. Your call (item 9).
- **The old tool has bugs that change its own PDF** (footers float up when the watermark is on; a status-subtitle overwrite;
  `openLightbox` is called but never defined). The new app does not copy them (section 7).
- **The v3.2 documentation overstates two things**: "real-time progress" (changing a card's status does not refresh the dashboard
  in the old tool, line 3268) and "Police" among the card types (the old code has no Police type).

---

## 3. The 53 items

Test names are given in short form. Backend: `R:` = `backend/modules/reports/src/test/...`, `C:` = cases module, `D:` = documents module.
Frontend tests are under `frontend/src/features/...`.

### A. Core report structure

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 1 | Page 1 cover: candidate, photo, status pill, overview, summary grid | ✅ | `templates/report/pages.html` fragment `cover`; `ReportModelAssembler.cover()`; `R: HtmlRendererTest.theCoverCarriesTheCandidateOverviewSummaryAndInlineRemarks`; fixture PDF page 1 (`side-by-side/page-1.png`) | Same layout. Visible differences on this page are in section 5 (subtitle text, phone format, names in capitals). Empty photo prints "Photo not available"; the old tool printed its on-screen prompt "Upload Photo". |
| 2 | Remarks page with smart placement (≤2 groups inline; 3..layout max own page 2; more merged into overflow) | ✅ | `layout/Pagination.java`; `R: PaginationTest` (`oneOrTwoGroups…`, `threeUpToTheLayoutLimit…`, `moreGroupsThanTheLayout…`); fixture has 4 groups, so remarks are alone on page 2 (`page-2.png`) | The old tool's own page 2 has its footer floating under the content when the watermark is on; new keeps it at the bottom (D-035). |
| 3 | One detail page per check | ✅ | `ReportModelAssembler.addCheckPages`; `R: ReportModelAssemblerTest.twoChecksAreCoverTwoDetailPagesAndServices`; fixture pages 3-10 | |
| 4 | Services page always last | ✅ | `R: HtmlRendererTest.everyPlannedPageIsOnePageElementAndTheServicesPageIsLast`; fixture page 11 | Page 11 is visually identical in both tools. |
| 5 | Pagination by icon group (from `CheckType`, not title); 4 or 6 on page 1; overflow | ✅ | `Check.iconGroup` comes from the YAML `iconGroup`; `R: PaginationTest.checksOfTheSameKindShareOneSummaryCard…`, `sixCardLayoutFitsSixOnPageOne`, `aBigOverflowIsSpread…`; fixture: 5 checks (Aadhaar + PAN share one card) give 4 summary cards | Caps 12/14 (4-layout), 14/16 (6-layout) are the old ones. A group's card shows the **most serious** status (old: the first card's status; D-032). |
| 6 | Footer on every page: status legend + page numbers | ✅ | `pages.html` fragment `footer`; `R: PdfRendererTest.everyFooterStaysPinnedToTheBottom…`, `HtmlRendererTest.theDetailPageHasTheChecksTable…FooterLegend`; every fixture page | Legend on all pages except cover and services, like the old tool. It lists four statuses (as the old one did); Pending and In Progress are not in it in either tool. |
| 7 | Watermark, default `NEXLYN VERIFIED`, max 40 chars | ✅ | `SettingsSection.tsx` + `schemas.ts` (`max(40)`); `C: CaseApiIntegrationTest.reportSettingsAreValidated`; `R: HtmlRendererTest.theWatermarkAppearsOnEveryPage…`, `PdfRendererTest.theWatermarkIsPrintedOnEveryPage…`, `ReportModelAssemblerTest.theWatermarkTextFallsBackToTheDefault`; fixture: on all 11 pages | Per report (the old tool kept one setting per browser). Long texts shrink so they fit (D-032). |

### B. Candidate information

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 8 | Report ID (auto, editable, unique), Issue Date, Company name with line breaks | ✅ | `ReportInfoSection.tsx`; unique index `ux_cases_report_id` (V2 migration); `ReportIdGenerator`; `C: CaseApiIntegrationTest.reportIdsMustBeWellFormedAndUnique`, `newCasesGetSequentialReportIds…`; `R: HtmlRendererTest.theCompanyNameKeepsItsLineBreak`; fixture page 1 ("Acme Corp / Private Limited") | The old tool never checked uniqueness. Format `NX-YYYY-NNNN` is still open item 4 of CLAUDE.md §17. |
| 9 | Full Name, Employee ID, DOB, Phone (+91) | ⚠️ | `CandidateSection.tsx`; `IndianPhone.format`; fixture page 1 shows both phones | Fields all exist. **Phone differs:** new prints `+91 98765 43210` (CLAUDE.md §6.2); the old tool prints `+91 9876543210` (`formatIndianPhone`, line 5566). DOB and dates are browser date pickers, not typed `dd/mm/yyyy` (see 53j, 👤). |
| 10 | Father/Guardian toggle, label switches everywhere including the PDF | ✅ | `CandidateSection.tsx` radio; YAML `labelByParentType` on Aadhaar, PAN, Court; `C: CheckApiIntegrationTest.theFatherLabelBecomesGuardianWhenTheCandidateHasAGuardian`; `R: ReportModelAssemblerTest.aGuardianChangesTheLabelAndTheHiddenPeriodDisappears`; frontend `CaseWorkspacePage.test` "switches the parent label between Father and Guardian" | Old renamed the row on every card; new renames it on the three types that have that row (the only ones that do). |
| 11 | Photo upload with placeholder | ✅ | `PhotoUploader.tsx` + `PhotoUploader.test.tsx`; `D: DocumentApiIntegrationTest.thePhotoIsSetReplacedAndRemoved`; `R: ReportModelAssemblerTest.aMissingPhotoFileFallsBackToThePlaceholder…`; fixture page 1 | JPEG/PNG only; camera data stripped. |
| 12 | Verification Period toggle hides the whole row, keeps dates | ✅ | `PeriodSection.tsx`; `R: HtmlRendererTest.theHiddenPeriodRowIsNotInTheHtmlAtAll`; `C: CaseApiIntegrationTest.theVerificationPeriodMustRunForwardsButMayBeHidden` | Fixture printed with the period shown; the hidden case is proven by the HTML test. |
| 13 | Address fields, country default India | ✅ | `CandidateSection.tsx`; DB default `country … DEFAULT 'India'` (V2 migration); PIN rule `^[1-9][0-9]{5}$` (`schemas.ts`, `FieldValues`) | |

### C. Verification cards

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 14 | All §8 types incl. POLICE, each with its own fields | ✅ | 18 files in `check-types/` incl. `police.yml`; `C: CheckTypeRegistryTest.loadsAllEighteenTypesFromTheSpecInOrder`, `everyTypeHasItsOwnFieldsInsteadOfSharingTheAadhaarList`; `C: CheckApiIntegrationTest.everyOneOfTheEighteenTypesCanBeAddedAndSaved` | The old library has no Police, and 14 of its types reuse the Aadhaar rows (CLAUDE.md §6.4 items 1, 2, 7). Provisional field sets still await your review (§17 item 1). |
| 15 | Add / remove / reorder cards | ✅ | `ChecksSection.tsx` (Add check, Remove with confirmation, up/down arrows); frontend `ChecksSection.test` "reorders…", "asks before removing…", "adds a check from the picker…"; `C: CheckApiIntegrationTest.reorderNeedsEveryCheckExactlyOnce` | The old tool could not reorder. |
| 16 | Several cards of the same type | ✅ | `CheckService.add` has no per-type limit (only 50 checks per case); no unique index in `V3__verification_checks.sql`; `R: ReportModelAssemblerTest.checksOfOneKindShareASummaryCard…` | No test adds the same type twice through the API; code shows no restriction. Suggest one small test. |
| 17 | Icon-group grouping for summary grid and pagination | ✅ | See item 5; `R: PaginationTest.groupsAreInOrderOfFirstAppearance`; fixture page 1 | |
| 18 | "This Card Verifies" falls back | ✅ | `CheckEditor.tsx` field; `CaseApiImpl.reportCheck`: This-card-verifies → summary description → document name; `R: ReportModelAssemblerTest.theDetailPageHas…` | Same order as the old tool (its fallback is the summary description). **Small UI text error:** the hint says it falls back to the document name even when a summary description exists. |
| 19 | Summary description for page 1 | ✅ | `CheckEditor.tsx` ("Summary description (page 1)"); `summaryCard()` in the assembler; fixture page 1 | |
| 20 | Checks table with ticks, details grid, extra rows | ✅ | `pages.html` `detailPage`; `FieldInput.tsx` (a "Verified" tick per row; the old tool's ticks were always on with no control); frontend "adds and removes extra detail rows"; fixture pages 3, 6, 8, 10 | Detail rows are now always Verification Type, Document Type, Requested, Completed, then extras; the old Court entry had no Document Type row and a different order (page 6 of the comparison). Dates in the **rows** follow the date-format switch here; in the old tool they printed as typed. |
| 21 | Card remarks, bold only | ✅ | `BoldTextEditor.tsx`; `BoldOnlyHtml.sanitize`; `C: CheckApiIntegrationTest.theCardItsExtraDetailsAndRemarksAreSavedAndCleaned`; `R: HtmlRendererTest.everythingUserTypedIsEscapedAndOnlyBoldSurvives…` | The old tool pasted card comments in as raw HTML, and its Analyst Remarks did not render bold at all (page 2 of the comparison). |
| 22 | Free sections (text and image) | ⚠️ | `FreeSections.tsx`; `C: …freeTextSectionsCanBeAddedEditedAndRemovedButImagesWaitForDocuments`; text block on fixture page 6 | Text and picture blocks work. **Missing:** the old tool's *blank* blocks ("Free Sections (Blank)": an empty block prints an 80 px blank area to write on later, lines 3000-3010). New refuses an empty text block (button disabled) and a picture block needs a picture; the assembler drops empty ones. |
| 23 | Legal attestation: seal, Bar Council `KAR/670/06`, default disclaimer, needs `ATTESTATION_APPLY` | ✅ | `ReportModelAssembler.DEFAULT_*`; `CheckService.applyAttestation`; `C: CheckApiIntegrationTest.onlySomeoneWithAttestationPermissionCanApplyOrChangeAnAttestation`; fixture page 6 (identical block) | 🔁 by CLAUDE.md §6.4 item 6: Court's default ON is applied only when the creator holds the permission, so an ANALYST who adds a Court check gets it off and cannot turn it on. |

### D. Statuses

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 24 | Six statuses, icons and colours | ✅ | `ReportFormat.status` (badge classes copied from the old CSS); UI marks `STATUS_MARKS` (✓ ✕ ⓘ − ⏱ ↻); fixture pages 1 and 10 (Verified, Unable to Verify) | In the PDF the icons are SVG drawn shapes, not text glyphs (D-032: no missing-symbol boxes). Pending and In Progress are not in the fixture; they use the same mapping. |
| 25 | Four pill presets, exact colours, text editable | ✅ | `ReportFormat.pill`: `#d1fae5/#6ee7b7/#059669`, `#fee2e2/#fca5a5/#dc2626`, `#fef3c7/#fbbf24/#b45309`, `#f3f4f6/#d1d5db/#374151` (checked against CLAUDE.md §6.2); `OverviewSection.tsx`; `C: …theStatusPillUsesPresetTextUnlessEdited…`; frontend "fills in the standard status wording when a preset is chosen" | On-screen preset tiles use Tailwind shades (slightly different text tint); only the PDF colours are exact. |

### E. Supporting documents

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 26 | Several documents per card | ✅ | `CheckDocuments.tsx`; `D: DocumentApiIntegrationTest.limitsHowManyFilesACheckCanHold`; fixture pages 3-4 and 8-9 | Also accepts PDFs (first page shown). The old file picker said PDF but its script refused it. |
| 27 | Auto-renumber by visual order; labels | ✅ | Assembler numbering; `R: ReportModelAssemblerTest.documentsAreNumberedInPageOrderMovedOnesContinueTheCount`; `D: DocumentApiIntegrationTest.numbersTheDocumentsOriginalThenAdditional`; fixture pages 8-9: the staying document is "Document 1", the moved one "Document 2" | Old tool's real label for added documents is "Additional Document" (no number); new is "Additional Document 1, 2…" as CLAUDE.md §6.2 says. |
| 28 | Move to Next Page | ✅ | `DocumentPlacement.tsx`; `R: PdfRendererTest.aMovedDocumentStartsANewPageInTheStandardBox`, `ReferenceToolComparisonTest.aMovedDocumentIsTheSame`; fixture pages 4 and 9 (measured 400 px box in both tools) | PROGRESS.md notes the switch was not clicked in a real browser against the running stack. |
| 29 | Use Larger Box | ✅ | `R: PdfRendererTest.aLargerBoxIsPrintedNearFullPage…`, `ReferenceToolComparisonTest.aLargerBoxIsTheSame`; fixture page 7 (both tools: box 800 px, picture 700 px, measured) | Switching the larger box on also moves the document, as in the old tool; switching the move off also turns the larger box off (the old tool kept a dead setting). |
| 30 | Individual document status | ⚠️ | Old: a `✓` / `○` mark per document row meaning "image uploaded / no image" (`dStatus`, lines 3674-3677). New: `CheckDocuments.tsx` lists only uploaded documents, with quality, size, "New page", "Larger box", "Cropped" badges | The new app has no empty document slot, so the mark has nothing to show. Not a verification status. Nothing needed unless you want empty slots back. |
| 31 | Crop / zoom editor | ⚠️ | `CropEditor.tsx` (drag to choose, four sliders, "Use the whole picture"), `crop.ts`; frontend "draws a crop by dragging on the picture"; `R: ReportModelAssemblerTest.aCropIsAppliedToThePictureThatIsEmbedded` | Crop works and is non-destructive (the old tool overwrote the picture as JPEG and could only Undo in that session). **Missing:** zoom in/out/reset buttons, the full-screen editor, an Undo button (a narrow crop is the only zoom). |
| 32 | Quality badge (≥1500 High, ≥800 Medium, else Low) | ✅ | `ImageQuality.java` (same thresholds); `D: ImageProcessingTest.qualityFollowsTheLongerSide`; `CheckDocuments.tsx` badge | In the old tool the badge, size, and resolution also **print in the PDF** frame header (see section 5, difference 7). New shows it only in the app. |

### F. Progress tracking

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 33 | Progress dashboard: total, count and % per status, bar, "COMPLETE" badge | ❌ | Backend returns it: `ProgressView(percent, sections, totalChecks, checksByStatus)` (`CaseViews.java:90`, `CaseInsightService.progress`). Frontend keeps the fields in `types.ts:147-148` but **nothing displays them**; `SectionNavigator.tsx` shows a bar of *sections saved* (percent of 7). Per-check status marks appear only in the check list | Old: total, six status rows with count and %, bar of concluded checks, "✓ COMPLETE" at 100% (lines 1774-1836, 4071-4148). |
| 34 | Progress live or after saving | 🔁 | New: figures come from the server after each Save (`GET /progress`). CLAUDE.md §6.4 item 3: "explicit saves with visible errors". | The old tool was not fully live either: the status dropdown only sets `verifCards[i].status` (line 3268) and does not redraw the dashboard until something else does. |

### G. Quality control

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 35 | Validation before generating (errors block; warnings confirm) | ✅ | `CaseInsightService.validate`; `C: CaseApiIntegrationTest.validationSeparatesBlockingErrorsFromWarnings`, `validationFollowsWhatHasBeenFilledIn`; frontend `GenerateSection` + "lists what blocks the report and jumps to the section that needs work" | Errors and warnings are exactly the §7.1 lists. Better than old: period-date warnings only when the period is shown; extra warnings for check dates, unfinished statuses, missing required check fields. |
| 36 | Required fields highlighted | ⚠️ | `Field` shows "(required)" for Report ID, Issue date, Client only (`ReportInfoSection.tsx`) | Full name and Employee ID are §7.1 errors but not marked in section 2; no red highlight of empty required fields after checking (old: red border + `*`, lines 2134-2135). Section marks ⚠ with counts do exist. |
| 37 | "Fix Issues" and "Print Anyway" equivalents | ✅ | "Go to section" on every issue (opens the exact check); "Generate anyway" / "Submit anyway" confirmation; frontend "asks for confirmation when there are warnings…" (`ReportPanel.test`) | Old "Fix Issues" also focused the first empty field; new goes to the section only. |

### H. Export

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 38 | PDF visually matches the old tool | ⚠️ | Section 5: same page plan (11 = 11), same structure, same services page | Twelve visible differences are listed there; five are intentional and logged, the rest are decisions for you. |
| 39 | Password-protected PDF (AES-256, optional open password, fails loudly) | 👤 | `PdfEncryptionService`; `R: PdfEncryptionServiceTest` (`itIsAes256`, `withAPasswordTheFileCannotBeOpenedWithoutIt`, `theOwnerPasswordIsRandomPerFile`, `aBrokenInputFailsLoudly…`); `FinalizeDialog` (password 8-128 + repeat; frontend "finalizes a draft made after the approval, with an optional password") | Code and tests are complete. **Never run end to end through the stack** (PROGRESS.md): approve as a second admin, fresh draft, finalize with a password, open the file. Drafts are plain PDFs; only the final is protected, and it is always protected. |
| 40 | Watermark rendered in the PDF | ✅ | Fixture PDF: diagonal text on all 11 pages; `R: PdfRendererTest.theWatermarkIsPrintedOnEveryPageWhenSwitchedOn` | |

### I. Design

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 41 | Inter bundled locally | ✅ | `static/report/fonts/inter-latin-400…900-normal.woff2`; no `http` in `report.css`; `R: HtmlRendererTest.theFontIsBundledAndTheReferenceStylesArePresent`, `PdfRendererTest.everyFontInAReportIsBundledAndEmbedded`; start-up font self-check (D-036) | The old tool loaded Google Fonts. Weight 300 is not bundled (the old CSS uses it once). |
| 42 | Nexlyn branding: logo, brand-block font, tagline, footer | ⚠️ | Fixture page 1 and footers: logo, layout, tagline colour and footer identical | The brand block, title and footer name no font file in the old tool (system stack, Segoe UI on Windows). New uses the bundled **Selawik** (D-036), so NEXLYN prints Bold instead of Segoe UI Black (a little lighter); the `\|` separators are drawn bars. Logged, but not pixel-exact. |
| 43 | Colour palette | ✅ | Hex colours in `report.css` and templates compared with the old CSS and markup: no colour exists in the new files that is not in the old tool; fixture pages 1 and 11 | |

### J. Data management

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 44 | Auto-save vs explicit saves; unsaved-changes warning | ✅ | `CaseWorkspacePage.tsx` (`useBlocker` + `beforeunload`), `dirtyGuard.ts`; frontend "asks before leaving a section with unsaved changes…", "does not ask when nothing was changed", "asks before leaving the case for another page"; `ChecksSection.test` "asks before switching to another check…" | 🔁 auto-save → explicit Save per section, on purpose (§6.4 item 3: the old auto-save failed silently over ~5 MB). Every save shows its error. |
| 45 | "Reset all data" | ⚠️ | No reset. The nearest things: Remove check (with confirmation), `DELETE /api/cases/{id}` (soft delete, `CASE_DELETE`, SUPER_ADMIN only; `C: CaseApiIntegrationTest.deletingHidesTheCaseEverywhere…`) | The delete API exists but **no screen calls it**. Not needed for a shared database; optional button. |
| 46 | JSON export / backup | 🔁 | No export or import. Data is in PostgreSQL with backups (`docs/runbooks/backup-restore.md`). CLAUDE.md §1: "everything is stored in PostgreSQL"; §6.4 item 3 | Only needed if you want old-tool reports moved in: that would be a one-off importer (your call). |

### K. Advanced features

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 47 | Date format toggle, screen and PDF | ✅ | `SettingsSection.tsx`; `R: ReportModelAssemblerTest.textDatesUseTheMonthName`; fixture pages 1, 3, 6, 8 in `11-Jun-2026` style | The dashboard's own screens always show `dd/mm/yyyy`; the choice affects the report and the HTML preview (frontend test "writes the issue date day first even when this case's report prints text dates"). |
| 48 | Date master and ★/🔄/✏️ badges | ✅ | `CheckService.applyDates`; `C: CheckApiIntegrationTest.theFirstChecksDatesFlowToTheOthersUntilTheyAreSetByHand`, `reorderingMakesTheNewFirstCheckTheMaster`; `CheckEditor.tsx` `SYNC_BADGES`; frontend "opens a check from the address bar…" asserts "★ Master" | Small differences: one "manual" flag covers both dates (old: one per date); a later check typed to equal the master stays Auto (old: became Manual). |
| 49 | Overview auto values with manual override | ✅ | `OverviewCalculator` (+`OverviewCalculatorTest`); `OverviewSection.tsx` (placeholders show the automatic value, empty box = automatic); `C: …theStatusPillUsesPresetText…AndOverridesReplaceAutomaticNumbers` | Automatic "Completed" now counts concluded checks; the old tool copied the total. |
| 50 | Prefill from candidate unless edited | ✅ | `CheckPrefillService`; YAML `prefill:`; `C: CheckApiIntegrationTest.checksFollowTheCandidateUntilSomeoneTypesTheirOwnValue`; frontend "saves: edited prefilled fields become manual, untouched ones keep following the candidate" | Matching is by field key, not label text (CLAUDE.md §6.2, §6.4 item 8). "Use candidate value" button and source badges exist. |
| 51 | Formatters: Aadhaar 4-4-4, PAN caps, PIN 6, phone +91, caps inputs | ⚠️ | Aadhaar is stored as digits and shown `XXXX XXXX 1234`; PAN upper-cased on save (`FieldValues`); PIN 6-digit rule; phone normalised (`FieldValuesTest`) | **Missing:** the old tool upper-cases **Full Name and Father Name** as you type (`data-uppercase`, line 5515; the comparison PDF prints `ASHA RAO` vs `Asha Rao`). **Different:** nothing formats while typing (no live 4-4-4, no green/red border); checks run on Save. Phone grouping: see item 9. |
| 52 | Masked by default in app and PDF; audited reveal | ✅ | `PiiMasker`; `C: CheckApiIntegrationTest.anAadhaarNumberIsValidatedEncryptedAndOnlyEverShownMasked`, `revealingNeedsPiiUnmaskAccessToTheCaseAndIsAudited`, `noAuditRowEverHoldsAFullIdentityNumber`; frontend "shows only the masked number, and reveals the real one… (then hides it)"; fixture pages 3 and 5 print `XXXX XXXX 0124`, `ABXXXXX12F` | 🔁 §6.4 item 5 (the old tool never masked). Full-vs-masked on the PDF is still open item 3 in §17; masked is built. |

### L. Anything else

| # | Feature | Status | Proof | Notes |
|---|---|---|---|---|
| 53 | Every other button, toggle, option, shortcut or behaviour in the old HTML | ⚠️ | Section 4 (22 sub-items) | Gaps: drag-and-drop and paste upload, "Comments on next page", blank free blocks (22), bulk paste. |

---

## 4. Item 53: everything else found in the old tool

| Sub | Old behaviour (where) | Status | New app / note |
|---|---|---|---|
| 53a | Toast after most actions ("Added ✓", "Cropped ✓", …) (`showToast`) | 🔁 | Inline "Saved" and error messages instead; no toasts. |
| 53b | "Apply Changes" button copies the sidebar to the report | 🔁 | Explicit Save per section; the HTML Preview shows the result. |
| 53c | Bulk paste into the first card: a comma/tab/newline list fills several rows (lines 3432-3456) | ❌ | Not built. Niche; skip unless you used it. |
| 53d | "+ Add Another [Type] Card" one-click duplicate (line 3915) | ⚠️ | The Add check dialog does the same in two clicks (and lists the client's usual checks first). |
| 53e | Rename, add and remove rows of a card's check table (`+ Add Row`) | ⚠️ | Rows come from the type's YAML (CLAUDE.md §2, §8: config-driven). Only "Extra details" rows are editable per case. Decision for you. |
| 53f | Click a picture to enlarge (`openLightbox`) | ✅ | **Broken in the old tool: the function is called (lines 3124, 3162, 3186) but never defined**, so a click throws an error. New: "View" opens the picture in a new tab. |
| 53g | Drag-and-drop a file onto a document or picture block; paste an image from the clipboard | ❌ | New: file picker (several files at once). D-030 lists drag-and-drop as "not done". |
| 53h | Inline zoom bar (−, +, reset, Crop, Undo, full screen) | ⚠️ | See item 31. |
| 53i | Green/red border while typing Aadhaar, PIN, PAN, phone, date | ⚠️ | New validates on Save with a message beside the field (frontend "checks values before sending and shows the problem next to the field"). |
| 53j | Dates typed as `dd/mm/yyyy` with automatic slashes (`wireDateInputs`) | 👤 | New uses browser date pickers (`type="date"`); the box shows the browser's own order (may be month-first on a US-locale browser). Check on your machine. |
| 53k | Move **Supporting Documents** as a whole to the next page (`splitDoc`) | ✅ | Per-document Move to Next Page covers it (D-032). The old flag also hid the documents from the main page while its extra page showed only an empty legacy frame, so copying it would be wrong. |
| 53l | Move **Comments** (and the attestation) to a "— Continued" page (`splitRemarks`) | ❌ | Not ported (D-032: "moving a document covers the need", which is true for documents, not for long comments). |
| 53m | Image quality, size and resolution printed in each PDF frame header | ⚠️ | Not printed in the new PDF (the badge is on screen only). Decision: probably better left out of a client report. |
| 53n | Print / Save PDF = browser print | 🔁 | Server-side PDF (CLAUDE.md §2). |
| 53o | Protected PDF: two steps, owner password only, minimum 4 characters, eye toggle | ✅ | Replaced by the finalize dialog (8-128 characters, repeat box, optional open password, no eye toggle). CLAUDE.md §6.4 item 4. |
| 53p | Escape closes the full-screen editor; Enter in the confirm box moves on | ✅ | Every dialog closes on Escape (`components/ui/dialog.tsx`). |
| 53q | Status pill subtitle is overwritten with "All N Requested Verifications Completed" on every redraw (line 4193) | 🔁 | Not copied; the subtitle you write is kept (D-032). |
| 53r | Watermark on/off and text saved per browser | 🔁 | Per report now. |
| 53s | Default Overall Status "Clear"; totals padded to two digits | ✅ | Same (`OverviewCalculator`, `ReportFormat.twoDigits`). |
| 53t | Cannot remove the last card or the last row | ✅ / n/a | New allows removing the last check; validation then blocks generating ("Add at least one verification check"). |
| 53u | Validation window closes on backdrop click; "Restored from auto-save" toast; localStorage state | 🔁 | Not applicable (no local state; §6.4 item 3, §11.5). |
| 53v | Photo box says "Upload Photo" / "Change Photo" over the picture | ✅ | Upload / Replace photo buttons beside it. |

Not in the old tool but in CLAUDE.md (kept, not a gap): reorder cards, per-field Verified tick, PDF as a supporting document, per-report watermark.

---

## 5. PDF comparison (fixture)

**Fixture (the same data in both tools):** Report `NX-2026-0142`; company "Acme Corp / Private Limited"; candidate Asha Rao,
father Ravi Rao, EMP-1001, DOB 17/05/1994; period 19/05/2026 to 11/06/2026; layout 4 cards; **text dates**; **watermark ON**
(`NEXLYN VERIFIED`); analyst remarks and final recommendation (with a bold word).

| Check | Status | Documents |
|---|---|---|
| Aadhaar | Verified | 2: one stays, one **Move to Next Page** |
| PAN | Verified | 1 |
| Court (attestation ON, remarks, free text block) | Verified | 1 with **Use Larger Box** |
| Employment | Verified | 2: the first moved, the second stays (checks renumbering) |
| Education | Unable to Verify (remarks) | 1 |

Both PDFs have **11 pages** and the browser's page count equals the plan (no page overflows reported for the new one).
Page order: cover, remarks page (4 groups), Aadhaar, Aadhaar moved document, PAN, Court, Court larger box, Employment,
Employment moved document, Education, services.

Files: `docs/ui/2026-09-26-parity-audit/old-tool.pdf`, `new-app.pdf`, and `side-by-side/page-N.png` (old tool left, new app right).
The little programs that built them are in `fixture-source/` (`.txt` so they are not compiled). They read the repo but write only to a
scratch folder.

**Same in both:** page plan and page numbers; cover structure, photo, overview, summary cards and status badges; the four-status
legend; checks tables and details grids; attestation block (seal, Bar Council number, disclaimer); free text block; "— Continued"
title bars; document numbering and continued numbering; the 400 px standard and 800 px larger boxes (measured in the browser:
400/280 and 800/700 in both); services page (page 11 looks identical); watermark on every page; palette.

**Every visible difference:**

| # | Where | Old tool | New app | Cause |
|---|---|---|---|---|
| 1 | Page 1 pill subtitle | "All **5** Requested Verifications Completed" | "All Requested Verifications Completed" | Old bug (overwrites the subtitle); D-032 |
| 2 | Page 1 names | `ASHA RAO`, `RAVI RAO` (capitals) | `Asha Rao`, `Ravi Rao` | Missing: no auto-capitals (item 51) |
| 3 | Page 1 phone | `+91 9876543210` | `+91 98765 43210` | CLAUDE.md §6.2 vs old code (item 9) |
| 4 | Page 2 remarks | Footer and legend float directly under the content; analyst-remarks bold shows as plain text | Footer pinned to the bottom; bold works | Old bug with the watermark on (D-035); bold rule |
| 5 | Detail-page title bar | Status badge right after the title | Badge at the far right edge | New CSS `.det-title-text{flex:1}` so long titles wrap (D-032, but its side effect on every page is not mentioned there) |
| 6 | Pages 3-10 row dates | `17/05/1994`, `01/07/2019` as typed | `17-May-1994`, `01-Jul-2019` | New applies the date format to check rows; old only to page-1 and the details grid |
| 7 | Every document box header | `Document 1 — Aadhaar Card — Original Document ● Medium Quality · 1200×800 · 20 KB` | Same without the quality part | Not printed in new (53m) |
| 8 | Added documents | "Additional Document" | "Additional Document 1" | CLAUDE.md §6.2 says "N" |
| 9 | Page 6 Court details | 5 rows defined (no Document Type): Verification Type, Court Type, Jurisdiction, Requested, Completed; the fifth (Completed) is cut off by the grid's box | 6 rows: Verification Type, Document Type, Requested, Completed, Court Type, Jurisdiction | Different row set and order; the old page clips |
| 10 | Pages 3, 8, 10 documents on the check's own page | Frame ~227-260 px, picture clipped at its bottom edge, gap above the footer | Frame ~305-337 px, whole picture, flush to the footer | With the watermark on the old tool's footer floats (its bug), so its frame is shorter. With no watermark the two agree to within a few px (`ReferenceToolComparisonTest`, D-041) |
| 11 | Status badge icons, employment icon | Text glyphs (ⓘ) and the library icon | SVG shapes, slightly different briefcase | D-032 |
| 12 | Brand block | Segoe UI (this Windows machine) | Selawik (bundled) | D-036 |

Also, not visible on this page but different: full Aadhaar and PAN numbers print unmasked in the old tool; the new PDF prints `XXXX XXXX 0124`
(by design, item 52).

Not done: the same report produced through the signed-in app (needs your login), and the same fixture with the watermark off.

---

## 6. In the v4.0 documentation but beyond v3.2 (extra, not required)

The v4.0 document says it is the v3.2 backup. These are the parts that go beyond v3.2 and are **not** counted as gaps:

| Item in the document | State |
|---|---|
| Phase 1 "UI Enterprise Polish" (spacing, typography, badges) | Done for the admin dashboard in the UI/UX phase (§16.1, D-037 to D-040). The PDF was deliberately not touched. |
| Phase 2: Verification Timeline, Risk Score badge, Confidence bars | Not built. In CLAUDE.md §15 "Later". |
| Phase 3: QR code display, Digital Signature block, Investigator Notes page | Not built. "Later". |
| Phase 4: Client Logo customization, higher PDF quality, final testing | Client logo: not built ("Later"; the `CLIENT_LOGO` document kind exists). |
| "v4.0+ = 10/10" rating table, competitor comparison | Marketing text; ignore. |
| 3-2-1 backup strategy, "use JSON export before major changes" | Replaced by database backups (`docs/runbooks/backup-restore.md`, key backup tool). |

**Where the v4.0 text and the old code disagree** (I followed the code): "real-time" progress (status dropdown does not redraw); "Police" is
listed among the card types (no such library entry); "Individual document status" is only the ✓/○ upload mark; file "~5,800 lines, 547 KB"
(actual 6,087 lines); "Google Fonts (Inter)" was a runtime dependency.

---

## 7. Old-tool bugs deliberately not carried over (for the record)

1. `openLightbox` is called but never defined: clicking a picture throws.
2. With the watermark on, `.with-watermark .page > *` makes every footer float up (fixed for the new report in D-035).
3. The status subtitle is overwritten on every redraw.
4. The status dropdown does not redraw the progress dashboard or the summary.
5. Analyst Remarks are inserted as text (no bold); card comments are inserted as raw HTML (no sanitising).
6. Every free image block registers a page-wide paste handler, so one pasted image fills all image blocks.
7. `splitDoc` hides the documents from the main page and shows an empty legacy frame on its extra page.
8. The PDF file picker says PDF but the script rejects everything except images.
9. Phone-format comment says 5-5 grouping; code does not group.

---

## 8. Things only you can check (👤), and what to spot-check in the browser

Item 39 is the only 👤 row, but these ✅ rows rest on tests, not on a real browser session. Sign in at http://localhost:5173.
(The Docker stack was last rebuilt on 2026-09-26.)

1. **Item 39, protected final PDF (about 10 minutes).** Use a case with a checked, complete draft. Submit for review, sign in as a **second**
   admin (the preparer can never approve), Approve. Back on the case (Section 8) **Generate draft PDF** again (a draft made after the approval
   is required), then **Finalize report**, type a password twice, Finalize. Download the final: it should ask for the password, open, and refuse
   copy and edit. Try finalize with the password box empty too.
2. **Items 28, 29, 27.** In a check, add three pictures. Click **Move to Next Page** on the first and **Use Larger Box** on the second. Both badges
   should appear at once. Generate a draft and check the numbering "Document 1, 2, 3" follows the page order.
3. **Item 48.** Set dates on the first check, add a second: it should show 🔄 Auto. Change its dates: ✏️ Manual. Change the first check's dates: the manual one stays.
4. **Item 53j / 9.** In Candidate details, look at the Date of birth box and type a phone as `9876543210`. Note the date order shown (day first?) and the phone shown after saving.
5. **Items 36 and 33.** Leave Full name empty, open section 8: you should see "Candidate's full name is required", but no red mark in section 2, and no check-status counts anywhere. This confirms the two gaps.
6. **Item 5/17.** Add two identity checks (Aadhaar and PAN) and a Court check; Preview: two summary cards, remarks on page 1.
7. **Item 12.** Untick "Show the verification period on the report", Save, Preview: the whole row is gone; tick again: the dates are still there.
8. **Item 7/47.** Turn the watermark on with text over 40 characters (should be refused), then a short text, choose Text dates, Save, Preview and generate: watermark on every page, dates like `11-Jun-2026`.

---

## 9. Proposed fixes (nothing has been done; waiting for your decision)

| Item | Size | Suggested fix |
|---|---|---|
| 33 Progress dashboard | small | Add a "Report progress" card to the workspace sidebar from the data the server already sends: total, six status rows with count and %, a bar of concluded checks, "✓ COMPLETE" at 100%. Frontend only. |
| 9 Phone format | small | Your choice: keep `+91 98765 43210` (CLAUDE.md) or match the old tool's `+91 9876543210`. One line in `IndianPhone.format` plus tests. |
| 51 / 2 Capitals and live formatting | small to medium | Upper-case Full name and Father/Guardian name as typed (input `onChange`), PAN as typed, digits-only PIN, live phone/Aadhaar grouping. Frontend only; the server already normalises. |
| 36 Required marks | small | Pass `required` to Full name and Employee ID; on "Go to section", focus the first empty required field and show the red state. |
| 22 Blank free blocks | medium | Allow an empty text block and an empty picture block that print an 80 px blank area. Needs the server rule (empty text is refused today), the UI, and the assembler. |
| 53l Comments to next page | small to medium | Add a per-check "Comments (and attestation) on next page" switch that adds a "— Continued" page, like `splitRemarks`. Touches pagination and the page count. |
| 53g Drag-and-drop / paste upload | medium | Drop zone on the documents list and picture blocks; paste handler for images. Frontend only. |
| 31 Zoom and undo | small | Add a large preview dialog with zoom buttons; the crop's "Use the whole picture" already acts as undo. |
| 5 (in 38) Title-bar badge | small | Keep the badge next to the title unless the title is long (drop `flex:1` for short titles), or accept the right-aligned badge and log the visible effect in D-032. |
| 53m Quality line in the PDF | decision | Recommend leaving it out of the client PDF; log it in D-032. |
| 18 Hint text | trivial | Say the fallback is the summary description, then the document name. |
| 45 Delete case | small, optional | A "Delete case" button for SUPER_ADMIN with a confirmation. |
| 16 Test | trivial | One test that adds two checks of the same type. |
| 46 / 34 | decision | No JSON export; only write an importer if old-tool reports must be moved in. |
| 30, 42, 53c, 53d, 53e | no action suggested | Empty document slots, wordmark weight (D-036), bulk paste, duplicate-card shortcut, editable check rows: your decision, none needed for parity of the report. |
