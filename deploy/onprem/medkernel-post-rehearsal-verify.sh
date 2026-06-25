#!/usr/bin/env bash
# MedKernel 134 全功能与全知识演练后验收脚本。
# 只在八段演练全部通过后执行：严格 TLS、真实重启、关系库持久化、备份与隔离恢复缺一不可。
set -euo pipefail

APP_HOME="${MEDKERNEL_APP_HOME:-/zoesoft/medkernel}"
RUNTIME_ROOT="${MEDKERNEL_RUNTIME_ROOT:-$APP_HOME/var}"
SERVICE="${MEDKERNEL_SERVICE:-medkernel}"
DATABASE="${MEDKERNEL_DATABASE:-medkernel}"
DATABASE_OWNER="${MEDKERNEL_DATABASE_OWNER:-medkernel}"
PORT="${SERVER_PORT:-18080}"
EVIDENCE_ROOT="$RUNTIME_ROOT/evidence/current-launch"
FULL_SYSTEM_EVIDENCE="$EVIDENCE_ROOT/full-system.json"
FULL_KNOWLEDGE_EVIDENCE="$EVIDENCE_ROOT/full-knowledge.json"
CREDENTIALS_FILE="$RUNTIME_ROOT/credentials/current-launch.json"

EXPECTED_HOST=""
EXPECTED_SOURCE=""
EXTERNAL_BASE_URL=""
PROVIDER_CODE=""
EXPECTED_BUSINESS_TABLES=""
EXPECTED_FLYWAY_VERSION=""
CONFIRM_RESTART=0
CONFIRM_DATABASE=""
TLS_CA_FILE="${MEDKERNEL_TLS_CA_FILE:-}"
TLS_HOST=""
TLS_PORT=""

WORK_DIR=""
RESTORE_DATABASE=""
BACKUP_DIR=""
BEFORE_PID=""
AFTER_PID=""

info() { printf '[*] %s\n' "$*"; }
ok() { printf '[OK] %s\n' "$*"; }
die() { printf '[X] %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
MedKernel 134 全功能与全知识演练后验收

用法：
  medkernel-post-rehearsal-verify.sh \
    --expected-host <目标机 hostname> \
    --expected-source <40 位提交哈希> \
    --external-base-url https://<正式域名或具备 SAN 的地址>/medkernel \
    --provider-code ollama-launch \
    --expected-business-tables <候选模式清单表数> \
    --expected-flyway-version 1 \
    --confirm-restart \
    --confirm-database medkernel

验收内容：八段演练证据、严格 TLS、服务重启、四职责、全知识、模型生产、沙盘、审计、
演练后数据库备份与隔离恢复。脚本不输出密码、Cookie、JWT、模型凭据或患者数据。
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --expected-host) EXPECTED_HOST="${2:-}"; shift 2 ;;
    --expected-source) EXPECTED_SOURCE="${2:-}"; shift 2 ;;
    --external-base-url) EXTERNAL_BASE_URL="${2:-}"; shift 2 ;;
    --provider-code) PROVIDER_CODE="${2:-}"; shift 2 ;;
    --expected-business-tables) EXPECTED_BUSINESS_TABLES="${2:-}"; shift 2 ;;
    --expected-flyway-version) EXPECTED_FLYWAY_VERSION="${2:-}"; shift 2 ;;
    --confirm-restart) CONFIRM_RESTART=1; shift ;;
    --confirm-database) CONFIRM_DATABASE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数：$1" ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

