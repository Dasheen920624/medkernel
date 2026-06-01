#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BACKEND_JAR="${MEDKERNEL_BACKEND_JAR:-${ROOT_DIR}/medkernel-backend/target/medkernel-backend.jar}"

if [[ ! -f "${BACKEND_JAR}" ]]; then
  echo "未找到后端 Jar，请先构建，或通过 MEDKERNEL_BACKEND_JAR 指向真实 Jar。" >&2
  exit 2
fi

exec java -jar "${BACKEND_JAR}" \
  --spring.main.web-application-type=none \
  "$@"
