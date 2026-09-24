#!/bin/sh
set -eu
cd "$(dirname "$0")/../infra/local"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created infra/local/.env from .env.example"
fi
docker compose up --build "$@"
