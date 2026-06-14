#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
required=(
  "deploy/docker/.env.example"
  "deploy/docker/compose.yml"
  "deploy/docker/compose.monitoring.yml"
  "deploy/docker/monitoring/prometheus.yml"
  "deploy/docker/backend/Dockerfile"
  "deploy/docker/frontend/Dockerfile"
  "deploy/docker/frontend/nginx.conf"
  "deploy/onprem/templates/medkernel.nginx.conf"
  "deploy/docker/scripts/common.sh"
  "deploy/docker/scripts/bootstrap-runtime.sh"
  "deploy/docker/scripts/up.sh"
  "deploy/docker/scripts/down.sh"
  "deploy/docker/scripts/healthcheck.sh"
  "deploy/docker/scripts/backup.sh"
  "deploy/docker/scripts/restore.sh"
  "deploy/docker/scripts/backup-restore-drill.sh"
  "deploy/docker/scripts/govcloud-smoke.sh"
  "deploy/docker/scripts/rotate-dify-initial-secrets.sh"
  "deploy/docker/dify/compose.lock.yml"
  "deploy/docker/dify/.env.override.example"
  "deploy/docker/dify/README.md"
  "deploy/docker/README.md"
  "medkernel-backend/src/main/resources/application-container.yml"
)
legacy_removed=(
  "deploy/manifest.template.json"
  "deploy/monitoring/README.md"
  "deploy/monitoring/prometheus/prometheus-medkernel.yml"
  "deploy/nginx"
  "deploy/profiles"
  "deploy/scripts"
  "deploy/systemd"
)

for path in "${required[@]}"; do
  test -f "$ROOT/$path" || {
    printf 'missing required deployment file: %s\n' "$path" >&2
    exit 1
  }
done

for path in "${legacy_removed[@]}"; do
  test ! -e "$ROOT/$path" || {
    printf 'legacy deployment asset should be removed: %s\n' "$path" >&2
    exit 1
  }
done

grep -q 'classpath:db/migration/postgres' "$ROOT/medkernel-backend/src/main/resources/application-container.yml"
grep -q 'postgres:16.14-bookworm' "$ROOT/deploy/docker/compose.yml"
grep -q 'neo4j:5.23.0-community' "$ROOT/deploy/docker/compose.yml"
grep -q 'MEDKERNEL_RUNTIME_ROOT' "$ROOT/deploy/docker/compose.yml"
grep -q 'SPRING_NEO4J_URI: bolt://neo4j:7687' "$ROOT/deploy/docker/compose.yml"
! grep -q '^      NEO4J_PASSWORD:' "$ROOT/deploy/docker/compose.yml"
grep -q 'MEDKERNEL_NEO4J_PASSWORD' "$ROOT/medkernel-backend/src/main/resources/application-container.yml"
grep -q 'MEDKERNEL_NEO4J_HEALTH_ENABLED:false' "$ROOT/medkernel-backend/src/main/resources/application-container.yml"
grep -q 'v1.14.0' "$ROOT/deploy/docker/scripts/bootstrap-runtime.sh"
grep -q '^DIFY_GIT_REF=1.14.0$' "$ROOT/deploy/docker/.env.example"
grep -q 'DIFY_GIT_REF' "$ROOT/deploy/docker/scripts/bootstrap-runtime.sh"
grep -q 'configure_new_dify_secrets' "$ROOT/deploy/docker/scripts/bootstrap-runtime.sh"
grep -q 'ALTER ROLE' "$ROOT/deploy/docker/scripts/rotate-dify-initial-secrets.sh"
grep -q -- '--confirm-unconfigured' "$ROOT/deploy/docker/scripts/rotate-dify-initial-secrets.sh"
grep -q 'DIFY_LOCK_COMPOSE_FILE' "$ROOT/deploy/docker/scripts/common.sh"
grep -q 'checksum_file' "$ROOT/deploy/docker/scripts/common.sh"
grep -q 'verify_checksum' "$ROOT/deploy/docker/scripts/common.sh"
grep -q 'nginx@sha256:' "$ROOT/deploy/docker/dify/compose.lock.yml"
grep -q 'ubuntu/squid@sha256:' "$ROOT/deploy/docker/dify/compose.lock.yml"
! grep -q ':latest' "$ROOT/deploy/docker/dify/compose.lock.yml"
grep -q -- '--mount=type=cache,target=/root/.m2' "$ROOT/deploy/docker/backend/Dockerfile"
! grep -q 'mvn -B -q' "$ROOT/deploy/docker/backend/Dockerfile"
grep -q 'maven.test.skip=true' "$ROOT/deploy/docker/backend/Dockerfile"
grep -A4 'location = /healthz' "$ROOT/deploy/docker/frontend/nginx.conf" | grep -q 'proxy_set_header Host \$host;'

