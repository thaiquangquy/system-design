#!/bin/bash
# Entrypoint for shard replica containers: bootstraps PGDATA from the primary via pg_basebackup
# (only on first start, when PGDATA is empty) with -R, which writes standby.signal and the
# primaryconninfo needed for streaming replication automatically. Once bootstrapped, PGDATA is
# already a full copy of the primary's data directory, so the normal postgres entrypoint just
# starts it in standby mode — no separate schema init needed on the replica, it inherits the
# primary's schema.
set -e
if [ -z "$(ls -A "$PGDATA" 2>/dev/null)" ]; then
  until pg_basebackup -h "$PRIMARY_HOST" -U "$PGUSER" -D "$PGDATA" -Fp -Xs -P -R; do
    echo "Waiting for primary to be ready for basebackup..."
    sleep 2
  done
  chmod 0700 "$PGDATA"
fi
exec docker-entrypoint.sh postgres
