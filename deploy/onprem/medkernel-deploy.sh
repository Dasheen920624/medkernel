#!/usr/bin/env bash
# ============================================================================
# MedKernel 单机快捷发布脚本  (部署到 /zoesoft/medkernel/bin/medkernel-deploy.sh)
# 用途：上传新包到 incoming/ 后，一条命令完成「备份→替换→重启→健康检查→失败自动回滚」。
# 只更新程序包（后端 jar / 前端 dist）；数据库 schema 迁移由应用启动时 Flyway 自动前向执行，本脚本不动库。
# ============================================================================
set -Euo pipefail

APP_HOME="${MEDKERNEL_APP_HOME:-/zoesoft/medkernel}"
LIB="$APP_HOME/lib/medkernel.jar"
FE_DIR="$APP_HOME/frontend"
ENV_FILE="$APP_HOME/conf/medkernel.env"
INCOMING_DEFAULT="$APP_HOME/incoming"
BACKUP_ROOT="$APP_HOME/backups"
LOG="$APP_HOME/logs/deploy.log"
SVC=medkernel
DATABASE="${MEDKERNEL_DATABASE:-medkernel}"
DATABASE_OWNER="${MEDKERNEL_DATABASE_OWNER:-medkernel}"
APP_USER="${MEDKERNEL_DEPLOY_APP_USER:-medkernel}"
APP_GROUP="${MEDKERNEL_DEPLOY_APP_GROUP:-$APP_USER}"
HEALTH_TIMEOUT=120
EXTERNAL_BASE_URL="${MEDKERNEL_EXTERNAL_BASE_URL:-}"
RECOVERY_ARMED=false
RECOVERY_IN_PROGRESS=false
RECOVERY_REASON=""
DATABASE_RESTORE_REQUIRED=false

PORT="$(grep -E '^SERVER_PORT=' "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2 | tr -d '\r')"
PORT="${PORT:-18080}"
HEALTH_URL="http://127.0.0.1:${PORT}/medkernel/actuator/health/readiness"

if [ -t 1 ]; then C0=$'\e[0m'; CR=$'\e[31m'; CG=$'\e[32m'; CY=$'\e[33m'; CB=$'\e[36m'; else C0=; CR=; CG=; CY=; CB=; fi
ts(){ date '+%Y-%m-%d %H:%M:%S'; }
log(){ echo "${CB}[$(ts)]${C0} $*" | tee -a "$LOG"; }
ok(){ echo "${CG}[$(ts)] OK ${C0}$*" | tee -a "$LOG"; }
warn(){ echo "${CY}[$(ts)] !  $*${C0}" | tee -a "$LOG"; }
err(){ echo "${CR}[$(ts)] X  $*${C0}" | tee -a "$LOG" >&2; }
die(){
  err "$*"
  if [ "$RECOVERY_ARMED" = true ]; then
    handle_failure "ERROR:$*" 1
  fi
  exit 1
}

usage(){
cat <<USAGE
MedKernel 快捷发布脚本

用法： medkernel-deploy [选项]

发布（默认）：备份当前 -> 替换程序包 -> 重启 -> 健康检查（失败自动回滚）
  --jar <path>        后端 jar（默认取 incoming 下最新 *.jar）
  --frontend <path>   前端 dist 包 .tar.gz（内含 dist/；默认取 incoming 下 dist*.tar.gz）
  --incoming <dir>    上传暂存目录（默认 ${INCOMING_DEFAULT}）
  --source <text>     记入 manifest 的来源说明（如 git 短哈希）
  --no-restart        只替换不重启
  --skip-health       重启后不做健康检查
  --health-timeout N  健康检查最长等待秒数（默认 ${HEALTH_TIMEOUT}）

回滚： --rollback [备份目录]   省略则回滚到最新 deploy-* 备份
状态： --status               打印部署/服务/健康/备份概况
运维： --sync-bootstrap-token  将 medkernel.env 内接管码同步到服务器交付文件（不输出明文）
帮助： -h | --help

约定：把新包传到 ${INCOMING_DEFAULT}/ （后端 *.jar 任意命名；前端打成 dist.tar.gz 内含 dist/），再执行 sudo medkernel-deploy
注意：制品切换后的任何失败、ERR、INT 或 TERM 都会强制恢复发布前配置、systemd、前后端制品并验证旧版 readiness。
USAGE
}

