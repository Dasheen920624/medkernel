# API-01 Standard Context API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 D2 API-01 标准上下文快照 API 对齐卡片要求：统一 §1.4 入参、`request_id` 幂等、12 类资源往返、包版本快照、缺失/映射诚实读回、组织作用域拒绝与诊断可追溯。

**Architecture:** 保留既有 `ContextSnapshotController/Service` 入口，扩展 `ContextSnapshotRequest/Response` 为标准字段；旧三类包版本作为兼容别名，标准 `packageVersion` 作为统一输出。快照资源仍复用 `CanonicalResource` 12 类模型，不新增临床权威实体；新增轻量 V48 迁移只补快照头审计字段和索引。

**Tech Stack:** Java 21、Spring Boot 3.3、Spring Data JDBC、Flyway、Jackson、JUnit 5、Mockito、AssertJ。

---

### Task 1: 红灯测试锁定 API-01 契约

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/context/ContextSnapshotServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/context/ContextSnapshotRepositoryTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`

- [ ] **Step 1: 写失败测试**
  - `ContextSnapshotServiceTest` 增加：
    - `shouldCreateSnapshotFromUnifiedRequestAndReturnStandardContract`
    - `shouldUseRequestIdAsTenantScopedIdempotencyKey`
    - `shouldReadPersistedResourcesMissingFieldsAndMappingStatus`
    - `shouldRejectWhenRequestTenantExceedsCurrentScope`
  - `ContextSnapshotRepositoryTest` 增加 V48 字段持久化断言。
  - 迁移测试把 V48 纳入权威迁移序列与 H2 数量。

- [ ] **Step 2: 运行红灯测试**
  - Run: `mvn -q -Dtest=ContextSnapshotServiceTest,ContextSnapshotRepositoryTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`
  - Expected: FAIL，失败点集中在缺少 request/org/package 字段、V48 迁移缺失或响应无 resources/packageVersion。

### Task 2: 实现 DTO、Service 与仓储往返

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextSnapshotRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextSnapshotResponse.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextSnapshot.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/CanonicalResourceRepository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextSnapshotService.java`

- [ ] **Step 1: 扩展请求与响应**
  - `ContextSnapshotRequest` 增加 `requestId/traceId/tenantId/groupId/hospitalId/campusId/siteId/departmentId/specialtyId/userId/roleCodes/packageVersion`。
  - 保留旧 7 参数构造器，旧调用继续可编译。
  - `ContextSnapshotResponse` 回出 `resources/packageVersion/knowledgePackageVersion/rulePackageVersion/pathwayPackageVersion`。

- [ ] **Step 2: Service 对齐行为**
  - 幂等键优先使用 `request.requestId()`，其次兼容 `Idempotency-Key`。
  - 请求 `tenantId` 与当前 `RequestContext` 不一致时抛 `DATA_SCOPE_DENIED` 并写失败审计。
  - `packageVersion` 存在时同步绑定三类包版本；旧三字段仍可用。
  - `findById` 与幂等命中必须从持久化 JSON 读回 `missingFields/mappingStatus/resources`。

- [ ] **Step 3: 运行绿灯测试**
  - Run: `mvn -q -Dtest=ContextSnapshotServiceTest,ContextSnapshotRepositoryTest test`
  - Expected: PASS。

### Task 3: 迁移与文档同步

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/h2/V48__context_snapshot_standard_contract.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/postgres/V48__context_snapshot_standard_contract.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/oracle/V48__context_snapshot_standard_contract.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/dm/V48__context_snapshot_standard_contract.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/kingbase/V48__context_snapshot_standard_contract.sql`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/audit/deferred-issues.md`
- Modify: `docs/backlog.md`
- Modify: `docs/cards/D2/API-01.md`

- [ ] **Step 1: 补 V48**
  - 为 `context_snapshot` 增加 `request_id/org_path/package_version`。
  - 增加 `idx_context_snapshot_org_path`、`idx_context_snapshot_package_version`、`idx_context_snapshot_tenant_request`。
  - PostgreSQL / Oracle / Kingbase 保持中文 COMMENT；达梦只同步结构，不宣称真实实例验证。

- [ ] **Step 2: 文档同步**
  - `_HANDOFF` 归档 D1 #246，并新增 D2 API-01 在途工作线。
  - `deferred-issues.md` 登记“错误码命名与卡片别名未统一”的非阻塞项，关闭证据指向错误码治理阶段。
  - `API-01.md` 写入本 PR 的完成证据；当前 PR 未覆盖的项不得勾选。

- [ ] **Step 3: 验证**
  - Run: `mvn -q -Dtest=ContextSnapshotServiceTest,ContextSnapshotRepositoryTest,ContextSnapshotControllerSecurityTest,ContextSnapshotTraceEndToEndTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`
  - Run: `node --test scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`
  - Run: `node scripts/authenticity-guard.mjs --mode=all`
  - Run: `scripts/check-comment-zh.sh --mode=full`
  - Run: `git diff --check`
  - Expected: 所有命令退出 0；历史 COMMENT GAP 只能作为 `DEFER-006`，不得宣称全量清零。

### Task 4: PR、CI、合并与继续下一卡

**Files:**
- No code files beyond Task 1-3.

- [ ] **Step 1: 提交与 PR**
  - Run: `git status --short`
  - Run: `git add <changed-files>`
  - Run: `git commit -m "D2 API-01 标准上下文契约收口"`
  - Run: `git push -u origin codex/d2-api-01-standard-context-api`
  - Create PR with scope, verification, unresolved DEFER items, migration impact.

- [ ] **Step 2: CI 与合并**
  - Wait for CI 8/8.
  - Squash merge after CI passes.
  - Confirm `origin/main` contains merge commit.
  - Delete remote branch, remove worktree, delete local branch.
  - Continue from latest `origin/main` to the next D2 pending card.
