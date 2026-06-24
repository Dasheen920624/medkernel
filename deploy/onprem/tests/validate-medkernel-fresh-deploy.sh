#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deploy/onprem/medkernel-fresh-deploy.sh"
ENV_TEMPLATE="$ROOT/deploy/onprem/templates/medkernel.env.example"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

test -f "$SCRIPT"
bash -n "$SCRIPT"
bash "$SCRIPT" --help > "$TMP_ROOT/help.log"
grep -q 'ERR/INT/TERM' "$TMP_ROOT/help.log"
grep -q '^MEDKERNEL_FIELD_ENCRYPTION_KEY=<RANDOM_32B_BASE64URL>$' "$ENV_TEMPLATE"

mkdir -p "$TMP_ROOT/app/conf"
cat > "$TMP_ROOT/app/conf/medkernel.env" <<'ENV'
MEDKERNEL_AUTH_JWT_SECRET=jwt-secret-at-least-thirty-two-bytes
MEDKERNEL_INTEGRATION_SECRET_KEY=integration-secret-at-least-thirty-two-bytes
MEDKERNEL_BOOTSTRAP_INIT_TOKEN=bootstrap-token-at-least-thirty-two-bytes
ENV
chmod 600 "$TMP_ROOT/app/conf/medkernel.env"

if MEDKERNEL_APP_HOME="$TMP_ROOT/app" \
  bash "$SCRIPT" --validate-environment-only >"$TMP_ROOT/missing-key.log" 2>&1; then
  printf 'missing field encryption key was accepted\n' >&2
  exit 1
fi
grep -q 'MEDKERNEL_FIELD_ENCRYPTION_KEY' "$TMP_ROOT/missing-key.log"

printf 'MEDKERNEL_FIELD_ENCRYPTION_KEY=short\n' >> "$TMP_ROOT/app/conf/medkernel.env"
if MEDKERNEL_APP_HOME="$TMP_ROOT/app" \
  bash "$SCRIPT" --validate-environment-only >"$TMP_ROOT/short-key.log" 2>&1; then
  printf 'short field encryption key was accepted\n' >&2
  exit 1
fi
grep -q 'MEDKERNEL_FIELD_ENCRYPTION_KEY' "$TMP_ROOT/short-key.log"

sed -i.bak \
  's/^MEDKERNEL_FIELD_ENCRYPTION_KEY=.*/MEDKERNEL_FIELD_ENCRYPTION_KEY="<RANDOM_32B_BASE64URL>"/' \
  "$TMP_ROOT/app/conf/medkernel.env"
if MEDKERNEL_APP_HOME="$TMP_ROOT/app" \
  bash "$SCRIPT" --validate-environment-only >"$TMP_ROOT/placeholder-key.log" 2>&1; then
  printf 'placeholder field encryption key was accepted\n' >&2
  exit 1
fi
grep -q 'MEDKERNEL_FIELD_ENCRYPTION_KEY' "$TMP_ROOT/placeholder-key.log"

printf -v valid_field_secret '%032d' 0
sed -i.bak \
  "s/^MEDKERNEL_FIELD_ENCRYPTION_KEY=.*/MEDKERNEL_FIELD_ENCRYPTION_KEY=$valid_field_secret/" \
  "$TMP_ROOT/app/conf/medkernel.env"
MEDKERNEL_APP_HOME="$TMP_ROOT/app" \
  bash "$SCRIPT" --validate-environment-only >"$TMP_ROOT/valid-env.log" 2>&1
grep -q '生产运行环境预检通过' "$TMP_ROOT/valid-env.log"
if grep -Fq "$valid_field_secret" "$TMP_ROOT/valid-env.log"; then
  printf 'field encryption key leaked to validation log\n' >&2
  exit 1
fi

printf -v duplicate_field_secret 'b2%.0s' {1..16}
printf 'MEDKERNEL_FIELD_ENCRYPTION_KEY=%s\n' "$duplicate_field_secret" \
  >> "$TMP_ROOT/app/conf/medkernel.env"
