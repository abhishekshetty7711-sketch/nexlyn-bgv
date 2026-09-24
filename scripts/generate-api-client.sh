#!/bin/sh
# Regenerates frontend/src/api/generated from the backend's OpenAPI spec.
# Requires the backend running locally (springdoc-openapi exposes the spec
# at /v3/api-docs once the `auth`/`cases` controllers exist from Phase 2+).
set -eu
root="$(dirname "$0")/.."
SPEC_URL="${SPEC_URL:-http://localhost:8080/v3/api-docs}"

npx --yes openapi-typescript "$SPEC_URL" \
  --output "$root/frontend/src/api/generated/schema.ts"

echo "Generated frontend/src/api/generated/schema.ts from $SPEC_URL"
