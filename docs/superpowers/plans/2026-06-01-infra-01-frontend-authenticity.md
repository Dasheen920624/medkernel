# INFRA-01 Frontend Authenticity Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 INFRA-01 前端真实性门禁，一次性收口 FR-1~FR-6 与 AC-1~AC-5。

**Architecture:** 复用现有前端 ESLint flat config 与 stylelint 配置，不新造门禁体系。增强 `medkernel/no-page-mock` 规则覆盖 mock/fixture import、包装函数绕过、医学常量、技术对象裸露和白名单；新增独立 Node 规则测试，并接入 CI 前端 lint job。`scripts/authenticity-guard.mjs` 继续作为 touched-file T-GATE 总门禁，INFRA-01 本 PR 把 ESLint/stylelint 半边补到可独立证伪。

**Tech Stack:** ESLint 9 RuleTester、Node `node:test`、stylelint 17、Vite/React/TypeScript、GitHub Actions。

---

## 执行约束

- 先写 RED 测试，再改规则；不能用“脚本已有覆盖”代替 `medkernel/no-page-mock` 的规则级证据。
- 测试 / Storybook / `src/test` / `src/mocks` 必须放行；`theme.ts` token 文件继续由 `no-hardcoded-color` 白名单放行。
- 现有生产前端 lint/stylelint 基线必须保持绿色；若发现存量债务，不绕门禁，按当前卡范围修或登记为 deferred。
- 本 PR 不改业务页面体验，不做页面重构。

## File Map

- Modify: `frontend/eslint-rules/no-page-mock.js` — 扩展前端真实性 AST 规则。
- Create: `frontend/eslint-rules/no-page-mock.test.js` — RuleTester 覆盖 FR-1~FR-4/FR-6。
- Create: `frontend/stylelint.config.test.js` — stylelint 配置阻断/放行测试，覆盖 FR-5/FR-6。
- Modify: `frontend/package.json` — 新增 `test:lint-rules`，并纳入 `verify`。
- Modify: `.github/workflows/ci.yml` — `frontend-lint` job 跑 `npm run test:lint-rules`。
- Modify: `frontend/eslint-rules/README.md` — 记录 `no-page-mock` 完整职责与测试命令。
- Modify: `docs/cards/D0/INFRA-01.md` — 勾选 FR/AC，补证据。
- Modify: `docs/backlog.md` — INFRA-01 标为 `done`。
- Modify: `docs/_HANDOFF.md` — 归档 SYS-05 PR2，登记 INFRA-01 进度和下一步。

## Task 1: Baseline And RED Tests

**Files:**
- Create: `frontend/eslint-rules/no-page-mock.test.js`
- Create: `frontend/stylelint.config.test.js`
- Modify: `frontend/package.json`

- [x] **Step 1: Record green baseline**

Run:

```bash
cd frontend
npm run lint
npm run stylelint
cd ..
node --test scripts/authenticity-guard.test.mjs
```

Expected before edits: lint/stylelint exit `0`; authenticity guard tests 20/20 pass. If `eslint` / `stylelint` is missing in a fresh worktree, run `npm install --no-audit --no-fund --no-package-lock` in `frontend` and rerun.

- [x] **Step 2: Add RED RuleTester tests**

Create `frontend/eslint-rules/no-page-mock.test.js` with RuleTester cases:

