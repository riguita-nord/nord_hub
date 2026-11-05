# Nord Workspace

Java 21 productivity workspace with two separate JVM processes, Core and internal Admin, connected by a loopback secret.

## V1 foundation

- Vertical Discord-like workspace icon rail. No selector dropdown.
- Explorer-style folders. Clicking a folder navigates to a dedicated project page.
- Project tasks, Git repository links, HTTPS document links, members and team chat.
- First successful registration becomes PLATFORM_OWNER; subsequent users are USER.
- Session-cookie login, SQLite, PBKDF2 password hashing.
- Admin statistics and user listing limited to Platform Owner; Admin port is loopback-only.

## Install on Ubuntu/Debian

From the repository checkout:

    sudo bash install.sh

The installer fetches OpenJDK 21, Maven, OpenSSL and curl via apt, builds the Java jar, provisions a dedicated service account and creates two systemd services.

- Core: http://127.0.0.1:8091
- Admin: http://127.0.0.1:8092 (never expose to internet)
- DB: /var/lib/nord-workspace/workspace.db
- Configuration: /etc/nord-workspace/workspace.env
- JAR: /opt/nord-workspace/nord-workspace.jar

Before public use: configure a reverse proxy with HTTPS to Core and set NORD_PUBLIC_ORIGIN=https://yourdomain in workspace.env. Restart both services after config changes.

    sudo systemctl restart nord-workspace-core nord-workspace-admin
    sudo journalctl -u nord-workspace-core -f

## Local development

    mvn package
    export NORD_ADMIN_SECRET="$(openssl rand -hex 48)"
    java -cp target/nord-workspace-0.1.0.jar com.nordlab.workspace.CoreServer

In a second terminal using the same environment variables:

    java -cp target/nord-workspace-0.1.0.jar com.nordlab.workspace.AdminServer

Open http://127.0.0.1:8091.

## Security cautions

The first-registration grant assumes a privately controlled initial deployment. Restrict initial access until the intended owner registers.

This is a working foundation but not production-certified. Missing: rate limiting and lockouts, MFA, backup and restore, schema migrations, extensive automated tests, full audit history and threat-model review.

Git and documents are links, not hosted repositories or file uploads. Chat uses polling, not WebSockets.
