# KNOW-01 来源内容指纹与引用锚点 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 D2 `KNOW-01` PR1：统一来源版本 / 片段 / 知识资产版本的真实内容指纹生成与校验，补齐引用锚点偏移字段，并清理触碰范围旧口径。

**Architecture:** 复用现有 `engine/knowledge` 服务和 API-03 客户面，不新增页面、不新造知识引擎。新增一个小而集中的 `ContentHash` 值对象承载 SHA-256 计算 / 规范化 / 校验；来源版本允许由原文 `content` 计算 hash，也允许接收院方离线文件的真实 SHA-256，但必须是规范 64 位十六进制。引用仍以 `citation` 表为权威关系，本 PR 增加 `start_offset` / `end_offset` 精确定位到 `SourceFragment` 内偏移。

**Tech Stack:** Spring Boot 3 / Spring MVC / Spring Data JDBC / JUnit 5 / MockMvc / Flyway / H2 + PostgreSQL + Oracle 迁移验证；当前真实运行保障仍只要求 PostgreSQL + Oracle，达梦 / 人大金仓真实环境继续登记 `DEFER-001`。

---

## File Map

- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/ContentHash.java`：统一 SHA-256 内容指纹计算、规范化和格式校验。
- Modify: `KnowledgeIdentityService.java`：来源版本 hash 由 `ContentHash` 统一处理；片段 hash 复用同一工具；不再接受非规范伪 hash。
- Modify: `KnowledgeVersionService.java`：资产版本 hash 复用 `ContentHash`；去掉重复手写 SHA-256。
- Modify: `KnowledgeSourceVersionCreateRequest.java` / `SourceVersionRegisterRequest.java`：新增可选 `content` 字段；`content_hash` 与 `content` 至少一项有效。
- Modify: `Citation.java`：新增 `startOffset` / `endOffset` 字段。
- Add: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V49__knowledge_citation_anchor_offsets.sql`：给 `citation` 增加偏移列与校验约束 / 中文注释。
- Modify: `KnowledgeEngineTest.java` / `KnowledgeIdentityServiceTest.java` / `KnowledgeVersionServiceTest.java` / `KnowledgeAssetApiContractTest.java`：按 TDD 先补红灯，再实现。
- Modify: `MigrationBaselineContractTest.java`：登记 V49 并校验 5 方言迁移文件存在、偏移列和注释存在。
- Modify: `docs/cards/D2/KNOW-01.md` / `docs/backlog.md` / `docs/_HANDOFF.md`：只在验证完成后更新 PR1 证据与接力状态。

## Baseline

- [x] Focused baseline:
  `mvn -q -Dtest=KnowledgeEngineTest,KnowledgeIdentityServiceTest,KnowledgeVersionServiceTest test`
  Observed: exit code 0；仅 JVM / Mockito agent 提示，无测试失败。

## Tasks

### Task 1: ContentHash 值对象与来源版本 hash 红灯

- [x] Add RED tests in `KnowledgeIdentityServiceTest`:
  - `registerSourceVersionComputesHashFromContentWhenContentHashMissing`：传入 `content="真实指南原文"` 且 `contentHash=null`，期望保存的 `SourceVersion.contentHash()` 等于该内容 SHA-256。
  - `registerSourceVersionRejectsNonSha256ContentHash`：传入 `contentHash="sha256-real-source"` 且无 `content`，期望 `VALIDATION_FAILED`，并且不保存。
  - `registerSourceVersionNormalizesUppercaseSha256`：传入 64 位大写 SHA-256，期望保存为小写。
- [x] Run:
  `mvn -q -Dtest=KnowledgeIdentityServiceTest test`
  Expected RED: 新测试因构造器缺 `content` 字段或服务仍接受非规范 hash 而失败。
- [x] Create `ContentHash` with:
  - `static String sha256(String content)`：UTF-8 SHA-256，小写 64 位 hex；空白原文拒绝。
  - `static String normalizeExternalSha256(String hash)`：trim 后必须匹配 `[0-9a-fA-F]{64}`，返回小写。
  - `static String resolve(String content, String externalHash)`：优先用非空 `content` 计算；否则规范化外部 hash；两者都缺失则 `VALIDATION_FAILED`。
- [x] Update `SourceVersionRegisterRequest` and `KnowledgeSourceVersionCreateRequest` to carry optional `content`, and map path-level `sourceDocumentId` unchanged.
- [x] Update `KnowledgeIdentityService.registerSourceVersion` and `createFragment` to use `ContentHash`.
- [x] Run:
  `mvn -q -Dtest=KnowledgeIdentityServiceTest,KnowledgeEngineTest test`
  Expected GREEN: 来源版本和片段 hash 行为都通过。

### Task 2: 资产版本 hash 去重收口