```js
import { RuleTester } from "eslint";
import test from "node:test";

import rule from "./no-page-mock.js";

RuleTester.setDefaultConfig({
  languageOptions: {
    ecmaVersion: 2022,
    sourceType: "module",
    parserOptions: { ecmaFeatures: { jsx: true } },
  },
});

const tester = new RuleTester();

test("medkernel/no-page-mock blocks production mock and authenticity debt", () => {
  tester.run("no-page-mock", rule, {
    valid: [
      {
        filename: "/repo/frontend/src/pages/Login.test.tsx",
        code: 'import MockAdapter from "axios-mock-adapter"; const value = Math.random();',
      },
      {
        filename: "/repo/frontend/src/features/Sample.stories.tsx",
        code: 'const DEMO_ROWS = [{ id: 1 }]; export const Basic = {};',
      },
      {
        filename: "/repo/frontend/src/pages/GoodPage.tsx",
        code: 'export function GoodPage() { return <section className="mk-page">真实接口返回后展示</section>; }',
      },
    ],
    invalid: [
      {
        filename: "/repo/frontend/src/pages/BadImport.tsx",
        code: 'import MockAdapter from "axios-mock-adapter"; export function BadImport() { return null; }',
        errors: [{ messageId: "mockImport" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadFixture.tsx",
        code: 'import { rows } from "../fixtures/patient"; export function BadFixture() { return rows.length; }',
        errors: [{ messageId: "mockImport" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadWrapped.tsx",
        code: "function getMokRows() { return [{ id: 1 }]; } export function BadWrapped() { return getMokRows().length; }",
        errors: [{ messageId: "wrappedMock" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadMedical.tsx",
        code: 'export function BadMedical() { return <input defaultValue="高血压 DRUG-001 I10" />; }',
        errors: [{ messageId: "medicalConstant" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadTechnical.tsx",
        code: 'export function BadTechnical() { return <pre className="font-mono">{JSON.stringify({ traceId: "t" })}</pre>; }',
        errors: [{ messageId: "technicalObject" }, { messageId: "technicalObject" }],
      },
    ],
  });
});
```

- [x] **Step 3: Add RED stylelint config tests**

Create `frontend/stylelint.config.test.js`:

```js
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

async function withCss(content, run) {
  const dir = await mkdtemp(join(tmpdir(), "medkernel-stylelint-"));
  const file = join(dir, "Bad.module.css");
  try {
    await writeFile(file, content, "utf8");
    return await run(file);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

test("stylelint blocks hardcoded color and px tokens", async () => {
  await withCss(".bad { color: #1565c0; border-radius: 8px; font-size: 14px; }", (file) => {
    assert.throws(() => execFileSync("npx", ["stylelint", "--config", "stylelint.config.mjs", file], {
      cwd: new URL(".", import.meta.url),
      encoding: "utf8",
      stdio: "pipe",
    }));
  });
});

test("stylelint allows token variables", async () => {
  await withCss(".good { color: var(--ant-color-text); border-radius: var(--ant-border-radius); font-size: var(--ant-font-size); }", (file) => {
    assert.doesNotThrow(() => execFileSync("npx", ["stylelint", "--config", "stylelint.config.mjs", file], {
      cwd: new URL(".", import.meta.url),
      encoding: "utf8",
      stdio: "pipe",
    }));
  });
});
```

- [x] **Step 4: Wire test script and verify RED**

Add to `frontend/package.json`:

```json
"test:lint-rules": "node --test eslint-rules/*.test.js stylelint.config.test.js"
```

Change `verify` to:

```json
"verify": "npm run lint && npm run stylelint && npm run test:lint-rules && npm run format:check && npm run typecheck && npm test"
```

Run:

```bash
cd frontend
npm run test:lint-rules
```

Expected RED: fails because `no-page-mock` does not yet report `mockImport` / `wrappedMock` / `medicalConstant` / `technicalObject`.

## Task 2: Implement Rule Coverage

**Files:**
- Modify: `frontend/eslint-rules/no-page-mock.js`

- [x] **Step 1: Add allowlist and path normalization**

Implement:

```js
const APPLICABLE_PATH = /\/src\/(?:pages|features|widgets)\/.+\.(?:tsx|ts)$/;
const ALLOWLIST_PATH =
  /\.(?:test|spec|stories)\.(?:tsx|ts)$|\/src\/(?:test|mocks)\//;

function normalizedFilename(context) {
  return (context.filename ?? context.getFilename?.() ?? "").replaceAll("\\", "/");
}
```

Return `{}` when `!APPLICABLE_PATH.test(filename) || ALLOWLIST_PATH.test(filename)`.

