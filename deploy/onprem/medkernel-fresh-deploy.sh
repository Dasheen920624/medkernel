#!/usr/bin/env bash
# MedKernel PostgreSQL 全新发布脚本。
# 只用于明确要求抛弃旧运行数据的首次/重建发布：先备份并隔离恢复验证，再清库、清旧制品并发布显式候选。
set -euo pipefail

APP_HOME="${MEDKERNEL_APP_HOME:-/zoesoft/medkernel}"
BACKUP_ROOT="$APP_HOME/backups"
ENV_FILE="$APP_HOME/conf/medkernel.env"
SERVICE="${MEDKERNEL_SERVICE:-medkernel}"
DATABASE="${MEDKERNEL_DATABASE:-medkernel}"
DATABASE_OWNER="${MEDKERNEL_DATABASE_OWNER:-medkernel}"
DEPLOY_COMMAND="${MEDKERNEL_DEPLOY_COMMAND:-/usr/local/bin/medkernel-deploy}"
PORT=""

JAR=""
FRONTEND=""
SOURCE=""
EXPECTED_FLYWAY_VERSION=""
CONFIRM_FRESH=0
CONFIRM_DATABASE=""
PRUNE_OLD_BACKUPS=0
CONFIRM_PRUNE_BACKUPS=0

BACKUP_DIR=""
STAGED_JAR=""
STAGED_FRONTEND=""
RESTORE_DATABASE=""
DESTRUCTIVE_ACTION_PERFORMED=false

info() { printf '[*] %s\n' "$*"; }
ok() { printf '[OK] %s\n' "$*"; }
die() { printf '[X] %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
MedKernel PostgreSQL 全新发布

用法：
  medkernel-fresh-deploy.sh \
    --jar /path/to/medkernel.jar \
    --frontend /path/to/dist.tar.gz \
    --source <完整提交哈希> \
    --expected-flyway-version <版本> \
    --confirm-fresh \
    --confirm-database medkernel \
    [--prune-old-backups --confirm-prune-backups]

安全边界：
  1. 未显式确认数据库名时拒绝运行。
  2. 先完成数据库备份与隔离恢复验证，之后才允许停服和清库。
  3. 清库后只发布显式指定候选，不从 incoming 自动发现旧包。
  4. 不自动回滚旧程序包或旧数据库；失败时保留本次备份与证据。
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --jar) JAR="${2:-}"; shift 2 ;;
    --frontend) FRONTEND="${2:-}"; shift 2 ;;
    --source) SOURCE="${2:-}"; shift 2 ;;
    --expected-flyway-version) EXPECTED_FLYWAY_VERSION="${2:-}"; shift 2 ;;
    --confirm-fresh) CONFIRM_FRESH=1; shift ;;
    --confirm-database) CONFIRM_DATABASE="${2:-}"; shift 2 ;;
    --prune-old-backups) PRUNE_OLD_BACKUPS=1; shift ;;
    --confirm-prune-backups) CONFIRM_PRUNE_BACKUPS=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数：$1" ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

run_as_postgres() {
  (
    cd /tmp
    sudo -u postgres "$@"
  )
}

database_query() {
  local database_name="$1"
  local sql="$2"
  run_as_postgres psql -X -v ON_ERROR_STOP=1 -Atq -d "$database_name" -c "$sql"
}

cleanup_restore_database() {
  if [ -n "$RESTORE_DATABASE" ]; then
    run_as_postgres dropdb --if-exists "$RESTORE_DATABASE" >/dev/null 2>&1 || true
  fi
}
trap cleanup_restore_database EXIT

