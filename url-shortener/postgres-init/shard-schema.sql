-- Schema for each shard primary. Applied via docker-entrypoint-initdb.d; replicas inherit this
-- automatically through streaming replication (see replica-entrypoint.sh), so this only needs
-- to be mounted on the *-primary services. Hibernate ddl-auto is "none" in the sharded profile
-- (see application-sharded.yml) since it can only ever reach the default/idgen DataSource target,
-- not every physical shard.
CREATE TABLE IF NOT EXISTS short_url (
    id BIGSERIAL PRIMARY KEY,
    short_url VARCHAR(16) NOT NULL,
    long_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_short_url_code ON short_url (short_url);
CREATE INDEX IF NOT EXISTS idx_short_url_long_url ON short_url (long_url);
CREATE INDEX IF NOT EXISTS idx_short_url_expires_at ON short_url (expires_at);
