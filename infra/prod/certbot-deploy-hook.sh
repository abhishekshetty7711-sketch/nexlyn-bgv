#!/bin/sh
# certbot deploy hook (runs as root after every successful issue or renewal). Copies the certificate out of
# /etc/letsencrypt (root-only, and made of links into ../../archive) into ./certs as plain files that the proxy
# container's user (uid/gid 101, nginx-unprivileged) can read, then asks the proxy to reload.
# certbot sets RENEWED_LINEAGE; to run it by hand:
#   sudo RENEWED_LINEAGE=/etc/letsencrypt/live/nexlyn-bgv-app.nexlynservices.com ./certbot-deploy-hook.sh
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
: "${RENEWED_LINEAGE:?certbot sets RENEWED_LINEAGE; see the comment above}"

install -d -m 0700 -o 101 -g 101 "$HERE/certs"
install -m 0644 -o 101 -g 101 "$RENEWED_LINEAGE/fullchain.pem" "$HERE/certs/fullchain.pem"
install -m 0600 -o 101 -g 101 "$RENEWED_LINEAGE/privkey.pem"   "$HERE/certs/privkey.pem"

# Reload only when the proxy is running (on the very first certificate the stack is not up yet).
if docker compose -f "$HERE/docker-compose.yml" --env-file "$HERE/.env" ps --status running --services 2>/dev/null | grep -qx proxy; then
    docker compose -f "$HERE/docker-compose.yml" --env-file "$HERE/.env" exec -T proxy nginx -s reload
fi