if MEDKERNEL_APP_HOME="$TMP_ROOT/app" \
  bash "$SCRIPT" --validate-environment-only >"$TMP_ROOT/duplicate-key.log" 2>&1; then
  printf 'duplicate field encryption key was accepted\n' >&2
  exit 1
fi
grep -q 'MEDKERNEL_FIELD_ENCRYPTION_KEY' "$TMP_ROOT/duplicate-key.log"
sed -i.bak '$d' "$TMP_ROOT/app/conf/medkernel.env"

chmod 644 "$TMP_ROOT/app/conf/medkernel.env"
if MEDKERNEL_APP_HOME="$TMP_ROOT/app" \
  bash "$SCRIPT" --validate-environment-only >"$TMP_ROOT/insecure-mode.log" 2>&1; then
  printf 'insecure environment file mode was accepted\n' >&2
  exit 1
fi
grep -q '权限必须为 600' "$TMP_ROOT/insecure-mode.log"
chmod 600 "$TMP_ROOT/app/conf/medkernel.env"

grep -q -- '--confirm-fresh' "$SCRIPT"
grep -q -- '--confirm-database' "$SCRIPT"
grep -q -- '--expected-host' "$SCRIPT"
grep -q -- '--expected-business-tables' "$SCRIPT"
grep -q -- '--external-base-url' "$SCRIPT"
grep -q -- '--confirm-prune-backups' "$SCRIPT"
grep -q -- '--service-unit' "$SCRIPT"
grep -q -- '--deploy-script' "$SCRIPT"
grep -q 'platform-knowledge/t-1/literature-materials' "$SCRIPT"
grep -q 'pg_dump.*--format=custom' "$SCRIPT"
grep -q 'sha256sum.*SHA256SUMS' "$SCRIPT"
grep -q 'sha256sum -c.*SHA256SUMS' "$SCRIPT"
grep -q 'pg_restore.*--exit-on-error' "$SCRIPT"
grep -q -- '--dbname "\$RESTORE_DATABASE" < "\$BACKUP_DIR/database/medkernel.dump"' "$SCRIPT"
grep -q "grep -c '\\^dist/index.html\\$'" "$SCRIPT"
if grep -q "grep -q.*dist/index.html" "$SCRIPT"; then
  printf 'frontend artifact validation accepted ambiguous path match\n' >&2
  exit 1
fi
grep -q 'systemctl stop "\$SERVICE"' "$SCRIPT"
grep -q 'systemctl reset-failed "\$SERVICE"' "$SCRIPT"
grep -q 'MainPID' "$SCRIPT"
grep -q 'dropdb --if-exists "\$DATABASE"' "$SCRIPT"
grep -q 'createdb --owner="\$DATABASE_OWNER" "\$DATABASE"' "$SCRIPT"
grep -q '"\$DEPLOY_COMMAND"' "$SCRIPT"
if grep -q -- '--no-rollback' "$SCRIPT"; then
  printf 'fresh deployment disabled mandatory rollback\n' >&2
  exit 1
fi
grep -q '^restore_previous_release()' "$SCRIPT"
grep -q "trap .*ERR" "$SCRIPT"
grep -q "trap .*INT" "$SCRIPT"
grep -q "trap .*TERM" "$SCRIPT"
grep -q -- '--dbname "\$DATABASE" < "\$BACKUP_DIR/database/medkernel.dump"' "$SCRIPT"
grep -q 'frontend-dist.tar.gz' "$SCRIPT"
grep -q 'medkernel.service' "$SCRIPT"
grep -q 'verify_previous_release_readiness' "$SCRIPT"
grep -q 'openssl s_client' "$SCRIPT"
grep -q 'openssl x509.*-checkend' "$SCRIPT"
grep -q 'openssl x509.*-checkhost\|openssl x509.*-checkip' "$SCRIPT"
grep -q 'subjectAltName' "$SCRIPT"
if grep -Eq 'curl.*([[:space:]]--insecure|[[:space:]]-[[:alpha:]]*k[[:alpha:]]*)' "$SCRIPT"; then
  printf 'fresh deployment may not bypass TLS validation\n' >&2
  exit 1