print_status(){
  echo "==== MedKernel 部署状态 ===="
  echo "jar : $LIB"
  echo "  sha256 : $(sha256sum "$LIB" 2>/dev/null | awk '{print $1}')"
  echo "  size   : $(stat -c %s "$LIB" 2>/dev/null) bytes   mtime: $(stat -c %y "$LIB" 2>/dev/null)"
  echo "manifest:"; sed 's/^/  /' "$APP_HOME/manifest.properties" 2>/dev/null
  echo "service : active=$(systemctl is-active $SVC) enabled=$(systemctl is-enabled $SVC 2>/dev/null) NRestarts=$(systemctl show -p NRestarts --value $SVC) MainPID=$(systemctl show -p MainPID --value $SVC)"
  local code; code=$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" 2>/dev/null)
  echo "health  : HTTP $code  $(curl -s "$HEALTH_URL" 2>/dev/null)"
  if [ -n "$EXTERNAL_BASE_URL" ]; then
    local https_code
    https_code=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
      "$EXTERNAL_BASE_URL/actuator/health/readiness" 2>/dev/null || echo 000)
    echo "nginx   : HTTP $https_code  $EXTERNAL_BASE_URL/actuator/health/readiness"
  fi
  echo "backups (最近5):"; ls -dt "$BACKUP_ROOT"/deploy-* 2>/dev/null | head -5 | sed 's/^/  /'
}

