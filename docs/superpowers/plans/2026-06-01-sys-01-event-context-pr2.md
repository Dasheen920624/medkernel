# SYS-01 Clinical Event Context PR2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 SYS-01 PR2：`ClinicalEventContext`、临床事件到规则/路径/CDSS 的同源入口、12 类对象编码字段字典映射锚点，并清理误导性的旧接力状态。

**Architecture:** `engine/context` 继续作为标准上下文入口，新增事件上下文与字典锚点的纯契约对象；`ClinicalEventProcessor` 从已持久化事件和 payload 构造同一个 `ClinicalEventContext`，交给按引擎注册的 `ClinicalEventEngineAdapter`。规则/CDSS 使用真实服务入口，路径先建立明确的上下文接收入口，不在 D0 擅自生成路径业务事实。

**Tech Stack:** Spring Boot / Spring Data JDBC / JUnit 5 / Mockito / AssertJ / Flyway 五方言迁移 / Maven。

---

### Task 1: 红测 ClinicalEventContext 与引擎同源派发

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/context/ClinicalEventProcessorTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/context/ClinicalEventContextContractTest.java`

- [x] **Step 1: 写失败测试**

Add tests requiring:
- `ClinicalEventContext` exposes eventId、tenantId、patientId、encounterId、orgScope、occurredAt、triggerSource、traceId、payloadDigest。
- `ClinicalEventProcessor.process` dispatches the same context instance to RULE、PATHWAY、CDSS adapters before saving `PROCESSED`.
- Missing payload still fails before dispatch.

- [x] **Step 2: 运行红测**

Run: `mvn -B -q -Dtest=ClinicalEventContextContractTest,ClinicalEventProcessorTest test`

Expected: compile/test failure because `ClinicalEventContext` and dispatcher classes do not exist yet.

- [x] **Step 3: 最小实现**

Create focused production files:
- `ClinicalEventContext.java`
- `ClinicalEventContextFactory.java`
- `ClinicalEventEngine.java`
- `ClinicalEventEngineDispatchStatus.java`
- `ClinicalEventEngineDispatchResult.java`
- `ClinicalEventEngineAdapter.java`
- `ClinicalEventEngineDispatcher.java`

Update `ClinicalEventProcessor` constructor to inject the factory and dispatcher, build the context from `ClinicalEvent + ClinicalEventPayload`, dispatch it, then move to `PROCESSED`.

- [x] **Step 4: 绿测**

Run: `mvn -B -q -Dtest=ClinicalEventContextContractTest,ClinicalEventProcessorTest,ClinicalEventServiceTest,ClinicalEventOutboxWorkerTest test`

Expected: PASS.

### Task 2: 持久化组织上下文，避免异步事件丢失组织维度

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ClinicalEvent.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ClinicalEventService.java`
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V39__clinical_event_context_scope.sql`
- Modify: migration smoke/baseline tests for version 39.

- [x] **Step 1: 写失败测试**

Extend `ClinicalEventServiceTest.receiveAsyncPersistsEventPayloadOutboxAndHistory` to assert persisted event has `orgScopeJson` containing tenant and department from `RequestContext`.

- [x] **Step 2: 运行红测**

Run: `mvn -B -q -Dtest=ClinicalEventServiceTest,H2BaselineMigrationTest test`

Expected: compile/test failure because `ClinicalEvent.orgScopeJson` and V39 migration are missing.

- [x] **Step 3: 最小实现**

Persist `RequestContext.currentOrgScope()` as JSON in `clinical_event.org_scope_json`; factory reads it back and falls back to `OrgScope.tenant(event.tenantId())` only if legacy rows are blank.

- [x] **Step 4: 绿测**

Run: `mvn -B -q -Dtest=ClinicalEventServiceTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest,MigrationBaselineContractTest test`

Expected: PASS.

### Task 3: 字典映射锚点覆盖 12 类对象

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/context/ClinicalCodeMappingAnchor.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/context/ClinicalCodeMappingAnchorDefinition.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/context/ClinicalCodeMappingAnchorRegistry.java`
- Modify: `TerminologyMappingPort.java`
- Modify: `TerminologyMappingConfig.java`
- Modify: `ContextSnapshotService.java`
- Test: `ClinicalCodeMappingAnchorRegistryTest.java`
- Test: `ContextSnapshotServiceTest.java`

- [x] **Step 1: 写失败测试**

Require registry definitions for all 12 `CanonicalResourceType` values, and actual anchors for diagnosis、observation、medication、procedure、document、care plan、follow-up、claim examples. Require `ContextSnapshotService` to call `TerminologyMappingPort.evaluate(tenantId, anchors)`.

- [x] **Step 2: 运行红测**

Run: `mvn -B -q -Dtest=ClinicalCodeMappingAnchorRegistryTest,ContextSnapshotServiceTest test`

Expected: failure because anchors and new port signature are missing.

- [x] **Step 3: 最小实现**

Use explicit extraction, no reflection, so code fields are readable and maintainable. Noop mapping returns `UNKNOWN` per `anchor.key()` so unmapped local terms are traceable.

- [x] **Step 4: 绿测**

Run: `mvn -B -q -Dtest=ClinicalCodeMappingAnchorRegistryTest,ContextSnapshotServiceTest,ContextValidatorTest,StandardClinicalModelContractTest test`

Expected: PASS.

### Task 4: 真实引擎适配器

**Files:**
- Create: `ClinicalEventRuleEngineAdapter.java`
- Create: `ClinicalEventPathwayEngineAdapter.java`
- Create: `ClinicalEventRecommendationEngineAdapter.java`
- Modify: `PathwayEngineService.java`
- Create: `PathwayEventDispatchResponse.java`
- Test: `ClinicalEventEngineAdapterTest.java`

- [x] **Step 1: 写失败测试**

Require:
- Rule adapter calls `RuleEngineService.evaluate` with context JSON and `eventId`.
- Pathway adapter calls `PathwayEngineService.dispatchClinicalEvent`.
- CDSS adapter calls `RecommendationEngineService.trigger` and creates a real `NO_CARD` trigger when no candidate card exists.

- [x] **Step 2: 运行红测**

Run: `mvn -B -q -Dtest=ClinicalEventEngineAdapterTest test`

Expected: compile/test failure because adapters do not exist.

- [x] **Step 3: 最小实现**

Implement adapters with real service calls. If an adapter fails, dispatcher propagates the exception so the outbox retry/dead-letter path remains honest.

- [x] **Step 4: 绿测**

Run: `mvn -B -q -Dtest=ClinicalEventEngineAdapterTest,RuleEngineServiceTest,PathwayEngineServiceTest,RecommendationEngineServiceTest test`

Expected: PASS.

### Task 5: 文档接力、清理旧口径、整体验证

**Files:**
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/cards/D0/SYS-01.md`
- Modify: `docs/cards/D2/_brief.md`
- Modify: `docs/audit/deferred-issues.md` only if a new nonblocking issue is discovered.

- [x] **Step 1: 更新文档**

Move SYS-01 PR1 to archived evidence with PR #219 / merge `cab3669`; add SYS-01 PR2 in-progress line; add PR2 evidence to SYS-01 without checking entire card; replace stale `Symptom` wording with `NursingAssessment`.

- [x] **Step 2: 完整验证**

Run:
- `mvn -B -q test`
- `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`
- `node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`
- `node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`
- `scripts/check-comment-zh.sh`
- `git diff --check origin/main...HEAD`

Expected: all exit 0. If an external-only blocker appears, register it in `docs/audit/deferred-issues.md` and continue; do not mark failed verification as passed.