- [x] Add RED tests in `KnowledgeVersionServiceTest`:
  - `createDraftVersionStoresCanonicalSha256`：资产内容生成 64 位小写 SHA-256。
  - `createDraftVersionRejectsBlankContentInsteadOfHashingEmptyString`：空白内容返回 `VALIDATION_FAILED`。
- [x] Run:
  `mvn -q -Dtest=KnowledgeVersionServiceTest test`
  Expected RED: 空白内容当前会生成空串 hash 或通过。
- [x] Update `KnowledgeVersionService.createDraftVersion` to call `ContentHash.sha256(request.content())`.
- [x] Remove duplicated private `sha256` helpers from knowledge services; keep tests using their own helper only where expected value needs独立计算。
- [x] Run:
  `mvn -q -Dtest=KnowledgeVersionServiceTest,KnowledgeEngineTest test`
  Expected GREEN: 资产版本 hash 和重复内容冲突仍通过。

### Task 3: Citation 精确偏移迁移

- [x] Add RED assertions in `MigrationBaselineContractTest`:
  - 5 方言 expected migrations include `V49__knowledge_citation_anchor_offsets.sql`。
  - V49 DDL contains `start_offset`, `end_offset`, and Chinese `COMMENT ON COLUMN citation.start_offset` / `citation.end_offset` where dialect supports comments.
  - Check constraint or equivalent guard ensures `end_offset >= start_offset` when both present.
- [x] Run:
  `mvn -q -Dtest=MigrationBaselineContractTest test`
  Expected RED: V49 does not exist.
- [x] Add V49 migration for h2/postgres/oracle/dm/kingbase:
  - `ALTER TABLE citation ADD COLUMN start_offset ... NULL`
  - `ALTER TABLE citation ADD COLUMN end_offset ... NULL`
  - add check constraint for non-negative offsets and `end_offset >= start_offset` when both present, using dialect-appropriate syntax.
  - add Chinese comments explaining “来源片段内起始/结束偏移，用于把引用锚点精确到 SourceFragment 文本范围”。
- [x] Update `Citation` record constructor fields and all test fixtures with `null, null` offsets unless a test needs exact offsets.
- [x] Run:
  `mvn -q -Dtest=MigrationBaselineContractTest,KnowledgeIdentityServiceTest,KnowledgeVersionServiceTest,KnowledgeEngineTest test`
  Expected GREEN.

### Task 4: API 合同与旧口径清理

- [x] Update `KnowledgeAssetApiContractTest.sourceVersionUsesNestedSourceRoute`:
  - Replace fake `content_hash="sha256-real-source"` with either real `content` or a real 64-hex hash.
  - Add assertion that snake_case `content` is accepted for source version registration.
- [x] Search touched knowledge scope:
  `rg -n "GA-ENG-KNOW-01|物理|后续.*KNOW-01|sha256-real-source|my-content-hash|source-hash|fragment-hash" medkernel-backend/src/main/java/com/medkernel/engine/knowledge medkernel-backend/src/test/java/com/medkernel/engine/knowledge`
  Clean stale comments / fake hash fixtures in touched files.
- [x] Run:
  `mvn -q -Dtest=KnowledgeAssetApiContractTest,KnowledgeIdentityServiceTest,KnowledgeVersionServiceTest,KnowledgeEngineTest test`
  Expected GREEN.

### Task 5: Documentation And Handoff

- [x] Update `docs/_HANDOFF.md` in the in-progress section:
  - Add line `D2 KNOW-01 PR1 来源内容指纹与引用锚点 🚧` with branch, scope, baseline, and next command.
- [x] After local verification passes, update `docs/cards/D2/KNOW-01.md`:
  - Check only FR/AC covered by PR1: FR-1/2/3/4 and AC-1/2 if evidence supports them.
  - Leave FR-5/6 and AC-3/4 unchecked for PR2/PR3.
- [x] Update `docs/backlog.md` only when whole `KNOW-01` card is complete. PR1 alone must not mark `KNOW-01` done.
- [x] If a new non-current external blocker appears, append next `DEFER-XXX` to `docs/audit/deferred-issues.md` with impact, non-blocking reason, stage, and closure evidence.

### Task 6: Verification And PR

- [x] Backend focused:
  `mvn -q -Dtest=KnowledgeAssetApiContractTest,KnowledgeIdentityServiceTest,KnowledgeVersionServiceTest,KnowledgeEngineTest,MigrationBaselineContractTest test`
- [x] Migration smoke:
  `mvn -q -Dtest=FlywayMultiDialectSmokeTest test`
- [x] Backend full:
  `mvn -q test`
- [ ] Changed T-GATE:
  `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`
  `node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`
  `node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`
  `scripts/check-comment-zh.sh`
  `git diff --check origin/main...HEAD`
- [ ] Commit in Chinese, push `codex/d2-know-01-source-content-hash`, create PR, wait for CI 8/8, squash merge, sync root `main`, delete branch/worktree, then read `_HANDOFF` and continue next task or next `KNOW-01` PR.