BK=""
require_command(){
  command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

run_as_postgres(){
  (
    cd /tmp
    sudo -u postgres "$@"
  )
}

database_query(){
  local database_name="$1" sql="$2"
  run_as_postgres psql -X -v ON_ERROR_STOP=1 -Atq -d "$database_name" -c "$sql"
}

write_backup_checksums(){
  local dir="$1"
  (
    cd "$dir"
    find . -type f ! -name SHA256SUMS -print0 |
      sort -z |
      while IFS= read -r -d '' file; do
        sha256sum "$file"
      done > SHA256SUMS
    sha256sum -c SHA256SUMS >/dev/null
  )
}

verify_backup_checksums(){
  local dir="$1"
  [ -s "$dir/SHA256SUMS" ] || return 1
  (cd "$dir" && sha256sum -c SHA256SUMS >/dev/null)
}

do_backup(){
  BK="$BACKUP_ROOT/deploy-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$BK/lib" "$BK/conf" "$BK/database"
  [ -f "$LIB" ] && cp -a "$LIB" "$BK/lib/medkernel.jar"
  [ -d "$FE_DIR/dist" ] && tar -C "$FE_DIR" -czf "$BK/dist.tar.gz" dist
  cp -a "$APP_HOME/conf/." "$BK/conf/" 2>/dev/null || true
  cp -a /etc/nginx/conf.d/medkernel.conf "$BK/medkernel.conf" 2>/dev/null || true
  cp -a /etc/systemd/system/medkernel.service "$BK/medkernel.service" 2>/dev/null || true
  cp -a "$APP_HOME/manifest.properties" "$BK/manifest.properties" 2>/dev/null || true
  run_as_postgres pg_dump --format=custom --no-owner --no-acl "$DATABASE" \
    > "$BK/database/medkernel.dump" || die "发布前数据库备份失败"
  [ -s "$BK/database/medkernel.dump" ] || die "发布前数据库备份为空"
  {
    printf 'jar_present=%s\n' "$([ -f "$LIB" ] && printf true || printf false)"
    printf 'frontend_present=%s\n' "$([ -d "$FE_DIR/dist" ] && printf true || printf false)"
    printf 'manifest_present=%s\n' "$([ -f "$APP_HOME/manifest.properties" ] && printf true || printf false)"
    printf 'nginx_present=%s\n' "$([ -f /etc/nginx/conf.d/medkernel.conf ] && printf true || printf false)"
    printf 'service_unit_present=%s\n' "$([ -f /etc/systemd/system/medkernel.service ] && printf true || printf false)"
    printf 'service_active=%s\n' "$(systemctl is-active "$SVC" 2>/dev/null || true)"
    printf 'service_enabled=%s\n' "$(systemctl is-enabled "$SVC" 2>/dev/null || true)"
    printf 'jar_sha256=%s\n' "$(sha256sum "$LIB" 2>/dev/null | awk '{print $1}')"
    printf 'database=%s\n' "$DATABASE"
    printf 'database_owner=%s\n' "$DATABASE_OWNER"
    printf 'database_dump_sha256=%s\n' \
      "$(sha256sum "$BK/database/medkernel.dump" | awk '{print $1}')"
  } > "$BK/previous-state.properties"
  write_backup_checksums "$BK" || die "发布前备份摘要生成或校验失败"
  ok "已备份并校验当前部署摘要 -> $BK"
}

swap_jar(){
  log "替换后端 jar <- $1"
  install -o "$APP_USER" -g "$APP_GROUP" -m 644 "$1" "$LIB" || return 1
  ok "jar 已更新（sha256 $(sha256sum "$LIB" | awk '{print $1}')）"
}

swap_frontend(){
  local src="$1" stage="$APP_HOME/tmp/.deploy-stage"
  log "替换前端 dist <- $src"
  rm -rf "$stage"; mkdir -p "$stage"
  tar -xzf "$src" -C "$stage" || { rm -rf "$stage"; return 1; }
  [ -f "$stage/dist/index.html" ] || { err "包内未找到 dist/index.html"; rm -rf "$stage"; return 1; }
  rm -rf "$FE_DIR/dist"; mv "$stage/dist" "$FE_DIR/dist"; chown -R "$APP_USER:$APP_GROUP" "$FE_DIR/dist"; rm -rf "$stage"
  ok "前端 dist 已更新（$(find "$FE_DIR/dist" -type f | wc -l) 文件）"
}

update_manifest(){
  local sha; sha=$(sha256sum "$LIB" 2>/dev/null | awk '{print $1}')
  cat > "$APP_HOME/manifest.properties" <<MF
source=${1:-manual-deploy}
commit=${1:-unknown}
deployedAt=$(date -Iseconds)
jarSha256=$sha
MF
  chown "$APP_USER:$APP_GROUP" "$APP_HOME/manifest.properties"
}

update_runtime_release_fingerprint(){
  local fingerprint="$1" tmp
  [ -f "$ENV_FILE" ] || { err "未找到运行环境文件：$ENV_FILE"; return 1; }
  [ -n "$fingerprint" ] && [ "${#fingerprint}" -le 128 ] \
    || { err "交付内容指纹必须为 1–128 个字符"; return 1; }
  printf '%s' "$fingerprint" | grep -Eq '^[A-Za-z0-9._:-]+$' \
    || { err "交付内容指纹仅允许字母、数字、点、下划线、冒号和连字符"; return 1; }

  tmp="$(mktemp "$APP_HOME/conf/.medkernel-env.XXXXXX")"
  awk -v value="$fingerprint" '
    BEGIN { replaced = 0 }
    /^MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT=/ {
      if (!replaced) {
        print "MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT=" value
        replaced = 1
      }
      next
    }
    { print }
    END {
      if (!replaced) {
        print "MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT=" value
      }
    }
  ' "$ENV_FILE" > "$tmp" || { rm -f "$tmp"; return 1; }
  install_for_app_user "$tmp" "$ENV_FILE" 600 || { rm -f "$tmp"; return 1; }
  rm -f "$tmp"
  ok "交付内容指纹已绑定本次发布来源"
}

install_for_app_user(){
  local src="$1" dst="$2" mode="$3"
  if [ "$(id -u)" -eq 0 ] && id -u "$APP_USER" >/dev/null 2>&1; then
    install -o "$APP_USER" -g "$APP_GROUP" -m "$mode" "$src" "$dst"
  else
    install -m "$mode" "$src" "$dst"
  fi
}

sync_bootstrap_delivery_token(){
  mkdir -p "$(dirname "$LOG")"
  [ -f "$ENV_FILE" ] || { warn "未找到环境文件，跳过接管码交付文件同步：$ENV_FILE"; return 0; }
  local token tmp target
  token="$(
    set -a
    # shellcheck source=/dev/null
    . "$ENV_FILE"
    set +a
    printf '%s' "${MEDKERNEL_BOOTSTRAP_INIT_TOKEN:-}"
  )"
  [ -n "$token" ] || { warn "环境文件未配置 MEDKERNEL_BOOTSTRAP_INIT_TOKEN，跳过交付文件同步"; return 0; }
  target="$APP_HOME/conf/bootstrap-init-token.txt"
  mkdir -p "$APP_HOME/conf"
  tmp="$(mktemp "$APP_HOME/conf/.bootstrap-init-token.XXXXXX")"
  chmod 600 "$tmp"
  printf '%s\n' "$token" > "$tmp"
  install_for_app_user "$tmp" "$target" 600 || { rm -f "$tmp"; return 1; }
  rm -f "$tmp"
  ok "bootstrap 接管码交付文件已同步（未输出明文）"
}

