#!/usr/bin/env bash
# ============================================================================
# MedKernel 单机快捷发布脚本  (部署到 /zoesoft/medkernel/bin/medkernel-deploy.sh)
# 用途：上传新包到 incoming/ 后，一条命令完成「备份→替换→重启→健康检查→失败自动回滚」。
# 只更新程序包（后端 jar / 前端 dist）；数据库 schema 迁移由应用启动时 Flyway 自动前向执行，本脚本不动库。
# ============================================================================
set -uo pipefail

APP_HOME="${MEDKERNEL_APP_HOME:-/zoesoft/medkernel}"
LIB="$APP_HOME/lib/medkernel.jar"
FE_DIR="$APP_HOME/frontend"
ENV_FILE="$APP_HOME/conf/medkernel.env"
INCOMING_DEFAULT="$APP_HOME/incoming"
BACKUP_ROOT="$APP_HOME/backups"
LOG="$APP_HOME/logs/deploy.log"
SVC=medkernel
APP_USER="${MEDKERNEL_DEPLOY_APP_USER:-medkernel}"
APP_GROUP="${MEDKERNEL_DEPLOY_APP_GROUP:-$APP_USER}"
HEALTH_TIMEOUT=120

PORT="$(grep -E '^SERVER_PORT=' "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2 | tr -d '\r')"
PORT="${PORT:-18080}"
HEALTH_URL="http://127.0.0.1:${PORT}/medkernel/actuator/health/readiness"

if [ -t 1 ]; then C0=$'\e[0m'; CR=$'\e[31m'; CG=$'\e[32m'; CY=$'\e[33m'; CB=$'\e[36m'; else C0=; CR=; CG=; CY=; CB=; fi
ts(){ date '+%Y-%m-%d %H:%M:%S'; }
log(){ echo "${CB}[$(ts)]${C0} $*" | tee -a "$LOG"; }
ok(){ echo "${CG}[$(ts)] OK ${C0}$*" | tee -a "$LOG"; }
warn(){ echo "${CY}[$(ts)] !  $*${C0}" | tee -a "$LOG"; }
err(){ echo "${CR}[$(ts)] X  $*${C0}" | tee -a "$LOG" >&2; }
die(){ err "$*"; exit 1; }

usage(){
cat <<USAGE
MedKernel 快捷发布脚本

用法： medkernel-deploy [选项]

发布（默认）：备份当前 -> 替换程序包 -> 重启 -> 健康检查（失败自动回滚）
  --jar <path>        后端 jar（默认取 incoming 下最新 *.jar）
  --frontend <path>   前端 dist 包 .tar.gz（内含 dist/；默认取 incoming 下 dist*.tar.gz）
  --incoming <dir>    上传暂存目录（默认 $INCOMING_DEFAULT）
  --source <text>     记入 manifest 的来源说明（如 git 短哈希）
  --no-restart        只替换不重启
  --skip-health       重启后不做健康检查
  --no-rollback       健康检查失败时不自动回滚
  --health-timeout N  健康检查最长等待秒数（默认 $HEALTH_TIMEOUT）

回滚： --rollback [备份目录]   省略则回滚到最新 deploy-* 备份
状态： --status               打印部署/服务/健康/备份概况
运维： --sync-bootstrap-token  将 medkernel.env 内接管码同步到服务器交付文件（不输出明文）
帮助： -h | --help

约定：把新包传到 $INCOMING_DEFAULT/ （后端 *.jar 任意命名；前端打成 dist.tar.gz 内含 dist/），再执行  sudo medkernel-deploy
注意：若新版本数据库迁移失败导致起不来，会自动回滚程序包，但已执行的库迁移不会回滚，需人工排查（flyway repair）。
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
  local https_url https_code
  for https_url in \
    "https://127.0.0.1/medkernel/actuator/health/readiness" \
    "https://127.0.0.1:8443/medkernel/actuator/health/readiness"; do
    https_code=$(curl -ks -o /dev/null -w '%{http_code}' "$https_url" 2>/dev/null || echo 000)
    [ "$https_code" = "200" ] && { echo "nginx   : HTTP $https_code  $https_url"; break; }
  done
  echo "backups (最近5):"; ls -dt "$BACKUP_ROOT"/deploy-* 2>/dev/null | head -5 | sed 's/^/  /'
}

