ALTER TABLE app_users DROP CONSTRAINT chk_user_role;

ALTER TABLE app_users ADD COLUMN job_title VARCHAR(120);
ALTER TABLE app_users ADD COLUMN department VARCHAR(120);
ALTER TABLE app_users ADD COLUMN phone_number VARCHAR(20);
ALTER TABLE app_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE app_users
SET email = LOWER(email),
    updated_at = created_at;

ALTER TABLE app_users ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE app_users
    ADD CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'MANAGER', 'WORKER'));

CREATE INDEX idx_app_users_directory ON app_users (active, role, created_at DESC);
