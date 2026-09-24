#!/bin/sh
set -eu
root="$(dirname "$0")/.."

echo "==> backend: ./mvnw verify"
( cd "$root/backend" && ./mvnw verify )

echo "==> frontend: npm install && npm run build && npm test"
( cd "$root/frontend" && npm install && npm run build && npm test )
