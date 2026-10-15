#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT_DIR/.env"
DRY_RUN=0
SKIP_PACKAGES=0

usage(){ cat <<'HELP'
Nord Hub installer for Ubuntu 24.04
Usage: sudo ./install.sh [--dry-run] [--skip-packages]

--dry-run       Validate the host and print planned actions without changing it.
--skip-packages Use already installed Docker Engine and Compose plugin.
HELP
}
for arg in "$@"; do case "$arg" in --dry-run) DRY_RUN=1;; --skip-packages) SKIP_PACKAGES=1;; -h|--help) usage; exit 0;; *) echo "Argumento desconhecido: $arg" >&2; usage; exit 2;; esac; done
run(){ if (( DRY_RUN )); then printf '+ '; printf '%q ' "$@"; printf '\n'; else "$@"; fi; }

if [[ $EUID -ne 0 ]]; then echo 'Executa com sudo: sudo ./install.sh' >&2; exit 1; fi
if [[ ! -r /etc/os-release ]]; then echo 'Não foi possível identificar o sistema operativo.' >&2; exit 1; fi
. /etc/os-release
if [[ "${ID:-}" != ubuntu || "${VERSION_ID:-}" != 24.04 ]]; then echo 'Este instalador suporta Ubuntu 24.04.' >&2; exit 1; fi
if (( DRY_RUN )); then
  echo 'Validação concluída: Ubuntu 24.04 e permissões root.'
  (( SKIP_PACKAGES )) || echo 'Instalaria docker.io, docker-compose-v2, openssl e curl.'
  [[ -e "$ENV_FILE" ]] && echo '.env existente será preservado.' || echo 'Criaria .env com segredos únicos e permissões 600.'
  echo 'Iniciaria os serviços e aguardaria a API.'
  exit 0
fi

if (( ! SKIP_PACKAGES )); then
  run apt-get update
  run env DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 openssl curl
fi
command -v docker >/dev/null || { echo 'Docker não está instalado.' >&2; exit 1; }
docker compose version >/dev/null || { echo 'Docker Compose plugin não está instalado.' >&2; exit 1; }
run systemctl enable --now docker

if [[ ! -f "$ENV_FILE" ]]; then
  read -r -p 'Email do administrador Nord Hub: ' ADMIN_EMAIL
  [[ "$ADMIN_EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] || { echo 'Email inválido.' >&2; exit 1; }
  DB_PASSWORD="$(openssl rand -hex 32)"
  JWT_SECRET="$(openssl rand -hex 48)"
  SETUP_TOKEN="$(openssl rand -hex 32)"
  cat > "$ENV_FILE" <<ENV
POSTGRES_DB=nord_hub
POSTGRES_USER=nord_hub
POSTGRES_PASSWORD=$DB_PASSWORD
NORD_HUB_JWT_SECRET=$JWT_SECRET
NORD_HUB_SETUP_TOKEN=$SETUP_TOKEN
NORD_HUB_ADMIN_EMAILS=$ADMIN_EMAIL
NORD_HUB_WEB_PORT=8080
NORD_HUB_CORS_ORIGINS=http://localhost:8080
ENV
  chmod 600 "$ENV_FILE"
else
  echo '.env já existe; configuração e segredos preservados.'
  SETUP_TOKEN="$(sed -n 's/^NORD_HUB_SETUP_TOKEN=//p' "$ENV_FILE" | tail -n1)"
  if [[ -z "$SETUP_TOKEN" ]]; then SETUP_TOKEN="$(openssl rand -hex 32)"; printf '\nNORD_HUB_SETUP_TOKEN=%s\n' "$SETUP_TOKEN" >> "$ENV_FILE"; chmod 600 "$ENV_FILE"; fi
fi

cd "$ROOT_DIR"
run docker compose up -d --build --remove-orphans
PORT="$(sed -n 's/^NORD_HUB_WEB_PORT=//p' "$ENV_FILE" | tail -n1)"; PORT="${PORT:-8080}"
for attempt in $(seq 1 60); do
  if curl --fail --silent "http://127.0.0.1:$PORT/api/v1/health" >/dev/null; then
    echo "Nord Hub está disponível em http://<servidor>:$PORT"
    [[ -n "${SETUP_TOKEN:-}" ]] && printf 'Token único para criar o primeiro administrador: %s\nGuarda-o e remove NORD_HUB_SETUP_TOKEN do .env após a configuração.\n' "$SETUP_TOKEN"
    exit 0
  fi
  sleep 2
done
echo 'A API não ficou disponível. Diagnóstico: docker compose ps && docker compose logs --tail=100 api db' >&2
exit 1
