#!/usr/bin/env bash
# MedKernel · 引擎层中文 Javadoc / SQL COMMENT 软门禁
#
# 用法：
#   scripts/check-comment-zh.sh              # CI 默认：检查 origin/main...HEAD diff
#   scripts/check-comment-zh.sh --mode=full  # 全量扫描，仅汇总，不影响退出码
#   scripts/check-comment-zh.sh --self-test  # 跑 fixtures 自检
#
# 设计文档：docs/superpowers/specs/2026-05-27-engine-comment-language-design.md
set -euo pipefail

MODE="${1:-diff}"
case "$MODE" in
  ""|diff|--mode=diff) MODE="diff" ;;
  full|--mode=full) MODE="full" ;;
  self-test|--self-test) MODE="self-test" ;;
  *) echo "未知参数：$MODE" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

CJK_HELPER="$ROOT/scripts/check-comment-zh/cjk-detect.pl"

# ---- 工具：判断 Java 文件类级 Javadoc 是否含中文 ----
javadoc_has_chinese() {
  # $1: java 文件路径
  # 抓取第一个 class/record/interface/enum 上方紧邻的 /** ... */ 块，覆盖包内可见类型。
  awk '
    BEGIN { in_doc=0; doc=""; pending=""; printed=0 }
    /^[[:space:]]*\/\*\*/ { in_doc=1; doc=$0; next }
    in_doc && /\*\// { doc=doc"\n"$0; in_doc=0; pending=doc; next }
    in_doc { doc=doc"\n"$0; next }
    /^[[:space:]]*(@[A-Za-z]+(\([^)]*\))?[[:space:]]*)*((public|protected|private)[[:space:]]+)?(abstract[[:space:]]+|final[[:space:]]+|sealed[[:space:]]+|non-sealed[[:space:]]+)*(class|record|interface|enum|@interface)/ && !printed {
      print pending; printed=1; exit
    }
  ' "$1" | perl "$CJK_HELPER" stream
}

# ---- 工具：判断 SQL 文件每个 CREATE TABLE 是否有中文 COMMENT ON TABLE ----
sql_table_has_comment() {
  # $1: sql 文件路径
  local file="$1"
  local missing=""
  while IFS= read -r table; do
    [ -z "$table" ] && continue
    if ! perl "$CJK_HELPER" sql-table-comment "$file" "$table"; then
      missing="${missing}${table} "
    fi
  done < <(grep -ioE 'CREATE TABLE (IF NOT EXISTS )?[A-Za-z_][A-Za-z0-9_]*' "$file" \
           | sed -E 's/[Cc][Rr][Ee][Aa][Tt][Ee] [Tt][Aa][Bb][Ll][Ee] ([Ii][Ff] [Nn][Oo][Tt] [Ee][Xx][Ii][Ss][Tt][Ss] )?//')
  if [ -n "$missing" ]; then
    echo "  ✗ $file 缺 COMMENT ON TABLE 中文注释：$missing" >&2
    return 1
  fi
  return 0
}

