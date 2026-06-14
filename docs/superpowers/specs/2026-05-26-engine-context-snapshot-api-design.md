# GA-ENG-API-01 标准上下文 API 设计

> 版本：1.0 · 2026-05-26
> 状态：设计待审
> 任务：[backlog.md](../../backlog.md) GA-ENG-API-01 标准上下文 API
> 关联：[详细规范 §1.5.2 / §7.4 / §7.6](../../MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md)

---

## 1. 目标与口径

引擎不得直接消费院内系统原始字段。任何业务（规则、路径、推荐、评估、随访、嵌入、模型）的输入都必须先经过 **`POST /api/v1/engine/context/snapshots`** 将院内来源数据规范化为 **标准临床上下文（ContextSnapshot）**。本任务交付该接口的最小可用集，承担 E2 后续 API-02..07 的"数据入口"。

- **唯一权威**：snapshot 一经创建即不可变（CarePlan/FollowUp 等动态对象由后续状态机管理，本接口仅固化某时点的资源集合）
- **包版本快照**：每个 snapshot 关联当时生效的 `knowledge_package_version` / `rule_package_version` / `pathway_package_version`，保证后续可重放
- **缺失字段与映射状态**：返回值显式给出 schema 必填项缺失列表与字典映射状态（联动 API-04），但不阻断创建——质量信号写入 `quality_status`
- **范围**：12 个标准临床对象（Patient / Encounter / Condition / Symptom / Observation / DiagnosticReport / Medication / Procedure / Document / CarePlan / FollowUp / Claim）一次到位

---

## 2. 数据模型

### 2.1 表族（V7__clinical_context_baseline.sql · 五方言同步）

| 表 | 用途 | 关键字段 |
|---|---|---|
| `context_snapshot` | 一次"上下文固化"的顶层聚合 | snapshot_id (PK, UUID), tenant_id, org_unit_id, patient_id, encounter_id, knowledge_pkg_version, rule_pkg_version, pathway_pkg_version, status (DRAFT/ACTIVE/SUPERSEDED/REJECTED), missing_fields (JSON), mapping_status (JSON), trace_id, created_at, created_by, signature (SM3) |
| `canonical_resource` | snapshot 内的每个标准对象 | resource_id (PK), snapshot_id (FK), resource_type (枚举 12 类), resource_payload (JSON), source_system, source_record_id, mapped_version, event_time, received_time, quality_status (VALID/PARTIAL/INVALID), seq_no |
| `clinical_event` | 触发 snapshot 创建的事件来源（与 API-02 共表） | event_id (PK), tenant_id, event_type (DIAG/ORDER/REPORT/DISCHARGE/FOLLOWUP), source_system, payload_digest, occurred_at, received_at, snapshot_id (nullable FK), processing_status |

- 索引：`uk_context_snapshot_tenant_patient_encounter_time`、`idx_canonical_resource_snapshot_type`、`idx_clinical_event_tenant_received`
- 约束：snapshot.status 受控状态机、canonical_resource.resource_type ∈ 12 枚举、quality_status 三态枚举
- 审计：每次创建/读取写 `audit_event`（action=CONTEXT_CREATED/CONTEXT_READ），含 traceId

### 2.2 标准临床对象 schema（Java Record）

每个对象都是 Record DTO，集中放在 `com.medkernel.engine.context.dto.canonical.*`：

```java
public record CanonicalPatient(
    @NotBlank String mpi,
    @NotBlank String name,
    LocalDate birthDate,
    Gender gender,
    List<Allergy> allergies,
    List<SpecialPopulation> specialPopulations,
    String sourceSystem, String sourceRecordId, String mappedVersion,
    Instant eventTime, Instant receivedTime, QualityStatus qualityStatus
) {}
```

12 对象同结构（必填字段不同），统一携带 7 个标准元数据字段（source_system / source_record_id / mapped_version / event_time / received_time / quality_status / trace_id 由顶层 snapshot 注入）。

---

## 3. 接口契约

### 3.1 POST `/api/v1/engine/context/snapshots`

**输入**：

```json
{
  "patientId": "MPI-...",
  "encounterId": "ENC-...",
  "orgUnitId": "ORG-...",
  "knowledgePackageVersion": "kpv-2026.05",
  "rulePackageVersion": "rpv-2026.05",
  "pathwayPackageVersion": "ppv-2026.05",
  "resources": {
    "patient": { ... },
    "encounters": [ ... ],
    "conditions": [ ... ],
    "symptoms": [ ... ],
    "observations": [ ... ],
    "diagnosticReports": [ ... ],
    "medications": [ ... ],
    "procedures": [ ... ],
    "documents": [ ... ],
    "carePlans": [ ... ],
    "followUps": [ ... ],
    "claims": [ ... ]
  }
}
```

