#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

violations="$({
  find "$ROOT/deploy" -path '*/tests/*.sh' -type f -print0 |
    xargs -0 grep -nE '^[[:space:]]*![[:space:]]+(grep|rg)([[:space:]]|$)'
} 2>/dev/null || true)"

if [ -n "$violations" ]; then
  printf '%s\n' '部署测试禁止使用顶层 ! grep/rg；请改为显式 if 命中即退出：' >&2
  printf '%s\n' "$violations" >&2
  exit 1
fi

printf '%s\n' 'shell test assertion contract passed'
