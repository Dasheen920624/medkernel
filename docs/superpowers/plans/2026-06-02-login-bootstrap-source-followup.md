# 登录与首次部署体验修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 先把登录页、首次部署接管页和误生成租户清理恢复到正式交付体验，再继续处理平台主源、包和知识读取 / 覆盖链路。

**Architecture:** 本批前端以“登录卡片单目标居中”为主，平台主租户作为默认隐藏上下文，不再要求用户选择；客户 / 集团租户存在时使用紧凑字典按钮而非下拉。首次部署页把说明与表单放入同一接管容器，形成明确产品边界。后续批次继续平台主源 `t-1` 与租户覆盖层治理，包只能维护主源或发布只读快照 / 订阅引用。

**Tech Stack:** React 18、Ant Design 5、Vitest、Spring Boot、Oracle、Flyway、GitHub PR/CI、192.168.8.191 现场 HTTPS 部署。

---

### Task 1: 登录页产品体验

**Files:**

- Modify: `frontend/src/pages/Login.test.tsx`
- Modify: `frontend/src/pages/Login.tsx`
- Modify: `frontend/src/pages/Login.module.css`
- Modify: `frontend/src/shared/config/tenantDictionary.ts`

- [ ] **Step 1: Write failing tests**

Add or update tests so platform-only login renders MedKernel brand, does not render a visible tenant combobox, does not render院方统一身份入口, and still submits `tenantId: "t-1"`.

Run: `cd frontend && npm test -- --run src/pages/Login.test.tsx`
Expected: FAIL because current implementation still renders `Select` and SSO entry.

- [ ] **Step 2: Implement minimal login changes**

Replace the visible platform tenant `Select` with hidden/default tenant context. For customer / group tenants, render compact selectable tenant buttons; platform login remains a second-layer action and hides院方信息. Restore MedKernel brand lockup and shorten footer / help blocks.

- [ ] **Step 3: Verify login tests**

Run: `cd frontend && npm test -- --run src/pages/Login.test.tsx`
Expected: PASS.

### Task 2: 首次部署接管页框架

**Files:**

- Modify: `frontend/src/pages/Bootstrap.test.tsx`
- Modify: `frontend/src/pages/Bootstrap.tsx`
- Modify: `frontend/src/pages/Bootstrap.module.css`

- [ ] **Step 1: Write failing tests**

Assert the page contains one framed接管 shell around说明区和表单区, no technical labels, and every phase still has “返回登录”.

Run: `cd frontend && npm test -- --run src/pages/Bootstrap.test.tsx`
Expected: FAIL because current page is still naked two-column layout.

- [ ] **Step 2: Implement minimal bootstrap changes**

Wrap hero and panel in a cohesive shell, give the left说明区 its own framed card, keep one primary action per phase, and keep MFA offline QR/manual details.

- [ ] **Step 3: Verify bootstrap tests**

Run: `cd frontend && npm test -- --run src/pages/Bootstrap.test.tsx`
Expected: PASS.

### Task 3: 错误租户与新部署状态

**Files:**

- Modify or create: deployment / operations cleanup script if repository lacks a safe repeatable cleanup path.
- Inspect: Oracle data dictionary for `TENANT_ID` tables.

- [ ] **Step 1: Verify current local/现场 data**

Search repository and Oracle for `medkernel-basic`; confirm whether it appears in business tenant columns, credentials, role assignment, login attempts, config, audit, or org tables.

- [ ] **Step 2: Clear only authorized wrong business data**

Delete or normalize `medkernel-basic` rows while preserving platform tenant `t-1`, technical namespace `SYSTEM`, migration history, built-in roles and first-deploy seeds.

- [ ] **Step 3: Verify clean new-deploy state**

Re-query every tenant-bearing table and login tenant directory to confirm no `medkernel-basic` business data remains and platform tenant remains unique.

### Task 4: Merge and deploy first batch

**Files:**

- Update: `docs/_HANDOFF.md`

- [ ] **Step 1: Run verification**

Run focused frontend tests, frontend `npm run verify`, frontend `npm run build`, relevant backend auth/bootstrap tests, T-GATE scripts, `git diff --check`.

- [ ] **Step 2: PR and merge**

Commit on `codex/login-bootstrap-polish`, push branch, create PR, wait for CI, merge to `main` through PR.

- [ ] **Step 3: Deploy**

Package latest main, replace `/zoesoft/medkernel` on `192.168.8.191`, run HTTPS readiness/login/bootstrap smoke checks.

### Task 5: Continue platform source / package / knowledge batch

**Files:**

- Resume from stash: `stash@{0}: On platform-tenant-overlay: wip platform overlay implementation`
- Modify: package and knowledge services / tests identified by platform overlay design.

- [ ] **Step 1: Resume after deployed login batch**

Create or restore a fresh branch from latest `main`, re-apply only relevant Overlay work, and keep the login deployment changes as base.

- [ ] **Step 2: Finish TDD coverage**

Lock platform source fallback, local tenant override, package main-source-only maintenance, and customer read-only snapshot / subscription behavior.

- [ ] **Step 3: Verify, merge and deploy**

Run focused backend tests, full verification as feasible, PR/CI/merge to `main`, then deploy and smoke test.
