#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deploy/onprem/mk-publish.sh"
POWERSHELL_SCRIPT="$ROOT/deploy/onprem/mk-publish.ps1"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

grep -q 'COPYFILE_DISABLE=1 tar --no-xattrs -czf "\$DIST_TAR"' "$SCRIPT"
! grep -q 'COPYFILE_DISABLE=1 tar -czf "\$DIST_TAR"' "$SCRIPT"
! grep -q '^[[:space:]]*tar -czf "\$DIST_TAR"' "$SCRIPT"

# 发布来源必须是当前干净工作树的完整提交哈希；禁止复用无法证明来源的旧制品。
grep -q 'rev-parse HEAD' "$SCRIPT"
! grep -q 'rev-parse --short HEAD' "$SCRIPT"
grep -q '工作树存在未提交改动' "$SCRIPT"
grep -q '发布来源必须是当前 HEAD 的完整 40 位提交哈希' "$SCRIPT"
! grep -q -- '--skip-build' "$SCRIPT"

grep -q 'rev-parse HEAD' "$POWERSHELL_SCRIPT"
! grep -q 'rev-parse --short HEAD' "$POWERSHELL_SCRIPT"
grep -q '工作树存在未提交改动' "$POWERSHELL_SCRIPT"
grep -q '发布来源必须是当前 HEAD 的完整 40 位提交哈希' "$POWERSHELL_SCRIPT"
! grep -q 'SkipBuild' "$POWERSHELL_SCRIPT"

mkdir -p "$TMP_ROOT/repo/medkernel-backend"
printf '<project/>\n' > "$TMP_ROOT/repo/medkernel-backend/pom.xml"
git -C "$TMP_ROOT/repo" init -q
git -C "$TMP_ROOT/repo" config user.name 'MedKernel Test'
git -C "$TMP_ROOT/repo" config user.email 'test@medkernel.invalid'
git -C "$TMP_ROOT/repo" add medkernel-backend/pom.xml
git -C "$TMP_ROOT/repo" commit -qm 'init'
head_source="$(git -C "$TMP_ROOT/repo" rev-parse HEAD)"

if bash "$SCRIPT" --repo-root "$TMP_ROOT/repo" --backend --source "${head_source:0:8}" \
    >"$TMP_ROOT/short-source.log" 2>&1; then
  printf 'short publish source was accepted\n' >&2
  exit 1
fi
grep -q '发布来源必须是当前 HEAD 的完整 40 位提交哈希' "$TMP_ROOT/short-source.log"

printf 'dirty\n' > "$TMP_ROOT/repo/untracked.txt"
if bash "$SCRIPT" --repo-root "$TMP_ROOT/repo" --backend --source "$head_source" \
    >"$TMP_ROOT/dirty-worktree.log" 2>&1; then
  printf 'dirty worktree was accepted\n' >&2
  exit 1
fi
grep -q '工作树存在未提交改动' "$TMP_ROOT/dirty-worktree.log"

printf 'onprem publish package contract passed\n'
