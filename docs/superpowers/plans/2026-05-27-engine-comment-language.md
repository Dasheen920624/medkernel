# 引擎层中文注释治理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `medkernel-backend` 5 个新引擎模块（~170 个 Java 文件 + 7 张 V8-V14 表）补齐中文 Javadoc 与 Oracle/Postgres/Kingbase `COMMENT ON`，同时落地规范和 CI 软门禁，从源头防止再退化。

**Architecture:** 6 个独立 PR：PR1 写规范+CI 门禁（脚本 + workflow 步骤），PR2-6 五个模块各一个 PR 补 Javadoc+SQL。本会话目标是把全部 PR push 上去。每个 PR 都从 `main` 切独立 `codex/comment-zh-*` 分支，互不依赖。

**Tech Stack:** Bash（脚本）、GitHub Actions（workflow）、Java 21 + Spring Boot 3（Javadoc）、Oracle 23ai / PostgreSQL / Kingbase（SQL COMMENT）。

> 设计文档：[docs/superpowers/specs/2026-05-27-engine-comment-language-design.md](../specs/2026-05-27-engine-comment-language-design.md)

---

## 全局执行约定

- 每个 PR 从最新 `origin/main` 切独立分支：`codex/comment-zh-pr1-policy-ci`、`codex/comment-zh-pr2-terminology` …… 直到 `codex/comment-zh-pr6-pathway`。
- 每个 PR 完成后立即 push 并通过 `gh pr create` 开 PR，**不等 review/合并**——本会话目标是开完 6 个 PR，合并由用户后续处理。
- PR 描述按 AGENTS.md 要求用中文，注明：范围、验证结果（mvn 编译通过、`scripts/check-comment-zh.sh` 通过）、是否影响医疗安全/部署/数据迁移（本批均不影响）。
- 不直接 push 到 main。`gh pr edit` 因 Projects classic 受限，PR body 通过 `gh pr create --body` 一次性写入。
- 共同 footer：`Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`

---

## PR1：规范文档 + CI 软门禁

**分支：** `codex/comment-zh-pr1-policy-ci`

**File Structure：**
- 新增：`scripts/check-comment-zh.sh`（bash 校验脚本）
- 新增：`scripts/check-comment-zh/fixtures/`（5 个 fixture 文件用于 self-test）
- 修改：`AGENTS.md`（语言要求章节追加引擎层注释规则）
- 修改：`docs/DOCUMENTATION_LANGUAGE_POLICY.md`（§ 2 / § 3 同步追加）
- 修改：`.github/workflows/ci.yml`（新增 `comment-language-check` job）

### Task 1：创建 PR1 分支

**Files:** 无（git 操作）

- [ ] **Step 1.1：从 origin/main 切分支**

```bash
git fetch origin main
git checkout -b codex/comment-zh-pr1-policy-ci origin/main
```

- [ ] **Step 1.2：确认分支干净**

```bash
git status
```

Expected: `nothing to commit, working tree clean`，分支名 `codex/comment-zh-pr1-policy-ci`。

### Task 2：写 `scripts/check-comment-zh.sh` 主逻辑

**Files:**
- Create: `scripts/check-comment-zh.sh`

- [ ] **Step 2.1：写脚本**

```bash
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
  --mode=full) MODE="full" ;;
  --mode=diff|"") MODE="diff" ;;
  --self-test) MODE="self-test" ;;
  *) echo "未知参数：$MODE" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ZH_REGEX='[\xe4-\xe9][\x80-\xbf]{2}'  # UTF-8 编码下 CJK 字符范围（grep -P 不可用时的备选）
CJK_PCRE='[\x{4e00}-\x{9fa5}]'

fail=0
warn=0

# ---- 工具：判断 Javadoc 是否含中文 ----
javadoc_has_chinese() {
  # $1: java 文件路径
  # 抓取第一个 public class/record/interface/enum 上方紧邻的 /** ... */ 块
  awk '
    BEGIN { in_doc=0; doc=""; printed=0 }
    /^\s*\/\*\*/ { in_doc=1; doc=$0; next }
    in_doc && /\*\// { doc=doc"\n"$0; in_doc=0; pending=doc; next }
    in_doc { doc=doc"\n"$0; next }
    /^\s*(@[A-Za-z]+(\(.*\))?\s*)*public\s+(class|record|interface|enum|@interface)/ && !printed {
      print pending; printed=1; exit
    }
  ' "$1" | grep -qP "$CJK_PCRE"
}

# ---- 工具：判断 SQL 文件每个 CREATE TABLE 是否有中文 COMMENT ON TABLE ----
sql_table_has_comment() {
  # $1: sql 文件路径
  local file="$1"
  local missing=""
  # 抓所有 CREATE TABLE 表名（忽略 IF NOT EXISTS、不分大小写）
  while IFS= read -r table; do
    if ! grep -qiP "COMMENT\s+ON\s+TABLE\s+${table}\s+IS\s+'[^']*${CJK_PCRE}" "$file"; then
      missing="${missing}${table} "
    fi
  done < <(grep -ioE 'CREATE TABLE (IF NOT EXISTS )?[A-Za-z_][A-Za-z0-9_]*' "$file" \
           | sed -E 's/CREATE TABLE (IF NOT EXISTS )?//I')
  if [ -n "$missing" ]; then
    echo "  ✗ $file 缺 COMMENT ON TABLE 中文注释：$missing"
    return 1
  fi
  return 0
}

# ---- 模式分发 ----
case "$MODE" in
  self-test)
    "$ROOT/scripts/check-comment-zh/run-fixtures.sh"
    exit $?
    ;;
  diff)
    BASE="${BASE_REF:-origin/main}"
    if ! git rev-parse --verify "$BASE" >/dev/null 2>&1; then
      echo "::warning::找不到 $BASE，跳过 diff 检查"
      exit 0
    fi
    MERGE_BASE="$(git merge-base "$BASE" HEAD)"

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
    fi

    echo "=== 检查 3：修改文件 / 全量缺口（warn） ==="
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

    if [ "$fail" -gt 0 ]; then
      echo "::error::中文注释门禁 fail：$fail 处缺口（warn：$warn）"
      exit 1
    fi
    echo "中文注释门禁通过：0 fail，$warn warn"
    ;;
  full)
    echo "=== 全量扫描：engine/** 各模块类级 Javadoc 中文覆盖率 ==="
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
```