- [x] **Step 2: Add message IDs**

Replace the old single message with:

```js
messages: {
  noPageMock: "...硬编码数据数组常量...",
  mockImport: "前端生产路径禁止引入 mock / mocks / fixture / fixtures / MockAdapter。",
  wrappedMock: "前端生产路径禁止用 mock/mok/demo/dem/fixture 命名包装本地假数据。",
  medicalConstant: "前端生产路径禁止写死疾病、药品、编码等医学常量。",
  technicalObject: "客户面默认视图禁止裸露 JSON / font-mono 等技术对象。",
}
```

- [x] **Step 3: Add AST checks**

Add visitors:

```js
ImportDeclaration(node) { ... }
CallExpression(node) { ... }
VariableDeclarator(node) { ... }
FunctionDeclaration(node) { ... }
JSXAttribute(node) { ... }
JSXExpressionContainer(node) { ... }
Literal(node) { ... }
TemplateLiteral(node) { ... }
```

Rules:
- import source or imported/local name matches `mock|mocks|fixture|fixtures|MockAdapter` → `mockImport`.
- `vi.mock` / `jest.mock` / bare `mock()` in production path → `mockImport`.
- variable/function name matches `mock|mok|fixture|demo|dem` and initializes/returns an array/object → `wrappedMock`.
- old SHOUTY object-array constants still report `noPageMock`.
- string/template contains medical constants from existing authenticity guard list → `medicalConstant`.
- `className` string contains `font-mono` or JSX expression contains `JSON.stringify(` → `technicalObject`.

- [x] **Step 4: Verify GREEN**

Run:

```bash
cd frontend
npm run test:lint-rules
npm run lint
npm run stylelint
```

Expected: all exit `0`.

## Task 3: CI And Docs

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `frontend/eslint-rules/README.md`
- Modify: `docs/cards/D0/INFRA-01.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Add CI test step**

In `.github/workflows/ci.yml` frontend-lint job, after ESLint add:

```yaml
      - name: ESLint/stylelint rule tests
        working-directory: frontend
        run: npm run test:lint-rules
```

- [x] **Step 2: Update README**

Add `no-page-mock` scope:

```md
`no-page-mock` blocks mock/fixture imports, wrapper names such as mok/demo/dem, hardcoded medical constants, `font-mono`, and JSX `JSON.stringify` in production pages/features/widgets. Tests, Storybook, `src/test`, and `src/mocks` are allowlisted.
```

Add command:

```bash
npm run test:lint-rules
```

- [x] **Step 3: Update card and backlog**

In `docs/cards/D0/INFRA-01.md`:
- check FR-1~FR-6 and AC-1~AC-5;
- add evidence commands and note `scripts/authenticity-guard.mjs` remains touched-file T-GATE.

In `docs/backlog.md`, mark `INFRA-01` as `done`.

- [x] **Step 4: Update handoff**

Move SYS-05 PR2 to archived line with PR #227 / merge `6a52117`; set active line to INFRA-01 until PR is merged.

## Task 4: Final Verification And PR

- [x] **Step 1: Full frontend verification**

Run:

```bash
cd frontend
npm run verify
npm run build
```

Expected: both exit `0`.

- [x] **Step 2: Root T-GATE**

Run:

```bash
node --test scripts/authenticity-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main...HEAD
```

Expected: all exit `0`.

- [ ] **Step 3: Commit, PR, CI, merge**

Commit:

```bash
git commit -m "完成 INFRA-01 前端真实性门禁"
```

Push, create PR, wait for remote CI 8/8, squash merge, pull main, remove worktree and local branch, then领取下一张 D0 pending 卡。

## Self Review

- Spec coverage: FR-1/2/3/4/6 covered by RuleTester; FR-5 covered by stylelint config test and `npm run stylelint`; CI step ensures future PRs run both.
- Placeholder scan: no TBD/TODO/“后续实现”；all commands and files explicit.
- Scope: no business page refactor; only guard rules, tests, CI and docs.
