# Nord Hub

Developer workspace for focused project delivery.

## Development preview

The repository was empty when implementation began. Phase 1 is being built on the `frontend/phase-1-workspace` branch. The current version is a frontend preview with a simulated service layer; it is not production-ready and does not yet persist data to a server.

### Run locally

No build step is required. Serve this directory with any static web server, for example:

```bash
python3 -m http.server 8080
```

Then open `http://localhost:8080`. The demo stores its sample workspace in browser `localStorage`; clearing browser storage resets the demo data.

## Included in this frontend stage

- Work Center, focus project, today's tasks, and resume note
- Project and task lists with create, filter, and completion flows
- Calendar and rule-based weekly planner
- Idea Inbox, quick capture, emergency capture, and idea draw
- Development notes and resume points
- Focus mode with a 25-minute timer
- Project releases, team overview, notifications, and Gateway status panels
- Responsive dark/light UI, keyboard shortcuts, and a service boundary in `src/app.js`

## Not implemented yet

The frontend uses seeded demo data only. Java 21 backend, PostgreSQL, migrations, authentication/RBAC, real API persistence, operational Gateway, installer, GitHub synchronization, AI planning, and Nord SaaS integration remain pending. English translation is also pending; the first UI pass is in Portuguese (Portugal).

See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for the phase checklist and known verification limits.
