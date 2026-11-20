# Nord Hub — Home Balanced V0.1

## Scope of this increment
This increment upgrades the **existing** Java 21/SQLite Nord Workspace codebase in place. It does not migrate the application to Quarkus, React, PostgreSQL or organization-level tenancy. Those require a separate, versioned migration.

## API
- `GET /api/home?workspaceId=<id>` authenticated, checks workspace membership; responds with `summary`, `tasks`, `projects`, `activity`, `attention`, `infrastructure`.
- `GET /api/home/preferences?workspaceId=<id>` returns user/workspace-specific Balanced widget preferences.
- `PATCH /api/home/preferences?workspaceId=<id>` with `{"layout":"balanced","hiddenWidgets":"activity,projects"}` saves preferences. Modifying requests require an Origin header matching the app host (existing security policy).
- Tasks are still changed through `PATCH /api/tasks/{id}` after existing project membership validation.

## Security and data semantics
Every task, project and event query joins `project_members` and scopes to the requested workspace; the workspace must also belong to the authenticated user. Existing app supports workspaces rather than separate organizations: **cross-organization isolation is not yet provided**.
Infrastructure reports `not_connected` with null counts until a trusted provider exists. Never replace these values with mock statuses in production.
Activity currently represents **task creation events only**; task completion history requires an audit/event table.
Preferences hide widgets for presentation only and never grant API access.

## Operations
Existing `sudo bash install.sh` packages and deploys this version. As documented in README, the installer enables HTTP testing on a trusted LAN and is **not production-safe for public exposure**. Keep existing `/var/lib/nord-workspace/workspace.db` backups before updating.

## Verification checklist
- Maven package on Java 21
- Existing login / workspace / projects / tasks routes unchanged
- Workspace A user cannot GET Home/preferences for workspace B
- Project membership limits Home tasks/project listings
- Empty workspace shows proper no-data UX
- Preference changes persist through reload and are isolated per user/workspace
- A disconnected provider always shows Not Connected rather than Online
- Changing workspaces never displays a late response from the previous workspace
- Smartphone and tablet layout regression checks

## Next milestones
1. Full tenancy migration: `organizations`, `organization_members`, workspace `organization_id`, versioned migrations, cross-tenant isolation tests.
2. Quarkus + PostgreSQL modular backend migration with compatibility and backup/restore strategy.
3. React + TypeScript dashboard and reusable design system.
4. Proxmox read-only provider with restricted credentials, background polling and freshness thresholds.
5. Audit event timeline, activity pagination, SSE, MFA, rate limiting, deployment hardening.
