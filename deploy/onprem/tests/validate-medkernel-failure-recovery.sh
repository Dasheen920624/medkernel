#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FRESH_SCRIPT="$ROOT/deploy/onprem/medkernel-fresh-deploy.sh"
DEPLOY_SCRIPT_PATH="$ROOT/deploy/onprem/medkernel-deploy.sh"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

bash -n "$FRESH_SCRIPT"
bash -n "$DEPLOY_SCRIPT_PATH"

# 三个故障点必须全部落入同一强制恢复合同，不能只记录失败或要求人工回滚。
grep -q 'RECOVERY_REASON=.*drop' "$FRESH_SCRIPT"
grep -q 'RECOVERY_REASON=.*publish' "$FRESH_SCRIPT"
grep -q 'RECOVERY_REASON=.*readiness' "$FRESH_SCRIPT"
grep -q 'restore_previous_release' "$FRESH_SCRIPT"
grep -q 'verify_previous_release_readiness' "$FRESH_SCRIPT"

# ERR、INT、TERM 均必须进入恢复处理器；恢复成功前不得解除事务。
grep -q "trap .*handle_failure.*ERR" "$FRESH_SCRIPT"
grep -q "trap .*handle_signal.*INT" "$FRESH_SCRIPT"
grep -q "trap .*handle_signal.*TERM" "$FRESH_SCRIPT"
grep -q 'RECOVERY_ARMED=false' "$FRESH_SCRIPT"

# 普通制品发布也必须保持强制回滚，不允许调用方关闭。
grep -q "trap .*handle_failure.*ERR" "$DEPLOY_SCRIPT_PATH"
grep -q "trap .*handle_signal.*INT" "$DEPLOY_SCRIPT_PATH"
grep -q "trap .*handle_signal.*TERM" "$DEPLOY_SCRIPT_PATH"
grep -q 'restore_previous_database' "$DEPLOY_SCRIPT_PATH"
grep -q 'pg_restore.*--exit-on-error' "$DEPLOY_SCRIPT_PATH"
if grep -q -- '--no-rollback' "$DEPLOY_SCRIPT_PATH"; then
  printf 'artifact deployment still exposes --no-rollback\n' >&2
  exit 1
fi

# 载入真实恢复函数，但不执行 main；全部文件、数据库与 systemd 操作均限制在临时目录和本地替身。
sed '$d' "$FRESH_SCRIPT" > "$TMP_ROOT/fresh-lib.sh"
# shellcheck source=/dev/null
source "$TMP_ROOT/fresh-lib.sh"
trap - ERR INT TERM EXIT
trap 'rm -rf "$TMP_ROOT"' EXIT

APP_HOME="$TMP_ROOT/app"
BACKUP_DIR="$TMP_ROOT/backup"
SYSTEMD_UNIT_PATH="$TMP_ROOT/systemd/medkernel.service"
NGINX_CONF_PATH="$TMP_ROOT/nginx/medkernel.conf"
DEPLOY_COMMAND="$TMP_ROOT/bin/medkernel-deploy"
DATABASE=medkernel
DATABASE_OWNER=medkernel
SERVICE=medkernel
PORT=18080
EXTERNAL_BASE_URL=https://medkernel.test/medkernel
TLS_CA_FILE=""
DATABASE_MUTATION_STARTED=true

mkdir -p "$APP_HOME"/{bin,conf,frontend,lib,var,mock-third-party} \
  "$BACKUP_DIR"/{artifacts,database,evidence} \
  "$(dirname "$SYSTEMD_UNIT_PATH")" "$(dirname "$NGINX_CONF_PATH")" \
  "$(dirname "$DEPLOY_COMMAND")"

printf 'old-jar\n' > "$BACKUP_DIR/artifacts/medkernel.jar"
printf 'old-manifest\n' > "$BACKUP_DIR/artifacts/manifest.properties"
printf 'old-service\n' > "$BACKUP_DIR/artifacts/medkernel.service"
printf 'old-nginx\n' > "$BACKUP_DIR/artifacts/medkernel.nginx.conf"
printf '#!/usr/bin/env bash\n' > "$BACKUP_DIR/artifacts/medkernel-deploy.sh"
printf 'database-dump\n' > "$BACKUP_DIR/database/medkernel.dump"

mkdir -p "$TMP_ROOT/archive/conf" "$TMP_ROOT/archive/frontend/dist" \
  "$TMP_ROOT/archive/var" "$TMP_ROOT/archive/mock-third-party"
printf 'old-config\n' > "$TMP_ROOT/archive/conf/medkernel.env"
printf 'old-frontend\n' > "$TMP_ROOT/archive/frontend/dist/index.html"
printf 'old-runtime\n' > "$TMP_ROOT/archive/var/state"
printf 'old-mock\n' > "$TMP_ROOT/archive/mock-third-party/state"
tar -czf "$BACKUP_DIR/artifacts/conf.tar.gz" -C "$TMP_ROOT/archive" conf
tar -czf "$BACKUP_DIR/artifacts/frontend-dist.tar.gz" -C "$TMP_ROOT/archive/frontend" dist
tar -czf "$BACKUP_DIR/artifacts/runtime-var.tar.gz" -C "$TMP_ROOT/archive" var
tar -czf "$BACKUP_DIR/artifacts/mock-third-party.tar.gz" -C "$TMP_ROOT/archive" mock-third-party

