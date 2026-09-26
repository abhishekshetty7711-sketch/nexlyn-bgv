-- "Comments on next page" (audit item 53l): a check's comments and its legal attestation can be moved from the
-- check's main page to a page of their own, straight after it, like the reference tool's per-card "Move Comments to
-- next page" switch. Off by default, so every existing check keeps its layout.

ALTER TABLE verification_checks
    ADD COLUMN comments_on_next_page BOOLEAN NOT NULL DEFAULT FALSE;