**输出（`ApiResult<ContextSnapshotResponse>`）**：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "snapshotId": "ctx-uuid",
    "status": "ACTIVE",
    "missingFields": [
      { "resourceType": "Patient", "field": "birthDate", "level": "WARN" }
    ],
    "mappingStatus": {
      "Medication.code": "PARTIAL",
      "Condition.code": "VALID"
    },
    "createdAt": "...",
    "traceId": "..."
  }
}
```

- 权限：`@PreAuthorize("@perm.has('context.write')")` + `@DataScope(requireTenant=true)`
- 幂等：`Idempotency-Key` header（24h TTL），相同 key 返回首次结果
  - 存储：新建 `context_idempotency_key` 表（snapshot_id 外键 + key + 过期时间 + 租户），与 snapshot 同事务写入
  - 后续 E4 可切换到 Redis 等共享缓存，但接口语义不变
- 错误码：ENG-CONTEXT-001（schema 失败）、ENG-CONTEXT-002（包版本不存在）、ENG-CONTEXT-003（quality_status=INVALID 拒绝创建）、ENG-CONTEXT-004（幂等键冲突且 payload 不一致）

### 3.2 GET `/api/v1/engine/context/snapshots/{snapshotId}`

- 输出：完整 snapshot（含 12 类 resources）
- 权限：`@perm.has('context.read')` + `@DataScope`
- 错误码：ENG-CONTEXT-404

### 3.3 GET `/api/v1/engine/context/snapshots`

- 查询参数：`patientId` / `encounterId` / `eventTimeFrom` / `eventTimeTo` / `status` / `page` / `size` / `sort`
- 输出：`ApiResult<PageResponse<ContextSnapshotSummary>>`（不含 resources 详情，仅 ID + 元数据）
- 权限：同上
- 默认 page=0, size=20, max size=200

---

## 4. 模块边界

对齐现有 `engine/org`、`engine/knowledge`、`engine/terminology` 的扁平 package 风格，新增模块下不再分子包：

```
com.medkernel.engine.context/
├── ContextSnapshotController.java         ← REST 入口
├── ContextSnapshotService.java            ← 业务编排
├── ContextValidator.java                  ← schema + 必填项 + quality_status 计算
├── PackageVersionResolver.java            ← 验证 knowledge/rule/pathway 包版本存在性
├── MappingStatusEvaluator.java            ← 调字典映射端口
├── TerminologyMappingPort.java            ← 端口接口（domain），避免循环依赖
├── ContextSnapshot.java                   ← 聚合根
├── ContextSnapshotStatus.java             ← 枚举
├── CanonicalResource.java                 ← 12 类资源单元
├── CanonicalResourceType.java             ← 枚举（12 类）
├── QualityStatus.java                     ← 枚举（VALID/PARTIAL/INVALID）
├── ClinicalEvent.java                     ← 触发事件聚合
├── ContextSnapshotRequest.java            ← 顶层入参 DTO
├── ContextSnapshotResponse.java           ← 创建/读单 DTO
├── ContextSnapshotSummary.java            ← 列表条目 DTO
├── ContextSnapshotFilter.java             ← 列表查询入参
├── ContextSnapshotRepository.java         ← Spring Data JDBC 仓储
├── CanonicalResourceRepository.java
├── ClinicalEventRepository.java
├── ContextIdempotencyKeyRepository.java
└── canonical/                             ← 12 个 Record DTO，唯一允许的子包
    ├── CanonicalPatient.java
    ├── CanonicalEncounter.java
    ├── CanonicalCondition.java
    ├── CanonicalSymptom.java
    ├── CanonicalObservation.java
    ├── CanonicalDiagnosticReport.java
    ├── CanonicalMedication.java
    ├── CanonicalProcedure.java
    ├── CanonicalDocument.java
    ├── CanonicalCarePlan.java
    ├── CanonicalFollowUp.java
    └── CanonicalClaim.java
