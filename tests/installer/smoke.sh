#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
bash -n "$ROOT_DIR/install.sh" "$ROOT_DIR"/scripts/*.sh
for script in install.sh scripts/backup.sh scripts/restore.sh scripts/update.sh scripts/repair.sh; do
  bash -n "$ROOT_DIR/$script"
done
"$ROOT_DIR/install.sh" --help >/dev/null
if [[ $EUID -eq 0 ]]; then "$ROOT_DIR/install.sh" --dry-run --skip-packages >/dev/null; else sudo "$ROOT_DIR/install.sh" --dry-run --skip-packages >/dev/null; fi
echo 'Installer smoke checks passed.'
