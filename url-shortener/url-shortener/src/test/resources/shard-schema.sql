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
