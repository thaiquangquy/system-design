#!/bin/bash
# Runs as a docker-entrypoint-initdb.d script on shard primaries only: allows the paired
# replica container to open a streaming-replication connection. Local demo only — see
# POSTGRES_HOST_AUTH_METHOD=trust on the primary services in docker-compose.yml.
set -e
echo "host replication all 0.0.0.0/0 trust" >> "$PGDATA/pg_hba.conf"