validate_inputs() {
  [ "$(id -u)" -eq 0 ] || die "需要 root 权限"
  [ "$CONFIRM_FRESH" -eq 1 ] || die "缺少 --confirm-fresh"
  [ "$CONFIRM_DATABASE" = "$DATABASE" ] || die "--confirm-database 必须精确等于 $DATABASE"
  [ -n "$SOURCE" ] || die "缺少 --source"
  [ -n "$EXPECTED_FLYWAY_VERSION" ] || die "缺少 --expected-flyway-version"
  [[ "$DATABASE" =~ ^[a-zA-Z0-9_]+$ ]] || die "数据库名包含非法字符"
  [[ "$DATABASE_OWNER" =~ ^[a-zA-Z0-9_]+$ ]] || die "数据库 owner 包含非法字符"
  [[ "$SOURCE" =~ ^[a-fA-F0-9]{40}$ ]] || die "--source 必须是 40 位提交哈希"
  [[ "$EXPECTED_FLYWAY_VERSION" =~ ^[0-9]+$ ]] || die "Flyway 版本必须是正整数"
  [ -f "$JAR" ] || die "后端候选不存在：$JAR"
  [ -f "$FRONTEND" ] || die "前端候选不存在：$FRONTEND"
  [ -f "$ENV_FILE" ] || die "环境文件不存在：$ENV_FILE"
  [ -x "$DEPLOY_COMMAND" ] || die "发布命令不可执行：$DEPLOY_COMMAND"
  [ "$(stat -c %s "$JAR")" -gt 1000000 ] || die "后端候选体积异常"
  [ "$(tar -tzf "$FRONTEND" | grep -c '^dist/index.html$')" -gt 0 ] ||
    die "前端候选缺少 dist/index.html"
  if command -v unzip >/dev/null 2>&1; then
    [ "$(unzip -l "$JAR" 2>/dev/null | grep -c 'BOOT-INF/')" -gt 0 ] || die "后端候选不是 Spring Boot 包"
  fi
  if [ "$PRUNE_OLD_BACKUPS" -eq 1 ] && [ "$CONFIRM_PRUNE_BACKUPS" -ne 1 ]; then
    die "清理旧备份还需要 --confirm-prune-backups"
  fi
  for command_name in pg_dump pg_restore psql createdb dropdb sha256sum tar curl systemctl; do
    require_command "$command_name"
  done
  PORT="$(sed -n 's/^SERVER_PORT=//p' "$ENV_FILE" | head -1 | tr -d '\r')"
  PORT="${PORT:-18080}"
  [[ "$PORT" =~ ^[0-9]+$ ]] || die "SERVER_PORT 不是有效端口"
}

prepare_backup_directory() {
  local timestamp safe_source
  timestamp="$(date '+%Y%m%d-%H%M%S')"
  safe_source="${SOURCE:0:12}"
  BACKUP_DIR="$BACKUP_ROOT/p9-fresh-preclear-${safe_source}-${timestamp}"
  mkdir -p "$BACKUP_DIR"/{artifacts,database,evidence,staged}
  chmod 700 "$BACKUP_DIR"
  STAGED_JAR="$BACKUP_DIR/staged/medkernel.jar"
  STAGED_FRONTEND="$BACKUP_DIR/staged/dist.tar.gz"
}

create_backup() {
  info "创建清库前备份：$BACKUP_DIR"
  install -m 600 "$JAR" "$STAGED_JAR"
  install -m 600 "$FRONTEND" "$STAGED_FRONTEND"

  [ -f "$APP_HOME/lib/medkernel.jar" ] &&
    install -m 600 "$APP_HOME/lib/medkernel.jar" "$BACKUP_DIR/artifacts/medkernel.jar"
  [ -d "$APP_HOME/frontend/dist" ] &&
    tar --xattrs --acls -czf "$BACKUP_DIR/artifacts/frontend-dist.tar.gz" \
      -C "$APP_HOME/frontend" dist
  tar --xattrs --acls -czf "$BACKUP_DIR/artifacts/conf.tar.gz" -C "$APP_HOME" conf
  [ -f "$APP_HOME/manifest.properties" ] &&
    install -m 600 "$APP_HOME/manifest.properties" "$BACKUP_DIR/artifacts/manifest.properties"
  [ -d "$APP_HOME/var" ] &&
    tar --xattrs --acls -czf "$BACKUP_DIR/artifacts/runtime-var.tar.gz" -C "$APP_HOME" var
  [ -d "$APP_HOME/mock-third-party" ] &&
    tar --xattrs --acls -czf "$BACKUP_DIR/artifacts/mock-third-party.tar.gz" \
      -C "$APP_HOME" mock-third-party
  [ -f /etc/nginx/conf.d/medkernel.conf ] &&
    install -m 600 /etc/nginx/conf.d/medkernel.conf "$BACKUP_DIR/artifacts/medkernel.nginx.conf"
  [ -f /etc/systemd/system/medkernel.service ] &&
    install -m 600 /etc/systemd/system/medkernel.service "$BACKUP_DIR/artifacts/medkernel.service"

  run_as_postgres pg_dump --format=custom --no-owner --no-acl "$DATABASE" \
    > "$BACKUP_DIR/database/medkernel.dump"
  [ -s "$BACKUP_DIR/database/medkernel.dump" ] || die "数据库备份为空"

  {
    printf 'source=%s\n' "$SOURCE"
    printf 'created_at=%s\n' "$(date -Iseconds)"
    printf 'database=%s\n' "$DATABASE"
    printf 'database_owner=%s\n' "$DATABASE_OWNER"
    printf 'database_dump_sha256=%s\n' \
      "$(sha256sum "$BACKUP_DIR/database/medkernel.dump" | awk '{print $1}')"
    printf 'candidate_jar_sha256=%s\n' "$(sha256sum "$STAGED_JAR" | awk '{print $1}')"
    printf 'candidate_frontend_sha256=%s\n' \
      "$(sha256sum "$STAGED_FRONTEND" | awk '{print $1}')"
    printf 'destructive_action_performed=false\n'
  } > "$BACKUP_DIR/evidence/pre-clear.properties"
  ok "清库前备份完成"
}

