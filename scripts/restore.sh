#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP="${1:-}"
[[ -n "$BACKUP" && -f "$BACKUP" ]] || { echo 'Uso: sudo ./scripts/restore.sh backups/nord_hub-AAAA....dump' >&2; exit 2; }
[[ "${2:-}" == '--confirm' ]] || { echo 'Restaurar substitui os dados atuais. Repete com --confirm após fazer um backup atual.' >&2; exit 2; }
cd "$ROOT_DIR"
docker compose exec -T db pg_restore --clean --if-exists --no-owner --exit-on-error -U "$(sed -n 's/^POSTGRES_USER=//p' .env | tail -n1)" -d "$(sed -n 's/^POSTGRES_DB=//p' .env | tail -n1)" < "$BACKUP"
docker compose restart api
echo 'Restauro concluído. Confirma o estado em /api/v1/health.'
