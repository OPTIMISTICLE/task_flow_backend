CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_user_role CHECK (role IN ('MANAGER', 'WORKER'))
);

CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(4000),
    due_date TIMESTAMP WITH TIME ZONE,
    progress_status VARCHAR(20) NOT NULL,
    creator_id UUID NOT NULL REFERENCES app_users(id),
    assignee_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_task_progress CHECK (progress_status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_tasks_creator ON tasks(creator_id, created_at DESC);
CREATE INDEX idx_tasks_assignee ON tasks(assignee_id, created_at DESC);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);

CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    uploaded_by_id UUID NOT NULL REFERENCES app_users(id),
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(100) NOT NULL UNIQUE,
    storage_path VARCHAR(1000) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_attachments_task ON attachments(task_id, uploaded_at);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID,
    actor_email VARCHAR(254),
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80),
    resource_id VARCHAR(100),
    outcome VARCHAR(20) NOT NULL,
    details VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_events_actor ON audit_events(actor_id, occurred_at DESC);