restart_service(){
  log "重启 $SVC ..."
  systemctl reset-failed "$SVC" 2>/dev/null || true
  systemctl daemon-reload 2>/dev/null || true
  systemctl restart "$SVC" || { err "systemctl restart 失败"; return 1; }
  ok "已发出重启（MainPID=$(systemctl show -p MainPID --value $SVC)）"
}

health_check(){
  local waited=0 code
  log "健康检查：$HEALTH_URL （最多 ${HEALTH_TIMEOUT}s）"
  while [ "$waited" -lt "$HEALTH_TIMEOUT" ]; do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" 2>/dev/null || echo 000)
    if [ "$code" = "200" ]; then ok "健康检查通过：$(curl -s "$HEALTH_URL" 2>/dev/null)"; return 0; fi
    if systemctl is-failed --quiet "$SVC"; then err "服务进入 failed 状态，健康检查中止"; return 1; fi
    printf '.'; sleep 3; waited=$((waited+3))
  done
  echo; err "健康检查超时（${HEALTH_TIMEOUT}s 未就绪），最后 HTTP=$code"; return 1
}

read_backup_state(){
  local dir="$1" key="$2"
  sed -n "s/^${key}=//p" "$dir/previous-state.properties" | head -1
}

restore_previous_database(){
  local dir="$1"
  [ "$DATABASE_RESTORE_REQUIRED" = true ] || return 0
  log "恢复发布前数据库：$DATABASE"
  database_query postgres \
    "select pg_terminate_backend(pid) from pg_stat_activity where datname='$DATABASE' and pid <> pg_backend_pid();" \
    >/dev/null 2>&1 || true
  run_as_postgres dropdb --if-exists "$DATABASE" || return 1
  run_as_postgres createdb --owner="$DATABASE_OWNER" "$DATABASE" || return 1
  run_as_postgres pg_restore --exit-on-error --no-owner --no-acl \
    --dbname "$DATABASE" < "$dir/database/medkernel.dump" || return 1
  ok "已恢复发布前数据库"
}

restore_previous_release(){
  local dir="$1" stage="$APP_HOME/tmp/.deploy-stage"
  local service_active service_enabled expected_jar_sha
  [ -z "$dir" ] && dir=$(ls -dt "$BACKUP_ROOT"/deploy-* 2>/dev/null | head -1 || true)
  { [ -n "$dir" ] && [ -d "$dir" ]; } || { err "找不到可回滚的备份目录"; return 1; }
  verify_backup_checksums "$dir" || { err "回滚备份摘要校验失败：$dir"; return 1; }
  log "回滚 <- $dir"
  systemctl stop "$SVC" >/dev/null 2>&1 || true
  restore_previous_database "$dir" || return 1
  if [ "$(read_backup_state "$dir" jar_present)" = true ]; then
    install_for_app_user "$dir/lib/medkernel.jar" "$LIB" 644 || return 1
    ok "已恢复 jar"
  else
    rm -f "$LIB"
  fi
  if [ -f "$dir/dist.tar.gz" ]; then
    rm -rf "$stage"; mkdir -p "$stage"
    tar -xzf "$dir/dist.tar.gz" -C "$stage" || return 1
    rm -rf "$FE_DIR/dist"
    mv "$stage/dist" "$FE_DIR/dist" || return 1
    chown -R "$APP_USER:$APP_GROUP" "$FE_DIR/dist" 2>/dev/null || true
    ok "已恢复前端 dist"
    rm -rf "$stage"
  else
    rm -rf "$FE_DIR/dist"
  fi
  rm -rf "$APP_HOME/conf"
  mkdir -p "$APP_HOME/conf"
  cp -a "$dir/conf/." "$APP_HOME/conf/" || return 1
  ok "已恢复运行环境文件"
  if [ "$(read_backup_state "$dir" manifest_present)" = true ]; then
    install_for_app_user "$dir/manifest.properties" "$APP_HOME/manifest.properties" 644 || return 1
    ok "已恢复 manifest"
  else
    rm -f "$APP_HOME/manifest.properties"
  fi
  if [ "$(read_backup_state "$dir" nginx_present)" = true ]; then
    install -m 644 "$dir/medkernel.conf" /etc/nginx/conf.d/medkernel.conf || return 1
  else
    rm -f /etc/nginx/conf.d/medkernel.conf
  fi
  if [ "$(read_backup_state "$dir" service_unit_present)" = true ]; then
    install -m 644 "$dir/medkernel.service" /etc/systemd/system/medkernel.service || return 1
  else
    rm -f /etc/systemd/system/medkernel.service
  fi
  systemctl daemon-reload || return 1
  service_enabled="$(read_backup_state "$dir" service_enabled)"
  if [ "$service_enabled" = enabled ]; then
    systemctl enable "$SVC" >/dev/null || return 1
  else
    systemctl disable "$SVC" >/dev/null 2>&1 || true
  fi
  service_active="$(read_backup_state "$dir" service_active)"
  [ "$service_active" = active ] || { err "发布前服务不是 active，无法满足旧版 readiness 恢复合同"; return 1; }
  restart_service || return 1
  expected_jar_sha="$(read_backup_state "$dir" jar_sha256)"
  [ -n "$expected_jar_sha" ] && [ "$(sha256sum "$LIB" | awk '{print $1}')" = "$expected_jar_sha" ] ||
    { err "旧版 jar 摘要恢复不一致"; return 1; }
  health_check || { err "旧版本 readiness 验证失败"; return 1; }
  ok "旧版本制品、配置、systemd 与 readiness 已恢复"
}

