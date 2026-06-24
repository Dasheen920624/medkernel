#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deploy/onprem/medkernel-deploy.sh"
SERVICE_UNIT="$ROOT/deploy/onprem/templates/medkernel.service"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

APP_HOME="$TMP_ROOT/app"
mkdir -p "$APP_HOME/conf"
cat > "$APP_HOME/conf/medkernel.env" <<'ENV'
MEDKERNEL_BOOTSTRAP_INIT_TOKEN=FreshRuntimeToken_20260612
ENV
printf 'OldDeliveryToken\n' > "$APP_HOME/conf/bootstrap-init-token.txt"
chmod 600 "$APP_HOME/conf/bootstrap-init-token.txt"

MEDKERNEL_APP_HOME="$APP_HOME" bash "$SCRIPT" --help > "$TMP_ROOT/help.log"
grep -q '失败自动回滚' "$TMP_ROOT/help.log"

MEDKERNEL_APP_HOME="$APP_HOME" MEDKERNEL_DEPLOY_APP_USER="$(id -un)" \
  bash "$SCRIPT" --sync-bootstrap-token >/tmp/medkernel-onprem-deploy-test.log

test "$(cat "$APP_HOME/conf/bootstrap-init-token.txt")" = "FreshRuntimeToken_20260612"
if stat -c '%a' "$APP_HOME/conf/bootstrap-init-token.txt" >/dev/null 2>&1; then
  mode="$(stat -c '%a' "$APP_HOME/conf/bootstrap-init-token.txt")"
else
  mode="$(stat -f '%Lp' "$APP_HOME/conf/bootstrap-init-token.txt")"
fi
test "$mode" = "600"
if grep -q 'FreshRuntimeToken_20260612' /tmp/medkernel-onprem-deploy-test.log; then
  printf 'bootstrap token leaked to deployment log\n' >&2
  exit 1
fi
grep -q '^SuccessExitStatus=143$' "$SERVICE_UNIT"
grep -q '^MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT=development$' \
  "$ROOT/deploy/onprem/templates/medkernel.env.example"
grep -q '^update_runtime_release_fingerprint(){' "$SCRIPT"
grep -q 'RELEASE_FINGERPRINT="${SRC_TXT:-sha256:' "$SCRIPT"
grep -q '已恢复运行环境文件' "$SCRIPT"
grep -q 'sha256sum.*SHA256SUMS' "$SCRIPT"
grep -q 'sha256sum -c.*SHA256SUMS' "$SCRIPT"
grep -q 'pg_dump.*--format=custom' "$SCRIPT"
grep -q 'pg_restore.*--exit-on-error' "$SCRIPT"
grep -q 'dropdb --if-exists "\$DATABASE"' "$SCRIPT"
grep -q 'createdb --owner="\$DATABASE_OWNER" "\$DATABASE"' "$SCRIPT"
grep -q 'DATABASE_RESTORE_REQUIRED=true' "$SCRIPT"
grep -q '^restore_previous_release()' "$SCRIPT"
grep -q "trap .*ERR" "$SCRIPT"
grep -q "trap .*INT" "$SCRIPT"
grep -q "trap .*TERM" "$SCRIPT"
grep -q 'medkernel.service' "$SCRIPT"
grep -q 'medkernel.conf' "$SCRIPT"
grep -q 'health_check' "$SCRIPT"
if grep -q -- '--no-rollback' "$SCRIPT"; then
  printf 'deployment script still allows mandatory rollback to be disabled\n' >&2
  exit 1
fi
if grep -Eq 'curl.*([[:space:]]--insecure|[[:space:]]-[[:alpha:]]*k[[:alpha:]]*)' "$SCRIPT"; then
  printf 'deployment status may not bypass TLS validation\n' >&2
  exit 1
fi
if rg -nP '\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]' "$SCRIPT"; then
  printf 'deployment script has an unbraced variable adjacent to non-ASCII text under set -u\n' >&2
  exit 1
fi

printf 'onprem deployment script contract passed\n'