old_jar_sha="$(sha256sum "$BACKUP_DIR/artifacts/medkernel.jar" | awk '{print $1}')"
cat > "$BACKUP_DIR/evidence/pre-clear.properties" <<EOF
jar_present=true
frontend_present=true
manifest_present=true
runtime_var_present=true
mock_third_party_present=true
nginx_present=true
service_unit_present=true
deploy_script_present=true
service_active=active
service_enabled=enabled
old_jar_sha256=$old_jar_sha
EOF
(
  cd "$BACKUP_DIR"
  find artifacts database evidence -type f -print0 |
    sort -z |
    while IFS= read -r -d '' file; do sha256sum "$file"; done > SHA256SUMS
)

run_as_postgres() {
  printf '%s\n' "$*" >> "$TMP_ROOT/database-operations.log"
  if [ "${1:-}" = pg_restore ]; then
    cat >/dev/null
  fi
}
database_query() { return 0; }
systemctl() {
  printf '%s\n' "$*" >> "$TMP_ROOT/systemd-operations.log"
  return 0
}
curl() {
  printf '%s\n' "$*" >> "$TMP_ROOT/readiness-operations.log"
  printf '200'
}
sleep() { :; }

run_failure_case() {
  local reason="$1"
  printf 'candidate-jar\n' > "$APP_HOME/lib/medkernel.jar"
  printf 'candidate-config\n' > "$APP_HOME/conf/medkernel.env"
  printf 'candidate-frontend\n' > "$APP_HOME/frontend/index.html"
  printf 'candidate-service\n' > "$SYSTEMD_UNIT_PATH"
  printf 'candidate-nginx\n' > "$NGINX_CONF_PATH"
  : > "$TMP_ROOT/database-operations.log"
  : > "$TMP_ROOT/systemd-operations.log"
  : > "$TMP_ROOT/readiness-operations.log"
  RECOVERY_REASON="$reason"

  restore_previous_release

  grep -q '^old-jar$' "$APP_HOME/lib/medkernel.jar"
  grep -q '^old-config$' "$APP_HOME/conf/medkernel.env"
  grep -q '^old-frontend$' "$APP_HOME/frontend/dist/index.html"
  grep -q '^old-service$' "$SYSTEMD_UNIT_PATH"
  grep -q '^old-nginx$' "$NGINX_CONF_PATH"
  grep -q 'dropdb --if-exists medkernel' "$TMP_ROOT/database-operations.log"
  grep -q 'createdb --owner=medkernel medkernel' "$TMP_ROOT/database-operations.log"
  grep -q 'pg_restore --exit-on-error --no-owner --no-acl --dbname medkernel' \
    "$TMP_ROOT/database-operations.log"
  grep -q 'psql -X -v ON_ERROR_STOP=1 -d medkernel' "$TMP_ROOT/database-operations.log"
  grep -q 'restart medkernel' "$TMP_ROOT/systemd-operations.log"
  test "$(grep -c 'actuator/health/readiness' "$TMP_ROOT/readiness-operations.log")" -ge 2
  grep -q "recovery_reason=$reason" "$BACKUP_DIR/evidence/recovery.properties"
}

run_failure_case 'drop:database'
run_failure_case 'publish:artifacts'
run_failure_case 'readiness:candidate'

# 快捷发布的候选 JAR 可能已执行 Flyway；验证其真实数据库恢复函数会重建并回灌旧库。
sed '/^ACTION=deploy;/,$d' "$DEPLOY_SCRIPT_PATH" > "$TMP_ROOT/deploy-lib.sh"
(
  # shellcheck source=/dev/null
  set +e
  source "$TMP_ROOT/deploy-lib.sh"
  set -e
  trap - ERR INT TERM EXIT
  DATABASE=medkernel
  DATABASE_OWNER=medkernel
  DATABASE_RESTORE_REQUIRED=true
  LOG="$TMP_ROOT/deploy-restore.log"
  mkdir -p "$TMP_ROOT/deploy-backup/database"
  printf 'deploy-database-dump\n' > "$TMP_ROOT/deploy-backup/database/medkernel.dump"
  : > "$TMP_ROOT/deploy-database-operations.log"
  run_as_postgres() {
    printf '%s\n' "$*" >> "$TMP_ROOT/deploy-database-operations.log"
    if [ "${1:-}" = pg_restore ]; then
      cat >/dev/null
    fi
  }
  database_query() { return 0; }
  restore_previous_database "$TMP_ROOT/deploy-backup"
)
grep -q 'dropdb --if-exists medkernel' "$TMP_ROOT/deploy-database-operations.log"
grep -q 'createdb --owner=medkernel medkernel' "$TMP_ROOT/deploy-database-operations.log"
grep -q 'pg_restore --exit-on-error --no-owner --no-acl --dbname medkernel' \
  "$TMP_ROOT/deploy-database-operations.log"

printf 'onprem failure recovery injection contract passed\n'
