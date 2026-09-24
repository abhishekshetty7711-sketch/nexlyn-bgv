#!/bin/sh
set -eu

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing local/nexlyn-bgv
mc anonymous set none local/nexlyn-bgv
echo "Bucket nexlyn-bgv ready (private)."
