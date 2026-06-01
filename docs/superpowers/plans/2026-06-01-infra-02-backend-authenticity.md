# INFRA-02 Backend Authenticity Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 INFRA-02 后端真实性门禁收口，保证 FR-1~FR-6 与 AC-1~AC-5 有可执行脚本、规则级测试、CI 证据和文档状态。

**Architecture:** 复用现有 `scripts/authenticity-guard.mjs`，不新造第二套后端门禁。现有脚本已经阻断 Math.random、医学常量、catch 成功、UUID 充 hash、占位 Javadoc 等后端生产路径问题；本 PR 补齐 dev profile bean 白名单的 RED→GREEN 证据，并把 INFRA-02 卡片、backlog、handoff 从 pending 状态收口。

**Tech Stack:** Node `node:test`、现有真实性门禁脚本、GitHub Actions `guard-rules`、MedKernel 后端 Java 源码扫描。

---

## 执行约束

- TDD：先补失败测试，再改 `scripts/authenticity-guard.mjs`。
- 只处理 INFRA-02 后端真实性门禁，不改业务服务逻辑，不清理合法 ID 生成用 `UUID.randomUUID()`。
- 白名单只放行测试目录、迁移 SQL、明确 `@Profile("dev")` 的 dev bean / 配置；不得放宽生产路径扫描。
- `docs/audit/deferred-issues.md` 只登记外部环境或非当前阶段问题，不得用于延期本卡主链路门禁。

## File Map

- Modify: `scripts/authenticity-guard.test.mjs` — 增加 INFRA-02 后端白名单测试，覆盖测试目录、迁移 SQL、dev profile bean。
- Modify: `scripts/authenticity-guard.mjs` — 增加后端 dev profile bean allowlist。
- Modify: `docs/cards/D0/INFRA-02.md` — 勾选 FR/AC 并补真实验证证据。
- Modify: `docs/backlog.md` — INFRA-02 标为 `done`。
- Modify: `docs/_HANDOFF.md` — 归档 INFRA-01，登记 INFRA-02 当前状态和下一步。
- Create: `docs/superpowers/plans/2026-06-01-infra-02-backend-authenticity.md` — 本计划。

## Task 1: Baseline And RED Tests

**Files:**
- Modify: `scripts/authenticity-guard.test.mjs`

- [x] **Step 1: Record green baseline**

Run:

```bash
node --test scripts/authenticity-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=inventory
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
```

Expected before edits: authenticity tests 20/20 pass; inventory scans the repo and reports no blocking violations; changed scan reports no blocking violations.

- [x] **Step 2: Add RED backend allowlist test**

Append this test to `scripts/authenticity-guard.test.mjs`:

```js
test("后端真实性门禁放行测试目录、迁移 SQL 与 dev profile bean", async () => {
  await withFixture(
    {
      "medkernel-backend/src/test/java/com/medkernel/engine/BadServiceTest.java": `
        package com.medkernel.engine;

        /** 演示测试服务，占位实现。 */
        class BadServiceTest {
          double randomScore() {
            return Math.random();
          }
        }
      `,
      "medkernel-backend/src/main/resources/db/migration/postgres/V99__demo.sql": `
        COMMENT ON TABLE demo_table IS '演示迁移占位';
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/dev/DevOnlyConfig.java": `
        package com.medkernel.engine.dev;

        import org.springframework.context.annotation.Bean;
        import org.springframework.context.annotation.Configuration;
        import org.springframework.context.annotation.Profile;

        /** dev profile 演示配置，仅本地开发使用。 */
        @Configuration
        @Profile("dev")
        public class DevOnlyConfig {
          @Bean
          String localHealthValue() {
            return "dev-" + Math.random();
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/test/java/com/medkernel/engine/BadServiceTest.java",
        "medkernel-backend/src/main/resources/db/migration/postgres/V99__demo.sql",
        "medkernel-backend/src/main/java/com/medkernel/engine/dev/DevOnlyConfig.java",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});
```

- [x] **Step 3: Verify RED**

Run:

```bash
node --test scripts/authenticity-guard.test.mjs
```

Expected RED: fails on `DevOnlyConfig.java` with `backend.random-business-value` and `backend.placeholder-javadoc`, proving dev profile bean is not yet allowlisted. Test and migration files should already be ignored by path scope.

## Task 2: Implement Dev Profile Backend Allowlist

**Files:**
- Modify: `scripts/authenticity-guard.mjs`

- [x] **Step 1: Add dev profile helper**

Add:

```js
function isBackendDevProfileBean(content) {
  return /@Profile\s*\(\s*(?:["']dev["']|\{[\s\S]*?["']dev["'][\s\S]*?\})\s*\)/.test(content) &&
    /@(Configuration|Component|Bean)\b/.test(content);
}
```

- [x] **Step 2: Apply helper before adding backend violations**

In `scanFiles`, after reading `content` and before `addRuleViolations`, add:

```js
    if (BACKEND_JAVA.test(file) && isBackendDevProfileBean(content)) {
      continue;
    }
```

- [x] **Step 3: Verify GREEN**

Run:

```bash
node --test scripts/authenticity-guard.test.mjs
```

Expected: 21/21 pass.

## Task 3: Docs And Handoff

**Files:**
- Modify: `docs/cards/D0/INFRA-02.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Update INFRA-02 card**

Check FR-1~FR-6 and AC-1~AC-5. Evidence must include:

```bash
node --test scripts/authenticity-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=inventory
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
```

- [x] **Step 2: Update backlog**

Mark `INFRA-02` as `done`.

- [x] **Step 3: Update handoff**

Archive INFRA-01 with PR #228 / merge `37a8907`; set active line to INFRA-02 until this PR is merged. Keep the long-task rule explicit: open deferred issues are recorded and do not block unrelated next tasks; current-card authenticity, login, permission, and medical-safety issues are not deferrable.

## Task 4: Final Verification And PR

- [x] **Step 1: Backend guard verification**

Run:

```bash
node --test scripts/authenticity-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=inventory
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
```

Expected: tests 21/21 pass; inventory and changed scans pass with no blocking violations.

- [x] **Step 2: Root T-GATE**

Run:

```bash
node --test scripts/migration-convention-guard.test.mjs
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
node --test scripts/config-boundary-guard.test.mjs
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check
```

Expected: all exit `0`.

- [ ] **Step 3: Commit, PR, CI, merge**

Commit:

```bash
git commit -m "完成 INFRA-02 后端真实性门禁"
```

Push, create PR, wait for remote CI 8/8, squash merge, pull main, remove worktree and local branch, then领取下一张 D0 pending 卡。

## Self Review

- Spec coverage: FR-1~FR-5 already covered by existing backend guard tests; FR-6 is completed by the new allowlist test and dev profile helper.
- Placeholder scan: no TBD/TODO/“后续实现” in the plan.
- Scope: only guard script, tests, CI evidence docs, backlog and handoff; no runtime service behavior changes.