fi
if rg -nP '\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]' "$SCRIPT"; then
  printf 'fresh deployment has an unbraced variable adjacent to non-ASCII text under set -u\n' >&2
  exit 1
fi
grep -q 'destructive_action_performed=true' "$SCRIPT"
grep -q 'bootstrap_initialized=false' "$SCRIPT"
grep -q 'runtime-var.tar.gz' "$SCRIPT"
grep -q 'rm -rf "\$APP_HOME/mock-third-party"' "$SCRIPT"
grep -q 'EXPECTED_BUSINESS_TABLES + 1' "$SCRIPT"
grep -q 'fresh-preclear-' "$SCRIPT"
if grep -q 'p9-fresh-preclear-' "$SCRIPT"; then
  printf 'legacy P9 backup naming remained in fresh deployment\n' >&2
  exit 1
fi

if MEDKERNEL_APP_HOME="$TMP_ROOT/app" \
  bash "$SCRIPT" \
    --source 1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17 \
    --expected-flyway-version 1 \
    --expected-business-tables 207 \
    --confirm-fresh \
    --confirm-database medkernel \
    >"$TMP_ROOT/missing-host.log" 2>&1; then
  printf 'missing target host was accepted\n' >&2
  exit 1
fi
grep -q '缺少 --expected-host' "$TMP_ROOT/missing-host.log"

if MEDKERNEL_APP_HOME="$TMP_ROOT/app" \
  bash "$SCRIPT" \
    --expected-host medkernel-host-that-must-not-match \
    --jar "$TMP_ROOT/missing.jar" \
    --frontend "$TMP_ROOT/missing-dist.tar.gz" \
    --service-unit "$TMP_ROOT/missing.service" \
    --deploy-script "$TMP_ROOT/missing-deploy.sh" \
    --source 1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17 \
    --expected-flyway-version 1 \
    --expected-business-tables 207 \
    --confirm-fresh \
    --confirm-database medkernel \
    >"$TMP_ROOT/host-mismatch.log" 2>&1; then
  printf 'mismatched target host was accepted\n' >&2
  exit 1
fi
grep -q '目标主机不匹配' "$TMP_ROOT/host-mismatch.log"

main_line="$(grep -n '^main()' "$SCRIPT" | cut -d: -f1)"
backup_line="$(tail -n "+$main_line" "$SCRIPT" | grep -n '^[[:space:]]*create_backup$' | head -1 | cut -d: -f1)"
restore_line="$(tail -n "+$main_line" "$SCRIPT" | grep -n '^[[:space:]]*verify_backup_restore$' | head -1 | cut -d: -f1)"
runtime_line="$(tail -n "+$main_line" "$SCRIPT" | grep -n '^[[:space:]]*apply_runtime_contracts$' | head -1 | cut -d: -f1)"
stop_line="$(tail -n "+$main_line" "$SCRIPT" | grep -n '^[[:space:]]*stop_service$' | head -1 | cut -d: -f1)"
recreate_line="$(tail -n "+$main_line" "$SCRIPT" | grep -n '^[[:space:]]*recreate_database$' | head -1 | cut -d: -f1)"
purge_line="$(tail -n "+$main_line" "$SCRIPT" | grep -n '^[[:space:]]*purge_old_runtime$' | head -1 | cut -d: -f1)"
publish_line="$(tail -n "+$main_line" "$SCRIPT" | grep -n '^[[:space:]]*publish_candidate$' | head -1 | cut -d: -f1)"
verify_line="$(tail -n "+$main_line" "$SCRIPT" | grep -n '^[[:space:]]*verify_deployment$' | head -1 | cut -d: -f1)"

test "$backup_line" -lt "$restore_line"
test "$restore_line" -lt "$runtime_line"
test "$runtime_line" -lt "$stop_line"
test "$stop_line" -lt "$recreate_line"
test "$recreate_line" -lt "$purge_line"
test "$purge_line" -lt "$publish_line"
test "$publish_line" -lt "$verify_line"

printf 'onprem fresh deployment script contract passed\n'
