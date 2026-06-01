# API-13 大规模列表 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 API-13 的统一大规模列表契约，确保服务端分页、游标分页、排序 / 过滤白名单、`totalEstimate`、异步导出和前端全量加载门禁都可验证。

**Architecture:** 后端新增共享 `PageQuery` / `PageResult` 作为 API-13 单一契约，`LargeListEngineService` 只暴露白名单化的审计大列表查询和现有异步导出；审计仓库负责 5 方言兼容 keyset / offset SQL。前端复用 `ServerDataTable`，通过运行时断言与真实性门禁阻断超大 page size 写法回流。

**Tech Stack:** Spring Boot 3.3、Java 21 record DTO、JdbcTemplate、Flyway 五方言迁移、JUnit 5、Mockito、React 18、Vitest、Node 真实性门禁。

---

## Current Findings

- 当前分支：`codex/api-13-large-list`，基于 `origin/main` `34b2240`。
- 已建立绿色基线：`mvn -B -q -Dtest=LargeListEngineServiceTest,LargeListControllerSecurityTest,PageResponseTest,MigrationBaselineContractTest,H2BaselineMigrationTest test` 通过。
- 现有半成品问题必须在本卡修复，不能登记延期：`ListQueryRequest.normalize()` 静默截断超大 `pageSize`；排序字段未按白名单执行；未知过滤条件被忽略；旧 `ListQueryRequest` / `ListQueryResponse` 与卡片要求的 `PageQuery` / `PageResult` 不一致。
- 外部环境问题继续使用 `docs/audit/deferred-issues.md` 登记，不阻塞本卡；当前只保障 PostgreSQL + Oracle，达梦 / 人大金仓真实环境归 `DEFER-001`。

## File Map

- Create: `medkernel-backend/src/main/java/com/medkernel/shared/api/PageQuery.java`：API-13 查询 DTO，包含 `cursor`、`size`、`offset`、`sort`、`filters`，超大 size 直接抛 `PAGE_SIZE_EXCEEDED`。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/api/PageResult.java`：API-13 响应 DTO，包含 `items`、`nextCursor`、`totalEstimate`、`totalEstimated`、`hasMore`。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/list/LargeListResourceDefinition.java`：审计大列表的资源定义、排序白名单和过滤白名单。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/api/error/ErrorCode.java`：新增 `SORT_FIELD_NOT_ALLOWED`、`PAGE_SIZE_EXCEEDED`、`FILTER_FIELD_NOT_ALLOWED`。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/audit/persistence/AuditEventQuery.java`：补 `offset`、`sortField`、`sortDirection`。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/audit/persistence/AuditEventRepository.java`：生成白名单 SQL，支持 `id DESC/ASC` keyset、浅 offset、过滤白名单。
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/list/LargeListEngineService.java`：改用 `PageQuery` / `PageResult`，拒绝非法 sort/filter/size，保留异步导出真实任务。
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/list/LargeListController.java`：提供 `GET /api/v1/large-lists/audit-events/list` 查询端点，保持导出端点。
- Delete: `medkernel-backend/src/main/java/com/medkernel/engine/list/ListQueryRequest.java`、`medkernel-backend/src/main/java/com/medkernel/engine/list/ListQueryResponse.java`：删除旧半成品契约。
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V37__large_list_audit_event_indexes.sql`：为 `audit_event` 补 keyset 覆盖索引。
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`、`H2BaselineMigrationTest.java`、`FlywayMultiDialectSmokeTest.java`：迁移序列更新到 V37 并校验新增索引。
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/list/LargeListEngineServiceTest.java`、`LargeListControllerSecurityTest.java`：补 API-13 红绿测试。
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/list/LargeListAuditEventRepositoryTest.java`：H2 下验证 100k 审计行 keyset 深翻页无重复、SQL 不使用深 offset。
- Modify: `frontend/src/shared/ui/ServerDataTable.tsx`、`ServerDataTable.test.tsx`、`experienceTypes.ts`：运行时拒绝超过 100 的页面大小。
- Modify: `scripts/authenticity-guard.mjs`、`scripts/authenticity-guard.test.mjs`：新增前端全量加载门禁。
- Modify: `docs/cards/D0/API-13.md`、`docs/_HANDOFF.md`、`docs/backlog.md`：完成后更新证据和接力。

---

### Task 1: Red Tests For Backend Contract

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/list/LargeListEngineServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/list/LargeListControllerSecurityTest.java`
- Add: `medkernel-backend/src/test/java/com/medkernel/engine/list/LargeListAuditEventRepositoryTest.java`

- [x] **Step 1: Add failing service tests**

Add tests proving:
- `size=501` throws `PAGE_SIZE_EXCEEDED` and never calls `auditRepo.findPage`.
- `sort=summary,desc` throws `SORT_FIELD_NOT_ALLOWED`.
- unknown filter `payloadDigest` throws `FILTER_FIELD_NOT_ALLOWED`.
- valid filters `action/resourceType/actorUserId/outcome/environmentKey/orgPathPrefix/from/to` pass into `AuditEventQuery`.
- `totalEstimate` SQL contains `FETCH FIRST 10001 ROWS ONLY`.

