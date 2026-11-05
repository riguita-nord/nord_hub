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

- Core (LAN test mode): http://SERVER_LAN_IP:8091 (listens on 0.0.0.0)
- Admin: http://127.0.0.1:8092 (never expose to internet)
- DB: /var/lib/nord-workspace/workspace.db
- Configuration: /etc/nord-workspace/workspace.env
- JAR: /opt/nord-workspace/nord-workspace.jar

For **LAN testing**, open http://SERVER_LAN_IP:8091 in a browser on a different computer. The VM/server requires **terminal only**; no browser is installed or needed. The install script sets NORD_HOST=0.0.0.0 and clears NORD_PUBLIC_ORIGIN to allow HTTP test mode. Core is available on TCP port 8091; Admin stays on 127.0.0.1:8092. Find LAN IP with `hostname -I` or `ip -4 addr`. If UFW is enabled, permit port 8091 only from the actual trusted LAN subnet (example: `sudo ufw allow from 192.168.1.0/24 to any port 8091 proto tcp`; replace with your subnet). Do not forward 8091 on the router. For production/public Internet access, configure HTTPS and set NORD_PUBLIC_ORIGIN to the HTTPS origin; this HTTP mode is **not suitable for the Internet**.

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

## Updating an existing installation for LAN tests

    cd ~/nord_hub
    git pull origin main
    sudo bash install.sh
    hostname -I
    sudo ss -ltnp | grep -E ':(8091|8092)'
    curl http://127.0.0.1:8091/api/health

Open `http://LAN_IP:8091` from your own desktop/laptop browser (not from the VM). HTTP sends login credentials and sessions without transport encryption, so only test on a trusted isolated LAN and use a disposable test password.
