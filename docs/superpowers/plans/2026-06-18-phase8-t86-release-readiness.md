# Phase 8 T8.6 上线体验收口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收口 T8.6 的格式、视觉 token、老年字号、国产浏览器与移动端验收缺口，使前端达到 134 部署前的可持续上线门禁。

**Architecture:** 以 Ant Design CSS 变量为唯一字号与视觉 token 来源，把全局 CSS 纳入与 CSS Modules 相同的静态门禁；客户端只按实际 Web 能力生成浏览器预检证据，不依据 User-Agent 冒充国产浏览器认证。自动化使用 Chromium 内核代表性仿真做回归，134 现场仍以目标国产浏览器实测为最终证据。

**Tech Stack:** React 18、TypeScript、Ant Design 5 CSS variables、Vitest、Stylelint、ESLint、Playwright。

---

### Task 1: 恢复前端零告警与格式门禁

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/pages/advanced/AiWorkflows.tsx`
- Modify: `frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx`
- Modify: `frontend/src/pages/workbench/ReadinessValidation.tsx`
- Format: `frontend/src/pages/quality/KnowledgeGovernance.test.tsx`
- Format: `frontend/src/pages/quality/KnowledgeGovernance.tsx`
- Format: `frontend/src/shared/api/hooks.test.ts`

- [x] **Step 1:** 将 `lint` 改为 `eslint . --max-warnings=0`，运行 `npm run lint`，确认现有 4 个嵌套三元 warning 使门禁失败。
- [x] **Step 2:** 将嵌套三元拆成命名函数或顺序分支，不改变业务行为。
- [x] **Step 3:** 对 3 个格式失败文件运行 Prettier。
- [x] **Step 4:** 运行 `npm run lint && npm run format:check`，期望退出 0 且 warning=0。

### Task 2: 全局 CSS 纳入 token 门禁并修复老年字号

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/test/visualDebtGuard.test.ts`
- Modify: `frontend/src/app/index.css`
- Test: `frontend/stylelint.config.test.js`
- Test: `frontend/src/shared/config/theme.test.ts`

- [x] **Step 1:** 将视觉债测试从仅扫描 `*.module.css` 改为扫描全部生产 CSS；运行 `npm test -- --run src/test/visualDebtGuard.test.ts`，确认 `app/index.css` 的固定 px 被红灯捕获。
- [x] **Step 2:** 将 stylelint 脚本改为 `stylelint "src/**/*.css"`，运行 `npm run stylelint`，确认全局 CSS 被门禁捕获。
- [x] **Step 3:** 删除 `:root` 固定字号，把全局间距/宽度 px 改为 `--mk-unit` 计算，把 `.mk-text-xs`、导航品牌字号改为 `--ant-font-size*`。
- [x] **Step 4:** 运行视觉债测试、Stylelint 和主题测试，期望全部通过。
- [x] **Step 5:** 在浏览器切换 elder，核验 `--ant-font-size >= 21.333px`、`--ant-font-size-sm >= 20px`，并确认自定义小字不再固定为 12px。

### Task 3: 国产浏览器能力预检与诚实证据

**Files:**
- Create: `frontend/src/shared/lib/browserCompatibility.ts`
- Create: `frontend/src/shared/lib/browserCompatibility.test.ts`
- Modify: `frontend/src/pages/advanced/DomesticCheck.tsx`
- Modify: `frontend/src/pages/operationalControlPages.test.tsx`

- [x] **Step 1:** 先写能力判定测试，覆盖全部能力通过、关键能力缺失失败、可选能力缺失警告，以及报告不把 User-Agent 名称当作通过证据。
- [x] **Step 2:** 实现只读客户端能力探测：ES modules、Fetch、AbortController、URL、TextEncoder、Web Crypto、matchMedia、ResizeObserver、CSS Grid/CSS variables。
- [x] **Step 3:** 国产化自检页展示“当前浏览器能力预检”，明确“自动化预检不替代目标国产浏览器现场确认”。
- [x] **Step 4:** 导出报告时把客户端能力结果附加到后端报告，不写入凭据、Cookie、令牌或患者数据。
- [x] **Step 5:** 运行能力单测与国产化页面测试，期望全部通过。

### Task 4: 老年主题、国产 Chromium 仿真与移动端自动化

**Files:**
- Modify: `frontend/playwright.config.ts`
- Create: `frontend/e2e/theme-mobile-browser-compatibility.spec.ts`

- [x] **Step 1:** 先写登录页无后端依赖 E2E：5 主题可达、elder computed font-size ≥16pt、390px 无根节点横向溢出、浏览器控制台无 error。
- [x] **Step 2:** 新增“国产 Chromium 内核仿真”项目，使用代表性国产浏览器 User-Agent；测试名称与报告明确其不替代真实现场浏览器。
- [x] **Step 3:** 运行 Chromium 与国产 Chromium 仿真 E2E，期望全部通过。

### Task 5: 全量验证、文档证据与本地提交

**Files:**
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`

- [x] **Step 1:** 运行 `npm run verify`、`npm run build` 和新增 E2E。
- [x] **Step 2:** 运行后端全量、CLI/MCP、部署合同、B0、产品目录、真实性、配置、迁移、中文注释和 diff 门禁。
- [x] **Step 3:** 更新主计划 T8.6、`_HANDOFF` 当前事实与验证证据，不提前宣称 134 已部署。
- [x] **Step 4:** 检查 `git status`、`git diff`、`git diff --check`、`git diff --cached --check`。
- [x] **Step 5:** 创建中文本地提交；不 push、不建远程 PR、不合并 main。
