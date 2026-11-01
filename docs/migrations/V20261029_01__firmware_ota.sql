-- PostgreSQL, apply once using the deployment migration runner before prod startup.
-- Development retains its existing ddl-auto=create exception. Do not run dev
-- against production. Roll back application deployment without dropping history.
BEGIN;
CREATE TABLE firmware_release (
    id varchar(36) PRIMARY KEY,
    version varchar(32) NOT NULL,
    hardware_revision varchar(32) NOT NULL,
    sha256 varchar(64) NOT NULL,
    binary bytea NOT NULL,
    size integer NOT NULL CHECK (size >= 288 AND size <= 1966080),
    deprecated boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    UNIQUE (hardware_revision, version)
);
CREATE TABLE firmware_channel (
    hardware_revision varchar(32) PRIMARY KEY,
    release_id varchar(36) NOT NULL REFERENCES firmware_release(id),
    revision bigint NOT NULL DEFAULT 0
);
CREATE TABLE firmware_device (
    id varchar(12) PRIMARY KEY,
    owner_account_id bigint NOT NULL REFERENCES user_account(account_id),
    hardware_revision varchar(32) NOT NULL,
    revision bigint NOT NULL DEFAULT 0
);
CREATE TABLE firmware_session (
    token_hash varchar(64) PRIMARY KEY,
    device_id varchar(12) NOT NULL REFERENCES firmware_device(id),
    owner_account_id bigint NOT NULL REFERENCES user_account(account_id),
    release_id varchar(36) NOT NULL REFERENCES firmware_release(id),
    nonce varchar(32) NOT NULL,
    expires_at timestamptz NOT NULL
);
COMMIT;
