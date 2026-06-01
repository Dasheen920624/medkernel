# OBS-01 引擎可观测性骨干实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 OBS-01：用统一 trace/MDC、状态流转、DB payload 存储、错误码和诊断响应，把引擎执行从黑盒变成可按 traceId 还原的证据链。

**Architecture:** 复用 BASE-03 的 `RequestContext` / `TraceIdFilter` 与 BASE-04 审计骨干，不新造旁路。把旧 `state_transition_history` 口径收敛到当前迁移门禁接受的 `mk_obs_state_transition`，并用 `mk_obs_payload_store` 作为默认 `PayloadStoragePort` 实现；诊断端点只面向 `system.read` / `audit.read` 权限，供后续 D3/D6 专家模式消费。

**Tech Stack:** Spring Boot 3、Spring Data JDBC、Flyway 五方言迁移、JUnit 5、MockMvc、Testcontainers PostgreSQL/Oracle。

---

## 任务 1：接力与范围收口

**Files:**
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/audit/deferred-issues.md`

- [x] 确认 PR #216 已合并到 `origin/main`，远端分支已清理。
- [ ] 把 BASE-11 移入已归档工作线，并新增 OBS-01 在途线。
- [ ] 明确阻塞策略：open deferred issue 不阻塞 OBS-01，除非影响当前卡主链路、登录、权限、真实性或医疗安全。

## 任务 2：迁移契约红灯

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V8__observability_baseline.sql`

- [ ] 写失败测试：V8 必须包含 `mk_obs_state_transition` 与 `mk_obs_payload_store`，并具备 trace、tenant、orgPath、payload digest、软删除和租户索引。
- [ ] 运行：`mvn -B -q -Dtest=MigrationBaselineContractTest test`，确认因旧表名 / 缺 payload store 失败。
- [ ] 更新五方言 V8，保留 `canonical_resource.trace_id`，新增中文 COMMENT。
- [ ] 运行同一测试确认通过，并跑迁移规约 changed 门禁。

## 任务 3：DB PayloadStoragePort 红绿

**Files:**
- Delete: `medkernel-backend/src/main/java/com/medkernel/shared/observability/InMemoryPayloadStorage.java`
- Delete: `medkernel-backend/src/main/java/com/medkernel/shared/observability/PayloadStorageConfig.java`
- Delete: `medkernel-backend/src/test/java/com/medkernel/shared/observability/InMemoryPayloadStorageTest.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/observability/PayloadStoreRecord.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/observability/PayloadStoreRepository.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/observability/DbPayloadStorage.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/shared/observability/DbPayloadStorageTest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/observability/PayloadRef.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/observability/PayloadStoragePort.java`

- [ ] 写失败测试：`put` 真实落 DB、`get` 精确取回字节、digest 稳定、`delete` 软删除、`findByTraceId` 返回同 trace payload。
- [ ] 运行：`mvn -B -q -Dtest=DbPayloadStorageTest test`，确认缺实现失败。
- [ ] 实现 DB 存储，payload 以 Base64 文本落 `mk_obs_payload_store`，不使用 JVM 内存兜底。
- [ ] 运行 `DbPayloadStorageTest` 与 `StateTransitionHistoryRepositoryTest` 通过。

## 任务 4：状态流转与 trace 诊断端点

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/observability/StateTransitionHistory.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/observability/StateTransitionRecorder.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/observability/DiagnoseResponse.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/observability/DiagnoseResponseAssembler.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/observability/TraceDiagnoseResponse.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/observability/ObservabilityDiagnoseService.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/observability/ObservabilityDiagnoseController.java`
- Modify tests under `medkernel-backend/src/test/java/com/medkernel/shared/observability`

- [ ] 写失败测试：状态流转写入 `org_path/created_by`；诊断响应包含 payload contentType、耗时、降级原因；按 traceId 可取回状态流转 + payload 摘要。
- [ ] 运行目标测试确认失败。
- [ ] 实现 recorder 字段补齐、诊断 DTO 扩展和 `/api/v1/engine/diagnose/traces/{traceId}` 端点。
- [ ] 运行目标测试确认通过。

## 任务 5：触点适配与文档验收

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ClinicalEventService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/recommendation/RecommendationEngineService.java`
- Modify: `docs/cards/D0/OBS-01.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [ ] 保持临床事件与推荐诊断响应兼容新 `PayloadRef` / `DiagnoseResponse`，不暴露 payload 明文到客户面。
- [ ] 勾选 OBS-01 FR/AC，记录 PostgreSQL + Oracle 当前保障范围，国产化真实环境继续归 `DEFER-001`。
- [ ] 把 backlog 中 OBS-01 标记 done，并更新 `_HANDOFF` 下一步指向 `API-13`。

## 任务 6：验收、PR、CI

- [ ] 后端目标测试：`mvn -B -q -Dtest=MigrationBaselineContractTest,DbPayloadStorageTest,StateTransitionHistoryRepositoryTest,StateTransitionRecorderTest,DiagnoseResponseAssemblerTest,TraceIdPropagatorTest,MdcEnrichmentFilterTest test`
- [ ] 后端全量：`mvn -B -q test`
- [ ] T-GATE：`git diff --check`、真实性 changed、配置边界 changed、迁移 changed、中文注释门禁。
- [ ] 提交、推送、创建 PR，远端 CI 8/8 通过并合并后，才能领取下一阶段 `API-13`。
