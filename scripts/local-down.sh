#!/bin/sh
set -eu
cd "$(dirname "$0")/../infra/local"
docker compose down "$@"
