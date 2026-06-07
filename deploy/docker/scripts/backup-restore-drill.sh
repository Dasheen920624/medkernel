#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"
require_env_file
require_docker

require_safe_identifier() {
  local value="$1"
  [[ "$value" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || fail "unsafe PostgreSQL identifier: $value"
}

mkdir -p "$RUNTIME_ROOT/backups/drills"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="${1:-$RUNTIME_ROOT/backups/drills/medkernel-drill-$TIMESTAMP.dump}"
DRILL_DB="${MEDKERNEL_BACKUP_DRILL_DB:-medkernel_restore_drill}"
EVIDENCE_FILE="$RUNTIME_ROOT/backups/drills/restore-drill-$TIMESTAMP.txt"
LATEST_EVIDENCE_FILE="$RUNTIME_ROOT/backups/drills/latest-restore-drill.properties"
LATEST_EVIDENCE_TMP="$LATEST_EVIDENCE_FILE.tmp"

require_safe_identifier "$DRILL_DB"
case "$DRILL_DB" in
  "$MEDKERNEL_DB_NAME"|postgres|template0|template1)
    fail "backup drill database must not be an existing protected database: $DRILL_DB"
    ;;
esac

cleanup_drill_db() {
  core_compose exec -T postgres \
    psql -U "$MEDKERNEL_DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 \
      -c "DROP DATABASE IF EXISTS $DRILL_DB;" >/dev/null 2>&1 || true
}
trap cleanup_drill_db EXIT

"$SCRIPT_DIR/backup.sh" "$BACKUP_FILE"
verify_checksum "$BACKUP_FILE" >/dev/null

cleanup_drill_db
core_compose exec -T postgres \
  createdb -U "$MEDKERNEL_DB_USERNAME" "$DRILL_DB"

core_compose exec -T postgres \
  pg_restore -U "$MEDKERNEL_DB_USERNAME" -d "$DRILL_DB" --no-owner < "$BACKUP_FILE"

MIGRATION_COUNT="$(core_compose exec -T postgres \
  psql -U "$MEDKERNEL_DB_USERNAME" -d "$DRILL_DB" -At -v ON_ERROR_STOP=1 \
    -c "SELECT COUNT(*) FROM flyway_schema_history;")"
test "${MIGRATION_COUNT:-0}" -gt 0 || fail "restore drill did not recover flyway_schema_history"

{
  printf 'status=SUCCESS\n'
  printf 'completed_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'backup_file=%s\n' "$BACKUP_FILE"
  printf 'checksum_file=%s.sha256\n' "$BACKUP_FILE"
  printf 'drill_database=%s\n' "$DRILL_DB"
  printf 'flyway_schema_history_rows=%s\n' "$MIGRATION_COUNT"
  printf 'rpo=%s\n' "${MEDKERNEL_BACKUP_RPO:-未配置}"
  printf 'rto=%s\n' "${MEDKERNEL_BACKUP_RTO:-未配置}"
} > "$EVIDENCE_FILE"
cp "$EVIDENCE_FILE" "$LATEST_EVIDENCE_TMP"
mv "$LATEST_EVIDENCE_TMP" "$LATEST_EVIDENCE_FILE"

printf 'PostgreSQL restore drill completed with isolated database: %s\n' "$DRILL_DB"
printf 'restore drill evidence: %s\n' "$EVIDENCE_FILE"
printf 'latest restore drill evidence: %s\n' "$LATEST_EVIDENCE_FILE"
