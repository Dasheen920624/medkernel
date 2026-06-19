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

MEDKERNEL_APP_HOME="$APP_HOME" MEDKERNEL_DEPLOY_APP_USER="$(id -un)" \
  bash "$SCRIPT" --sync-bootstrap-token >/tmp/medkernel-onprem-deploy-test.log

test "$(cat "$APP_HOME/conf/bootstrap-init-token.txt")" = "FreshRuntimeToken_20260612"
if stat -c '%a' "$APP_HOME/conf/bootstrap-init-token.txt" >/dev/null 2>&1; then
  mode="$(stat -c '%a' "$APP_HOME/conf/bootstrap-init-token.txt")"
else
  mode="$(stat -f '%Lp' "$APP_HOME/conf/bootstrap-init-token.txt")"
fi
test "$mode" = "600"
! grep -q 'FreshRuntimeToken_20260612' /tmp/medkernel-onprem-deploy-test.log
grep -q '^SuccessExitStatus=143$' "$SERVICE_UNIT"
grep -q '^MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT=development$' \
  "$ROOT/deploy/onprem/templates/medkernel.env.example"
grep -q '^update_runtime_release_fingerprint(){' "$SCRIPT"
grep -q 'RELEASE_FINGERPRINT="${SRC_TXT:-sha256:' "$SCRIPT"
grep -q '已恢复运行环境文件' "$SCRIPT"

printf 'onprem deployment script contract passed\n'