do_rollback(){
  restore_previous_release "$1"
}

handle_failure(){
  local reason="${1:-ERR}" status="${2:-1}"
  [ "$RECOVERY_ARMED" = true ] || exit "$status"
  [ "$RECOVERY_IN_PROGRESS" = false ] || exit "$status"
  RECOVERY_IN_PROGRESS=true
  RECOVERY_REASON="$reason"
  trap - ERR INT TERM
  err "发布事务失败（${RECOVERY_REASON}），开始强制恢复"
  if restore_previous_release "$BK"; then
    RECOVERY_ARMED=false
    err "发布失败，旧版本已恢复并通过 readiness"
    exit "$status"
  fi
  err "自动恢复未完成，需要立即按备份 $BK 人工处置"
  exit 2
}

handle_signal(){
  local signal="$1" status=128
  [ "$signal" = INT ] && status=130
  [ "$signal" = TERM ] && status=143
  handle_failure "SIGNAL:$signal" "$status"
}
trap 'handle_failure ERR $?' ERR
trap 'handle_signal INT' INT
trap 'handle_signal TERM' TERM

ACTION=deploy; JAR_SRC=""; FE_SRC=""; INCOMING="$INCOMING_DEFAULT"; SRC_TXT=""
JAR_SPECIFIED=0; FE_SPECIFIED=0
DO_RESTART=1; DO_HEALTH=1; ROLLBACK_DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --jar) JAR_SRC="${2:-}"; JAR_SPECIFIED=1; shift 2;;
    --frontend) FE_SRC="${2:-}"; FE_SPECIFIED=1; shift 2;;
    --incoming) INCOMING="${2:-}"; shift 2;;
    --source) SRC_TXT="${2:-}"; shift 2;;
    --no-restart) DO_RESTART=0; shift;;
    --skip-health) DO_HEALTH=0; shift;;
    --health-timeout) HEALTH_TIMEOUT="${2:-120}"; shift 2;;
    --rollback) ACTION=rollback; if [ $# -ge 2 ] && [ "${2#-}" = "$2" ]; then ROLLBACK_DIR="$2"; shift; fi; shift;;
    --status) ACTION=status; shift;;
    --sync-bootstrap-token) ACTION=sync-bootstrap-token; shift;;
    -h|--help) usage; exit 0;;
    *) die "未知参数：$1（--help 查看用法）";;
  esac
done

[ "$ACTION" = status ] && { print_status; exit 0; }
[ "$ACTION" = sync-bootstrap-token ] && { sync_bootstrap_delivery_token; exit 0; }
[ "$(id -u)" -eq 0 ] || die "需要 root 权限（请用 sudo）"
mkdir -p "$INCOMING_DEFAULT" "$BACKUP_ROOT" "$(dirname "$LOG")"
[[ "$DATABASE" =~ ^[a-zA-Z0-9_]+$ ]] || die "数据库名包含非法字符"
[[ "$DATABASE_OWNER" =~ ^[a-zA-Z0-9_]+$ ]] || die "数据库 owner 包含非法字符"
for command_name in pg_dump pg_restore psql createdb dropdb sha256sum systemctl sudo; do
  require_command "$command_name"
