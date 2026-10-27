CREATE TABLE auth_sessions (
    id VARCHAR(36) PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES user_account(account_id),
    refresh_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    management BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_auth_sessions_account ON auth_sessions(account_id);
