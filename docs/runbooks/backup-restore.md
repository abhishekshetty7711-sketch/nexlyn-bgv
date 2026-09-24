# Backups and restoring

There are three things to protect. Losing any one of them loses data for good.

| What | Where it lives | How it is protected |
|---|---|---|
| Case data (all tables) | AWS RDS PostgreSQL | RDS automated snapshots (turn on, keep at least 14 days) **and** the daily dump from `scripts/backup-db.sh` |
| Uploaded files and PDFs | AWS S3 bucket | Bucket **versioning** on, so an overwrite or delete can be undone; a lifecycle rule removes old versions after 90+ days |
| The keys in `.env` (especially `PII_ENCRYPTION_KEY`) | The server | An **offline copy** kept by the owner: see [key-management.md](key-management.md). Not part of any automatic backup, on purpose |

Without `PII_ENCRYPTION_KEY`, restored Aadhaar / PAN / UAN values cannot be read.

## Daily dump

Install the PostgreSQL 16 client tools on the server (`sudo apt install postgresql-client-16`) and add a
cron job (`crontab -e`) for the user that owns the project:

```
30 2 * * *  /opt/nexlyn-bgv/scripts/backup-db.sh /opt/nexlyn-bgv/infra/prod/.env /var/backups/nexlyn-bgv >> /var/log/nexlyn-backup.log 2>&1
```

It keeps 14 days on the server. **Also copy the dumps off the machine** (for example
`aws s3 sync /var/backups/nexlyn-bgv s3://YOUR-BACKUP-BUCKET/db/` to a different, versioned bucket with restricted
access): a backup on the disk that fails is not a backup. The dump contains personal data; on the server it is
protected by file permissions (0600), and in S3 the bucket must have default encryption on.

## Check the backup works (do this every quarter)

Restore the newest dump into a scratch database and look at it:

```bash
createdb -h localhost -U postgres restore_test        # a throw-away local database, never production
pg_restore --no-owner -h localhost -U postgres -d restore_test /var/backups/nexlyn-bgv/nexlyn-bgv-LATEST.dump
psql -h localhost -U postgres restore_test -c "select count(*) from cases.cases"
dropdb -h localhost -U postgres restore_test
```

## Restoring production (something is lost or damaged)

First ask whether a restore is really needed: RDS can restore to any moment in the last 14 days (console →
*Restore to point in time*), which makes a NEW instance and touches nothing existing. That is the safer route
and needs no downtime while you check it.

To restore from a dump:

1. Stop the app: `docker compose --env-file .env stop backend frontend`.
2. Make a fresh dump of the current state first, even if damaged (`scripts/backup-db.sh`), so nothing is lost twice.
3. Create an empty database, recreate the schemas (`infra/local/postgres/init/01-create-schemas.sql`), and restore:
   `pg_restore --no-owner --dbname "$NEW_DB_URL" nexlyn-bgv-XXXX.dump`.
4. Point `DB_URL` in `.env` at the restored database and start: `docker compose --env-file .env up -d`.
5. Check the audit log and the newest cases; anything entered after the dump was taken has to be entered again.

## Files

To undo an accidental delete or overwrite in S3, open the bucket → *List versions* → delete the "delete marker"
or restore the older version. The app itself only ever soft-deletes files and removes them after 90 days
(setting `nexlyn.documents.retention-days`).
