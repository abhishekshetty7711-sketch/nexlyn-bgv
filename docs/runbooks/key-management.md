# Keys and secrets

Everything secret lives in `infra/prod/.env` on the server (permissions `chmod 600`, owner = the deploy user), or
in AWS Secrets Manager if you prefer to inject them from there. **Nothing secret is ever committed to git**, pasted
into chat, or sent by e-mail.

| Setting | What it does | If it leaks | If it is lost |
|---|---|---|---|
| `JWT_PRIVATE_KEY` | Signs login tokens | Anyone can make themselves an admin. **Rotate at once** ([key-rotation.md](key-rotation.md)) | Everyone is signed out; generate a new one. No data lost |
| `PII_ENCRYPTION_KEY` | Encrypts Aadhaar / PAN / UAN values in the database | Stored numbers can be read by anyone who also gets the database | **The numbers are gone for good.** Only the last 4 digits survive |
| `TOTP_ENCRYPTION_KEY` | Encrypts each admin's two-step secret | Two-step codes can be forged for whoever also gets the database | Every admin must set up two-step again (a SUPER_ADMIN resets them) |
| `DB_PASSWORD` | Database access | Change it in RDS and `.env`, restart | Reset it in RDS |
| `S3_*` | File access (better: leave empty and use an IAM role) | Deactivate the key in IAM, issue a new one | Same |

## Creating them (first setup)

Run on a trusted machine, not on a shared computer:

```bash
openssl rand -base64 32      # → PII_ENCRYPTION_KEY
openssl rand -base64 32      # → TOTP_ENCRYPTION_KEY (a different value!)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 | awk 'NF {printf "%s\\n", $0}'   # → JWT_PRIVATE_KEY
```

## The backup copy of `PII_ENCRYPTION_KEY` (mandatory, do it the same day)

1. Save the value in a password manager owned by the company (not a personal one), **and** keep a second copy
   offline (a printed sheet in a safe, or an encrypted USB stick kept by a second trusted person).
2. Record *who* holds each copy and when it was made, in the company's own records (not in this repository).
3. Every quarter check the copy still matches: on the server run `grep ^PII_ENCRYPTION_KEY infra/prod/.env`
   and compare the first 6 characters with the copy.

The same applies (less urgently) to `TOTP_ENCRYPTION_KEY` and `JWT_PRIVATE_KEY`.

## Who may see them

Only the owner and the person who runs the server. When someone with access leaves, treat every value they could
see as leaked: change `DB_PASSWORD` and the S3 keys, and rotate `JWT_PRIVATE_KEY`. `PII_ENCRYPTION_KEY` and
`TOTP_ENCRYPTION_KEY` can be changed only by a data migration that re-encrypts every stored value: ask a
developer, and never just replace the value, or the existing data becomes unreadable.

## The first administrator

Set `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` in `.env` for the first start only. Sign in,
set up two-step login, then **delete both lines** and run `docker compose --env-file .env up -d` again. The
password must be 12+ characters, 3 of 4 character kinds, and not a common password.
