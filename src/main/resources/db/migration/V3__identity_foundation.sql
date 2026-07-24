ALTER TABLE app_users ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE app_users ADD COLUMN pending_email VARCHAR(254);
ALTER TABLE app_users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;

UPDATE app_users
SET email_verified_at = created_at,
    account_status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END;

DROP INDEX idx_app_users_directory;
ALTER TABLE app_users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE app_users DROP COLUMN active;

ALTER TABLE app_users ADD CONSTRAINT chk_user_account_status
    CHECK (account_status IN ('PENDING', 'ACTIVE', 'INACTIVE'));

CREATE UNIQUE INDEX uq_app_users_pending_email ON app_users (pending_email);
CREATE INDEX idx_app_users_directory ON app_users (account_status, role, created_at DESC);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    user_agent VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(120)
);
CREATE INDEX idx_auth_sessions_user ON auth_sessions (user_id, created_at DESC);
CREATE INDEX idx_auth_sessions_expiry ON auth_sessions (expires_at);

CREATE TABLE auth_action_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    pending_email VARCHAR(254),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_auth_token_purpose CHECK (purpose IN ('INVITATION', 'PASSWORD_RESET', 'EMAIL_CHANGE'))
);
CREATE INDEX idx_auth_action_tokens_user ON auth_action_tokens (user_id, purpose, created_at DESC);
CREATE INDEX idx_auth_action_tokens_expiry ON auth_action_tokens (expires_at);

CREATE TABLE mfa_totp_credentials (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    encrypted_secret VARCHAR(512) NOT NULL,
    nonce VARCHAR(64) NOT NULL,
    last_used_step BIGINT,
    enabled_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE mfa_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_mfa_recovery_codes_user ON mfa_recovery_codes (user_id, used_at);

CREATE TABLE auth_login_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_auth_login_challenges_expiry ON auth_login_challenges (expires_at);

CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    recipient VARCHAR(254) NOT NULL,
    template_name VARCHAR(50) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    text_body TEXT NOT NULL,
    html_body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provider_message_id VARCHAR(120),
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_email_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
);
CREATE INDEX idx_email_outbox_dispatch ON email_outbox (status, next_attempt_at);
