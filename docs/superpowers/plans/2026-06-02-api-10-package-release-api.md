# API-10 包发布 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将包发布客户面收口到 `/api/v1/engine/pkg/**`，补齐 12 字段统一入参、校验/发布/同步日志合同、前端调用口径与触碰范围旧代码清理。

**Architecture:** 复用现有 `engine/pkg` 服务和真实同步状态机，不另造包发布引擎；控制器只做客户面合同、统一上下文校验和响应包络。前端 hooks 复用现有 `withStandardApiContext`，配置包中心只消费真实端点和真实 `NOT_SYNCED`/日志结果。

**Tech Stack:** Spring Boot 3 / Spring MVC / Spring Security / Spring Data JDBC / JUnit 5 / MockMvc / React 18 / React Query / Vitest / Ant Design。

**Progress 2026-06-02:** Task 1–5 已按 TDD 完成本地实现、聚焦验证、全量验证和文档同步；最终提交 / PR / CI / 合并仍按 Task 6 执行。已知非当前阶段问题继续归 `docs/audit/deferred-issues.md`，不得阻塞主线。

---

## File Map

- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineController.java`：根路径迁移到 `/api/v1/engine/pkg`，新增 validate/release/sync-logs，拦截旧 `/engine/packages`。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageApiContext.java`：API-10 写入请求统一 12 字段校验。
- Modify: `PackageCreateRequest.java` / `PackageItemRequest.java` / `PackageSyncRequest.java` / `PackageRollbackRequest.java`：实现 `PackageContextRequest` 并携带 `apiContext`。
- Create: `PackageValidateResponse.java`：包校验结果 DTO，返回包状态、条目数、问题清单。
- Modify: `PackageSyncResponse.java` / `SyncLogResponse.java`：确保 `NOT_SYNCED` 和日志列表能被客户面稳定读取。
- Modify: `PackageEngineService.java`：补 `validatePackage`、`releasePackage`、`listSyncLogs`，并保证空通道同步诚实 `NOT_SYNCED`。
- Modify: `SyncLogRepository.java`：补分页或按包发布计划查询所需方法。
- Modify: `PackageEngineControllerSecurityTest.java` / `PackageEngineServiceTest.java` / create `PackageEngineControllerContractTest.java`：先红灯覆盖新合同、旧入口 404、统一入参缺失、同步日志。
- Modify: `frontend/src/shared/api/hooks.ts` / `hooks.test.ts`：前端包发布调用迁移到 `/engine/pkg/**` 并注入标准上下文。
- Modify: `frontend/src/pages/tenant/ConfigPackages.tsx` / `ConfigPackages.test.tsx`：清理“物理/长链接”等旧文案，补真实同步日志和 `NOT_SYNCED` 体验。
- Modify: `docs/cards/D2/API-10.md` / `docs/backlog.md` / `docs/_HANDOFF.md`：同步完成状态与证据。

## Baseline

- [x] Backend focused baseline: `mvn -q -Dtest=PackageEngineControllerSecurityTest,PackageEngineServiceTest,LenientPackageSyncAdapterTest test`
  Expected and observed: tests completed with exit code 0; sync failure logs are intentional test fixtures.
- [x] Frontend dependency setup: run package install in the isolated frontend worktree.
  Observed: 581 packages installed; 7 known audit findings remain under `DEFER-002`.
- [x] Frontend focused baseline: `npm test -- --run src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx`
  Expected and observed: 2 files / 14 tests passed.

## Tasks

### Task 1: Lock API-10 Customer Root And Old Route Removal

- [ ] Add MockMvc RED tests in `PackageEngineControllerSecurityTest`:
  - Authorized read on `GET /api/v1/engine/pkg/packages` with missing tenant returns `ENG-BASE-001`.
  - Old `GET /api/v1/engine/packages` returns 404.
  - Old `POST /api/v1/engine/packages/pkg-1/sync` returns 404.
- [ ] Run:
  `mvn -q -Dtest=PackageEngineControllerSecurityTest test`
  Expected RED: new `/engine/pkg` assertions fail because controller still maps `/engine/packages`; old-route 404 assertions fail because old routes still exist.
- [ ] Change controller root to `/api/v1/engine/pkg` and update all package controller security tests to the new root.
- [ ] Run the same test.
  Expected GREEN: new root passes, old root returns 404, permission and data-scope behavior unchanged.

### Task 2: Add 12-Field Unified Input To Package Write APIs