写入路径：`/Users/zhikunzheng/个人/郑志坤/medkernel/claude/scripts/check-comment-zh.sh`

- [ ] **Step 2.2：给执行权限**

```bash
chmod +x scripts/check-comment-zh.sh
```

### Task 3：创建 self-test fixtures

**Files:**
- Create: `scripts/check-comment-zh/fixtures/good-class.java`
- Create: `scripts/check-comment-zh/fixtures/missing-javadoc.java`
- Create: `scripts/check-comment-zh/fixtures/english-only.java`
- Create: `scripts/check-comment-zh/fixtures/good-table.sql`
- Create: `scripts/check-comment-zh/fixtures/missing-comment.sql`
- Create: `scripts/check-comment-zh/run-fixtures.sh`

- [ ] **Step 3.1：写 good-class.java**

写入 `scripts/check-comment-zh/fixtures/good-class.java`：

```java
/**
 * 示例：合规的中文 Javadoc 类。
 */
public class GoodClass {}
```

- [ ] **Step 3.2：写 missing-javadoc.java**

写入 `scripts/check-comment-zh/fixtures/missing-javadoc.java`：

```java
public class MissingJavadoc {}
```

- [ ] **Step 3.3：写 english-only.java**

写入 `scripts/check-comment-zh/fixtures/english-only.java`：

```java
/**
 * Sample class with English-only Javadoc.
 */
public class EnglishOnly {}
```

- [ ] **Step 3.4：写 good-table.sql**

写入 `scripts/check-comment-zh/fixtures/good-table.sql`：

```sql
CREATE TABLE demo_table (
  id BIGINT PRIMARY KEY,
  status VARCHAR(32) NOT NULL
);
COMMENT ON TABLE demo_table IS '示例：合规的中文表注释';
```

- [ ] **Step 3.5：写 missing-comment.sql**

写入 `scripts/check-comment-zh/fixtures/missing-comment.sql`：

```sql
CREATE TABLE demo_no_comment (
  id BIGINT PRIMARY KEY
);
```

- [ ] **Step 3.6：写 run-fixtures.sh（self-test 入口）**

写入 `scripts/check-comment-zh/run-fixtures.sh`：

```bash
#!/usr/bin/env bash
# self-test：用 fixture 文件验证 javadoc_has_chinese / sql_table_has_comment 两个工具函数
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# 把脚本主体里的工具函数 source 进来（提取 javadoc_has_chinese + sql_table_has_comment）
CJK_PCRE='[\x{4e00}-\x{9fa5}]'
javadoc_has_chinese() {
  awk '
    BEGIN { in_doc=0; doc=""; printed=0 }
    /^\s*\/\*\*/ { in_doc=1; doc=$0; next }
    in_doc && /\*\// { doc=doc"\n"$0; in_doc=0; pending=doc; next }
    in_doc { doc=doc"\n"$0; next }
    /^\s*(@[A-Za-z]+(\(.*\))?\s*)*public\s+(class|record|interface|enum|@interface)/ && !printed {
      print pending; printed=1; exit
    }
  ' "$1" | grep -qP "$CJK_PCRE"
}
sql_table_has_comment() {
  local file="$1"
  local missing=""
  while IFS= read -r table; do
    if ! grep -qiP "COMMENT\s+ON\s+TABLE\s+${table}\s+IS\s+'[^']*${CJK_PCRE}" "$file"; then
      missing="${missing}${table} "
    fi
  done < <(grep -ioE 'CREATE TABLE (IF NOT EXISTS )?[A-Za-z_][A-Za-z0-9_]*' "$file" \
           | sed -E 's/CREATE TABLE (IF NOT EXISTS )?//I')
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

javadoc_has_chinese "$FIX/good-class.java"      && a=yes || a=no; check good-class yes "$a"
javadoc_has_chinese "$FIX/missing-javadoc.java" && a=yes || a=no; check missing-javadoc no "$a"
javadoc_has_chinese "$FIX/english-only.java"    && a=yes || a=no; check english-only no "$a"
sql_table_has_comment "$FIX/good-table.sql"     && a=yes || a=no; check good-table yes "$a"
sql_table_has_comment "$FIX/missing-comment.sql" && a=yes || a=no; check missing-comment no "$a"

echo "self-test: $pass pass, $fail fail"
exit $fail
```