BK=""
do_backup(){
  BK="$BACKUP_ROOT/deploy-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$BK/lib" "$BK/conf"
  [ -f "$LIB" ] && cp -a "$LIB" "$BK/lib/medkernel.jar"
  [ -d "$FE_DIR/dist" ] && tar -C "$FE_DIR" -czf "$BK/dist.tar.gz" dist
  cp -a "$APP_HOME/conf/." "$BK/conf/" 2>/dev/null || true
  cp -a /etc/nginx/conf.d/medkernel.conf "$BK/medkernel.conf" 2>/dev/null || true
  cp -a /etc/systemd/system/medkernel.service "$BK/medkernel.service" 2>/dev/null || true
  cp -a "$APP_HOME/manifest.properties" "$BK/manifest.properties" 2>/dev/null || true
  ok "已备份当前部署 -> $BK"
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

do_rollback(){
  local dir="$1" stage="$APP_HOME/tmp/.deploy-stage"
  [ -z "$dir" ] && dir=$(ls -dt "$BACKUP_ROOT"/deploy-* 2>/dev/null | head -1)
  { [ -n "$dir" ] && [ -d "$dir" ]; } || die "找不到可回滚的备份目录"
  log "回滚 <- $dir"
  [ -f "$dir/lib/medkernel.jar" ] && install -o "$APP_USER" -g "$APP_GROUP" -m 644 "$dir/lib/medkernel.jar" "$LIB" && ok "已恢复 jar"
  if [ -f "$dir/dist.tar.gz" ]; then
    rm -rf "$stage"; mkdir -p "$stage"
    tar -xzf "$dir/dist.tar.gz" -C "$stage" && rm -rf "$FE_DIR/dist" && mv "$stage/dist" "$FE_DIR/dist" && chown -R "$APP_USER:$APP_GROUP" "$FE_DIR/dist" && ok "已恢复前端 dist"
    rm -rf "$stage"
  fi
  [ -f "$dir/manifest.properties" ] && install -o "$APP_USER" -g "$APP_GROUP" -m 644 "$dir/manifest.properties" "$APP_HOME/manifest.properties" && ok "已恢复 manifest"
  restart_service
}

ACTION=deploy; JAR_SRC=""; FE_SRC=""; INCOMING="$INCOMING_DEFAULT"; SRC_TXT=""
JAR_SPECIFIED=0; FE_SPECIFIED=0
DO_RESTART=1; DO_HEALTH=1; AUTO_ROLLBACK=1; ROLLBACK_DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --jar) JAR_SRC="${2:-}"; JAR_SPECIFIED=1; shift 2;;
    --frontend) FE_SRC="${2:-}"; FE_SPECIFIED=1; shift 2;;
    --incoming) INCOMING="${2:-}"; shift 2;;
    --source) SRC_TXT="${2:-}"; shift 2;;
    --no-restart) DO_RESTART=0; shift;;
    --skip-health) DO_HEALTH=0; shift;;
    --no-rollback) AUTO_ROLLBACK=0; shift;;
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

if [ "$ACTION" = rollback ]; then
  do_rollback "$ROLLBACK_DIR"
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
sync_bootstrap_delivery_token || die "同步 bootstrap 接管码交付文件失败"
NEW_JAR=0
[ -n "$JAR_SRC" ] && { swap_jar "$JAR_SRC" || die "替换 jar 失败"; NEW_JAR=1; }
[ -n "$FE_SRC" ] && { swap_frontend "$FE_SRC" || die "替换前端失败"; }
[ "$NEW_JAR" = 1 ] && update_manifest "$SRC_TXT"

[ "$DO_RESTART" = 0 ] && { warn "按 --no-restart 跳过重启；稍后手动 systemctl restart $SVC"; exit 0; }
restart_service || die "重启失败"
[ "$DO_HEALTH" = 0 ] && { warn "按 --skip-health 跳过健康检查"; print_status; exit 0; }

if health_check; then
  [ -n "$JAR_SRC" ] && [ "${JAR_SRC#$INCOMING/}" != "$JAR_SRC" ] && mv -f "$JAR_SRC" "$BK/incoming-$(basename "$JAR_SRC")" 2>/dev/null || true
  [ -n "$FE_SRC" ] && [ "${FE_SRC#$INCOMING/}" != "$FE_SRC" ] && mv -f "$FE_SRC" "$BK/incoming-$(basename "$FE_SRC")" 2>/dev/null || true
  ok "==== 发布成功 ===="; print_status; exit 0
else
  err "==== 发布后健康检查未通过 ===="
  if [ "$AUTO_ROLLBACK" = 1 ]; then
    warn "自动回滚到 $BK ..."; do_rollback "$BK"
    if health_check; then ok "已回滚到上一版本并恢复健康"; else err "回滚后仍不健康！可能是数据库迁移失败（需人工 flyway repair）或环境问题，请查 $APP_HOME/logs/stdout.log"; fi
    print_status; exit 1
  else
    err "按 --no-rollback 未自动回滚；可手动执行： medkernel-deploy --rollback $BK"
    print_status; exit 1
  fi
fi