- [ ] Create RED tests for create/item/sync/rollback requests missing `apiContext`, expecting `ENG-API-002` ProblemDetail with a field error for `request_id` or `apiContext`.
- [ ] Create RED tests for tenant mismatch in `apiContext.tenant_id`, expecting `ENG-BASE-004`.
- [ ] Add `PackageApiContext` mirroring `RuleApiContext` and `PathwayApiContext`, with these aliases: `request_id`, `trace_id`, `tenant_id`, `group_id`, `hospital_id`, `campus_id`, `site_id`, `department_id`, `specialty_id`, `user_id`, `role_codes`, `package_version`.
- [ ] Add `PackageContextRequest` and make `PackageCreateRequest`, `PackageItemRequest`, `PackageSyncRequest`, `PackageRollbackRequest` expose `apiContext`.
- [ ] In controller write methods, call `request.apiContext().validateTenant(RequestContext.currentOrgScope().tenantId())` before service execution.
- [ ] Run:
  `mvn -q -Dtest=PackageEngineControllerSecurityTest test`
  Expected GREEN: missing context and tenant mismatch return `ProblemDetail`; authorized requests still reach the service mock.

### Task 3: Add Validate, Release, And Sync-Logs API Contract

- [ ] Add RED service tests:
  - `validatePackage` returns no issues for an existing package with at least one item.
  - `validatePackage` returns a blocking issue for an existing package with no items.
  - `releasePackage` delegates to the existing sync state machine with the same request body and returns `PackageSyncResponse`.
  - `listSyncLogs(packageId)` returns logs from release plans for that package, never fabricated logs.
- [ ] Add RED controller tests:
  - `POST /api/v1/engine/pkg/packages/{id}/validate`.
  - `POST /api/v1/engine/pkg/packages/{id}/release`.
  - `GET /api/v1/engine/pkg/packages/{id}/sync-logs`.
- [ ] Implement `PackageValidateResponse(packageId, status, itemCount, valid, issues)` and `PackageValidateIssue(field, severity, message)`.
- [ ] Implement `PackageEngineService.validatePackage`, `releasePackage`, and `listSyncLogs`.
- [ ] Add repository methods for release plans by package and logs by plan when required.
- [ ] Run:
  `mvn -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest test`
  Expected GREEN: validate/release/sync-logs contract works and no fake logs are generated.

### Task 4: Update Frontend API Hooks And Page Text

- [ ] Add RED tests in `hooks.test.ts` expecting:
  - offline export calls `/engine/pkg/packages/pkg-1/offline/export`;
  - offline import calls `/engine/pkg/packages/offline/import`;
  - package write hooks send standard context fields when called with a package version and security profile.
- [ ] Change package hooks to use `PACKAGE_API_ROOT = "/engine/pkg"` and remove all `/engine/packages` strings.
- [ ] Make `useCreatePackage`, `useAddPackageItem`, `useSyncPackage`, and `useRollbackPackage` inject standard context from `useSecurityProfile`.
- [ ] Add `usePackageSyncLogs(packageId)` for `GET /engine/pkg/packages/{id}/sync-logs`.
- [ ] Clean `ConfigPackages.tsx` visible copy in current scope:
  - Replace “物理投影/物理长链接/物理锁存” with “院内同步/同步执行/版本锁定”。
  - Render `NOT_SYNCED` as “未接入真实同步通道” and show error message without implying success.
  - Keep one primary action per modal and no local fake evidence.
- [ ] Run:
  `npm test -- --run src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx`
  Expected GREEN: package frontend calls use new root and context; page tests keep passing.

### Task 5: Documentation And Handoff

- [ ] Update `docs/cards/D2/API-10.md` FR/AC to checked only after tests pass.
- [ ] Update `docs/backlog.md` API-10 from `pending` to `done` only after local validation is green.
- [ ] Update `docs/_HANDOFF.md` with API-10 PR status and next exact action.
- [ ] If a non-current blocker appears, append `DEFER-013` or next number to `docs/audit/deferred-issues.md` with impact, non-blocking reason, phase, and closure evidence.

### Task 6: Final Verification

- [ ] Backend focused:
  `mvn -q -Dtest=PackageEngineControllerSecurityTest,PackageEngineServiceTest,LenientPackageSyncAdapterTest test`
- [ ] Backend full:
  `mvn -q test`
- [ ] Frontend focused:
  `npm test -- --run src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx`
- [ ] Frontend full:
  `npm run verify`
- [ ] Frontend build:
  `npm run build`
- [ ] T-GATE:
  `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`
  `node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`
  `node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`
  `scripts/check-comment-zh.sh`
  `git diff --check origin/main...HEAD`
- [ ] Commit, push, create PR, wait for CI 8/8, squash merge, confirm `origin/main` contains the merge, delete remote branch, remove worktree, then read `backlog.md` for the next current-stage task.