```

- 不直连 terminology 内部：通过 `TerminologyMappingPort` 接口调字典映射服务，避免循环依赖。terminology 模块按需提供该端口的实现 bean。
- 共享：复用 shared/audit、shared/api、shared/datascope、shared/trace

---

## 5. 错误处理与降级

| 场景 | 行为 |
|---|---|
| schema 必填项缺失 | 标记 `missingFields[*].level=ERROR`，但 quality_status=PARTIAL 仍可创建（不阻断引擎链路）；level=CRITICAL 则拒绝并返回 ENG-CONTEXT-001 |
| 字典映射服务不可用 | mappingStatus 全部置 `UNKNOWN`，snapshot 仍可创建，写降级日志 |
| 包版本不存在 | 拒绝创建，ENG-CONTEXT-002 |
| 数据库写入失败 | 事务回滚，traceId + 错误码透传，不留半截 snapshot |
| 幂等 key 命中 | 直接返回首次结果，不重复写 audit |

---

## 6. 测试范围

| 层 | 测试用例 |
|---|---|
| 单测 | ContextSnapshotServiceTest（12 对象 schema 校验 / 幂等 / 包版本解析） |
| 单测 | ContextValidatorTest（缺失字段分级 / quality_status 计算） |
| 单测 | MappingStatusEvaluatorTest（terminology 端口 mock） |
| 仓储 | ContextSnapshotRepositoryTest（@DataJdbcTest，H2 跑 V1..V7） |
| Controller | ContextSnapshotControllerSecurityTest（@perm 校验、@DataScope 校验、幂等） |
| 迁移 | V7 加入 MigrationBaselineContractTest（必填字段、状态枚举、索引） |
| FlywayMultiDialectSmokeTest | V7 五方言同步通过（CI Docker 环境） |
| 契约 | OpenAPI snapshot 通过 springdoc 自动暴露，写一个 contract test 锁定 path + schema |

---

## 7. 性能与 SLA

| 指标 | 目标 | 测量 |
|---|---|---|
| POST 创建 P95 | ≤ 300ms（12 对象 + 1000 资源单元） | k6 / Prometheus histogram |
| GET by ID P95 | ≤ 100ms | 同上 |
| GET list (size=20) P95 | ≤ 200ms | 同上 |
| 并发吞吐 | ≥ 200 RPS（POST，单租户）| k6-1000-concurrent.js 复用 |
| canonical_resource.payload 最大体积 | 1MB / 单资源；16MB / snapshot | 输入校验 |

- 性能验收延后到 E5 GA-ENG-QA-02 集成验收阶段统一压测；本任务只做接口正确性，不专门跑 1000 并发。
- 接口层加 Micrometer Timer（`engine.context.snapshot.create.duration` 等），便于后续观测。

---

## 8. 验收清单

- [ ] V7 五方言迁移文件齐全（postgres / oracle / dm / kingbase / h2）
- [ ] 12 个标准对象 Record DTO + Bean Validation 完整
- [ ] POST / GET by ID / GET list 三接口可用，OpenAPI 自动注册
- [ ] 幂等、权限、数据范围、审计、traceId 全链路打通
- [ ] mvn test 全绿；测试覆盖率 ≥ 已有 engine 模块基线（≥ 85% 业务行）
- [ ] backlog.md 4.14 修订记录写入；API-01 状态置 done
- [ ] PR 落到 main（按现有 `feat(GA-ENG-API-01)` 命名）

---

## 9. 不在本任务范围

- 前端样板页（属于 GA-ENG-API-01 验证而非交付物，留到 API-01 完成后单开 PR）
- API-02 临床事件 API 的同步事件接收（仅在 V7 表族中预留 `clinical_event.snapshot_id` 外键，本任务不实现 event 创建/触发逻辑）
- 缺失字段的自动补齐（属于 B1/B2 模型增强，本任务只检测不补齐）
- 跨租户 snapshot 合并、历史归档（属于 E3/E4）
- 1000 并发性能压测（统一在 E5 GA-ENG-QA-02 完成）

---

## 10. 风险与决策点

| 风险 | 缓解 |
|---|---|
| 12 对象一次到位工作量较大 | 12 个 Record DTO 同结构、可并行编写；schema 校验集中在 ContextValidator |
| canonical_resource.payload 用 JSON 而非展开列 | 各方言 JSON 支持差异：PG/H2/Oracle 21+/DM/KingBase 均支持 JSONB 或 CLOB 校验；统一用 CLOB + 应用层 JSON 校验 |
| 12 对象 schema 在 detail spec 第 1262 行后还有细节 | 本任务仅按"必填+元数据"实现；额外字段按 Open Content 模式允许 payload 携带（V8 起逐步收紧） |
| API-04 字典映射状态依赖 | 通过 `TerminologyMappingPort` 接口隔离；终端模块未发布对应口径时 mapping_status=UNKNOWN |

---

**End of GA-ENG-API-01 design.**