- [x] **Step 2: Add failing controller tests**

Add tests for `GET /api/v1/large-lists/audit-events/list`:
- authorized `qa-manager` gets 200 and calls service.
- `size=10000` returns code `ENG-LIST-006`.
- missing tenant still returns `ENG-BASE-001`.

- [x] **Step 3: Add failing repository performance contract**

Create an H2-backed test that:
- migrates H2 schema,
- batch inserts 100000 `audit_event` rows for one tenant,
- requests a page after cursor `90000`,
- asserts returned IDs are strictly descending, no duplicate, and query duration is below a loose local threshold,
- asserts generated SQL uses `id < ?` with `FETCH FIRST ? ROWS ONLY` rather than deep `OFFSET`.

- [x] **Step 4: Run red backend tests**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=LargeListEngineServiceTest,LargeListControllerSecurityTest,LargeListAuditEventRepositoryTest test
```

Expected: FAIL because `PageQuery` / `PageResult` / new error codes / GET endpoint / repository cursor behavior are not implemented yet.

---

### Task 2: Implement PageQuery/PageResult And Backend Whitelist

**Files:**
- Add: `medkernel-backend/src/main/java/com/medkernel/shared/api/PageQuery.java`
- Add: `medkernel-backend/src/main/java/com/medkernel/shared/api/PageResult.java`
- Add: `medkernel-backend/src/main/java/com/medkernel/engine/list/LargeListResourceDefinition.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/api/error/ErrorCode.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/list/LargeListEngineService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/list/LargeListController.java`
- Delete: `medkernel-backend/src/main/java/com/medkernel/engine/list/ListQueryRequest.java`
- Delete: `medkernel-backend/src/main/java/com/medkernel/engine/list/ListQueryResponse.java`

- [x] **Step 1: Add API-13 DTOs**

Implement `PageQuery` with `DEFAULT_SIZE=50` and `MAX_SIZE=500`; `validatedSize()` returns default for null / non-positive, throws `PAGE_SIZE_EXCEEDED` for values above max. Implement `PageResult<T>` with immutable copied `items`.

- [x] **Step 2: Add explicit error codes**

Add:
- `SORT_FIELD_NOT_ALLOWED("ENG-LIST-005", 400, "排序字段不在大规模列表白名单内", INPUT, false)`
- `PAGE_SIZE_EXCEEDED("ENG-LIST-006", 400, "请求页大小超过大规模列表上限", INPUT, false)`
- `FILTER_FIELD_NOT_ALLOWED("ENG-LIST-007", 400, "过滤字段不在大规模列表白名单内", INPUT, false)`

- [x] **Step 3: Add resource definition**

Define one current resource: audit events. Allowed sort field is `id`; allowed filters are `action`、`resourceType`、`actorUserId`、`outcome`、`environmentKey`、`orgPathPrefix`、`from`、`to`、`superAdminOnly`。Unknown sort/filter throws the new explicit error.

- [x] **Step 4: Replace query service contract**

Change service query method to `queryAuditEvents(PageQuery query)` and return `PageResult<AuditEventRecord>`. Keep export methods unchanged, but reuse the same filter validation for audit export snapshots where possible.

- [x] **Step 5: Replace controller query endpoint**

Add `GET /api/v1/large-lists/audit-events/list` with query params `cursor`、`size`、`offset`、`sort` plus filter params. Keep `@DataScope(requireTenant = true)` and `@PreAuthorize("@perm.has('audit.read')")`.

- [x] **Step 6: Remove old request/response classes**

Delete `ListQueryRequest.java` and `ListQueryResponse.java`; update imports/tests so no production or test code references them.

- [x] **Step 7: Run backend contract tests**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=LargeListEngineServiceTest,LargeListControllerSecurityTest test
```

Expected: PASS for service/controller tests or fail only on repository behavior still pending.

---

### Task 3: Implement Repository Cursor/Offset SQL And Indexes

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/audit/persistence/AuditEventQuery.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/audit/persistence/AuditEventRepository.java`
- Add: `medkernel-backend/src/main/resources/db/migration/h2/V37__large_list_audit_event_indexes.sql`
- Add: `medkernel-backend/src/main/resources/db/migration/postgres/V37__large_list_audit_event_indexes.sql`
- Add: `medkernel-backend/src/main/resources/db/migration/oracle/V37__large_list_audit_event_indexes.sql`
- Add: `medkernel-backend/src/main/resources/db/migration/dm/V37__large_list_audit_event_indexes.sql`
- Add: `medkernel-backend/src/main/resources/db/migration/kingbase/V37__large_list_audit_event_indexes.sql`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/FlywayMultiDialectSmokeTest.java`

- [x] **Step 1: Extend AuditEventQuery**

Add `offset`、`sortField`、`sortDirection` to the record while preserving existing convenience constructor defaults for current callers.

- [x] **Step 2: Generate safe SQL in repository**

