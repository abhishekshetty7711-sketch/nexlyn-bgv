# Production on one machine

Three containers on one Linux server (about 4 GB RAM): `proxy` (nginx, TLS, rate limits), `frontend` (the web app's
files) and `backend` (the Spring Boot app). The database is **AWS RDS PostgreSQL 16** and
the files are in **AWS S3 (ap-south-1)**; neither runs on the server.

```
Internet ─► proxy :80/:443 ─┬─ /api/**, /actuator/health ─► backend :8080 ─► RDS, S3
                            └─ everything else ──────────► frontend :8080 (static files)
```

Only the proxy publishes ports. The backend cannot be reached from outside.

## What only the owner can do before the first start

These need accounts, money, real secrets or DNS, so they are not automated and not done by the developer tools.

1. **A Linux server** (Ubuntu 22.04/24.04 LTS, 2 vCPU, 4 GB RAM, 40 GB disk) with Docker Engine and the Compose plugin,
   in the AWS Mumbai region (data stays in India).
2. **A domain name** pointing at the server (an `A` record), for example `bgv.nexlyn.example`.
3. **A TLS certificate.** Free option: Let's Encrypt with certbot. The proxy cannot start without a certificate, so
   the first one is fetched with `--standalone` (before the stack is up); renewals then use the webroot `./acme`:
   ```bash
   sudo certbot certonly --standalone -d YOUR-DOMAIN --deploy-hook /opt/nexlyn-bgv/infra/prod/certbot-deploy-hook.sh
   # after the stack is running:
   sudo certbot reconfigure --cert-name YOUR-DOMAIN --webroot -w /opt/nexlyn-bgv/infra/prod/acme \
        --deploy-hook /opt/nexlyn-bgv/infra/prod/certbot-deploy-hook.sh
   ```
   The hook copies the certificate into `infra/prod/certs` (readable by the proxy's user) and reloads the proxy;
   keep `TLS_CERT_DIR=./certs`. Mounting `/etc/letsencrypt/live/...` directly does not work (links into
   `../../archive`, root-only permissions).
4. **RDS**: PostgreSQL 16, not public, encrypted, automated backups on (14+ days), reachable only from the server's
   security group. Create the database `nexlyn_bgv`, then run `infra/local/postgres/init/01-create-schemas.sql` once
   as the master user. Create an app user with rights on that database (the app applies its own migrations).
5. **S3**: a private bucket in `ap-south-1` with *Block all public access* on, versioning on, default encryption on.
   Give the server an IAM role that can read, write and delete objects in that one bucket (then leave the `S3_*`
   keys empty), or create an access key limited to that bucket.
6. **Secrets**: generate and store the keys as described in [`docs/runbooks/key-management.md`](../../docs/runbooks/key-management.md),
   **including the offline copy of `PII_ENCRYPTION_KEY`**.
7. **The site name in the proxy**: `server_name` in `nginx/nexlyn.conf` is `nexlynservices.com`; change it if the
   host name changes.

## First start

```bash
sudo mkdir -p /opt/nexlyn-bgv && sudo chown $USER /opt/nexlyn-bgv
git clone <the repository> /opt/nexlyn-bgv && cd /opt/nexlyn-bgv/infra/prod
cp .env.example .env && chmod 600 .env
nano .env                                   # fill in every value (comments explain each)
docker compose --env-file .env config -q    # checks the file: prints nothing when it is fine
docker compose --env-file .env up -d --build
docker compose --env-file .env ps           # wait until backend is "healthy" (up to 90 seconds)
```

Then:

1. Open `https://YOUR-DOMAIN`, sign in with the bootstrap admin, set up two-step login (save the backup codes).
2. **Delete `BOOTSTRAP_SUPERADMIN_EMAIL` and `BOOTSTRAP_SUPERADMIN_PASSWORD` from `.env`** and run
   `docker compose --env-file .env up -d`.
3. Invite the other admins (Admins page). Create a client and a test case; generate a draft PDF.
4. Set up the daily backup: [`docs/runbooks/backup-restore.md`](../../docs/runbooks/backup-restore.md).
5. Firewall: allow only 80 and 443 in (and 22 from your own address). Nothing else.

## What is hardened (so you know what NOT to undo)

- Containers run as a non-root user with a read-only file system, no Linux capabilities, `no-new-privileges`,
  memory limits and rotated logs.
- The proxy speaks TLS 1.2/1.3 only, sends HSTS, limits sign-in to 10 tries a minute per address, limits request
  size (12 MB) and hides everything under `/actuator/` except `/actuator/health`.
- The web app sends a strict Content-Security-Policy and the usual security headers.
- The backend refuses to start in production without the signing key, the two encryption keys and a database; it
  never returns stack traces; logs are JSON with a correlation id and contain no personal data.

## Day to day

| Task | Where |
|---|---|
| Release a new version | [`deploy-update.md`](../../docs/runbooks/deploy-update.md) |
| Backups and restoring | [`backup-restore.md`](../../docs/runbooks/backup-restore.md) |
| Replace a key | [`key-rotation.md`](../../docs/runbooks/key-rotation.md) |
| Something is wrong / a possible break-in | [`incident-response.md`](../../docs/runbooks/incident-response.md) |
| Reports are slow or failing | [`pdf-load.md`](../../docs/runbooks/pdf-load.md) |
| Logs | `docker compose --env-file .env logs -f backend` |
| Status | `docker compose --env-file .env ps` and `curl https://YOUR-DOMAIN/actuator/health` |
