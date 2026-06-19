#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

SKIP_DIFY=false
if test "${1:-}" = "--skip-dify"; then
  SKIP_DIFY=true
elif test "$#" -gt 0; then
  fail "usage: $0 [--skip-dify]"
fi

DIFY_VERSION="${DIFY_VERSION:-v1.14.0}"
DIFY_GIT_REF="${DIFY_GIT_REF:-1.14.0}"
mkdir -p \
  "$RUNTIME_ROOT/env" \
  "$RUNTIME_ROOT/data/postgres" \
  "$RUNTIME_ROOT/data/neo4j/data" \
  "$RUNTIME_ROOT/data/neo4j/logs" \
  "$RUNTIME_ROOT/data/prometheus" \
  "$RUNTIME_ROOT/data/grafana" \
  "$RUNTIME_ROOT/backups" \
  "$RUNTIME_ROOT/backups/drills" \
  "$RUNTIME_ROOT/dify"

generate_if_placeholder() {
  local key="$1"
  if grep -q "^${key}=__GENERATED_BY_BOOTSTRAP__$" "$ENV_FILE"; then
    replace_setting "$key" "$(openssl rand -hex 24)" "$ENV_FILE"
  fi
}

ensure_setting() {
  local key="$1"
  local value="$2"
  if ! grep -q "^${key}=" "$ENV_FILE"; then
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

configure_new_dify_secrets() {
  local sandbox_key
  sandbox_key="$(openssl rand -hex 32)"
  replace_setting "SECRET_KEY" "sk-$(openssl rand -hex 32)" "$DIFY_DOCKER_DIR/.env"
  replace_setting "DB_PASSWORD" "$(openssl rand -hex 24)" "$DIFY_DOCKER_DIR/.env"
  replace_setting "REDIS_PASSWORD" "$(openssl rand -hex 24)" "$DIFY_DOCKER_DIR/.env"
  replace_setting "CODE_EXECUTION_API_KEY" "$sandbox_key" "$DIFY_DOCKER_DIR/.env"
  replace_setting "SANDBOX_API_KEY" "$sandbox_key" "$DIFY_DOCKER_DIR/.env"
  replace_setting "PLUGIN_DAEMON_KEY" "$(openssl rand -hex 32)" "$DIFY_DOCKER_DIR/.env"
  replace_setting "PLUGIN_DIFY_INNER_API_KEY" "$(openssl rand -hex 32)" "$DIFY_DOCKER_DIR/.env"
}

if ! test -f "$ENV_FILE"; then
  cp "$DOCKER_DIR/.env.example" "$ENV_FILE"
fi
replace_setting "MEDKERNEL_RUNTIME_ROOT" "$RUNTIME_ROOT" "$ENV_FILE"
ensure_setting "MEDKERNEL_NEO4J_HEALTH_ENABLED" "${MEDKERNEL_NEO4J_HEALTH_ENABLED:-false}"
ensure_setting "MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT" \
  "${MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT:-development}"
ensure_setting "DIFY_GIT_REF" "$DIFY_GIT_REF"
ensure_setting "DIFY_HTTPS_PORT" "${DIFY_HTTPS_PORT:-8443}"
generate_if_placeholder "MEDKERNEL_DB_PASSWORD"
generate_if_placeholder "MEDKERNEL_INTEGRATION_SECRET_KEY"
generate_if_placeholder "MEDKERNEL_NEO4J_PASSWORD"
generate_if_placeholder "MEDKERNEL_GRAFANA_ADMIN_PASSWORD"
chmod 600 "$ENV_FILE"

if "$SKIP_DIFY"; then
  printf 'runtime ready at %s (Dify checkout skipped)\n' "$RUNTIME_ROOT"
  exit 0
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
DIFY_VERSION="${DIFY_VERSION:-v1.14.0}"
DIFY_GIT_REF="${DIFY_GIT_REF:-1.14.0}"
DIFY_DIR="$RUNTIME_ROOT/dify/$DIFY_VERSION"
DIFY_DOCKER_DIR="$DIFY_DIR/docker"

if ! test -d "$DIFY_DIR/.git"; then
  git clone --depth 1 --branch "$DIFY_GIT_REF" https://github.com/langgenius/dify.git "$DIFY_DIR"
fi
test -f "$DIFY_DOCKER_DIR/.env.example" || fail "Dify .env.example was not found in the official checkout"
if ! test -f "$DIFY_DOCKER_DIR/.env"; then
  cp "$DIFY_DOCKER_DIR/.env.example" "$DIFY_DOCKER_DIR/.env"
  configure_new_dify_secrets
fi
replace_setting "EXPOSE_NGINX_PORT" "${DIFY_HTTP_PORT:-8090}" "$DIFY_DOCKER_DIR/.env"
replace_setting "EXPOSE_NGINX_SSL_PORT" "${DIFY_HTTPS_PORT:-8443}" "$DIFY_DOCKER_DIR/.env"
chmod 600 "$DIFY_DOCKER_DIR/.env"

printf 'runtime ready at %s with official Dify %s checkout (git tag %s)\n' \
  "$RUNTIME_ROOT" "$DIFY_VERSION" "$DIFY_GIT_REF"
