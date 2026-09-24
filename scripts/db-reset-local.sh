#!/bin/sh
# Drops the local Postgres data volume so the next `docker compose up`
# recreates schemas from scratch (infra/local/postgres/init) and Flyway
# reruns every module's migrations from V1.
set -eu
cd "$(dirname "$0")/../infra/local"
docker compose down -v postgres
echo "Postgres volume removed. Run local-up.sh to recreate it."
