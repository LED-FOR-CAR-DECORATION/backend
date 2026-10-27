CREATE TABLE google_identities (
    subject VARCHAR(255) PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE REFERENCES user_account(account_id)
);
