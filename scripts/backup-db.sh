#!/usr/bin/env bash
# Takes an encrypted-at-rest-friendly dump of the production database (CLAUDE.md section 14: daily backups).
# Meant for a daily cron job on the server (see docs/runbooks/backup-restore.md). Needs the PostgreSQL 16
# client tools (pg_dump). Reads the connection from the same .env file as the deployment.
#
#   scripts/backup-db.sh [path-to-.env] [output-folder]
#
# Keeps the last 14 daily dumps. AWS RDS also takes its own automated snapshots: this is the second, independent copy.
set -euo pipefail

ENV_FILE="${1:-infra/prod/.env}"
OUT_DIR="${2:-/var/backups/nexlyn-bgv}"
KEEP_DAYS=14

[ -f "$ENV_FILE" ] || { echo "No such file: $ENV_FILE" >&2; exit 1; }

# Read only the three values we need (the file also holds secrets this script must not touch).
value() { grep -E "^$1=" "$ENV_FILE" | head -n1 | cut -d= -f2-; }
DB_URL="$(value DB_URL)"
export PGUSER="$(value DB_USERNAME)"
export PGPASSWORD="$(value DB_PASSWORD)"

# jdbc:postgresql://host:5432/name?sslmode=require  ->  host, port, name
rest="${DB_URL#jdbc:postgresql://}"
hostport="${rest%%/*}"
dbname="${rest#*/}"; dbname="${dbname%%\?*}"
export PGHOST="${hostport%%:*}"
export PGPORT="${hostport#*:}"; [ "$PGPORT" = "$hostport" ] && PGPORT=5432
export PGSSLMODE=require

umask 077
mkdir -p "$OUT_DIR"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="$OUT_DIR/nexlyn-bgv-$stamp.dump"

# Custom format: compressed, and pg_restore can restore parts of it.
pg_dump --format=custom --no-owner --no-privileges --file "$file" "$dbname"

# A dump that cannot be listed is not a backup.
pg_restore --list "$file" > /dev/null
echo "Backup written: $file ($(du -h "$file" | cut -f1))"

find "$OUT_DIR" -name 'nexlyn-bgv-*.dump' -mtime +"$KEEP_DAYS" -delete
