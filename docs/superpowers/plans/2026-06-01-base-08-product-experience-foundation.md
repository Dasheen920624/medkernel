# BASE-08 产品体验底座 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 BASE-08 PR2 的真实闭环：保存视图后端持久化、异步导出真实任务接入、PostgreSQL + Oracle 可运行的大列表分页 / 估算 SQL，并清理旧口径。

**Architecture:** 前端体验组件继续作为单一入口；页面只消费 `experienceView` 与 `AsyncExportAction`。后端新增 `mk_experience_saved_view` / `mk_experience_export_task` 五方言迁移；大列表导出实体迁移到 `mk_experience_export_task`，保存视图由独立 `experience` 控制器和服务按租户 + 用户隔离。

**Tech Stack:** Spring Boot 3 / Spring Data JDBC / Flyway / React 18 / React Query / Vitest / Maven / Vite。

---

### Task 1: 迁移合同红灯

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V34__experience_foundation_persistence.sql`

- [x] **Step 1: Write the failing test**

在 `MigrationBaselineContractTest` 中把 `V34__experience_foundation_persistence.sql` 加入 `EXPECTED_MIGRATIONS`，把 `mk_experience_saved_view` 与 `mk_experience_export_task` 加入必需表、租户表、可审计表、生命周期字段；把旧 `large_list_export_job` 从权威表族移除。

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -B -q -Dtest=MigrationBaselineContractTest test`
Expected: FAIL，提示五方言缺 V34、缺 `mk_experience_saved_view` / `mk_experience_export_task`。

- [x] **Step 3: Write minimal implementation**

新增五方言 V34：`mk_experience_saved_view` 记录 `tenant_id/user_id/page_key/view_name/definition_json/default_flag/version/status`；V19 中旧 `large_list_export_job` 口径迁移为 `mk_experience_export_task`，记录 `tenant_id/resource_type/request_snapshot/status/file_* / trace_id / audit_id / idempotency_key`；保留中文 COMMENT、唯一约束和索引。

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -B -q -Dtest=MigrationBaselineContractTest test`
Expected: PASS。

### Task 2: 后端保存视图红绿

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/experience/*`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/experience/SavedViewServiceTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/experience/SavedViewControllerSecurityTest.java`

- [x] **Step 1: Write the failing tests**

测试 `upsert` 同一租户 / 用户 / 页面 / 视图名时更新而不是重复插入；测试跨租户不可见；测试含 token、password、patient、身份证等敏感片段的视图 JSON 被拒。

- [x] **Step 2: Run tests to verify they fail**

Run: `mvn -B -q -Dtest=SavedViewServiceTest,SavedViewControllerSecurityTest test`
Expected: FAIL，类型和端点不存在。

- [x] **Step 3: Write minimal implementation**

新增 `SavedView` record、Repository、Service、Controller；端点为 `/api/v1/experience/saved-views`，依赖 `RequestContext` 获取租户和当前用户，所有响应走 `ApiResult`。

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn -B -q -Dtest=SavedViewServiceTest,SavedViewControllerSecurityTest test`
Expected: PASS。

### Task 3: 大列表导出对齐与 Oracle 修复

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/list/*`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/list/LargeListEngineServiceTest.java`

- [x] **Step 1: Write the failing tests**

测试导出任务实体映射到 `mk_experience_export_task`；测试导出请求保存真实 JSON 快照；测试 count estimate 使用 `FETCH FIRST 10001 ROWS ONLY`，不出现 Oracle 不支持的 `LIMIT`。

- [x] **Step 2: Run tests to verify they fail**

Run: `mvn -B -q -Dtest=LargeListEngineServiceTest test`
Expected: FAIL，当前表名仍为 `large_list_export_job` 且估算 SQL 含 `LIMIT`。

- [x] **Step 3: Write minimal implementation**

将 `LargeListExportJob` 的表映射切到 `mk_experience_export_task`，字段改为 `task_id/request_snapshot/idempotency_key/audit_id` 口径；`ExportSubmitRequest` 接收前端视图快照和筛选；估算 SQL 改为 `FETCH FIRST`。

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn -B -q -Dtest=LargeListEngineServiceTest,LargeListControllerSecurityTest test`
Expected: PASS。

### Task 4: 前端持久化与导出接入

**Files:**
- Modify: `frontend/src/shared/api/hooks.ts`
- Modify: `frontend/src/shared/ui/experienceView.ts`
- Modify: `frontend/src/pages/tenant/TerminologyMapping.tsx`
- Modify: `frontend/src/pages/tenant/TerminologyMapping.test.tsx`

- [x] **Step 1: Write the failing tests**

测试字典映射页加载后端默认视图；点击“保存视图”调用保存视图 API；点击“导出”提交 `/large-lists/exports` 且携带 `Idempotency-Key`。

- [x] **Step 2: Run tests to verify they fail**

Run: `npm test -- TerminologyMapping.test.tsx`
Expected: FAIL，hooks 尚不存在或页面仍用本地存储 / 禁用导出。

- [x] **Step 3: Write minimal implementation**

新增 `useSavedView` / `useSaveView` / `useSubmitLargeListExport` / `useLargeListExportJob`，页面保存视图写后端，后端不可用时不伪造成功；导出启用真实任务和轮询。

- [x] **Step 4: Run tests to verify they pass**

Run: `npm test -- TerminologyMapping.test.tsx AsyncExportAction.test.tsx experienceView.test.ts`
Expected: PASS。

### Task 5: 收口验收

**Files:**
- Modify: `docs/cards/D0/BASE-08.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/audit/deferred-issues.md` if non-blocking issues remain

- [x] **Step 1: Run focused checks**

Run backend focused tests, frontend focused tests, `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm run build`, migration smoke, T-GATE。

- [x] **Step 2: Run full checks**

Run: `mvn -B -q test` and `npm test` before PR.

- [x] **Step 3: Update card and handoff**

勾选已真实完成的 BASE-08 FR/AC；未完成项只登记，不伪造。

- [ ] **Step 4: PR / CI / merge**

提交中文 commit，推送 `codex/base-08-product-experience-foundation`，开 PR，等 8/8 CI 通过后 squash merge，再清理分支和 worktree，继续下一阶段。
