#!/bin/sh
set -eu
cd "$(dirname "$0")/../infra/local"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created infra/local/.env from .env.example"
fi

# Generate persistent local-only secrets once, so sessions and enrolled 2FA survive restarts.
# They live only in the git-ignored .env and are never used in production.
# The private key is stored on ONE line with the newlines written as backslash-n, which the
# backend accepts.
if ! grep -q '^JWT_PRIVATE_KEY=.' .env; then
  if command -v openssl >/dev/null 2>&1; then
    jwt_key=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null | awk 'NF { printf "%s\\n", $0 }')
    totp_key=$(openssl rand -base64 32)
    grep -v -E '^(JWT_PRIVATE_KEY|JWT_KEY_ID|TOTP_ENCRYPTION_KEY)=' .env > .env.tmp || true
    {
      cat .env.tmp
      printf '%s\n' "JWT_PRIVATE_KEY=$jwt_key"
      printf '%s\n' "JWT_KEY_ID=local-1"
      printf '%s\n' "TOTP_ENCRYPTION_KEY=$totp_key"
    } > .env
    rm -f .env.tmp
    echo "Generated local JWT and 2FA encryption keys in infra/local/.env"
  else
    echo "openssl not found: the app will use temporary keys (sessions end on every restart)."
  fi
fi

docker compose up --build "$@"
