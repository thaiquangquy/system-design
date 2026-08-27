-- Schema for the id_ticket / idgen database (the sharded profile's "default" DataSource target).
-- ddl-auto is "none" in the sharded profile (see application-sharded.yml), so this table is
-- provisioned here instead.
CREATE TABLE IF NOT EXISTS id_ticket (
    id BIGSERIAL PRIMARY KEY
);