EMBED_BLOCK="$(grep -A6 'location = /embed/launch' "$ROOT/deploy/docker/frontend/nginx.conf")"
grep -q 'frame-ancestors \*' <<<"$EMBED_BLOCK"
if grep -q 'X-Frame-Options' <<<"$EMBED_BLOCK"; then
  echo "嵌入启动页不得返回 X-Frame-Options" >&2
  exit 1
fi
ONPREM_EMBED_BLOCK="$(grep -A7 'location = /embed/launch' "$ROOT/deploy/onprem/templates/medkernel.nginx.conf")"
grep -q 'frame-ancestors \*' <<<"$ONPREM_EMBED_BLOCK"
grep -q 'Strict-Transport-Security' <<<"$ONPREM_EMBED_BLOCK"
if grep -q 'X-Frame-Options' <<<"$ONPREM_EMBED_BLOCK"; then
  echo "院内部署嵌入启动页不得返回 X-Frame-Options" >&2
  exit 1
fi
grep -q 'pg_dump' "$ROOT/deploy/docker/scripts/backup.sh"
grep -q 'checksum_file' "$ROOT/deploy/docker/scripts/backup.sh"
grep -q '.sha256' "$ROOT/deploy/docker/scripts/backup.sh"
grep -q 'PostgreSQL backup checksum created' "$ROOT/deploy/docker/scripts/backup.sh"
grep -q 'pg_restore' "$ROOT/deploy/docker/scripts/restore.sh"
grep -q 'verify_checksum' "$ROOT/deploy/docker/scripts/restore.sh"
grep -q '.sha256' "$ROOT/deploy/docker/scripts/restore.sh"
grep -q 'PostgreSQL backup checksum verified' "$ROOT/deploy/docker/scripts/restore.sh"
grep -q 'MEDKERNEL_BACKUP_DRILL_DB' "$ROOT/deploy/docker/scripts/backup-restore-drill.sh"
grep -q 'flyway_schema_history' "$ROOT/deploy/docker/scripts/backup-restore-drill.sh"
grep -q 'restore drill evidence' "$ROOT/deploy/docker/scripts/backup-restore-drill.sh"
grep -q 'MEDKERNEL_GOV_DATABASE_DIALECT' "$ROOT/deploy/docker/scripts/govcloud-smoke.sh"
grep -q 'MEDKERNEL_GOV_JDBC_JAR' "$ROOT/deploy/docker/scripts/govcloud-smoke.sh"
grep -q 'MEDKERNEL_GOV_EVIDENCE_DIR' "$ROOT/deploy/docker/scripts/govcloud-smoke.sh"
grep -q 'govcloud smoke evidence' "$ROOT/deploy/docker/scripts/govcloud-smoke.sh"
grep -q 'jdbc_jar_sha256' "$ROOT/deploy/docker/scripts/govcloud-smoke.sh"
grep -q 'Domestic crypto smoke passed' "$ROOT/deploy/docker/scripts/govcloud-smoke.sh"
grep -q 'backend:18080' "$ROOT/deploy/docker/monitoring/prometheus.yml"
grep -q '/opt/medkernel/dashboards:ro' "$ROOT/deploy/docker/compose.monitoring.yml"
grep -q 'path: /opt/medkernel/dashboards' "$ROOT/deploy/monitoring/grafana/provisioning/dashboards/medkernel.yml"
! grep -q ':/etc/grafana/provisioning/dashboards/medkernel:ro' "$ROOT/deploy/docker/compose.monitoring.yml"
grep -q 'dify_compose exec -T api curl -fsS http://localhost:5001/health' "$ROOT/deploy/docker/scripts/healthcheck.sh"
grep -q 'DIFY_REQUIRED_SERVICES=' "$ROOT/deploy/docker/scripts/healthcheck.sh"
grep -q 'DIFY_HEALTHY_SERVICES=' "$ROOT/deploy/docker/scripts/healthcheck.sh"
grep -q 'dify_compose ps --services --status running' "$ROOT/deploy/docker/scripts/healthcheck.sh"
grep -q 'docker inspect --format' "$ROOT/deploy/docker/scripts/healthcheck.sh"
! grep -R -q 'host.docker.internal:5001' "$ROOT/deploy/docker" "$ROOT/medkernel-backend/src/main/resources/application-container.yml"

printf 'deployment asset contract passed\n'
