-- =====================================================================
-- V3__add_auth_stage2.sql
-- Adds support for: Google sign-in, email verification, and password
-- reset tokens (Stage 1 auth completion).
-- =====================================================================

ALTER TABLE app_user
    ADD COLUMN auth_provider varchar(20) NOT NULL DEFAULT 'LOCAL';

-- Google-only accounts have no local password.
ALTER TABLE app_user
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE app_user
    ADD COLUMN google_id varchar(255);

ALTER TABLE app_user
    ADD COLUMN email_verified_at timestamptz;

-- Local accounts registered before this migration are treated as already
-- verified so existing users are not suddenly locked out of anything that
-- may later gate on verification status.
UPDATE app_user SET email_verified_at = created_at WHERE email_verified_at IS NULL;

CREATE UNIQUE INDEX uq_app_user_google_id ON app_user (google_id) WHERE google_id IS NOT NULL;

-- Password reset tokens: only a hash of the token is stored, never the
-- plaintext value. Single-use (used_at) and time-limited (expires_at).
CREATE TABLE password_reset_token (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash    varchar(255) NOT NULL,
    expires_at    timestamptz NOT NULL,
    used_at       timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_password_reset_token_user_id ON password_reset_token (user_id);
CREATE UNIQUE INDEX uq_password_reset_token_hash ON password_reset_token (token_hash);

-- Email verification tokens: same single-use/expiring/hash-only pattern.
CREATE TABLE email_verification_token (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash    varchar(255) NOT NULL,
    expires_at    timestamptz NOT NULL,
    used_at       timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_email_verification_token_user_id ON email_verification_token (user_id);
CREATE UNIQUE INDEX uq_email_verification_token_hash ON email_verification_token (token_hash);


