# Incident response

An incident is anything that may have exposed personal data or let the wrong person act: a lost laptop or key,
an admin account used by someone else, unexpected changes in the audit log, a server break-in, a report sent to
the wrong recipient.

## 1. First hour: stop the damage

Do the first that applies. Each is quick and can be undone.

| Suspicion | Action |
|---|---|
| One admin's account was misused | Admins page → **Disable** them, then **Revoke sessions**. Their sessions stop immediately |
| A password or phone (two-step) was lost | Same, then invite them again later |
| The signing key or the server was compromised | Replace `JWT_PRIVATE_KEY` **without** the previous-key settings ([key-rotation.md](key-rotation.md)): everyone is signed out. Change `DB_PASSWORD` and the S3 keys as well |
| A report went to the wrong recipient | Note the report ID, version and time; go to step 3 |
| The site is under attack (floods of requests) | The proxy already rate-limits sign-in; if it continues, block the address range in the cloud firewall (security group) |

Do **not** delete logs, stop the containers or wipe anything before step 2: they are the evidence.

## 2. Find out what happened

- **Audit log** (Admin → Audit log): who did what and when. Filter by the account and the time. Every sign-in,
  failed sign-in, lockout, reveal of an Aadhaar/PAN, document view, download and status change is there. It cannot
  be edited or deleted, not even by a super admin.
- **Server logs**: `docker compose --env-file .env logs --since 24h backend > /tmp/backend.log`. Every line has a
  correlation id; the id also appears on the error a person saw, so a reported error can be traced.
- **S3**: turn on and read the bucket access logs (CloudTrail data events) for unusual downloads.

Write down: what, when it started, which accounts and cases, whether personal data left the system.

## 3. Tell the right people

- The owner immediately.
- If personal data of candidates may have been exposed, the company must assess its duty to notify the affected
  people and the Data Protection Board of India under the DPDP Act 2023 (and the client companies under their
  contracts). This is a legal decision: contact the company's lawyer the same day; do not wait for certainty.

## 4. Recover

- Reset the passwords / two-step of the affected admins (invite again).
- Rotate every secret the attacker could have seen ([key-management.md](key-management.md)).
- If data was changed or deleted, restore from a backup or an S3 earlier version ([backup-restore.md](backup-restore.md)).
- Update the server and images ([deploy-update.md](deploy-update.md)) if a software flaw was involved.

## After

Within a week write a short note: timeline, cause, what was done, what will change. Keep it with the company records
(not in this repository if it contains personal data).