verify_backup_restore() {
  local restored_tables restored_flyway
  RESTORE_DATABASE="${DATABASE}_restore_verify_$$"
  info "隔离恢复验证：$RESTORE_DATABASE"
  run_as_postgres dropdb --if-exists "$RESTORE_DATABASE"
  run_as_postgres createdb --owner="$DATABASE_OWNER" "$RESTORE_DATABASE"
  run_as_postgres pg_restore --exit-on-error --no-owner --no-acl \
    --dbname "$RESTORE_DATABASE" < "$BACKUP_DIR/database/medkernel.dump"

  restored_tables="$(
    database_query "$RESTORE_DATABASE" \
      "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE';"
  )"
  restored_flyway="$(
    database_query "$RESTORE_DATABASE" \
      "select version from flyway_schema_history where success order by installed_rank desc limit 1;"
  )"
  [ "$restored_tables" -gt 0 ] || die "隔离恢复后未发现业务表"
  [ -n "$restored_flyway" ] || die "隔离恢复后未发现 Flyway 版本"

  {
    printf 'restore_status=PASSED\n'
    printf 'restore_database=%s\n' "$RESTORE_DATABASE"
    printf 'restore_public_base_tables=%s\n' "$restored_tables"
    printf 'restore_flyway_version=%s\n' "$restored_flyway"
  } > "$BACKUP_DIR/evidence/restore.properties"

  run_as_postgres dropdb --if-exists "$RESTORE_DATABASE"
  RESTORE_DATABASE=""
  ok "隔离恢复验证通过"
}

stop_service() {
  info "停止服务：$SERVICE"
  systemctl stop "$SERVICE"
  [ "$(systemctl is-active "$SERVICE" || true)" = "inactive" ] ||
    die "服务未进入 inactive"
}

recreate_database() {
  info "终止连接并重建空数据库：$DATABASE"
  database_query postgres \
    "select pg_terminate_backend(pid) from pg_stat_activity where datname='$DATABASE' and pid <> pg_backend_pid();" \
    >/dev/null
  run_as_postgres dropdb --if-exists "$DATABASE"
  run_as_postgres createdb --owner="$DATABASE_OWNER" "$DATABASE"
  [ "$(database_query "$DATABASE" \
    "select count(*) from information_schema.tables where table_schema='public';")" = "0" ] ||
    die "重建后的数据库不是空库"
  DESTRUCTIVE_ACTION_PERFORMED=true
  printf 'destructive_action_performed=true\n' \
    > "$BACKUP_DIR/evidence/destructive-action.properties"
  ok "空数据库已重建"
}

purge_old_runtime() {
  info "清理旧活动制品、暂存、临时文件和运行日志"
  rm -f "$APP_HOME/lib/medkernel.jar" "$APP_HOME/manifest.properties"
  rm -rf "$APP_HOME/frontend/dist"
  find "$APP_HOME/incoming" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  find "$APP_HOME/tmp" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  find "$APP_HOME/run" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  find "$APP_HOME/var" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  find "$APP_HOME/logs" -mindepth 1 -maxdepth 1 -type f -delete
  rm -rf "$APP_HOME/mock-third-party"

  if [ "$PRUNE_OLD_BACKUPS" -eq 1 ]; then
    info "清理旧备份，仅保留本次清库前备份"
    find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 ! -path "$BACKUP_DIR" -exec rm -rf -- {} +
  fi
}

publish_candidate() {
  info "发布显式候选：$SOURCE"
  "$DEPLOY_COMMAND" \
    --jar "$STAGED_JAR" \
    --frontend "$STAGED_FRONTEND" \
    --source "$SOURCE" \
    --no-rollback
}