- [ ] **Step 3.7：给 run-fixtures.sh 执行权限**

```bash
chmod +x scripts/check-comment-zh/run-fixtures.sh
```

### Task 4：跑 self-test 验证脚本

- [ ] **Step 4.1：执行 self-test**

```bash
scripts/check-comment-zh.sh --self-test
```

Expected:
```
  PASS  good-class
  PASS  missing-javadoc
  PASS  english-only
  PASS  good-table
  PASS  missing-comment
self-test: 5 pass, 0 fail
```

如果有 FAIL，调脚本里对应函数。常见原因：grep -P 在 Linux/Mac BSD grep 差异 → 测试用 macOS 默认 grep 时可能不支持 `-P`，需 fallback。如果 mac 上 fail，可以临时安装 `brew install grep` 或在脚本里改用 `LC_ALL=C grep -E "$ZH_REGEX"` 的备选路径，但 CI 在 ubuntu-latest，GNU grep 支持 -P，所以本地 mac fail 不阻断 CI 通过。

### Task 5：跑全量扫描确认现状

- [ ] **Step 5.1：full 模式扫一次**

```bash
scripts/check-comment-zh.sh --mode=full
```

Expected: 输出引擎模块覆盖率，看到与 spec § 1 数据一致：
- engine/evaluation 大约 3%（1/39）
- engine/pathway 大约 4%（2/49）
- engine/recommendation 大约 3%（1/29）
- engine/rule 大约 6%（2/33）
- engine/terminology 大约 5%（1/21）
- shared/* 大多 100%

把控制台输出贴到 commit message 末尾作为基线，便于后续对照。

### Task 6：把 CI workflow 增加 `comment-language-check` job

**Files:**
- Modify: `.github/workflows/ci.yml`（在 `jdk-matrix-smoke` 之后追加）

- [ ] **Step 6.1：在 ci.yml 末尾追加 job**

打开 `.github/workflows/ci.yml`，在文件末尾（197 行后）追加：

```yaml

  # GA-ENG-DOC-01 · 引擎层中文 Javadoc / SQL COMMENT 软门禁
  # 设计文档：docs/superpowers/specs/2026-05-27-engine-comment-language-design.md
  comment-language-check:
    name: comment-language-check
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0  # 需要完整历史以便 git merge-base origin/main

      - name: Run self-test
        run: scripts/check-comment-zh.sh --self-test

      - name: Run diff check (new files fail, modified files warn)
        run: scripts/check-comment-zh.sh

      - name: Print full coverage summary
        if: always()
        run: scripts/check-comment-zh.sh --mode=full
```

注意：保留文件末尾换行符。

### Task 7：更新 `AGENTS.md`

**Files:**
- Modify: `AGENTS.md:6`（在"代码标识符…"那条之后插入一条）

- [ ] **Step 7.1：编辑 AGENTS.md**

把 AGENTS.md 第 6 行：

```
- 代码标识符、接口路径、命令、配置键、数据库字段、标准英文缩写、第三方产品名和必要原文引用可以保留英文，但必须用中文解释其业务含义。
```

改成（保留原句，紧接一条新规则）：

```
- 代码标识符、接口路径、命令、配置键、数据库字段、标准英文缩写、第三方产品名和必要原文引用可以保留英文，但必须用中文解释其业务含义。
- `medkernel-backend` 引擎层（`com.medkernel.engine.**` 与 `com.medkernel.shared.**`）公共类、公共方法的 Javadoc，以及 Oracle/PostgreSQL/Kingbase 迁移脚本中新增表与枚举/状态/外键列的 `COMMENT ON`，必须使用简体中文；CI 由 `scripts/check-comment-zh.sh` 软门禁兜底。
```

### Task 8：更新 `docs/DOCUMENTATION_LANGUAGE_POLICY.md`

**Files:**
- Modify: `docs/DOCUMENTATION_LANGUAGE_POLICY.md:15-16`（§ 2 末尾新增）
- Modify: `docs/DOCUMENTATION_LANGUAGE_POLICY.md:22`（§ 3 措辞收紧）

- [ ] **Step 8.1：在 § 2「必须使用中文的内容」末尾追加条目**

把第 14-15 行末（"页面文案、错误说明、验收口径和业务流程说明。"）之后追加一条：

```markdown
- `medkernel-backend` 引擎层（`com.medkernel.engine.**` 与 `com.medkernel.shared.**`）公共类、公共方法的 Javadoc，以及 Oracle/PostgreSQL/Kingbase 迁移脚本中新增表、枚举列、状态列、外键列的 `COMMENT ON`。前端 TypeScript 注释仍按 `frontend/README` 既有约定，不在此次治理范围。
```

- [ ] **Step 8.2：调整 § 3 第 22 行措辞**

把第 22 行：

```
- 命令、环境变量、日志片段、HTTP 状态、JSON / YAML / SQL / TypeScript / Java 代码块。
```

改成：

```
- 命令、环境变量、日志片段、HTTP 状态、JSON / YAML 配置片段、代码标识符与 SQL 字段名。
```

避免被理解成"代码注释也可保留英文"。

### Task 9：本地验证 + commit + push + 开 PR

- [ ] **Step 9.1：再次跑 diff 模式确认 PR1 自身通过**

```bash
scripts/check-comment-zh.sh
```

Expected: PR1 只改了文档/脚本/workflow，**没有新增 engine/* Java 或 SQL 文件**，检查 1/2 都 0 fail。可能有少量 warn（如果有 engine/* 修改）也无所谓。

- [ ] **Step 9.2：commit**

```bash
git add scripts/check-comment-zh.sh scripts/check-comment-zh/ \
        AGENTS.md docs/DOCUMENTATION_LANGUAGE_POLICY.md \
        .github/workflows/ci.yml \
        docs/superpowers/specs/2026-05-27-engine-comment-language-design.md \
        docs/superpowers/plans/2026-05-27-engine-comment-language.md
git commit -m "$(cat <<'EOF'
feat(ci): 引擎层中文 Javadoc / SQL COMMENT 软门禁

- 新增 scripts/check-comment-zh.sh：diff 模式 fail 新增 Java/SQL 缺中文注释、warn 修改文件、full 模式输出全量覆盖率
- 新增 scripts/check-comment-zh/ 下 5 个 fixture + run-fixtures.sh self-test
- AGENTS.md / DOCUMENTATION_LANGUAGE_POLICY.md 追加：引擎层 Javadoc + Oracle/Postgres/Kingbase COMMENT ON 必须中文
- ci.yml 新增 comment-language-check job（self-test + diff + 全量覆盖率汇总）
- 落盘 spec / plan 文档

设计文档：docs/superpowers/specs/2026-05-27-engine-comment-language-design.md
存量补齐由后续 PR2-6 分模块跟进。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 9.3：push 并开 PR**

```bash
git push -u origin codex/comment-zh-pr1-policy-ci
gh pr create --base main --head codex/comment-zh-pr1-policy-ci \
  --title "feat(ci): 引擎层中文 Javadoc / SQL COMMENT 软门禁" \
  --body "$(cat <<'EOF'
## 变更范围

- 新增 \`scripts/check-comment-zh.sh\`：
  - **diff 模式**：新增 \`engine/*\` 或 \`shared/*\` Java 文件类级 Javadoc 缺中文 → fail；新增 oracle/postgres/kingbase 迁移文件 \`CREATE TABLE\` 缺中文 \`COMMENT ON TABLE\` → fail；修改文件仅 warn。
  - **full 模式**：输出引擎模块覆盖率与 SQL COMMENT 缺口，写入 GitHub Actions step summary。
  - **self-test 模式**：5 个 fixture 验证工具函数。
- \`AGENTS.md\` 与 \`docs/DOCUMENTATION_LANGUAGE_POLICY.md\` 追加引擎层中文注释要求条款。
- \`.github/workflows/ci.yml\` 新增 \`comment-language-check\` job（fetch-depth=0 以便 merge-base）。
- 落盘设计文档 \`docs/superpowers/specs/2026-05-27-engine-comment-language-design.md\` 与实施计划。

## 验证

- [x] \`scripts/check-comment-zh.sh --self-test\` 全 PASS
- [x] \`scripts/check-comment-zh.sh\`（diff 模式）本地通过，0 fail
- [x] \`scripts/check-comment-zh.sh --mode=full\` 输出与 spec § 1 现状基线一致
- [ ] CI \`comment-language-check\` job 通过（push 后看远端）

## 后续

PR2-6 按模块（terminology → recommendation → rule → evaluation → pathway）补齐 ~170 个 Java 文件 Javadoc 与 V8-V14 SQL COMMENT。

## 影响范围

- 医疗安全：无
- 部署：无（仅 CI 步骤新增）
- 数据迁移：无

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

记录返回的 PR URL，本会话需要在最终汇总里列出。

---

## PR2-6 通用模板

PR2-6 模式高度一致，只是模块和文件清单不同。每个 PR 的 step 模板复用如下骨架。**每个 PR 都要执行所有步骤**，下面给出**模板** + 每个 PR 的**专属字段**。

### 通用 Step 模板

```
- [ ] 通用 Step A：从 origin/main 切分支 codex/comment-zh-pr{N}-{module}
- [ ] 通用 Step B：按 Javadoc 模板批量补类级 + 公共方法 Javadoc
- [ ] 通用 Step C：补 oracle/postgres/kingbase V{N} 表 COMMENT ON
- [ ] 通用 Step D：mvn -B -q compile 通过
- [ ] 通用 Step E：scripts/check-comment-zh.sh --mode=full 看本模块覆盖率 = 100%
- [ ] 通用 Step F：commit + push + gh pr create
```

### Javadoc 通用模板

**类级（每个 .java 文件必须有）：**

```java
/**
 * <一句话业务身份>，如：GA-ENG-API-XX 评估指标定义实体（DRAFT→PUBLISHED→ACTIVE 三态）。
 *
 * <p><上下文协作类、关键不变量、关键 API 入口（可选；复杂类必填）>。
 */
