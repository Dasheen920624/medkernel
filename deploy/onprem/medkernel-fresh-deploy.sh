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
APP_USER="${MEDKERNEL_DEPLOY_APP_USER:-medkernel}"
APP_GROUP="${MEDKERNEL_DEPLOY_APP_GROUP:-$APP_USER}"
DEPLOY_COMMAND="${MEDKERNEL_DEPLOY_COMMAND:-/usr/local/bin/medkernel-deploy}"
PORT=""

JAR=""
FRONTEND=""
SERVICE_UNIT=""
DEPLOY_SCRIPT=""
SOURCE=""
EXPECTED_HOST=""
EXTERNAL_BASE_URL=""
TLS_CA_FILE="${MEDKERNEL_TLS_CA_FILE:-}"
EXPECTED_FLYWAY_VERSION=""
EXPECTED_BUSINESS_TABLES=""
CONFIRM_FRESH=0
CONFIRM_DATABASE=""
PRUNE_OLD_BACKUPS=0
CONFIRM_PRUNE_BACKUPS=0
VALIDATE_ENVIRONMENT_ONLY=0

BACKUP_DIR=""
STAGED_JAR=""
STAGED_FRONTEND=""
STAGED_SERVICE_UNIT=""
STAGED_DEPLOY_SCRIPT=""
RESTORE_DATABASE=""
DESTRUCTIVE_ACTION_PERFORMED=false
DATABASE_MUTATION_STARTED=false
ARTIFACT_MUTATION_STARTED=false
RECOVERY_ARMED=false
RECOVERY_IN_PROGRESS=false
RECOVERY_REASON=""
ACTUAL_HOST=""
TLS_HOST=""
TLS_PORT=""
TLS_CERT_FILE=""
SYSTEMD_UNIT_PATH="${MEDKERNEL_SYSTEMD_UNIT_PATH:-/etc/systemd/system/medkernel.service}"
NGINX_CONF_PATH="${MEDKERNEL_NGINX_CONF_PATH:-/etc/nginx/conf.d/medkernel.conf}"

info() { printf '[*] %s\n' "$*"; }
ok() { printf '[OK] %s\n' "$*"; }
die() {
  printf '[X] %s\n' "$*" >&2
  if [ "$RECOVERY_ARMED" = true ]; then
    handle_failure "ERROR:$*" 1
  fi
  exit 1
}

usage() {
  cat <<'USAGE'
MedKernel PostgreSQL 全新发布

用法：
  medkernel-fresh-deploy.sh --validate-environment-only

或执行全新发布：
  medkernel-fresh-deploy.sh \
    --jar /path/to/medkernel.jar \
    --frontend /path/to/dist.tar.gz \
    --service-unit /path/to/medkernel.service \
    --deploy-script /path/to/medkernel-deploy.sh \
    --source <完整提交哈希> \
    --expected-host <目标机 hostname> \
    --external-base-url https://<正式域名或具备 SAN 的地址>/medkernel \
    --expected-flyway-version <版本> \
    --expected-business-tables <业务表数量> \
    --confirm-fresh \
    --confirm-database medkernel \
    [--prune-old-backups --confirm-prune-backups]

安全边界：
  1. 未显式确认数据库名时拒绝运行。
  2. 先完成数据库备份与隔离恢复验证，之后才允许停服和清库。
  3. 清库后只发布显式指定候选，不从 incoming 自动发现旧包。
  4. dropdb、制品切换、readiness 或 ERR/INT/TERM 失败时强制恢复旧数据库、配置、systemd 与前后端制品。
  5. 外部 HTTPS 必须通过可信链、SAN、有效期与严格 curl 校验，禁止跳过证书验证。
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --jar) JAR="${2:-}"; shift 2 ;;
    --frontend) FRONTEND="${2:-}"; shift 2 ;;
    --service-unit) SERVICE_UNIT="${2:-}"; shift 2 ;;
    --deploy-script) DEPLOY_SCRIPT="${2:-}"; shift 2 ;;
    --source) SOURCE="${2:-}"; shift 2 ;;
    --expected-host) EXPECTED_HOST="${2:-}"; shift 2 ;;
    --external-base-url) EXTERNAL_BASE_URL="${2:-}"; shift 2 ;;
    --expected-flyway-version) EXPECTED_FLYWAY_VERSION="${2:-}"; shift 2 ;;
    --expected-business-tables) EXPECTED_BUSINESS_TABLES="${2:-}"; shift 2 ;;
    --confirm-fresh) CONFIRM_FRESH=1; shift ;;
    --confirm-database) CONFIRM_DATABASE="${2:-}"; shift 2 ;;
    --prune-old-backups) PRUNE_OLD_BACKUPS=1; shift ;;
    --confirm-prune-backups) CONFIRM_PRUNE_BACKUPS=1; shift ;;
    --validate-environment-only) VALIDATE_ENVIRONMENT_ONLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数：$1" ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

