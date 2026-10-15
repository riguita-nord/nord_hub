#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${NORD_HUB_BACKUP_DIR:-$ROOT_DIR/backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"; chmod 700 "$BACKUP_DIR"
OUT="${1:-$BACKUP_DIR/nord_hub-$STAMP.dump}"
[[ ! -e "$OUT" ]] || { echo "O ficheiro já existe: $OUT" >&2; exit 2; }
cd "$ROOT_DIR"
docker compose exec -T db sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$OUT"
chmod 600 "$OUT"
docker compose exec -T db pg_restore --list < "$OUT" >/dev/null
echo "Backup PostgreSQL verificado: $OUT"