done

if [ "$ACTION" = rollback ]; then
  DATABASE_RESTORE_REQUIRED=true
  do_rollback "$ROLLBACK_DIR" || die "回滚失败"
  [ "$DO_HEALTH" = 1 ] && { health_check || warn "回滚后健康检查未通过，请查 $APP_HOME/logs/stdout.log"; }
  print_status; exit 0
fi

if [ "$JAR_SPECIFIED" = 0 ] && [ "$FE_SPECIFIED" = 0 ]; then
  JAR_SRC=$(ls -t "$INCOMING"/*.jar 2>/dev/null | head -1)
  FE_SRC=$(ls -t "$INCOMING"/dist*.tar.gz "$INCOMING"/*frontend*.tar.gz 2>/dev/null | head -1)
fi
{ [ -n "$JAR_SRC" ] || [ -n "$FE_SRC" ]; } || die "未指定且 $INCOMING 下无 jar / dist*.tar.gz；请先上传包或用 --jar/--frontend 指定"
[ -n "$JAR_SRC" ] && { [ -f "$JAR_SRC" ] || die "jar 不存在：$JAR_SRC"; }
[ -n "$FE_SRC" ] && { [ -f "$FE_SRC" ] || die "前端包不存在：$FE_SRC"; }

log "==== 开始发布 ===="
[ -n "$JAR_SRC" ] && log "后端 jar：$JAR_SRC"
[ -n "$FE_SRC" ] && log "前端包  ：$FE_SRC"

# jar 校验：先按体积，再（若有 unzip）确认含 BOOT-INF。
# 注意用 grep -c 读完整输入：grep -q 命中即关管道会让 unzip 收到 SIGPIPE(141)，在 pipefail 下被误判为非法包。
if [ -n "$JAR_SRC" ]; then
  [ "$(stat -c %s "$JAR_SRC" 2>/dev/null || echo 0)" -gt 1000000 ] || die "jar 体积异常（<1MB）：$JAR_SRC"
  if command -v unzip >/dev/null 2>&1; then
    [ "$(unzip -l "$JAR_SRC" 2>/dev/null | grep -c 'BOOT-INF/')" -gt 0 ] || die "不是 Spring Boot 包（缺 BOOT-INF）：$JAR_SRC"
  fi
fi

do_backup
RECOVERY_ARMED=true
sync_bootstrap_delivery_token || die "同步 bootstrap 接管码交付文件失败"
NEW_JAR=0
DATABASE_RESTORE_REQUIRED=true
[ -n "$JAR_SRC" ] && { swap_jar "$JAR_SRC" || die "替换 jar 失败"; NEW_JAR=1; }
[ -n "$FE_SRC" ] && { swap_frontend "$FE_SRC" || die "替换前端失败"; }
if [ "$NEW_JAR" = 1 ]; then
  RELEASE_FINGERPRINT="${SRC_TXT:-sha256:$(sha256sum "$LIB" | awk '{print $1}')}"
  update_runtime_release_fingerprint "$RELEASE_FINGERPRINT" \
    || { do_rollback "$BK"; die "写入交付内容指纹失败，已回滚"; }
  update_manifest "$SRC_TXT"
fi

[ "$DO_RESTART" = 0 ] && {
  RECOVERY_ARMED=false
  warn "按 --no-restart 跳过重启；当前备份为 $BK"
  exit 0
}
restart_service || die "重启失败"
[ "$DO_HEALTH" = 0 ] && {
  RECOVERY_ARMED=false
  warn "按 --skip-health 跳过健康检查；当前备份为 $BK"
  print_status
  exit 0
}

if health_check; then
  [ -n "$JAR_SRC" ] && [ "${JAR_SRC#$INCOMING/}" != "$JAR_SRC" ] && mv -f "$JAR_SRC" "$BK/incoming-$(basename "$JAR_SRC")" 2>/dev/null || true
  [ -n "$FE_SRC" ] && [ "${FE_SRC#$INCOMING/}" != "$FE_SRC" ] && mv -f "$FE_SRC" "$BK/incoming-$(basename "$FE_SRC")" 2>/dev/null || true
  RECOVERY_ARMED=false
  ok "==== 发布成功 ===="; print_status; exit 0
else
  err "==== 发布后健康检查未通过 ===="
  handle_failure "readiness" 1
fi