parse_tls_endpoint() {
  local authority
  [[ "$EXTERNAL_BASE_URL" =~ ^https://[^/?#@]+(/[^?#]*)?/medkernel$ ]] ||
    die "--external-base-url 必须是无凭据、查询和片段且以 /medkernel 结尾的 HTTPS 地址"
  authority="${EXTERNAL_BASE_URL#https://}"
  authority="${authority%%/*}"
  if [[ "$authority" =~ ^([A-Za-z0-9.-]+):([0-9]+)$ ]]; then
    TLS_HOST="${BASH_REMATCH[1]}"
    TLS_PORT="${BASH_REMATCH[2]}"
  else
    TLS_HOST="$authority"
    TLS_PORT=443
  fi
  [[ "$TLS_HOST" =~ ^[A-Za-z0-9.-]+$ ]] || die "TLS 主机名只允许 DNS 名称或 IPv4 地址"
  [[ "$TLS_PORT" =~ ^[0-9]+$ ]] || die "TLS 端口非法"
}

strict_tls_preflight() {
  local tls_dir san_text
  local -a s_client_args curl_args
  parse_tls_endpoint
  tls_dir="$(mktemp -d)"
  TLS_CERT_FILE="$tls_dir/leaf.pem"
  s_client_args=(-connect "${TLS_HOST}:${TLS_PORT}" -servername "$TLS_HOST" -verify_return_error -showcerts)
  curl_args=(--fail --silent --show-error)
  if [ -n "$TLS_CA_FILE" ]; then
    [ -f "$TLS_CA_FILE" ] || die "TLS CA 文件不存在：$TLS_CA_FILE"
    s_client_args+=(-CAfile "$TLS_CA_FILE")
    curl_args+=(--cacert "$TLS_CA_FILE")
  fi
  if ! openssl s_client "${s_client_args[@]}" </dev/null > "$tls_dir/chain.pem" 2> "$tls_dir/verify.log"; then
    rm -rf "$tls_dir"
    die "TLS 证书链验证失败"
  fi
  awk '
    /-----BEGIN CERTIFICATE-----/ { capture=1 }
    capture { print }
    /-----END CERTIFICATE-----/ { exit }
  ' "$tls_dir/chain.pem" > "$TLS_CERT_FILE"
  [ -s "$TLS_CERT_FILE" ] || { rm -rf "$tls_dir"; die "未获取 TLS 叶子证书"; }
  openssl x509 -in "$TLS_CERT_FILE" -noout -checkend 0 >/dev/null ||
    { rm -rf "$tls_dir"; die "TLS 证书不在有效期内"; }
  san_text="$(openssl x509 -in "$TLS_CERT_FILE" -noout -ext subjectAltName 2>/dev/null || true)"
  printf '%s\n' "$san_text" | grep -Eq 'DNS:|IP Address:' ||
    { rm -rf "$tls_dir"; die "TLS 证书缺少 SAN"; }
  if [[ "$TLS_HOST" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    openssl x509 -in "$TLS_CERT_FILE" -noout -checkip "$TLS_HOST" >/dev/null ||
      { rm -rf "$tls_dir"; die "TLS 证书 SAN 不包含目标 IP"; }
  else
    openssl x509 -in "$TLS_CERT_FILE" -noout -checkhost "$TLS_HOST" >/dev/null ||
      { rm -rf "$tls_dir"; die "TLS 证书 SAN 不包含目标主机名"; }
  fi
  curl "${curl_args[@]}" --output /dev/null \
    "$EXTERNAL_BASE_URL/actuator/health/readiness" || {
      rm -rf "$tls_dir"
      die "严格 TLS readiness 预检失败"
    }
  rm -rf "$tls_dir"
  TLS_CERT_FILE=""
  ok "TLS 可信链、SAN、有效期与严格 readiness 预检通过"
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

read_env_value() {
  local key="$1"
  awk -v key="$key" '
    index($0, key "=") == 1 {
      value = substr($0, length(key) + 2)
      sub(/\r$/, "", value)
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      first = substr(value, 1, 1)
      last = substr(value, length(value), 1)
      quote = sprintf("%c", 39)
      if ((first == "\"" && last == "\"") || (first == quote && last == quote)) {
        value = substr(value, 2, length(value) - 2)
      }
      print value
      exit
    }
  ' "$ENV_FILE"
}

env_key_count() {
  local key="$1"
  awk -v key="$key" 'index($0, key "=") == 1 { count += 1 } END { print count + 0 }' \
    "$ENV_FILE"
}

file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then
    stat -c '%a' "$1"
  else
    stat -f '%Lp' "$1"
  fi
}

require_env_secret() {
  local key="$1"
  local minimum_length="$2"
  local count value
  count="$(env_key_count "$key")"
  [ "$count" -eq 1 ] || die "生产环境的 $key 必须且只能配置一次"
  value="$(read_env_value "$key")"
  [ -n "$value" ] || die "生产环境缺少 $key"
  case "$value" in
    \<*\>|*RANDOM_*|*CHANGE_ME*)
      die "生产环境的 $key 仍是占位符"
      ;;
  esac
  [ "${#value}" -ge "$minimum_length" ] ||
    die "生产环境的 $key 长度至少为 $minimum_length 位"
}

validate_runtime_environment() {
  local mode
  [ -f "$ENV_FILE" ] || die "环境文件不存在：$ENV_FILE"
  mode="$(file_mode "$ENV_FILE")"
  [ "$mode" = 600 ] || die "环境文件权限必须为 600，当前为 $mode"
  require_env_secret MEDKERNEL_AUTH_JWT_SECRET 32
  require_env_secret MEDKERNEL_INTEGRATION_SECRET_KEY 32
  require_env_secret MEDKERNEL_FIELD_ENCRYPTION_KEY 32
  require_env_secret MEDKERNEL_BOOTSTRAP_INIT_TOKEN 32
  ok "生产运行环境预检通过"
}

cleanup_restore_database() {
  if [ -n "$RESTORE_DATABASE" ]; then
    run_as_postgres dropdb --if-exists "$RESTORE_DATABASE" >/dev/null 2>&1 || true
  fi
}
trap cleanup_restore_database EXIT

validate_inputs() {
  ACTUAL_HOST="$(hostname)"
  [ -n "$EXPECTED_HOST" ] || die "缺少 --expected-host"
  [ "$EXPECTED_HOST" = "$ACTUAL_HOST" ] ||
    die "目标主机不匹配：期望 ${EXPECTED_HOST}，实际 ${ACTUAL_HOST}"
  [ "$(id -u)" -eq 0 ] || die "需要 root 权限"
  [ "$CONFIRM_FRESH" -eq 1 ] || die "缺少 --confirm-fresh"
  [ "$CONFIRM_DATABASE" = "$DATABASE" ] || die "--confirm-database 必须精确等于 $DATABASE"
  [ -n "$SOURCE" ] || die "缺少 --source"
  [ -n "$EXTERNAL_BASE_URL" ] || die "缺少 --external-base-url"
  [ -n "$EXPECTED_FLYWAY_VERSION" ] || die "缺少 --expected-flyway-version"
  [ -n "$EXPECTED_BUSINESS_TABLES" ] || die "缺少 --expected-business-tables"
  [[ "$DATABASE" =~ ^[a-zA-Z0-9_]+$ ]] || die "数据库名包含非法字符"
  [[ "$DATABASE_OWNER" =~ ^[a-zA-Z0-9_]+$ ]] || die "数据库 owner 包含非法字符"
  [[ "$SOURCE" =~ ^[a-fA-F0-9]{40}$ ]] || die "--source 必须是 40 位提交哈希"
  [[ "$EXPECTED_FLYWAY_VERSION" =~ ^[0-9]+$ ]] || die "Flyway 版本必须是正整数"
  [[ "$EXPECTED_BUSINESS_TABLES" =~ ^[1-9][0-9]*$ ]] || die "业务表数量必须是正整数"
  [ -f "$JAR" ] || die "后端候选不存在：$JAR"
  [ -f "$FRONTEND" ] || die "前端候选不存在：$FRONTEND"
  [ -f "$SERVICE_UNIT" ] || die "systemd 单元候选不存在：$SERVICE_UNIT"
  [ -f "$DEPLOY_SCRIPT" ] || die "服务端发布脚本候选不存在：$DEPLOY_SCRIPT"
  grep -q '^SuccessExitStatus=143$' "$SERVICE_UNIT" ||
    die "systemd 单元必须把 Java SIGTERM 退出码 143 声明为正常"
  bash -n "$DEPLOY_SCRIPT" || die "服务端发布脚本语法检查失败"
  validate_runtime_environment
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
  for command_name in pg_dump pg_restore psql createdb dropdb sha256sum tar curl systemctl openssl; do
    require_command "$command_name"
  done
  PORT="$(sed -n 's/^SERVER_PORT=//p' "$ENV_FILE" | head -1 | tr -d '\r')"
  PORT="${PORT:-18080}"
  [[ "$PORT" =~ ^[0-9]+$ ]] || die "SERVER_PORT 不是有效端口"
  strict_tls_preflight
}

prepare_backup_directory() {
  local timestamp safe_source
  timestamp="$(date '+%Y%m%d-%H%M%S')"
  safe_source="${SOURCE:0:12}"
  BACKUP_DIR="$BACKUP_ROOT/fresh-preclear-${safe_source}-${timestamp}"
  mkdir -p "$BACKUP_DIR"/{artifacts,database,evidence,staged}
  chmod 700 "$BACKUP_DIR"
  STAGED_JAR="$BACKUP_DIR/staged/medkernel.jar"
  STAGED_FRONTEND="$BACKUP_DIR/staged/dist.tar.gz"
  STAGED_SERVICE_UNIT="$BACKUP_DIR/staged/medkernel.service"
  STAGED_DEPLOY_SCRIPT="$BACKUP_DIR/staged/medkernel-deploy.sh"
}

create_backup() {
  info "创建清库前备份：$BACKUP_DIR"
  install -m 600 "$JAR" "$STAGED_JAR"
  install -m 600 "$FRONTEND" "$STAGED_FRONTEND"
  install -m 600 "$SERVICE_UNIT" "$STAGED_SERVICE_UNIT"
  install -m 700 "$DEPLOY_SCRIPT" "$STAGED_DEPLOY_SCRIPT"

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
  [ -f "$NGINX_CONF_PATH" ] &&
    install -m 600 "$NGINX_CONF_PATH" "$BACKUP_DIR/artifacts/medkernel.nginx.conf"
  [ -f "$SYSTEMD_UNIT_PATH" ] &&
    install -m 600 "$SYSTEMD_UNIT_PATH" "$BACKUP_DIR/artifacts/medkernel.service"
  [ -f "$APP_HOME/bin/medkernel-deploy.sh" ] &&
    install -m 700 "$APP_HOME/bin/medkernel-deploy.sh" "$BACKUP_DIR/artifacts/medkernel-deploy.sh"

  run_as_postgres pg_dump --format=custom --no-owner --no-acl "$DATABASE" \
    > "$BACKUP_DIR/database/medkernel.dump"
  [ -s "$BACKUP_DIR/database/medkernel.dump" ] || die "数据库备份为空"

  {
    printf 'source=%s\n' "$SOURCE"
    printf 'expected_host=%s\n' "$EXPECTED_HOST"
    printf 'actual_host=%s\n' "$ACTUAL_HOST"
    printf 'created_at=%s\n' "$(date -Iseconds)"
    printf 'database=%s\n' "$DATABASE"
    printf 'database_owner=%s\n' "$DATABASE_OWNER"
    printf 'jar_present=%s\n' "$([ -f "$APP_HOME/lib/medkernel.jar" ] && printf true || printf false)"
    printf 'frontend_present=%s\n' "$([ -d "$APP_HOME/frontend/dist" ] && printf true || printf false)"
    printf 'manifest_present=%s\n' "$([ -f "$APP_HOME/manifest.properties" ] && printf true || printf false)"
    printf 'runtime_var_present=%s\n' "$([ -d "$APP_HOME/var" ] && printf true || printf false)"
    printf 'mock_third_party_present=%s\n' "$([ -d "$APP_HOME/mock-third-party" ] && printf true || printf false)"
    printf 'nginx_present=%s\n' "$([ -f "$NGINX_CONF_PATH" ] && printf true || printf false)"
    printf 'service_unit_present=%s\n' "$([ -f "$SYSTEMD_UNIT_PATH" ] && printf true || printf false)"
    printf 'deploy_script_present=%s\n' "$([ -f "$APP_HOME/bin/medkernel-deploy.sh" ] && printf true || printf false)"
    printf 'service_active=%s\n' "$(systemctl is-active "$SERVICE" 2>/dev/null || true)"
    printf 'service_enabled=%s\n' "$(systemctl is-enabled "$SERVICE" 2>/dev/null || true)"
    printf 'old_jar_sha256=%s\n' "$(sha256sum "$APP_HOME/lib/medkernel.jar" 2>/dev/null | awk '{print $1}')"
    printf 'database_dump_sha256=%s\n' \
      "$(sha256sum "$BACKUP_DIR/database/medkernel.dump" | awk '{print $1}')"
    printf 'candidate_jar_sha256=%s\n' "$(sha256sum "$STAGED_JAR" | awk '{print $1}')"
    printf 'candidate_frontend_sha256=%s\n' \
      "$(sha256sum "$STAGED_FRONTEND" | awk '{print $1}')"
    printf 'candidate_service_unit_sha256=%s\n' \
      "$(sha256sum "$STAGED_SERVICE_UNIT" | awk '{print $1}')"
    printf 'candidate_deploy_script_sha256=%s\n' \
      "$(sha256sum "$STAGED_DEPLOY_SCRIPT" | awk '{print $1}')"
    printf 'destructive_action_performed=false\n'
  } > "$BACKUP_DIR/evidence/pre-clear.properties"
  (
    cd "$BACKUP_DIR"
    find artifacts database staged evidence -type f ! -name SHA256SUMS -print0 |
      sort -z |
      while IFS= read -r -d '' file; do
        sha256sum "$file"
      done > SHA256SUMS
    sha256sum -c SHA256SUMS >/dev/null
  ) || die "清库前备份摘要生成或校验失败"
  ok "清库前备份与摘要校验完成"
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

apply_runtime_contracts() {
  info "同步 systemd 单元与服务端发布脚本"
  ARTIFACT_MUTATION_STARTED=true
  RECOVERY_REASON="publish:runtime-contracts"
  install -m 644 "$STAGED_SERVICE_UNIT" "$SYSTEMD_UNIT_PATH"
  install -m 755 "$STAGED_DEPLOY_SCRIPT" "$APP_HOME/bin/medkernel-deploy.sh"
  ln -sfn "$APP_HOME/bin/medkernel-deploy.sh" "$DEPLOY_COMMAND"
  systemctl daemon-reload
}

stop_service() {
  local state main_pid waited
  info "停止服务：$SERVICE"
  systemctl stop "$SERVICE"
  waited=0
  while [ "$waited" -lt 30 ]; do
    state="$(systemctl show "$SERVICE" -p ActiveState --value)"
    main_pid="$(systemctl show "$SERVICE" -p MainPID --value)"
    if { [ "$state" = "inactive" ] || [ "$state" = "failed" ]; } && [ "$main_pid" = "0" ]; then
      systemctl reset-failed "$SERVICE" 2>/dev/null || true
      ok "服务已停止（原状态 ${state}，MainPID=0）"
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  die "服务未在 30 秒内完全停止"
}

recreate_database() {
  info "终止连接并重建空数据库：$DATABASE"
  DATABASE_MUTATION_STARTED=true
  RECOVERY_REASON="drop:database"
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

prepare_managed_runtime_directories() {
  local literature_root="$APP_HOME/var/platform-knowledge/t-1/literature-materials"
  info "创建正式知识文献受管资料库目录：$literature_root"
  mkdir -p "$literature_root"
  chmod 750 "$APP_HOME/var" "$APP_HOME/var/platform-knowledge" \
    "$APP_HOME/var/platform-knowledge/t-1" "$literature_root"
  if id "$APP_USER" >/dev/null 2>&1; then
    chown -R "$APP_USER:$APP_GROUP" "$APP_HOME/var/platform-knowledge"
  fi
}

publish_candidate() {
  info "发布显式候选：$SOURCE"
  RECOVERY_REASON="publish:artifacts"
  "$DEPLOY_COMMAND" \
    --jar "$STAGED_JAR" \
    --frontend "$STAGED_FRONTEND" \
    --source "$SOURCE"
}

verify_deployment() {
  local candidate_jar_sha running_jar_sha manifest_source manifest_commit
  local service_status http_code https_code bootstrap_code
  local flyway_version public_tables database_owner expected_public_tables
  local managed_literature_root
  candidate_jar_sha="$(sha256sum "$STAGED_JAR" | awk '{print $1}')"
  running_jar_sha="$(sha256sum "$APP_HOME/lib/medkernel.jar" | awk '{print $1}')"
  manifest_source="$(sed -n 's/^source=//p' "$APP_HOME/manifest.properties")"
  manifest_commit="$(sed -n 's/^commit=//p' "$APP_HOME/manifest.properties")"
  service_status="$(systemctl is-active "$SERVICE")"
  RECOVERY_REASON="readiness:candidate"
  http_code="$(curl --silent --show-error -o "$BACKUP_DIR/evidence/readiness-internal.json" -w '%{http_code}' \
    "http://127.0.0.1:${PORT}/medkernel/actuator/health/readiness")"
  local -a curl_args
  curl_args=(--fail --silent --show-error)
  [ -n "$TLS_CA_FILE" ] && curl_args+=(--cacert "$TLS_CA_FILE")
  https_code="$(curl "${curl_args[@]}" -o "$BACKUP_DIR/evidence/readiness-https.json" -w '%{http_code}' \
    "$EXTERNAL_BASE_URL/actuator/health/readiness")"
  bootstrap_code="$(curl "${curl_args[@]}" -o "$BACKUP_DIR/evidence/bootstrap-status.json" -w '%{http_code}' \
    "$EXTERNAL_BASE_URL/api/v1/bootstrap/status")"
  flyway_version="$(
    database_query "$DATABASE" \
      "select version from flyway_schema_history where success order by installed_rank desc limit 1;"
  )"
  public_tables="$(
    database_query "$DATABASE" \
      "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE';"
  )"
  expected_public_tables=$((EXPECTED_BUSINESS_TABLES + 1))
  database_owner="$(
    database_query postgres \
      "select pg_get_userbyid(datdba) from pg_database where datname='$DATABASE';"
  )"
  managed_literature_root="$APP_HOME/var/platform-knowledge/t-1/literature-materials"

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
    die "Flyway 版本不匹配：期望 ${EXPECTED_FLYWAY_VERSION}，实际 ${flyway_version}"
  [ "$public_tables" -eq "$expected_public_tables" ] ||
    die "发布后表数量不匹配：期望 ${expected_public_tables}（业务表 ${EXPECTED_BUSINESS_TABLES} + Flyway 1），实际 ${public_tables}"
  [ "$database_owner" = "$DATABASE_OWNER" ] || die "数据库 owner 不匹配"
  [ -d "$managed_literature_root" ] || die "正式知识文献受管资料库目录不存在"

  {
    printf 'source=%s\n' "$SOURCE"
    printf 'expected_host=%s\n' "$EXPECTED_HOST"
    printf 'actual_host=%s\n' "$ACTUAL_HOST"
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
    printf 'managed_literature_material_root=%s\n' "$managed_literature_root"
    printf 'destructive_action_performed=%s\n' "$DESTRUCTIVE_ACTION_PERFORMED"
  } > "$BACKUP_DIR/evidence/post-deploy.properties"
  sha256sum "$BACKUP_DIR"/evidence/* > "$BACKUP_DIR/evidence/SHA256SUMS"
  ok "全新发布独立核验通过，证据：$BACKUP_DIR/evidence"
}

read_previous_state() {
  local key="$1"
  sed -n "s/^${key}=//p" "$BACKUP_DIR/evidence/pre-clear.properties" | head -1
}

verify_backup_checksums() {
  [ -s "$BACKUP_DIR/SHA256SUMS" ] || return 1
  (cd "$BACKUP_DIR" && sha256sum -c SHA256SUMS >/dev/null)
}

restore_previous_database() {
  [ "$DATABASE_MUTATION_STARTED" = true ] || return 0
  info "恢复发布前数据库：$DATABASE"
  database_query postgres \
    "select pg_terminate_backend(pid) from pg_stat_activity where datname='$DATABASE' and pid <> pg_backend_pid();" \
    >/dev/null 2>&1 || true
  run_as_postgres dropdb --if-exists "$DATABASE" || return 1
  run_as_postgres createdb --owner="$DATABASE_OWNER" "$DATABASE" || return 1
  run_as_postgres pg_restore --exit-on-error --no-owner --no-acl \
    --dbname "$DATABASE" < "$BACKUP_DIR/database/medkernel.dump" || return 1
  ok "发布前数据库已恢复"
}

restore_optional_directory() {
  local present="$1" archive="$2" parent="$3" name="$4"
  rm -rf "$parent/$name"
  if [ "$present" = true ]; then
    tar --xattrs --acls -xzf "$archive" -C "$parent" || return 1
  fi
}

verify_previous_release_readiness() {
  local expected_jar_sha waited internal_code external_code
  local -a curl_args
  expected_jar_sha="$(read_previous_state old_jar_sha256)"
  [ -n "$expected_jar_sha" ] || { printf '[X] 发布前 jar 摘要缺失\n' >&2; return 1; }
  [ "$(sha256sum "$APP_HOME/lib/medkernel.jar" | awk '{print $1}')" = "$expected_jar_sha" ] ||
    { printf '[X] 发布前 jar 摘要恢复不一致\n' >&2; return 1; }
  curl_args=(--fail --silent --show-error)
  [ -n "$TLS_CA_FILE" ] && curl_args+=(--cacert "$TLS_CA_FILE")
  waited=0
  while [ "$waited" -lt 90 ]; do
    internal_code="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
      "http://127.0.0.1:${PORT}/medkernel/actuator/health/readiness" 2>/dev/null || true)"
    external_code="$(curl "${curl_args[@]}" --output /dev/null --write-out '%{http_code}' \
      "$EXTERNAL_BASE_URL/actuator/health/readiness" 2>/dev/null || true)"
    if [ "$internal_code" = 200 ] && [ "$external_code" = 200 ]; then
      ok "旧版本内部与严格 TLS readiness 已恢复"
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  printf '[X] 旧版本未在 90 秒内恢复 readiness\n' >&2
  return 1
}

restore_previous_release() {
  local service_active service_enabled
  [ -n "$BACKUP_DIR" ] && [ -d "$BACKUP_DIR" ] || {
    printf '[X] 缺少清库前备份目录\n' >&2
    return 1
  }
  verify_backup_checksums || {
    printf '[X] 清库前备份摘要校验失败\n' >&2
    return 1
  }
  systemctl stop "$SERVICE" >/dev/null 2>&1 || true
  restore_previous_database || return 1

  rm -rf "$APP_HOME/conf"
  mkdir -p "$APP_HOME"
  tar --xattrs --acls -xzf "$BACKUP_DIR/artifacts/conf.tar.gz" -C "$APP_HOME" || return 1

  if [ "$(read_previous_state jar_present)" = true ]; then
    install -m 644 "$BACKUP_DIR/artifacts/medkernel.jar" "$APP_HOME/lib/medkernel.jar" || return 1
  else
    rm -f "$APP_HOME/lib/medkernel.jar"
  fi
  restore_optional_directory "$(read_previous_state frontend_present)" \
    "$BACKUP_DIR/artifacts/frontend-dist.tar.gz" "$APP_HOME/frontend" dist || return 1
  restore_optional_directory "$(read_previous_state runtime_var_present)" \
    "$BACKUP_DIR/artifacts/runtime-var.tar.gz" "$APP_HOME" var || return 1
  restore_optional_directory "$(read_previous_state mock_third_party_present)" \
    "$BACKUP_DIR/artifacts/mock-third-party.tar.gz" "$APP_HOME" mock-third-party || return 1

  if [ "$(read_previous_state manifest_present)" = true ]; then
    install -m 644 "$BACKUP_DIR/artifacts/manifest.properties" "$APP_HOME/manifest.properties" || return 1
  else
    rm -f "$APP_HOME/manifest.properties"
  fi
  if [ "$(read_previous_state nginx_present)" = true ]; then
    install -m 644 "$BACKUP_DIR/artifacts/medkernel.nginx.conf" "$NGINX_CONF_PATH" || return 1
  else
    rm -f "$NGINX_CONF_PATH"
  fi
  if [ "$(read_previous_state service_unit_present)" = true ]; then
    install -m 644 "$BACKUP_DIR/artifacts/medkernel.service" "$SYSTEMD_UNIT_PATH" || return 1
  else
    rm -f "$SYSTEMD_UNIT_PATH"
  fi
  if [ "$(read_previous_state deploy_script_present)" = true ]; then
    install -m 755 "$BACKUP_DIR/artifacts/medkernel-deploy.sh" "$APP_HOME/bin/medkernel-deploy.sh" ||
      return 1
    ln -sfn "$APP_HOME/bin/medkernel-deploy.sh" "$DEPLOY_COMMAND" || return 1
  else
    rm -f "$APP_HOME/bin/medkernel-deploy.sh"
    [ -L "$DEPLOY_COMMAND" ] && rm -f "$DEPLOY_COMMAND"
  fi

  systemctl daemon-reload || return 1
  service_enabled="$(read_previous_state service_enabled)"
  if [ "$service_enabled" = enabled ]; then
    systemctl enable "$SERVICE" >/dev/null || return 1
  else
    systemctl disable "$SERVICE" >/dev/null 2>&1 || true
  fi
  systemctl reload nginx >/dev/null 2>&1 || true
  service_active="$(read_previous_state service_active)"
  [ "$service_active" = active ] || {
    printf '[X] 发布前服务不是 active，无法满足旧版 readiness 恢复合同\n' >&2
    return 1
  }
  systemctl reset-failed "$SERVICE" >/dev/null 2>&1 || true
  systemctl restart "$SERVICE" || return 1
  verify_previous_release_readiness || return 1
  {
    printf 'recovery_status=PASSED\n'
    printf 'recovery_reason=%s\n' "$RECOVERY_REASON"
    printf 'recovered_at=%s\n' "$(date -Iseconds)"
    printf 'database_restored=%s\n' "$DATABASE_MUTATION_STARTED"
    printf 'old_readiness_verified=true\n'
  } > "$BACKUP_DIR/evidence/recovery.properties"
  return 0
}

handle_failure() {
  local reason="${1:-ERR}" status="${2:-1}"
  [ "$RECOVERY_ARMED" = true ] || return "$status"
  [ "$RECOVERY_IN_PROGRESS" = false ] || exit "$status"
  RECOVERY_IN_PROGRESS=true
  RECOVERY_REASON="${RECOVERY_REASON:-$reason}"
  trap - ERR INT TERM
  printf '[X] 清库发布事务失败（%s），开始强制恢复\n' "$RECOVERY_REASON" >&2
  if restore_previous_release; then
    RECOVERY_ARMED=false
    printf '[X] 候选发布失败，旧版本已恢复并通过 readiness\n' >&2
    exit "$status"
  fi
  printf '[X] 自动恢复未完成，必须立即使用备份 %s 人工处置\n' "$BACKUP_DIR" >&2
  exit 2
}

handle_signal() {
  local signal="$1" status=128
  [ "$signal" = INT ] && status=130
  [ "$signal" = TERM ] && status=143
  handle_failure "SIGNAL:$signal" "$status"
}

trap 'handle_failure ERR $?' ERR
trap 'handle_signal INT' INT
trap 'handle_signal TERM' TERM

main() {
  if [ "$VALIDATE_ENVIRONMENT_ONLY" -eq 1 ]; then
    validate_runtime_environment
    return
  fi
  validate_inputs
  prepare_backup_directory
  create_backup
  verify_backup_restore
  RECOVERY_ARMED=true
  apply_runtime_contracts
  stop_service
  recreate_database
  purge_old_runtime
  prepare_managed_runtime_directories
  publish_candidate
  verify_deployment
  RECOVERY_ARMED=false
}

main "$@"