For audit events:
- if cursor is present and sort is `id,DESC`, append `AND id < ?`;
- if offset is present and cursor is absent, append `OFFSET ? ROWS`;
- always append `ORDER BY id DESC FETCH FIRST ? ROWS ONLY`;
- bind values through `JdbcTemplate` params / types only, never string-concatenate user input.

- [x] **Step 3: Add V37 indexes**

Add indexes:
- `idx_audit_event_large_cursor` on `(tenant_id, id)`
- `idx_audit_event_large_action` on `(tenant_id, action, id)`
- `idx_audit_event_large_resource` on `(tenant_id, resource_type, id)`
- `idx_audit_event_large_actor` on `(tenant_id, actor_user_id, id)`

For Oracle / DM omit `IF NOT EXISTS` if the dialect does not support it, consistent with existing migrations.

- [x] **Step 4: Update migration tests**

Update expected migrations from V36 to V37 and required index list with the four new indexes.

- [x] **Step 5: Run repository/migration tests**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=LargeListAuditEventRepositoryTest,MigrationBaselineContractTest,H2BaselineMigrationTest test
```

Expected: PASS.

---

### Task 4: Red/Green Frontend Full-Load Guard

**Files:**
- Modify: `frontend/src/shared/ui/experienceTypes.ts`
- Modify: `frontend/src/shared/ui/ServerDataTable.tsx`
- Modify: `frontend/src/shared/ui/ServerDataTable.test.tsx`
- Modify: `scripts/authenticity-guard.mjs`
- Modify: `scripts/authenticity-guard.test.mjs`

- [x] **Step 1: Add failing UI runtime test**

Add a `ServerDataTable` test that casts an invalid request with `pageSize: 500` and expects an error matching `服务端分页每页最多 100 条`.

- [x] **Step 2: Add failing guard test**

Add an `authenticity-guard` fixture in a production frontend page with `pageSize: 1000` and expect rule id `frontend.full-list-load`.

- [x] **Step 3: Run red frontend tests**

Run:

```bash
npm --prefix frontend test -- ServerDataTable.test.tsx
node --test scripts/authenticity-guard.test.mjs
```

Expected: FAIL before guard implementation.

- [x] **Step 4: Implement runtime guard**

Export `EXPERIENCE_PAGE_SIZE_OPTIONS = [20, 50, 100]` and `MAX_EXPERIENCE_PAGE_SIZE = 100` from `experienceTypes.ts`; `ServerDataTable` throws if request/query page size exceeds 100.

- [x] **Step 5: Implement static guard**

Add `frontend.full-list-load` to frontend page/shared API scan rules; block `pageSize` / `size` / `limit` literals above 100 in production frontend source.

- [x] **Step 6: Run frontend tests**

Run:

```bash
npm --prefix frontend test -- ServerDataTable.test.tsx
node --test scripts/authenticity-guard.test.mjs
```

Expected: PASS.

---

### Task 5: Documentation, Cleanup, And Verification

**Files:**
- Modify: `docs/cards/D0/API-13.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Search for old contract references**

Run:

```bash
rg -n "ListQueryRequest|ListQueryResponse|/large-lists/query|pageSize\\s*[:=]\\s*(?:[5-9]\\d{2}|\\d{4,})|size\\s*[:=]\\s*(?:[5-9]\\d{2}|\\d{4,})" .
```

Expected: no production references to old query contract or oversized frontend page size.

- [x] **Step 2: Update API-13 card**

Mark FR/AC complete only after tests pass; include concrete evidence commands, not unverifiable claims.

- [x] **Step 3: Update handoff**

When local verification is complete, set API-13 state to ready for PR; preserve open deferred issues as non-blocking.

- [x] **Step 4: Run full target verification**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=LargeListEngineServiceTest,LargeListControllerSecurityTest,LargeListAuditEventRepositoryTest,PageResponseTest,MigrationBaselineContractTest,H2BaselineMigrationTest test
mvn -B -q test
cd ../frontend
npm test -- ServerDataTable.test.tsx
npm run typecheck
npm run build
cd ..
node --test scripts/authenticity-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main...HEAD
```

Expected: all PASS. If Docker is available, run PostgreSQL + Oracle migration smoke; if unavailable, keep `DEFER-001` for domestic DB only and do not claim unsupported smoke evidence.

- [ ] **Step 5: Commit, PR, CI, merge**

Commit in Chinese, push `codex/api-13-large-list`, open PR, wait for all CI checks, squash merge, confirm `origin/main` contains merge commit, delete branch/worktree, then create the next task branch from latest `origin/main`.

---

## Self-Review

- Spec coverage: FR-1 covered by `PageQuery` / `PageResult` and cursor/offset SQL; FR-2 by whitelist tests; FR-3 by estimate SQL test; FR-4 by existing async export plus regression tests; FR-5 by page size reject and frontend guard; FR-6 by 100k repository test and V37 indexes.
- Placeholder scan: no TBD / TODO / implement later placeholders are left in this plan.
- Type consistency: backend uses `PageQuery` / `PageResult`; frontend keeps existing `ExperiencePageRequest` / `ExperiencePageResponse` and only adds page-size constants / guard.