parse_tls_endpoint() {
  local authority
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
  local tls_dir cert_file san_text
  local -a s_client_args curl_args
  parse_tls_endpoint
  tls_dir="$(mktemp -d)"
  cert_file="$tls_dir/leaf.pem"
  s_client_args=(-connect "${TLS_HOST}:${TLS_PORT}" -servername "$TLS_HOST" -verify_return_error -showcerts)
  curl_args=(--fail --silent --show-error)
  if [ -n "$TLS_CA_FILE" ]; then
    [ -f "$TLS_CA_FILE" ] || { rm -rf "$tls_dir"; die "TLS CA 文件不存在：$TLS_CA_FILE"; }
    s_client_args+=(-CAfile "$TLS_CA_FILE")
    curl_args+=(--cacert "$TLS_CA_FILE")
  fi
  openssl s_client "${s_client_args[@]}" </dev/null > "$tls_dir/chain.pem" 2> "$tls_dir/verify.log" ||
    { rm -rf "$tls_dir"; die "TLS 证书链验证失败"; }
  awk '
    /-----BEGIN CERTIFICATE-----/ { capture=1 }
    capture { print }
    /-----END CERTIFICATE-----/ { exit }
  ' "$tls_dir/chain.pem" > "$cert_file"
  [ -s "$cert_file" ] || { rm -rf "$tls_dir"; die "未获取 TLS 叶子证书"; }
  openssl x509 -in "$cert_file" -noout -checkend 0 >/dev/null ||
    { rm -rf "$tls_dir"; die "TLS 证书不在有效期内"; }
  san_text="$(openssl x509 -in "$cert_file" -noout -ext subjectAltName 2>/dev/null || true)"
  printf '%s\n' "$san_text" | grep -Eq 'DNS:|IP Address:' ||
    { rm -rf "$tls_dir"; die "TLS 证书缺少 SAN"; }
  if [[ "$TLS_HOST" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    openssl x509 -in "$cert_file" -noout -checkip "$TLS_HOST" >/dev/null ||
      { rm -rf "$tls_dir"; die "TLS 证书 SAN 不包含目标 IP"; }
  else
    openssl x509 -in "$cert_file" -noout -checkhost "$TLS_HOST" >/dev/null ||
      { rm -rf "$tls_dir"; die "TLS 证书 SAN 不包含目标主机名"; }
  fi
  curl "${curl_args[@]}" --output /dev/null \
    "$EXTERNAL_BASE_URL/actuator/health/readiness" ||
    { rm -rf "$tls_dir"; die "严格 TLS readiness 预检失败"; }
  rm -rf "$tls_dir"
  ok "TLS 可信链、SAN、有效期与严格 readiness 预检通过"
}

strict_curl() {
  if [ -n "$TLS_CA_FILE" ]; then
    command curl --cacert "$TLS_CA_FILE" "$@"
  else
    command curl "$@"
  fi
}

verify_chinese_evidence_font() {
  local font_match font_families
  font_match="$(fc-match 'Noto Sans CJK SC:lang=zh-cn' 2>/dev/null || true)"
  font_families="$(fc-list :lang=zh family 2>/dev/null || true)"
  {
    printf '%s\n' "$font_match"
    printf '%s\n' "$font_families"
  } | grep -Eiq 'Noto.*CJK|Source Han|WenQuanYi|Droid Sans Fallback|CJK|fangsong|uming|ukai' ||
    die "缺少中文 CJK 字体，浏览器 E2E 截图会出现方块字；请安装 google-noto-cjk-fonts 或同等级字体"
  ok "中文截图证据字体可用：${font_match%%:*}"
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

assert_equal() {
  local label="$1" expected="$2" actual="$3"
  [ "$actual" = "$expected" ] || die "${label} 不匹配：期望 ${expected}，实际 ${actual}"
}

assert_positive() {
  local label="$1" actual="$2"
  [[ "$actual" =~ ^[0-9]+$ ]] && [ "$actual" -gt 0 ] || die "$label 必须大于 0，实际 $actual"
}

cleanup() {
  if [ -n "$RESTORE_DATABASE" ]; then
    run_as_postgres dropdb --if-exists "$RESTORE_DATABASE" >/dev/null 2>&1 || true
  fi
  if [ -n "$WORK_DIR" ]; then
    rm -rf "$WORK_DIR"
  fi
}
trap cleanup EXIT

validate_inputs() {
  local actual_host mode manifest_source manifest_commit
  [ "$(id -u)" -eq 0 ] || die "需要 root 权限"
  actual_host="$(hostname)"
  [ -n "$EXPECTED_HOST" ] || die "缺少 --expected-host"
  assert_equal "目标主机" "$EXPECTED_HOST" "$actual_host"
  [[ "$EXPECTED_SOURCE" =~ ^[a-fA-F0-9]{40}$ ]] || die "--expected-source 必须是 40 位提交哈希"
  [[ "$EXTERNAL_BASE_URL" =~ ^https://[^/?#@]+(/[^?#]*)?/medkernel$ ]] ||
    die "--external-base-url 必须是无凭据、查询和片段且以 /medkernel 结尾的 HTTPS 地址"
  [[ "$PROVIDER_CODE" =~ ^[a-z0-9][a-z0-9._-]{0,63}$ ]] || die "Provider 编码非法"
  [[ "$EXPECTED_BUSINESS_TABLES" =~ ^[1-9][0-9]*$ ]] || die "业务表数量必须是正整数"
  [[ "$EXPECTED_FLYWAY_VERSION" =~ ^[1-9][0-9]*$ ]] || die "Flyway 版本必须是正整数"
  [ "$CONFIRM_RESTART" -eq 1 ] || die "缺少 --confirm-restart"
  assert_equal "确认数据库" "$DATABASE" "$CONFIRM_DATABASE"
  [[ "$DATABASE" =~ ^[a-zA-Z0-9_]+$ ]] || die "数据库名包含非法字符"
  [[ "$DATABASE_OWNER" =~ ^[a-zA-Z0-9_]+$ ]] || die "数据库 owner 包含非法字符"
  [[ "$PORT" =~ ^[0-9]+$ ]] || die "SERVER_PORT 不是有效端口"

  for command_name in curl jq pg_dump pg_restore psql createdb dropdb sha256sum systemctl sudo openssl fc-match; do
    require_command "$command_name"
  done
  [ -f "$FULL_SYSTEM_EVIDENCE" ] || die "缺少整套演练证据：$FULL_SYSTEM_EVIDENCE"
  [ -f "$FULL_KNOWLEDGE_EVIDENCE" ] || die "缺少全知识证据：$FULL_KNOWLEDGE_EVIDENCE"
  [ -f "$CREDENTIALS_FILE" ] || die "缺少统一上线凭据：$CREDENTIALS_FILE"
  mode="$(stat -c '%a' "$CREDENTIALS_FILE")"
  assert_equal "统一上线凭据权限" "600" "$mode"
  [ -f "$APP_HOME/manifest.properties" ] || die "缺少运行 manifest.properties"
  manifest_source="$(sed -n 's/^source=//p' "$APP_HOME/manifest.properties")"
  manifest_commit="$(sed -n 's/^commit=//p' "$APP_HOME/manifest.properties")"
  assert_equal "manifest source" "$EXPECTED_SOURCE" "$manifest_source"
  assert_equal "manifest commit" "$EXPECTED_SOURCE" "$manifest_commit"
  [ "$(systemctl is-active "$SERVICE")" = "active" ] || die "服务未处于 active"
  [ "$(systemctl is-enabled "$SERVICE")" = "enabled" ] || die "服务未处于 enabled"
  verify_chinese_evidence_font
  strict_tls_preflight

  mkdir -p "$EVIDENCE_ROOT" "$APP_HOME/backups"
  WORK_DIR="$(mktemp -d "$RUNTIME_ROOT/post-rehearsal-verify.XXXXXX")"
  chmod 700 "$WORK_DIR"
}

verify_full_system_evidence() {
  jq -e --arg source "$EXPECTED_SOURCE" '
    def allPassed($expected):
      type == "array" and
      length == $expected and
      all(.[]; .status == "PASSED" and .evidenceStage == "launch-coverage" and (.code | type == "string" and length > 0));
    .status == "PASSED" and
    .stage == "FULL_SYSTEM_REHEARSAL" and
    .source == $source and
    ([.stages[].id] == [
      "account-bootstrap",
      "model-provider",
      "platform-baseline",
      "sandbox",
      "full-knowledge",
      "runtime-resilience",
      "browser-e2e",
      "launch-coverage"
    ]) and
    all(.stages[]; .status == "PASSED") and
    (.coverage | type == "object") and
    (.coverage.productLayers | allPassed(6)) and
    (.coverage.standardPatientResources | allPassed(13)) and
    (.coverage.versionedAssets | allPassed(13)) and
    (.coverage.knowledgeDomains | allPassed(11)) and
    (.coverage.semanticFamilies | allPassed(16)) and
    (.coverage.specialtyDomains | allPassed(15)) and
    (.coverage.scenarios | allPassed(41)) and
    (.coverage.deliveryShapes | allPassed(5)) and
    (.coverage.serviceCombinations | allPassed(7)) and
    (.coverage.thirdPartySystemFamilies | allPassed(13)) and
    (.coverage.organizationLevels | allPassed(9)) and
    (.coverage.specialDiseaseStages | allPassed(10)) and
    (.coverage.modelEnablementSurfaces | allPassed(12))
  ' "$FULL_SYSTEM_EVIDENCE" >/dev/null || die "八段整套演练证据未完整通过"

  jq -e '
    .status == "PASSED" and
    ((.coverage.expectedDomains | unique | length) == 11) and
    ((.coverage.publishedDomains | unique | length) == 11) and
    (.versionLifecycle.v1VersionId != null) and
    (.versionLifecycle.v2VersionId != null) and
    (.versionLifecycle.rollbackActiveVersionId == .versionLifecycle.v1VersionId) and
    (.versionLifecycle.restoredActiveVersionId == .versionLifecycle.v2VersionId) and
    (.versionLifecycle.finalStatus == "ACTIVE")
  ' "$FULL_KNOWLEDGE_EVIDENCE" >/dev/null || die "全知识 11 域或 V1/V2 回滚恢复证据不完整"

  jq -e '
    .schemaVersion == "1.0.0" and .status == "READY" and
    .platform.tenantId == "t-1" and .rehearsal.tenantId == "t-rehearsal" and
    (.platform.accounts | keys | sort) == (["auditor","clinical-user","engine-operator","platform-admin"] | sort) and
    (.rehearsal.accounts | keys | sort) == (["auditor","clinical-user","engine-operator","platform-admin"] | sort)
  ' "$CREDENTIALS_FILE" >/dev/null || die "统一上线凭据不是四职责标准结构"
  ok "八段全功能与全知识证据结构通过"
}

restart_and_wait() {
  local waited state pid code
  BEFORE_PID="$(systemctl show "$SERVICE" -p MainPID --value)"
  assert_positive "重启前 MainPID" "$BEFORE_PID"
  info "真实重启服务并按 readiness 条件等待"
  systemctl restart "$SERVICE"
  waited=0
  while [ "$waited" -lt 90 ]; do
    state="$(systemctl show "$SERVICE" -p ActiveState --value)"
    pid="$(systemctl show "$SERVICE" -p MainPID --value)"
    code="$(curl --silent --show-error --output "$WORK_DIR/readiness-internal.json" \
      --write-out '%{http_code}' \
      "http://127.0.0.1:${PORT}/medkernel/actuator/health/readiness" || true)"
    if [ "$state" = "active" ] && [[ "$pid" =~ ^[1-9][0-9]*$ ]] && [ "$code" = "200" ]; then
      AFTER_PID="$pid"
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
  [ -n "$AFTER_PID" ] || die "服务未在 90 秒内恢复 readiness"
  [ "$AFTER_PID" != "$BEFORE_PID" ] || die "服务重启后 MainPID 未变化"
  jq -e '.status == "UP"' "$WORK_DIR/readiness-internal.json" >/dev/null ||
    die "重启后内部 readiness 不是 UP"
  ok "服务真实重启并恢复 readiness"
}

verify_external_tls_and_api() {
  local readiness_code bootstrap_code login_code provider_code readiness_api_code
  local tenant_id username password
  readiness_code="$(strict_curl --fail --silent --show-error \
    --output "$WORK_DIR/readiness-external.json" --write-out '%{http_code}' \
    "$EXTERNAL_BASE_URL/actuator/health/readiness")"
  assert_equal "严格 TLS readiness HTTP" "200" "$readiness_code"
  jq -e '.status == "UP"' "$WORK_DIR/readiness-external.json" >/dev/null ||
    die "严格 TLS readiness 不是 UP"

  bootstrap_code="$(strict_curl --fail --silent --show-error \
    --output "$WORK_DIR/bootstrap-status.json" --write-out '%{http_code}' \
    "$EXTERNAL_BASE_URL/api/v1/bootstrap/status")"
  assert_equal "严格 TLS bootstrap HTTP" "200" "$bootstrap_code"
  jq -e '.data.initialized == true' "$WORK_DIR/bootstrap-status.json" >/dev/null ||
    die "重启后系统没有保持已接管状态"

  tenant_id="$(jq -er '.platform.accounts["engine-operator"].tenantId' "$CREDENTIALS_FILE")"
  username="$(jq -er '.platform.accounts["engine-operator"].username' "$CREDENTIALS_FILE")"
  password="$(jq -er '.platform.accounts["engine-operator"].password' "$CREDENTIALS_FILE")"
  : > "$WORK_DIR/cookies"
  chmod 600 "$WORK_DIR/cookies"
  login_code="$({
      jq -nc --arg tenantId "$tenant_id" --arg username "$username" --arg password "$password" \
        '{tenantId:$tenantId,username:$username,password:$password}'
    } | strict_curl --fail --silent --show-error \
      --cookie-jar "$WORK_DIR/cookies" \
      --header 'Content-Type: application/json' \
      --data-binary @- \
      --output "$WORK_DIR/login.json" --write-out '%{http_code}' \
      "$EXTERNAL_BASE_URL/api/v1/auth/login")"
  unset password
  assert_equal "重启后登录 HTTP" "200" "$login_code"
  jq -e --arg tenant "$tenant_id" '
    .data.tenantId == $tenant and
    .data.roles == ["engine-operator"] and
    .data.mustChangePwd == false and
    .data.mfaRequired == false and
    .data.mfaBound == false
  ' "$WORK_DIR/login.json" >/dev/null || die "重启后医疗引擎运营员登录或 MFA 默认状态异常"

  provider_code="$(strict_curl --fail --silent --show-error \
    --cookie "$WORK_DIR/cookies" \
    --output "$WORK_DIR/provider.json" --write-out '%{http_code}' \
    "$EXTERNAL_BASE_URL/api/v1/model-providers/$PROVIDER_CODE")"
  assert_equal "重启后 Provider HTTP" "200" "$provider_code"
  jq -e --arg code "$PROVIDER_CODE" '
    .data.providerCode == $code and .data.enabled == true and .data.status == "HEALTHY"
  ' "$WORK_DIR/provider.json" >/dev/null || die "重启后 Provider 没有保持启用且健康"

  readiness_api_code="$(strict_curl --fail --silent --show-error \
    --cookie "$WORK_DIR/cookies" \
    --get \
    --data-urlencode 'producer=API_MODEL' \
    --data-urlencode 'capabilityCode=knowledge.production.knowledge' \
    --data-urlencode "providerCode=$PROVIDER_CODE" \
    --output "$WORK_DIR/knowledge-readiness.json" --write-out '%{http_code}' \
    "$EXTERNAL_BASE_URL/api/v1/engine/knowledge-production/readiness")"
  assert_equal "重启后知识 readiness HTTP" "200" "$readiness_api_code"
  jq -e --arg code "$PROVIDER_CODE" '
    .data.providerCode == $code and .data.ready == true and .data.modelInvocationAllowed == true and
    all(.data.items[]; (.required != true) or (.ready == true))
  ' "$WORK_DIR/knowledge-readiness.json" >/dev/null || die "重启后模型知识生产 readiness 未保持全绿"

  jq '{status:"PASSED",tenantId:.data.tenantId,userId:.data.userId,roles:.data.roles,mfaRequired:.data.mfaRequired,mfaBound:.data.mfaBound}' \
    "$WORK_DIR/login.json" > "$WORK_DIR/post-restart-login.json"
  jq '{status:"PASSED",provider:{code:.data.providerCode,type:.data.providerType,modelVersion:.data.modelVersion,enabled:.data.enabled,status:.data.status,version:.data.version}}' \
    "$WORK_DIR/provider.json" > "$WORK_DIR/post-restart-provider.json"
  jq '{status:"PASSED",readiness:{providerCode:.data.providerCode,ready:.data.ready,modelInvocationAllowed:.data.modelInvocationAllowed,items:.data.items}}' \
    "$WORK_DIR/knowledge-readiness.json" > "$WORK_DIR/post-restart-knowledge-readiness.json"
  ok "严格 TLS、重启后登录、Provider 与知识 readiness 通过"
}

verify_live_database() {
  local expected_tables flyway_success flyway_failed flyway_version public_tables
  local users customer_role_assignments customer_role_kinds invalid_roles platform_roles rehearsal_roles
  local system_superadmin_assignments non_platform_superadmin_assignments
  local identities domains current_active versions lifecycle_identity lifecycle_v1 lifecycle_v2
  local lifecycle_versions lifecycle_current jobs model_tasks gate_jobs gate_failures shadow_review_jobs shadow_failures
  local sandbox_rules sandbox_cases sandbox_runtime_releases sandbox_runtime_hospitals
  local sandbox_runtime_active_items sandbox_runtime_disabled_items provider_rows eval_rows audit_events

  expected_tables=$((EXPECTED_BUSINESS_TABLES + 1))
  flyway_success="$(database_query "$DATABASE" 'select count(*) from flyway_schema_history where success=true;')"
  flyway_failed="$(database_query "$DATABASE" 'select count(*) from flyway_schema_history where success=false;')"
  flyway_version="$(database_query "$DATABASE" 'select version from flyway_schema_history where success=true order by installed_rank desc limit 1;')"
  public_tables="$(database_query "$DATABASE" "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE';")"
  assert_equal "成功 Flyway 迁移数量" "1" "$flyway_success"
  assert_equal "失败 Flyway 迁移数量" "0" "$flyway_failed"
  assert_equal "Flyway 版本" "$EXPECTED_FLYWAY_VERSION" "$flyway_version"
  assert_equal "public 基础表数量" "$expected_tables" "$public_tables"

  users="$(database_query "$DATABASE" "select count(*) from tenant_user where tenant_id in ('t-1','t-rehearsal') and status='ACTIVE';")"
  customer_role_assignments="$(database_query "$DATABASE" "select count(*) from user_role_assignment where tenant_id in ('t-1','t-rehearsal') and active_flag='Y' and role_code in ('platform-admin','engine-operator','clinical-user','auditor');")"
  customer_role_kinds="$(database_query "$DATABASE" "select count(distinct role_code) from user_role_assignment where tenant_id in ('t-1','t-rehearsal') and active_flag='Y' and role_code in ('platform-admin','engine-operator','clinical-user','auditor');")"
  invalid_roles="$(database_query "$DATABASE" "select count(*) from user_role_assignment where tenant_id in ('t-1','t-rehearsal') and active_flag='Y' and role_code not in ('platform-admin','engine-operator','clinical-user','auditor','system-superadmin');")"
  platform_roles="$(database_query "$DATABASE" "select count(*) from user_role_assignment where tenant_id='t-1' and active_flag='Y' and role_code in ('platform-admin','engine-operator','clinical-user','auditor');")"
  rehearsal_roles="$(database_query "$DATABASE" "select count(*) from user_role_assignment where tenant_id='t-rehearsal' and active_flag='Y' and role_code in ('platform-admin','engine-operator','clinical-user','auditor');")"
  system_superadmin_assignments="$(database_query "$DATABASE" "select count(*) from user_role_assignment where tenant_id in ('t-1','t-rehearsal') and active_flag='Y' and role_code='system-superadmin';")"
  non_platform_superadmin_assignments="$(database_query "$DATABASE" "select count(*) from user_role_assignment where tenant_id<>'t-1' and active_flag='Y' and role_code='system-superadmin';")"
  assert_equal "九个上线身份" "9" "$users"
  assert_equal "客户四职责有效分配" "12" "$customer_role_assignments"
  assert_equal "客户四职责种类" "4" "$customer_role_kinds"
  assert_equal "非法旧角色分配" "0" "$invalid_roles"
  assert_equal "平台客户四职责" "4" "$platform_roles"
  assert_equal "演练机构客户四职责" "8" "$rehearsal_roles"
  assert_equal "系统超级管理员保留分配" "1" "$system_superadmin_assignments"
  assert_equal "非平台超级管理员分配" "0" "$non_platform_superadmin_assignments"

  identities="$(database_query "$DATABASE" "select count(*) from knowledge_identity where tenant_id='t-1' and identity_code like 'launch.%';")"
  domains="$(database_query "$DATABASE" "select count(distinct domain) from knowledge_identity where tenant_id='t-1' and identity_code like 'launch.%';")"
  current_active="$(database_query "$DATABASE" "select count(*) from knowledge_identity i join knowledge_asset_version v on v.id=i.current_version_id and v.identity_id=i.id where i.tenant_id='t-1' and i.identity_code like 'launch.%' and i.status='ACTIVE' and v.status='ACTIVE';")"
  versions="$(database_query "$DATABASE" "select count(*) from knowledge_asset_version v join knowledge_identity i on i.id=v.identity_id where i.tenant_id='t-1' and i.identity_code like 'launch.%';")"
  assert_equal "正式全知识身份" "11" "$identities"
  assert_equal "正式全知识领域" "11" "$domains"
  assert_equal "正式全知识当前 ACTIVE" "11" "$current_active"
  assert_equal "十一域 V1 加代表域 V2 版本" "12" "$versions"

  lifecycle_identity="$(jq -er '.versionLifecycle.identityCode' "$FULL_KNOWLEDGE_EVIDENCE")"
  lifecycle_v1="$(jq -er '.versionLifecycle.v1VersionId' "$FULL_KNOWLEDGE_EVIDENCE")"
  lifecycle_v2="$(jq -er '.versionLifecycle.v2VersionId' "$FULL_KNOWLEDGE_EVIDENCE")"
  [[ "$lifecycle_identity" =~ ^launch\.[a-z0-9._-]+$ ]] || die "代表知识 identityCode 非法"
  [[ "$lifecycle_v1" =~ ^[1-9][0-9]*$ ]] || die "代表知识 V1 ID 非法"
  [[ "$lifecycle_v2" =~ ^[1-9][0-9]*$ ]] || die "代表知识 V2 ID 非法"
  lifecycle_versions="$(database_query "$DATABASE" "select count(*) from knowledge_asset_version v join knowledge_identity i on i.id=v.identity_id where i.tenant_id='t-1' and i.identity_code='$lifecycle_identity' and v.id in ($lifecycle_v1,$lifecycle_v2);")"
  lifecycle_current="$(database_query "$DATABASE" "select count(*) from knowledge_identity where tenant_id='t-1' and identity_code='$lifecycle_identity' and current_version_id=$lifecycle_v2;")"
  assert_equal "代表知识 V1/V2 持久化" "2" "$lifecycle_versions"
  assert_equal "回滚后恢复 V2 当前版本" "1" "$lifecycle_current"

  jobs="$(database_query "$DATABASE" "select count(*) from mk_knowledge_production_job where tenant_id='t-1' and producer='API_MODEL' and status='COMPLETED';")"
  model_tasks="$(database_query "$DATABASE" "select count(*) from model_capability_task where tenant_id='t-1' and capability_code='knowledge.production.knowledge' and status='SUCCEEDED' and model_mode<>'B0';")"
  gate_jobs="$(database_query "$DATABASE" "select count(distinct job_code) from mk_aik_gate_result where tenant_id='t-1';")"
  gate_failures="$(database_query "$DATABASE" "select count(*) from mk_aik_gate_result where tenant_id='t-1' and passed=false;")"
  shadow_review_jobs="$(database_query "$DATABASE" "select count(distinct job_code) from mk_knowledge_shadow_run where tenant_id='t-1' and status in ('PASSED','PENDING_REVIEW') and ready_for_review=true and degradation_detected=false;")"
  shadow_failures="$(database_query "$DATABASE" "select count(*) from mk_knowledge_shadow_run where tenant_id='t-1' and (status not in ('PASSED','PENDING_REVIEW') or ready_for_review=false or degradation_detected=true);")"
  assert_equal "模型知识生产任务" "12" "$jobs"
  assert_equal "真实模型成功调用" "12" "$model_tasks"
  assert_equal "安全门覆盖任务" "12" "$gate_jobs"
  assert_equal "安全门失败" "0" "$gate_failures"
  assert_equal "影子评测可复核任务" "12" "$shadow_review_jobs"
  assert_equal "影子评测失败或退化" "0" "$shadow_failures"

  sandbox_rules="$(database_query "$DATABASE" "select count(*) from rule_definition where tenant_id='t-rehearsal' and rule_code like 'SBX.%';")"
  sandbox_cases="$(database_query "$DATABASE" "select count(*) from rule_test_case c join rule_definition r on r.tenant_id=c.tenant_id and r.rule_id=c.rule_id where r.tenant_id='t-rehearsal' and r.rule_code like 'SBX.%';")"
  sandbox_runtime_releases="$(database_query "$DATABASE" "select count(*) from clinical_runtime_release where tenant_id='t-rehearsal';")"
  sandbox_runtime_hospitals="$(database_query "$DATABASE" "select count(distinct hospital_id) from clinical_runtime_release where tenant_id='t-rehearsal';")"
  sandbox_runtime_active_items="$(database_query "$DATABASE" "with latest as (select distinct on (tenant_id, hospital_id) release_id from clinical_runtime_release where tenant_id='t-rehearsal' order by tenant_id, hospital_id, revision_no desc, activated_at desc) select count(*) from latest l join clinical_runtime_release_item i on i.release_id=l.release_id where i.entry_state='ACTIVE';")"
  sandbox_runtime_disabled_items="$(database_query "$DATABASE" "with latest as (select distinct on (tenant_id, hospital_id) release_id from clinical_runtime_release where tenant_id='t-rehearsal' order by tenant_id, hospital_id, revision_no desc, activated_at desc) select count(*) from latest l join clinical_runtime_release_item i on i.release_id=l.release_id where i.entry_state='DISABLED';")"
  assert_equal "沙盘规则" "10" "$sandbox_rules"
  assert_equal "沙盘测试用例" "40" "$sandbox_cases"
  assert_positive "沙盘 CURRENT 机构生效版本" "$sandbox_runtime_releases"
  assert_equal "沙盘 CURRENT 医院数量" "1" "$sandbox_runtime_hospitals"
  assert_equal "沙盘 CURRENT 生效清单条目" "12" "$sandbox_runtime_active_items"
  assert_equal "沙盘 CURRENT 停用清单条目" "0" "$sandbox_runtime_disabled_items"

  provider_rows="$(database_query "$DATABASE" "select count(*) from mk_llm_provider where tenant_id='t-1' and provider_code='$PROVIDER_CODE' and enabled_flag='Y' and status='HEALTHY';")"
  eval_rows="$(database_query "$DATABASE" "select count(*) from mk_llm_eval_run where tenant_id='t-1' and provider_code='$PROVIDER_CODE' and capability_code='knowledge.production.knowledge' and total_cases=3 and passed_cases=3 and failed_cases=0 and status='PASSED' and fake_citation_detected='N' and red_line_breach='N' and hallucination_detected='N';")"
  audit_events="$(database_query "$DATABASE" "select count(*) from audit_event where tenant_id in ('t-1','t-rehearsal');")"
  assert_equal "启用健康 Provider" "1" "$provider_rows"
  assert_equal "Provider 三例医学回归" "1" "$eval_rows"
  assert_positive "演练审计事件" "$audit_events"

  cat > "$WORK_DIR/database-counts.properties" <<EOF
flyway_success_count=$flyway_success
flyway_failed_count=$flyway_failed
flyway_version=$flyway_version
public_base_tables=$public_tables
active_launch_users=$users
customer_role_assignments=$customer_role_assignments
customer_role_kinds=$customer_role_kinds
system_superadmin_assignments=$system_superadmin_assignments
knowledge_identities=$identities
knowledge_domains=$domains
knowledge_current_active=$current_active
knowledge_versions=$versions
model_production_jobs=$jobs
model_success_tasks=$model_tasks
shadow_review_ready_jobs=$shadow_review_jobs
shadow_failure_or_degradation_rows=$shadow_failures
sandbox_rules=$sandbox_rules
sandbox_cases=$sandbox_cases
sandbox_runtime_releases=$sandbox_runtime_releases
sandbox_runtime_hospitals=$sandbox_runtime_hospitals
sandbox_runtime_active_items=$sandbox_runtime_active_items
sandbox_runtime_disabled_items=$sandbox_runtime_disabled_items
provider_healthy_enabled=$provider_rows
provider_eval_passed=$eval_rows
audit_events=$audit_events
EOF
  ok "关系库全功能与全知识持久化验证通过"
}

backup_and_restore_database() {
  local timestamp source_short dump_file restored_tables restored_customer_roles restored_role_kinds
  local restored_invalid_roles restored_superadmin restored_non_platform_superadmin restored_knowledge
  local restored_shadow_review_jobs restored_shadow_failures restored_provider restored_rules restored_cases
  local restored_runtime_active_items restored_runtime_disabled_items restored_audit
  timestamp="$(date '+%Y%m%d-%H%M%S')"
  source_short="${EXPECTED_SOURCE:0:12}"
  BACKUP_DIR="$APP_HOME/backups/launch-acceptance-${source_short}-${timestamp}"
  mkdir -p "$BACKUP_DIR/database" "$BACKUP_DIR/evidence"
  chmod 700 "$BACKUP_DIR"
  dump_file="$BACKUP_DIR/database/medkernel-post-rehearsal.dump"
  info "创建演练后数据库备份并执行隔离恢复"
  run_as_postgres pg_dump --format=custom --no-owner --no-acl "$DATABASE" > "$dump_file"
  [ -s "$dump_file" ] || die "演练后数据库备份为空"
  (
    cd "$BACKUP_DIR/database"
    sha256sum "$(basename "$dump_file")" > SHA256SUMS
    sha256sum -c SHA256SUMS >/dev/null
  ) || die "演练后数据库备份摘要生成或校验失败"

  RESTORE_DATABASE="${DATABASE}_launch_acceptance_$$"
  run_as_postgres dropdb --if-exists "$RESTORE_DATABASE"
  run_as_postgres createdb --owner="$DATABASE_OWNER" "$RESTORE_DATABASE"
  run_as_postgres pg_restore --exit-on-error --no-owner --no-acl \
    --dbname "$RESTORE_DATABASE" < "$dump_file"

  restored_tables="$(database_query "$RESTORE_DATABASE" "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE';")"
  restored_customer_roles="$(database_query "$RESTORE_DATABASE" "select count(*) from user_role_assignment where tenant_id in ('t-1','t-rehearsal') and active_flag='Y' and role_code in ('platform-admin','engine-operator','clinical-user','auditor');")"
  restored_role_kinds="$(database_query "$RESTORE_DATABASE" "select count(distinct role_code) from user_role_assignment where tenant_id in ('t-1','t-rehearsal') and active_flag='Y' and role_code in ('platform-admin','engine-operator','clinical-user','auditor');")"
  restored_invalid_roles="$(database_query "$RESTORE_DATABASE" "select count(*) from user_role_assignment where tenant_id in ('t-1','t-rehearsal') and active_flag='Y' and role_code not in ('platform-admin','engine-operator','clinical-user','auditor','system-superadmin');")"
  restored_superadmin="$(database_query "$RESTORE_DATABASE" "select count(*) from user_role_assignment where tenant_id in ('t-1','t-rehearsal') and active_flag='Y' and role_code='system-superadmin';")"
  restored_non_platform_superadmin="$(database_query "$RESTORE_DATABASE" "select count(*) from user_role_assignment where tenant_id<>'t-1' and active_flag='Y' and role_code='system-superadmin';")"
  restored_knowledge="$(database_query "$RESTORE_DATABASE" "select count(*) from knowledge_identity i join knowledge_asset_version v on v.id=i.current_version_id and v.identity_id=i.id where i.tenant_id='t-1' and i.identity_code like 'launch.%' and v.status='ACTIVE';")"
  restored_shadow_review_jobs="$(database_query "$RESTORE_DATABASE" "select count(distinct job_code) from mk_knowledge_shadow_run where tenant_id='t-1' and status in ('PASSED','PENDING_REVIEW') and ready_for_review=true and degradation_detected=false;")"
  restored_shadow_failures="$(database_query "$RESTORE_DATABASE" "select count(*) from mk_knowledge_shadow_run where tenant_id='t-1' and (status not in ('PASSED','PENDING_REVIEW') or ready_for_review=false or degradation_detected=true);")"
  restored_provider="$(database_query "$RESTORE_DATABASE" "select count(*) from mk_llm_provider where tenant_id='t-1' and provider_code='$PROVIDER_CODE' and enabled_flag='Y' and status='HEALTHY';")"
  restored_rules="$(database_query "$RESTORE_DATABASE" "select count(*) from rule_definition where tenant_id='t-rehearsal' and rule_code like 'SBX.%';")"
  restored_cases="$(database_query "$RESTORE_DATABASE" "select count(*) from rule_test_case c join rule_definition r on r.tenant_id=c.tenant_id and r.rule_id=c.rule_id where r.tenant_id='t-rehearsal' and r.rule_code like 'SBX.%';")"
  restored_runtime_active_items="$(database_query "$RESTORE_DATABASE" "with latest as (select distinct on (tenant_id, hospital_id) release_id from clinical_runtime_release where tenant_id='t-rehearsal' order by tenant_id, hospital_id, revision_no desc, activated_at desc) select count(*) from latest l join clinical_runtime_release_item i on i.release_id=l.release_id where i.entry_state='ACTIVE';")"
  restored_runtime_disabled_items="$(database_query "$RESTORE_DATABASE" "with latest as (select distinct on (tenant_id, hospital_id) release_id from clinical_runtime_release where tenant_id='t-rehearsal' order by tenant_id, hospital_id, revision_no desc, activated_at desc) select count(*) from latest l join clinical_runtime_release_item i on i.release_id=l.release_id where i.entry_state='DISABLED';")"
  restored_audit="$(database_query "$RESTORE_DATABASE" "select count(*) from audit_event where tenant_id in ('t-1','t-rehearsal');")"
  assert_equal "隔离恢复表数量" "$((EXPECTED_BUSINESS_TABLES + 1))" "$restored_tables"
  assert_equal "隔离恢复客户四职责分配" "12" "$restored_customer_roles"
  assert_equal "隔离恢复客户四职责种类" "4" "$restored_role_kinds"
  assert_equal "隔离恢复非法旧角色分配" "0" "$restored_invalid_roles"
  assert_equal "隔离恢复系统超级管理员分配" "1" "$restored_superadmin"
  assert_equal "隔离恢复非平台超级管理员分配" "0" "$restored_non_platform_superadmin"
  assert_equal "隔离恢复全知识 ACTIVE" "11" "$restored_knowledge"
  assert_equal "隔离恢复影子评测可复核任务" "12" "$restored_shadow_review_jobs"
  assert_equal "隔离恢复影子评测失败或退化" "0" "$restored_shadow_failures"
  assert_equal "隔离恢复 Provider" "1" "$restored_provider"
  assert_equal "隔离恢复沙盘规则" "10" "$restored_rules"
  assert_equal "隔离恢复沙盘用例" "40" "$restored_cases"
  assert_equal "隔离恢复沙盘 CURRENT 生效清单条目" "12" "$restored_runtime_active_items"
  assert_equal "隔离恢复沙盘 CURRENT 停用清单条目" "0" "$restored_runtime_disabled_items"
  assert_positive "隔离恢复审计事件" "$restored_audit"

  cat > "$BACKUP_DIR/evidence/restore.properties" <<EOF
database_restore_status=PASSED
restore_public_base_tables=$restored_tables
restore_customer_role_assignments=$restored_customer_roles
restore_customer_role_kinds=$restored_role_kinds
restore_system_superadmin_assignments=$restored_superadmin
restore_knowledge_current_active=$restored_knowledge
restore_shadow_review_ready_jobs=$restored_shadow_review_jobs
restore_shadow_failure_or_degradation_rows=$restored_shadow_failures
restore_provider_healthy_enabled=$restored_provider
restore_sandbox_rules=$restored_rules
restore_sandbox_cases=$restored_cases
restore_sandbox_runtime_active_items=$restored_runtime_active_items
restore_sandbox_runtime_disabled_items=$restored_runtime_disabled_items
restore_audit_events=$restored_audit
database_dump_sha256=$(sha256sum "$dump_file" | awk '{print $1}')
EOF
  run_as_postgres dropdb --if-exists "$RESTORE_DATABASE"
  RESTORE_DATABASE=""
  ok "演练后数据库备份与隔离恢复通过"
}

write_acceptance_evidence() {
  local output_tmp dump_file
  dump_file="$BACKUP_DIR/database/medkernel-post-rehearsal.dump"
  output_tmp="$WORK_DIR/release-acceptance.properties"
  cat > "$output_tmp" <<EOF
release_status=PASSED
verified_at=$(date -Iseconds)
source=$EXPECTED_SOURCE
host=$EXPECTED_HOST
database=$DATABASE
service=$SERVICE
service_pid_before=$BEFORE_PID
service_pid_after=$AFTER_PID
external_base_url=$EXTERNAL_BASE_URL
strict_tls_verified=true
full_system_stage_count=8
full_system_evidence_sha256=$(sha256sum "$FULL_SYSTEM_EVIDENCE" | awk '{print $1}')
full_knowledge_evidence_sha256=$(sha256sum "$FULL_KNOWLEDGE_EVIDENCE" | awk '{print $1}')
database_dump_sha256=$(sha256sum "$dump_file" | awk '{print $1}')
database_restore_status=PASSED
backup_dir=$BACKUP_DIR
EOF
  install -m 600 "$output_tmp" "$EVIDENCE_ROOT/release-acceptance.properties"
  install -m 600 "$WORK_DIR/database-counts.properties" "$EVIDENCE_ROOT/database-counts.properties"
  install -m 600 "$WORK_DIR/post-restart-login.json" "$EVIDENCE_ROOT/post-restart-login.json"
  install -m 600 "$WORK_DIR/post-restart-provider.json" "$EVIDENCE_ROOT/post-restart-provider.json"
  install -m 600 "$WORK_DIR/post-restart-knowledge-readiness.json" \
    "$EVIDENCE_ROOT/post-restart-knowledge-readiness.json"
  cp "$EVIDENCE_ROOT/release-acceptance.properties" "$BACKUP_DIR/evidence/"
  cp "$EVIDENCE_ROOT/database-counts.properties" "$BACKUP_DIR/evidence/"
  sha256sum "$BACKUP_DIR"/evidence/* > "$BACKUP_DIR/evidence/SHA256SUMS"
  ok "完整上线验收证据已写入 $EVIDENCE_ROOT/release-acceptance.properties"
}

main() {
  validate_inputs
  verify_full_system_evidence
  restart_and_wait
  verify_external_tls_and_api
  verify_live_database
  backup_and_restore_database
  write_acceptance_evidence
}

main
