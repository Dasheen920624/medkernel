#!/usr/bin/env bash
# self-test：用 fixture 文件验证 javadoc_has_chinese / sql_table_has_comment 两个工具函数
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

CJK_HELPER="$ROOT/scripts/check-comment-zh/cjk-detect.pl"

javadoc_has_chinese() {
  awk '
    BEGIN { in_doc=0; doc=""; pending=""; printed=0 }
    /^[[:space:]]*\/\*\*/ {
      doc=$0
      if (/\*\//) { in_doc=0; pending=doc } else { in_doc=1 }
      next
    }
    in_doc && /\*\// { doc=doc"\n"$0; in_doc=0; pending=doc; next }
    in_doc { doc=doc"\n"$0; next }
    /^[[:space:]]*(@[A-Za-z]+(\([^)]*\))?[[:space:]]*)*((public|protected|private)[[:space:]]+)?(abstract[[:space:]]+|final[[:space:]]+|sealed[[:space:]]+|non-sealed[[:space:]]+)*(class|record|interface|enum|@interface)/ && !printed {
      print pending; printed=1; exit
    }
  ' "$1" | perl "$CJK_HELPER" stream
}

sql_table_has_comment() {
  local file="$1"
  local missing=""
  while IFS= read -r table; do
    [ -z "$table" ] && continue
    if ! perl "$CJK_HELPER" sql-table-comment "$file" "$table"; then
      missing="${missing}${table} "
    fi
  done < <(grep -ioE 'CREATE TABLE (IF NOT EXISTS )?[A-Za-z_][A-Za-z0-9_]*' "$file" \
           | sed -E 's/[Cc][Rr][Ee][Aa][Tt][Ee] [Tt][Aa][Bb][Ll][Ee] ([Ii][Ff] [Nn][Oo][Tt] [Ee][Xx][Ii][Ss][Tt][Ss] )?//')
  [ -z "$missing" ]
}

FIX="scripts/check-comment-zh/fixtures"
pass=0; fail=0
check() {
  local name="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  PASS  $name"; pass=$((pass + 1))
  else
    echo "  FAIL  $name (expected=$expected actual=$actual)"; fail=$((fail + 1))
  fi
}

a=""
javadoc_has_chinese "$FIX/good-class.java"      && a=yes || a=no; check good-class yes "$a"
javadoc_has_chinese "$FIX/good-package-record.java" && a=yes || a=no; check good-package-record yes "$a"
javadoc_has_chinese "$FIX/good-single-line-record.java" && a=yes || a=no; check good-single-line-record yes "$a"
javadoc_has_chinese "$FIX/missing-javadoc.java" && a=yes || a=no; check missing-javadoc no "$a"
javadoc_has_chinese "$FIX/english-only.java"    && a=yes || a=no; check english-only no "$a"
sql_table_has_comment "$FIX/good-table.sql"     && a=yes || a=no; check good-table yes "$a"
sql_table_has_comment "$FIX/missing-comment.sql" && a=yes || a=no; check missing-comment no "$a"

echo "self-test: $pass pass, $fail fail"
exit $fail
