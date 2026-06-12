#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deploy/onprem/mk-publish.sh"

grep -q 'COPYFILE_DISABLE=1 tar -czf "\$DIST_TAR"' "$SCRIPT"
! grep -q '^[[:space:]]*tar -czf "\$DIST_TAR"' "$SCRIPT"

printf 'onprem publish package contract passed\n'