# ---- 模式分发 ----
case "$MODE" in
  self-test)
    exec "$ROOT/scripts/check-comment-zh/run-fixtures.sh"
    ;;
  diff)
    BASE="${BASE_REF:-origin/main}"
    if ! git rev-parse --verify "$BASE" >/dev/null 2>&1; then
      echo "::warning::找不到 $BASE，跳过 diff 检查"
      exit 0
    fi
    MERGE_BASE="$(git merge-base "$BASE" HEAD)"

    fail=0
    warn=0

    echo "=== 检查 1：新增 Java 文件类级中文 Javadoc（fail） ==="
    NEW_JAVA="$(git diff --name-only --diff-filter=A "$MERGE_BASE"...HEAD -- \
      'medkernel-backend/src/main/java/com/medkernel/engine/*' \
      'medkernel-backend/src/main/java/com/medkernel/engine/**' \
      'medkernel-backend/src/main/java/com/medkernel/shared/*' \
      'medkernel-backend/src/main/java/com/medkernel/shared/**' \
      | grep '\.java$' || true)"
    if [ -n "$NEW_JAVA" ]; then
      while IFS= read -r f; do
        [ -z "$f" ] && continue
        if ! javadoc_has_chinese "$f"; then
          echo "  ✗ $f 类级 Javadoc 缺中文"
          fail=$((fail + 1))
        fi
      done <<< "$NEW_JAVA"
    else
      echo "  （无新增引擎/共享 Java 文件）"
    fi

    echo "=== 检查 2：新增 SQL 表 COMMENT ON TABLE 中文（fail） ==="
    NEW_SQL="$(git diff --name-only --diff-filter=A "$MERGE_BASE"...HEAD -- \
      'medkernel-backend/src/main/resources/db/migration/oracle/*.sql' \
      'medkernel-backend/src/main/resources/db/migration/postgres/*.sql' \
      'medkernel-backend/src/main/resources/db/migration/kingbase/*.sql' \
      || true)"
    if [ -n "$NEW_SQL" ]; then
      while IFS= read -r f; do
        [ -z "$f" ] && continue
        if ! sql_table_has_comment "$f"; then
          fail=$((fail + 1))
        fi
      done <<< "$NEW_SQL"
    else
      echo "  （无新增生产方言 SQL 文件）"
    fi

    echo "=== 检查 3：修改文件缺口（warn） ==="
    MOD_JAVA="$(git diff --name-only --diff-filter=M "$MERGE_BASE"...HEAD -- \
      'medkernel-backend/src/main/java/com/medkernel/engine/*' \
      'medkernel-backend/src/main/java/com/medkernel/engine/**' \
      'medkernel-backend/src/main/java/com/medkernel/shared/*' \
      'medkernel-backend/src/main/java/com/medkernel/shared/**' \
      | grep '\.java$' || true)"
    if [ -n "$MOD_JAVA" ]; then
      while IFS= read -r f; do
        [ -z "$f" ] && continue
        if ! javadoc_has_chinese "$f"; then
          echo "  ⚠ $f 类级 Javadoc 缺中文（修改文件，仅警告）"
          warn=$((warn + 1))
        fi
      done <<< "$MOD_JAVA"
    fi

    MOD_SQL="$(git diff --name-only --diff-filter=M "$MERGE_BASE"...HEAD -- \
      'medkernel-backend/src/main/resources/db/migration/oracle/*.sql' \
      'medkernel-backend/src/main/resources/db/migration/postgres/*.sql' \
      'medkernel-backend/src/main/resources/db/migration/kingbase/*.sql' \
      || true)"
    if [ -n "$MOD_SQL" ]; then
      while IFS= read -r f; do
        [ -z "$f" ] && continue
        if ! sql_table_has_comment "$f" 2>/dev/null; then
          echo "  ⚠ $f SQL COMMENT 缺口（修改文件，仅警告）"
          warn=$((warn + 1))
        fi
      done <<< "$MOD_SQL"
    fi

    if [ "$fail" -gt 0 ]; then
      echo "::error::中文注释门禁 fail：${fail} 处缺口（warn：${warn}）"
      exit 1
    fi
    echo "中文注释门禁通过：0 fail，${warn} warn"
    ;;
  full)
    echo "=== 全量扫描：engine/** 与 shared/** 各模块类级 Javadoc 中文覆盖率 ==="
    for d in $(find medkernel-backend/src/main/java/com/medkernel/engine \
                    medkernel-backend/src/main/java/com/medkernel/shared \
                    -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort); do
      total=0; with=0
      while IFS= read -r f; do
        [ -z "$f" ] && continue
        total=$((total + 1))
        if javadoc_has_chinese "$f"; then with=$((with + 1)); fi
      done < <(find "$d" -name '*.java' -type f)
      [ "$total" -eq 0 ] && continue
      pct=$(( with * 100 / total ))
      printf "  %3d%% (%3d/%3d) %s\n" "$pct" "$with" "$total" "${d#medkernel-backend/src/main/java/com/medkernel/}"
    done
    echo
    echo "=== 全量扫描：oracle/postgres/kingbase 各文件 CREATE TABLE 中文 COMMENT 覆盖 ==="
    for dialect in oracle postgres kingbase; do
      dir="medkernel-backend/src/main/resources/db/migration/$dialect"
      [ -d "$dir" ] || continue
      for f in "$dir"/*.sql; do
        [ -f "$f" ] || continue
        if sql_table_has_comment "$f" >/dev/null 2>&1; then
          printf "  OK   %s\n" "${f#medkernel-backend/src/main/resources/db/migration/}"
        else
          printf "  GAP  %s\n" "${f#medkernel-backend/src/main/resources/db/migration/}"
        fi
      done
    done
    ;;
esac