verify_deployment() {
  local candidate_jar_sha running_jar_sha manifest_source manifest_commit
  local service_status http_code https_code bootstrap_code
  local flyway_version public_tables database_owner
  candidate_jar_sha="$(sha256sum "$STAGED_JAR" | awk '{print $1}')"
  running_jar_sha="$(sha256sum "$APP_HOME/lib/medkernel.jar" | awk '{print $1}')"
  manifest_source="$(sed -n 's/^source=//p' "$APP_HOME/manifest.properties")"
  manifest_commit="$(sed -n 's/^commit=//p' "$APP_HOME/manifest.properties")"
  service_status="$(systemctl is-active "$SERVICE")"
  http_code="$(curl -sS -o "$BACKUP_DIR/evidence/readiness-internal.json" -w '%{http_code}' \
    "http://127.0.0.1:${PORT}/medkernel/actuator/health/readiness")"
  https_code="$(curl -ksS -o "$BACKUP_DIR/evidence/readiness-https.json" -w '%{http_code}' \
    https://127.0.0.1/medkernel/actuator/health/readiness)"
  bootstrap_code="$(curl -ksS -o "$BACKUP_DIR/evidence/bootstrap-status.json" -w '%{http_code}' \
    https://127.0.0.1/medkernel/api/v1/bootstrap/status)"
  flyway_version="$(
    database_query "$DATABASE" \
      "select version from flyway_schema_history where success order by installed_rank desc limit 1;"
  )"
  public_tables="$(
    database_query "$DATABASE" \
      "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE';"
  )"
  database_owner="$(
    database_query postgres \
      "select pg_get_userbyid(datdba) from pg_database where datname='$DATABASE';"
  )"

  [ "$candidate_jar_sha" = "$running_jar_sha" ] || die "运行 jar 与候选 SHA-256 不一致"
  [ "$manifest_source" = "$SOURCE" ] || die "manifest source 与候选提交不一致"
  [ "$manifest_commit" = "$SOURCE" ] || die "manifest commit 与候选提交不一致"
  [ "$service_status" = "active" ] || die "服务未处于 active"
  [ "$http_code" = "200" ] || die "内部 readiness 未返回 200"
  [ "$https_code" = "200" ] || die "HTTPS readiness 未返回 200"
  [ "$bootstrap_code" = "200" ] || die "bootstrap 状态未返回 200"
  grep -Eq '"initialized"[[:space:]]*:[[:space:]]*false' \
    "$BACKUP_DIR/evidence/bootstrap-status.json" ||
    die "全新数据库不应处于已接管状态"
  [ "$flyway_version" = "$EXPECTED_FLYWAY_VERSION" ] ||
    die "Flyway 版本不匹配：期望 $EXPECTED_FLYWAY_VERSION，实际 $flyway_version"
  [ "$public_tables" -gt 0 ] || die "发布后 public 业务表为空"
  [ "$database_owner" = "$DATABASE_OWNER" ] || die "数据库 owner 不匹配"

  {
    printf 'source=%s\n' "$SOURCE"
    printf 'deployed_at=%s\n' "$(date -Iseconds)"
    printf 'backup_dir=%s\n' "$BACKUP_DIR"
    printf 'candidate_jar_sha256=%s\n' "$candidate_jar_sha"
    printf 'running_jar_sha256=%s\n' "$running_jar_sha"
    printf 'manifest_source=%s\n' "$manifest_source"
    printf 'manifest_commit=%s\n' "$manifest_commit"
    printf 'service_status=%s\n' "$service_status"
    printf 'readiness_http=%s\n' "$http_code"
    printf 'readiness_https=%s\n' "$https_code"
    printf 'bootstrap_status_http=%s\n' "$bootstrap_code"
    printf 'bootstrap_initialized=false\n'
    printf 'flyway_version=%s\n' "$flyway_version"
    printf 'public_base_tables=%s\n' "$public_tables"
    printf 'database_owner=%s\n' "$database_owner"
    printf 'destructive_action_performed=%s\n' "$DESTRUCTIVE_ACTION_PERFORMED"
  } > "$BACKUP_DIR/evidence/post-deploy.properties"
  sha256sum "$BACKUP_DIR"/evidence/* > "$BACKUP_DIR/evidence/SHA256SUMS"
  ok "全新发布独立核验通过，证据：$BACKUP_DIR/evidence"
}

main() {
  validate_inputs
  prepare_backup_directory
  create_backup
  verify_backup_restore
  stop_service
  recreate_database
  purge_old_runtime
  publish_candidate
  verify_deployment
}

main "$@"
