# Releasing a new version

Nothing is deployed automatically. A release is: build the images, take a backup, switch the containers, check.

## 1. Build and publish the images

GitHub → Actions → **deploy** → *Run workflow* → type a new tag (for example `v1.0.1`). It builds the backend and
web images and pushes them to the registry. (The one-time set-up it needs is listed at the top of
`.github/workflows/deploy.yml`.)

If you are not using the registry, skip this and let the server build (step 4, variant B).

## 2. Back up first

```bash
cd /opt/nexlyn-bgv                 # wherever the project is on the server
scripts/backup-db.sh infra/prod/.env
```

Do not continue if it does not print `Backup written`. The database migrations that run at start-up cannot be
undone by going back to the old image, only by restoring this backup.

## 3. Read what changed

Look at the release notes / commit list for anything that asks for a new setting in `.env`. Add it now.

## 4. Switch

Variant A (images from the registry): edit `.env` and set
`BACKEND_IMAGE=<registry>/nexlyn-bgv-backend:v1.0.1` and `FRONTEND_IMAGE=<registry>/nexlyn-bgv-frontend:v1.0.1`, then

```bash
cd infra/prod
docker compose --env-file .env pull
docker compose --env-file .env up -d
```

Variant B (build on the server): `git pull`, then `docker compose --env-file .env up -d --build`.

The backend takes up to 90 seconds to become healthy (it applies database migrations first). The web containers
start only after it is healthy.

## 5. Check

```bash
docker compose --env-file .env ps                      # all three "healthy" / "running"
curl -fsS https://YOUR-SITE/actuator/health             # {"status":"UP"}
docker compose --env-file .env logs --tail 100 backend  # no ERROR lines
docker compose --env-file .env logs backend | grep "font check"   # "Report font check passed" (about 15 s after start)
```

Then sign in, open a case, and generate a draft PDF. Both should work.

## 6. If it went wrong

1. Put the previous tag back in `.env` and run `docker compose --env-file .env up -d`.
2. If the new version had changed the database (the log shows `Migrating schema ... to version`), the old
   version may refuse to start or misbehave: restore the backup from step 2 as described in
   [backup-restore.md](backup-restore.md), *then* start the old version.
3. Write down what happened (see [incident-response.md](incident-response.md), section "After").
