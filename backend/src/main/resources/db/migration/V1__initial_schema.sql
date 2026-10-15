CREATE TABLE hub_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    global_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    created_by UUID NOT NULL REFERENCES hub_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspace_members (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES hub_users(id) ON DELETE CASCADE,
    role VARCHAR(24) NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_workspace_member UNIQUE (workspace_id, user_id)
);
CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);

CREATE TABLE workspace_invitations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    role VARCHAR(24) NOT NULL CHECK (role IN ('EDITOR', 'VIEWER')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES hub_users(id),
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_workspace_invitations_pending ON workspace_invitations(workspace_id, expires_at) WHERE accepted_at IS NULL;

CREATE TABLE workspace_data (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    record_type VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID NOT NULL REFERENCES hub_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_workspace_data_list ON workspace_data(workspace_id, record_type, updated_at DESC);

CREATE TABLE platform_maintenance (
    id VARCHAR(16) PRIMARY KEY,
    mode VARCHAR(16) NOT NULL CHECK (mode IN ('NORMAL', 'READ_ONLY', 'FULL')),
    message VARCHAR(500),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    updated_by UUID REFERENCES hub_users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO platform_maintenance(id, mode, message) VALUES ('primary', 'NORMAL', NULL);

CREATE TABLE platform_releases (
    id UUID PRIMARY KEY,
    version VARCHAR(40) NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    changelog TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES hub_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gateway_audit_log (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES hub_users(id),
    action VARCHAR(80) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_gateway_audit_created ON gateway_audit_log(created_at DESC);