```

要点：
- record/Entity/枚举：1 行类级即可
- Controller / Service / Repository：1 行 + `<p>` 段补关键约束、错误码、协作类
- 引用其他类用 `{@link ClassName#method}`

**公共方法（仅 Controller / Service 的 public 方法）：**

```java
/**
 * <动作 + 对象>，如：创建评估指标草稿。
 *
 * <p><关键前置条件 + 可能抛出的 ApiException 错误码；可选>。
 */
```

要点：
- DTO 方法（accessor / getter）免注
- Repository 自定义查询方法（非 Spring Data 命名）：1 行说明业务意图
- 私有方法、`equals/hashCode/toString`、Spring 生命周期方法：免注

### SQL COMMENT 通用模板

每个 oracle/postgres/kingbase 方言文件，对其每个 `CREATE TABLE` 都补：

```sql
-- 表注释
COMMENT ON TABLE <table_name> IS '<业务身份 + 关键唯一约束>';

-- 枚举/状态列
COMMENT ON COLUMN <table_name>.<status_col> IS '<列名中文 + 各枚举值含义>';

-- 业务键列
COMMENT ON COLUMN <table_name>.<bk_id_col> IS '<业务键 ID，跨租户唯一>';

-- 外键列（如适用）
COMMENT ON COLUMN <table_name>.<fk_col> IS '<关联表 + 业务含义>';
```

**通用列免注**：`created_at` / `created_by` / `updated_at` / `updated_by` / `trace_id` / 自增 `id`。

H2 文件不动（仅测试 baseline，文件首行中文标题已够）。

### 通用 commit message 模板

```
docs(engine/{module}): 补齐 Javadoc 与 SQL COMMENT 中文注释

- 补 com/medkernel/engine/{module}/ 下 N 个 .java 类级 + 公共方法中文 Javadoc
- 补 db/migration/{oracle,postgres,kingbase}/V{N}__{module}.sql 全部 CREATE TABLE 的 COMMENT ON TABLE + 枚举/状态/外键 COMMENT ON COLUMN
- scripts/check-comment-zh.sh --mode=full 本模块覆盖率：100%

设计文档：docs/superpowers/specs/2026-05-27-engine-comment-language-design.md

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

### 通用 PR body 模板

```markdown
## 变更范围

- 补 com/medkernel/engine/{module}/ 下 N 个 .java 类级 + 公共方法中文 Javadoc
- 补 db/migration/{oracle,postgres,kingbase}/V{N}__{module}.sql 全部 CREATE TABLE 的 COMMENT ON TABLE 及枚举/状态/外键 COMMENT ON COLUMN

依赖 PR #<PR1 编号> 的 CI 门禁。

## 验证

- [x] mvn -B -q compile 通过
- [x] scripts/check-comment-zh.sh --mode=full 输出 engine/{module} 覆盖率 100%
- [x] scripts/check-comment-zh.sh（diff 模式）0 fail
- [ ] CI comment-language-check 通过

## 影响范围

- 医疗安全：无（仅注释变更）
- 部署：无（COMMENT ON 是 DDL 元数据，Flyway 会按版本号新建 baseline，但本批 PR 是补已有 V8-V14 文件的注释——这些迁移在 GA 切换前都未在生产执行，所以补注释相当于初始版本带注释。如有生产环境已经执行过 V11-V14，请按"重新执行 baseline DDL"或"手工补 COMMENT ON"处理，由实施侧决定）
- 数据迁移：补 COMMENT ON 不影响数据，但需要 Flyway 容忍迁移文件校验和变化——本批 PR 完成后请检查 medkernel-backend 的 flyway 配置是否启用 \`flyway.validateOnMigrate=false\` 或保留 \`repair\` 步骤；若生产已用 V11-V14 baseline，建议改为新增 V15__\*_comments.sql 增量补注释（这条由具体 PR 评估决定）

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

> ⚠️ **重要决策点：补 COMMENT 是改既有 V11-V14 文件还是新建 V15__*_comments.sql？**
>
> 各方案利弊：
> - **改既有 V8-V14**：文件简洁、单一来源；但 Flyway checksum 变化，已经执行过该 baseline 的环境会迁移失败。
> - **新建 V15__\*_comments.sql**：不破坏 checksum；但 14 个 V 版本散开看不到完整表语义。
>
> 现状：仓库尚未对外发布、生产无 V11-V14 执行记录（v1.0-GA-evidence 文档 [docs/release/v1.0.0-ga-evidence.md](docs/release/v1.0.0-ga-evidence.md) 可查证 GA 还未生效），所以**默认改既有 V8-V14**。
>
> 但每个 PR 在 push 前必须用 `grep -n "validateOnMigrate" medkernel-backend/src/main/resources/application*.yml` 和 `git log --oneline V11 V12 V13 V14` 确认仓库基线迁移尚未投入使用，否则改为新增 `V15__{module}_comments.sql`。

---

## PR2：terminology 模块（最小，先做暴露 CI 问题）

**分支：** `codex/comment-zh-pr2-terminology`

**Files：**

Java（21 个，路径前缀 `medkernel-backend/src/main/java/com/medkernel/engine/terminology/`）：
- `LocalTerm.java`, `LocalTermRepository.java`
- `StandardTerm.java`, `StandardTermRepository.java`
- `MappingCandidate.java`, `MappingCandidateRepository.java`
- `MappingConflict.java`, `MappingConflictRepository.java`
- `TermMapping.java`, `TermMappingRepository.java`
- `TermMappingPackage.java`, `TermMappingPackageRepository.java`
- `TermMappingPackageItem.java`, `TermMappingPackageItemRepository.java`
- `TermMappingPackageRelease.java`, `TermMappingPackageReleaseRepository.java`
- `TerminologyController.java`, `TerminologyService.java`
- `TerminologyEnums.java`, `TerminologyFilters.java`, `TerminologyRequests.java`

> 注：`TerminologyService.java` 已含部分中文（业务字符串），但**类级 Javadoc 缺失**——仍需补。

SQL（V4，3 个方言）：
- `medkernel-backend/src/main/resources/db/migration/oracle/V4__terminology_mapping_baseline.sql`
- `medkernel-backend/src/main/resources/db/migration/postgres/V4__terminology_mapping_baseline.sql`
- `medkernel-backend/src/main/resources/db/migration/kingbase/V4__terminology_mapping_baseline.sql`

> 注：V4 是早期迁移，可能 oracle/postgres 已经有部分 COMMENT。补齐缺口，已有的不动。

### Task 10：执行 PR2

- [ ] **Step 10.1：切分支**

```bash
git checkout main && git pull --ff-only
git checkout -b codex/comment-zh-pr2-terminology
```

- [ ] **Step 10.2：批量补 Javadoc**

对 21 个文件应用 Javadoc 通用模板。**建议方式**：先 Read 每个文件，按上面的"Javadoc 通用模板"插入。重点是：

1. **实体类（record）**：1 行类级，描述业务身份 + 关键唯一约束。例：
   ```java
   /**
    * 标准临床术语字典记录（ICD-10 / SNOMED CT / LOINC 等标准词条）。
    *
    * <p>跨租户共享，由术语包 {@link TermMappingPackage} 发布版本管理；
    * 业务键 standard_code 在同 system 内唯一。
    */
   ```
2. **Repository**：1 行类级。例：
   ```java
   /**
    * 标准术语字典持久化仓库。
    */
   ```
3. **Controller / Service**：类级 + 每个 public 方法。
4. **枚举/Filter/Request 聚合类**（TerminologyEnums/Filters/Requests）：类级 + 每个 nested type 一行注释。

- [ ] **Step 10.3：补 SQL COMMENT**

读 V4 三个方言文件，按 SQL 通用模板补 `COMMENT ON`。具体每个表的业务说明从 [docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md](docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md) 和 [docs/specs/2026-05-25-terminology-mapping-api-design.md](docs/specs/2026-05-25-terminology-mapping-api-design.md) 抄。

需要补的表（V4）：`local_term`、`standard_term`、`term_mapping`、`mapping_candidate`、`mapping_conflict`、`term_mapping_package`、`term_mapping_package_item`、`term_mapping_package_release`。

- [ ] **Step 10.4：编译 + 检查脚本**

```bash
cd medkernel-backend && mvn -B -q compile && cd ..
scripts/check-comment-zh.sh --mode=full | grep terminology
```

Expected: terminology 行显示 `100% (21/21)`。

- [ ] **Step 10.5：commit + push + 开 PR**

按通用 commit/PR body 模板写，**模块名 = terminology，V 版本 = V4**。

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/terminology/ \
        medkernel-backend/src/main/resources/db/migration/oracle/V4__terminology_mapping_baseline.sql \
        medkernel-backend/src/main/resources/db/migration/postgres/V4__terminology_mapping_baseline.sql \
        medkernel-backend/src/main/resources/db/migration/kingbase/V4__terminology_mapping_baseline.sql
git commit -m "$(cat <<'EOF'
docs(engine/terminology): 补齐 Javadoc 与 SQL COMMENT 中文注释

- 补 com/medkernel/engine/terminology/ 下 21 个 .java 类级 + 公共方法中文 Javadoc
- 补 db/migration/{oracle,postgres,kingbase}/V4__terminology_mapping_baseline.sql 全部 CREATE TABLE 的 COMMENT ON TABLE + 枚举/状态/外键 COMMENT ON COLUMN
- scripts/check-comment-zh.sh --mode=full 本模块覆盖率：100%

设计文档：docs/superpowers/specs/2026-05-27-engine-comment-language-design.md

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
git push -u origin codex/comment-zh-pr2-terminology
gh pr create --base main --head codex/comment-zh-pr2-terminology \
  --title "docs(engine/terminology): 补齐 Javadoc 与 SQL COMMENT 中文注释" \
  --body "<参考 PR body 通用模板，替换 module=terminology / N=21 / V=V4>"
```

---

## PR3：recommendation 模块

**分支：** `codex/comment-zh-pr3-recommendation`

**Files：**

Java（29 个，路径前缀 `medkernel-backend/src/main/java/com/medkernel/engine/recommendation/`）：完整列表见 plan 顶部的 Bash 输出，包括：
- Controller + Service：`RecommendationEngineController.java`, `RecommendationEngineService.java`
- Entity record：`RecommendationCard.java`, `RecommendationTrigger.java`, `RecommendationSource.java`, `RecommendationFeedback.java`, `RecommendationFatigueSignal.java`
- Repository（5 个）
- Request/Response/Filter（7 个）
- Enum（7 个：CardStatus / CardType / FeedbackType / FatigueSignalType / TriggerStatus / RiskLevel / InterruptLevel / SourceType）

SQL（V13，3 个方言）。

### Task 11：执行 PR3

按 PR2 同样的 6 步执行，模块名 = recommendation，V 版本 = V13，文件数 = 29。

**业务说明参考来源**：[docs/superpowers/specs/2026-05-27-engine-recommendation-cdss-api-design.md](docs/superpowers/specs/2026-05-27-engine-recommendation-cdss-api-design.md)。

关键模式（写 Javadoc 时直接抄业务术语）：
- `RecommendationCard` 是 CDSS 推荐卡片，状态机：PENDING → VIEWED → ACCEPTED/REJECTED/DEFERRED/DISMISSED → EXPIRED/SUPPRESSED
- `RecommendationTrigger` 是触发源（lab 异常、规则命中等）
- `RecommendationFatigueSignal` 用于警报疲劳治理
- 强打断必须高风险（service 里的业务规则）

V13 涉及表：`recommendation_trigger`、`recommendation_card`、`recommendation_source`、`recommendation_feedback`、`recommendation_fatigue_signal`。

---

## PR4：rule 模块

**分支：** `codex/comment-zh-pr4-rule`

**Files：**

Java（33 个，路径前缀 `medkernel-backend/src/main/java/com/medkernel/engine/rule/`）。

SQL（V11，3 个方言）。

### Task 12：执行 PR4

按 PR2 同样的 6 步执行，模块名 = rule，V 版本 = V11，文件数 = 33。

**业务说明参考来源**：[docs/superpowers/specs/2026-05-27-engine-rule-api-design.md](docs/superpowers/specs/2026-05-27-engine-rule-api-design.md)。

关键模式：
- `RuleDefinition` + `RuleVersion`：规则定义与版本（DRAFT/PUBLISHED/ACTIVE/OFFLINE）
- `RuleTestCase`：规则测试用例（fixtures，GIVEN/EXPECT）
- `RuleDslEvaluator` + `RuleDslEvaluation`：DSL 评估器和单次执行记录
- `RuleExecutionLog`：执行日志
- 涉及枚举：`RuleType`（命中/不命中/参考）、`RuleRiskLevel`、`RuleAuthoringMode`（DSL/Drools/Python/...）

V11 涉及表（按 `V11__rule_engine_api.sql` 实际定义）：先 Read 该文件确认表名清单，然后逐表写 `COMMENT ON`。

---

## PR5：evaluation 模块

**分支：** `codex/comment-zh-pr5-evaluation`

**Files：**

Java（39 个）。

SQL（V14，3 个方言）。

### Task 13：执行 PR5

按 PR2 同样的 6 步执行，模块名 = evaluation，V 版本 = V14，文件数 = 39。

**业务说明参考来源**：[docs/superpowers/specs/2026-05-27-engine-evaluation-quality-api-design.md](docs/superpowers/specs/2026-05-27-engine-evaluation-quality-api-design.md)。

关键模式：
- `EvaluationIndicator` 状态机：DRAFT → PENDING_REVIEW → PUBLISHED → ACTIVE → OFFLINE → ARCHIVED
- `EvaluationRun`：评估批次（MANUAL_SAMPLE / UPSTREAM_RESULT / BATCH_IMPORT）
- `EvaluationResult` 结果级别：PASS / ATTENTION / NON_COMPLIANT / CRITICAL
- `QualityFinding` 严重度：P0-P3，状态：NEW → ASSIGNED → REMEDIATING → CLOSED / WAIVED
- `RectificationTask` 整改任务流转
- `EvaluationIdempotencyKey`：整改提交/复核幂等键

V14 涉及表：`evaluation_indicator`、`evaluation_run`、`evaluation_result`、`quality_finding`、`rectification_task`、`rectification_review`、`evaluation_idempotency_key`（7 张）。

---

## PR6：pathway 模块（最大，最后做）

**分支：** `codex/comment-zh-pr6-pathway`

**Files：**

Java（49 个）。

SQL（V12，3 个方言）。

### Task 14：执行 PR6

按 PR2 同样的 6 步执行，模块名 = pathway，V 版本 = V12，文件数 = 49。

**业务说明参考来源**：[docs/superpowers/specs/2026-05-27-engine-pathway-api-design.md](docs/superpowers/specs/2026-05-27-engine-pathway-api-design.md)。

关键模式：
- `PathwayTemplate` + `PathwayNode` + `PathwayEdge`：临床路径模板（图结构）
- `PatientPathway` + `PathwayProgressor`：患者执行实例 + 推进引擎
- `ClinicalClock`：临床时钟（用于路径节点时限计算）
- `PathwayVariance`：偏差记录
- `SpecialtyPackage` / `SpecialtyProfile` / `SpecialtyMetricBinding`：科室级路径包

V12 涉及表（按 `V12__pathway_engine_api.sql` 实际定义）：先 Read 该文件确认表名清单。

---

## 收尾：本会话汇总

### Task 15：本会话最后输出 6 个 PR URL

- [ ] **Step 15.1：列出 6 个 PR**

```bash
gh pr list --state open --search "author:@me head:codex/comment-zh-" --json url,title,headRefName
```

Expected: 6 行 JSON，每行一个 PR。

- [ ] **Step 15.2：在会话最后给用户输出 markdown 列表**

格式：
```
PR1: <url> · feat(ci): 引擎层中文 Javadoc / SQL COMMENT 软门禁
PR2: <url> · docs(engine/terminology): ...
PR3: <url> · docs(engine/recommendation): ...
PR4: <url> · docs(engine/rule): ...
PR5: <url> · docs(engine/evaluation): ...
PR6: <url> · docs(engine/pathway): ...
```

并附一句话提示用户：先合 PR1（CI 门禁），再按依赖顺序合 PR2-6（彼此独立可乱序）。

---

## 风险提示与降级路径

1. **Maven 编译失败**：如果补 Javadoc 时不小心动到 import 或代码本身，先 `git diff` 看是不是非注释改动，恢复成"只动注释"。Javadoc 写错也可能让 javadoc plugin 报错——本仓库 `mvn compile` 不跑 javadoc plugin，所以放心。
2. **scripts/check-comment-zh.sh 在 macOS BSD grep 上 `-P` 不支持**：脚本里已用 `grep -qP "$CJK_PCRE"`。macOS 默认 BSD grep 不支持 `-P`。本地跑可改用 `LC_ALL=en_US.UTF-8 grep -E '<UTF-8 字符范围>'`，但 CI 在 ubuntu-latest，GNU grep 支持 -P，所以 CI 一定通过。
3. **PR 数量太多，本会话上下文耗尽**：每个 PR 完成后立即 commit + push，不积压。如真的 token 不足，至少保证 PR1 完整 + PR2 完整作为后续模板示范，PR3-6 在下个会话继续——但要在本会话最后明确告诉用户当前进度。
4. **Flyway 校验失败**：spec § 3.3 已明确 H2 不动。oracle/postgres/kingbase 是否会因为修改既有 V8-V14 而 checksum 失败，取决于这些版本是否已经在生产 baseline 跑过。看 [docs/release/v1.0.0-ga-evidence.md](docs/release/v1.0.0-ga-evidence.md) 确认 GA 状态——若 GA 还没生效（spec 是 2026-05-27 计划阶段），改既有 V 文件不会触发 Flyway 校验问题。

---

## Self-Review 结果

### Spec 覆盖

- spec § 1（背景）→ 不需任务（仅情况说明）✓
- spec § 2（目标/非目标）→ 由 PR1-6 整体满足 ✓
- spec § 3.1（PR 拆分）→ Task 9 / Task 10-14 ✓
- spec § 3.2（Javadoc 模板）→ PR2-6 通用 Javadoc 模板 ✓
- spec § 3.3（SQL COMMENT 模板）→ PR2-6 通用 SQL COMMENT 模板 ✓
- spec § 3.4（CI 软门禁脚本）→ Task 2-5（脚本）+ Task 6（workflow）✓
- spec § 3.5（规范文档）→ Task 7-8 ✓
- spec § 4（失败模式表）→ 在 Task 6 的 workflow + Task 4 的 self-test 中验证 ✓
- spec § 5（测试）→ Task 4 / Task 5 ✓
- spec § 6（风险）→ "风险提示与降级路径"章节 ✓
- spec § 7（完成定义）→ Task 15 ✓

### 占位符扫描

无 TBD / TODO / fill-in 项。所有代码块给出完整内容。

### 类型一致性

脚本函数名 `javadoc_has_chinese` / `sql_table_has_comment` 在 Task 2 / Task 3.6 两处定义，签名一致。CI workflow step name `comment-language-check` 在 Task 6 / Task 15 引用一致。
