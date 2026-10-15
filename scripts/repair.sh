#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESTART=0
[[ "${1:-}" == '--restart' ]] && RESTART=1
cd "$ROOT_DIR"
docker compose config -q
docker compose ps
if (( RESTART )); then docker compose restart db api web; fi
DB_USER="$(sed -n 's/^POSTGRES_USER=//p' .env | tail -n1)"; DB_NAME="$(sed -n 's/^POSTGRES_DB=//p' .env | tail -n1)"
docker compose exec -T db pg_isready -U "$DB_USER" -d "$DB_NAME"
PORT="$(sed -n 's/^NORD_HUB_WEB_PORT=//p' .env | tail -n1)"; PORT="${PORT:-8080}"
curl --fail --silent --show-error "http://127.0.0.1:$PORT/api/v1/health"
echo; echo 'Diagnóstico concluído.'
