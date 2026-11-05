#!/usr/bin/env bash
set -Eeuo pipefail
if [[ $EUID -ne 0 ]]; then echo "Run with sudo"; exit 1; fi
if ! command -v apt-get >/dev/null; then echo "Only Debian/Ubuntu supported"; exit 1; fi
ROOT="$(pwd)"
APP="/opt/nord-workspace"
DATA="/var/lib/nord-workspace"
CONFIG="/etc/nord-workspace"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y openjdk-21-jdk-headless maven ca-certificates openssl curl
(cd "$ROOT" && mvn -B -DskipTests package)
JAR="$ROOT/target/nord-workspace-0.1.0.jar"
test -s "$JAR" || { echo "Missing jar"; exit 1; }
id -u nord-workspace >/dev/null 2>&1 || useradd --system --home "$DATA" --shell /usr/sbin/nologin nord-workspace
install -d -m 0750 -o nord-workspace -g nord-workspace "$APP" "$DATA"
install -d -m 0750 -o root -g nord-workspace "$CONFIG"
install -m 0644 "$JAR" "$APP/nord-workspace.jar"
if [[ ! -e "$CONFIG/workspace.env" ]]; then
 SECRET="$(openssl rand -hex 48)"
 cat > "$CONFIG/workspace.env" <<EOF
NORD_DB=$DATA/workspace.db
NORD_HOST=127.0.0.1
NORD_PORT=8091
NORD_ADMIN_PORT=8092
NORD_ADMIN_SECRET=$SECRET
# NORD_PUBLIC_ORIGIN=https://workspace.example.com
EOF
 chown root:nord-workspace "$CONFIG/workspace.env"
 chmod 0640 "$CONFIG/workspace.env"
fi
cat >/etc/systemd/system/nord-workspace-core.service <<'EOF'
[Unit]
Description=Nord Workspace Core
After=network-online.target
Wants=network-online.target
[Service]
Type=simple
User=nord-workspace
Group=nord-workspace
WorkingDirectory=/var/lib/nord-workspace
EnvironmentFile=/etc/nord-workspace/workspace.env
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=65 -cp /opt/nord-workspace/nord-workspace.jar com.nordlab.workspace.CoreServer
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/nord-workspace
UMask=0077
[Install]
WantedBy=multi-user.target
EOF
cat >/etc/systemd/system/nord-workspace-admin.service <<'EOF'
[Unit]
Description=Nord Workspace Internal Admin
After=nord-workspace-core.service
Requires=nord-workspace-core.service
[Service]
Type=simple
User=nord-workspace
Group=nord-workspace
WorkingDirectory=/var/lib/nord-workspace
EnvironmentFile=/etc/nord-workspace/workspace.env
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=40 -cp /opt/nord-workspace/nord-workspace.jar com.nordlab.workspace.AdminServer
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/nord-workspace
UMask=0077
[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now nord-workspace-core.service
for i in $(seq 1 15); do
 if curl --silent --fail http://127.0.0.1:8091/api/health >/dev/null; then break; fi
 sleep 1
done
systemctl enable --now nord-workspace-admin.service
curl --fail --silent http://127.0.0.1:8091/api/health || { echo "Core healthcheck failed"; exit 1; }
echo
echo "Nord Workspace installed locally on http://127.0.0.1:8091"
echo "Configure HTTPS reverse proxy and NORD_PUBLIC_ORIGIN before public access."
echo "First registration receives PLATFORM_OWNER."
