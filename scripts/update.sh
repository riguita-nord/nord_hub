#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
BACKUP="$(./scripts/backup.sh | sed -n 's/^Backup PostgreSQL verificado: //p')"
[[ -f "$BACKUP" ]] || { echo 'O backup pré-atualização falhou; atualização cancelada.' >&2; exit 1; }
OLD_ID="$(docker compose images -q api | head -n1 || true)"
OLD_TAG=''
if [[ -n "$OLD_ID" ]]; then OLD_TAG="$(docker image inspect "$OLD_ID" --format '{{index .RepoTags 0}}' 2>/dev/null || true)"; fi
if ! docker compose build api || ! docker compose up -d --remove-orphans; then
  echo 'Atualização falhou. O backup da base de dados foi preservado.' >&2
  if [[ -n "$OLD_ID" && -n "$OLD_TAG" ]]; then docker image tag "$OLD_ID" "$OLD_TAG" || true; docker compose up -d --no-deps api || true; fi
  echo "Se a migration alterou dados, revê o backup: $BACKUP" >&2
  exit 1
fi
PORT="$(sed -n 's/^NORD_HUB_WEB_PORT=//p' .env | tail -n1)"; PORT="${PORT:-8080}"
for attempt in $(seq 1 45); do
  if curl --fail --silent "http://127.0.0.1:$PORT/api/v1/health" >/dev/null; then echo "Atualização validada. Backup disponível em $BACKUP"; exit 0; fi
  sleep 2
done
echo 'A API não ficou saudável após atualizar.' >&2
if [[ -n "$OLD_ID" && -n "$OLD_TAG" ]]; then docker image tag "$OLD_ID" "$OLD_TAG"; docker compose up -d --no-deps api; echo "A imagem anterior foi reposta. Confirma a compatibilidade da base de dados; backup: $BACKUP" >&2; fi
exit 1
