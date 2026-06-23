#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deploy/onprem/medkernel-post-rehearsal-verify.sh"

test -f "$SCRIPT"
bash -n "$SCRIPT"
grep -q 'RUNTIME_ROOT="${MEDKERNEL_RUNTIME_ROOT:-$APP_HOME/var}"' "$SCRIPT"
grep -q 'CREDENTIALS_FILE="$RUNTIME_ROOT/credentials/current-launch.json"' "$SCRIPT"

grep -q -- '--expected-host' "$SCRIPT"
grep -q -- '--expected-source' "$SCRIPT"
grep -q -- '--external-base-url' "$SCRIPT"
grep -q -- '--provider-code' "$SCRIPT"
grep -q -- '--expected-business-tables' "$SCRIPT"
grep -q -- '--expected-flyway-version' "$SCRIPT"
grep -q -- '--confirm-restart' "$SCRIPT"
grep -q -- '--confirm-database' "$SCRIPT"

grep -q 'runtime-resilience' "$SCRIPT"
grep -q 'browser-e2e' "$SCRIPT"
grep -q 'full-knowledge.json' "$SCRIPT"
grep -q '.source == \$source' "$SCRIPT"
grep -q 'systemctl restart "\$SERVICE"' "$SCRIPT"
grep -q 'MainPID' "$SCRIPT"
grep -q 'actuator/health/readiness' "$SCRIPT"
grep -q 'openssl s_client' "$SCRIPT"
grep -q 'openssl x509.*-checkend' "$SCRIPT"
grep -q 'openssl x509.*-checkhost\|openssl x509.*-checkip' "$SCRIPT"
grep -q 'subjectAltName' "$SCRIPT"
if grep -Eq 'curl.*([[:space:]]--insecure|[[:space:]]-[[:alpha:]]*k[[:alpha:]]*)' "$SCRIPT"; then
  printf 'post rehearsal verification may not bypass TLS validation\n' >&2
  exit 1
fi
if rg -nP '\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]' "$SCRIPT"; then
  printf 'post rehearsal verifier has an unbraced variable adjacent to non-ASCII text under set -u\n' >&2
  exit 1
fi

grep -q 'pg_dump.*--format=custom' "$SCRIPT"
grep -q 'sha256sum.*SHA256SUMS' "$SCRIPT"
grep -q 'sha256sum -c.*SHA256SUMS' "$SCRIPT"
grep -q 'pg_restore.*--exit-on-error' "$SCRIPT"
grep -q 'flyway_schema_history' "$SCRIPT"
grep -q 'knowledge_identity' "$SCRIPT"
grep -q 'knowledge_asset_version' "$SCRIPT"
grep -q 'mk_knowledge_production_job' "$SCRIPT"
grep -q 'model_capability_task' "$SCRIPT"
grep -q 'mk_llm_provider' "$SCRIPT"
grep -q 'mk_llm_eval_run' "$SCRIPT"
grep -q 'rule_definition' "$SCRIPT"
grep -q 'rule_test_case' "$SCRIPT"
grep -q 'mk_sandbox_runtime_binding' "$SCRIPT"
grep -q 'audit_event' "$SCRIPT"
grep -q 'user_role_assignment' "$SCRIPT"
grep -q 'release_status=PASSED' "$SCRIPT"
grep -q 'database_restore_status=PASSED' "$SCRIPT"

if bash "$SCRIPT" --help | grep -q '全功能与全知识演练后验收'; then
  :
else
  printf 'post rehearsal verifier help contract missing\n' >&2
  exit 1
fi

printf 'onprem post rehearsal verification script contract passed\n'
