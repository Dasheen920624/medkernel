# AIK-STD-13 知识生产编排与多生产器双形态接入 · 设计

> 卡片：[docs/cards/wave2/AIK-STD-13.md](../../cards/wave2/AIK-STD-13.md)。批次 P2-B。日期 2026-06-15。
> 权威读序：核心 §7（唯一权威/平台源不可污染）· §9（客户覆盖只归本租户/不反写主源）· 铁律 #1（真实性）· #4（B0 先于模型）· #5（AI 只产候选）。
> 隔离语义依据：[平台主源与租户覆盖层设计](2026-06-02-platform-tenant-overlay-design.md)。

## 1. 关键核查（写给下个 AI）

| 卡片预设 | 既有地基 | 裁决 |
|---|---|---|
| 统一编排层 + 多生产器 + 双形态隔离 | **全无**（grep `knowledge_production`/`Producer` 零命中）——早期 AIK 只假设 Dify/Ollama 推理 | **真新建**（编排层 + job 表） |
| 候选池 / 审核链 | **已成熟**：`KnowledgeVersionService` 产 `KnowledgeAssetVersion`(CANDIDATE) + `mk_knowledge_candidate_classification`(候选池) + `ReviewAssignment`(审核队列) | **复用，候选入既有链**，不另起资产表 |
| 平台主源 / 覆盖隔离 | **已有**：`PlatformTenant.ID="t-1"` + `isPlatformTenant()`；覆盖 spec（2026-06-02）定 §9 语义 | 复用守卫基座 |
| 资产信封 | AIK-STD-01 `KnowledgeAssetEnvelope` + 校验闸（#617）；LLM-06 已产此信封（#618） | 复用作生产器产物契约 |

**真正新增**：编排 job 表 `mk_knowledge_production_job` + 编排服务（生产器路由 + 双形态隔离守卫 + 候选入既有链 + 血缘/审计）。

## 2. PR 切片（仿 DATASVC-01 分期）

- **PR1（本设计落点）= 编排核心**：FR-1 job 骨架（建/查/进度）· FR-3 统一候选池（消费信封入既有候选链）· **FR-4 双形态物理隔离守卫**（§9 红线）· FR-5 血缘/审计 · FR-2 的 **MANUAL/确定性生产器**（B0）。
- **PR2+（后续）**：FR-5 job 重放 + 中止；FR-6 候选按归属+风险+领域路由会签（接审核分派）；FR-2 外部模型生产器实接（API/本地，经 LLM-01/08 网关，**P6 闸控**）；FR-7 院内覆盖角色边界五维资产权限细化。

## 3. PR1 组件设计

### 3.1 数据（唯一新表）`mk_knowledge_production_job`（V130 五方言 + 中文 COMMENT）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | PK | |
| `tenant_id` | VARCHAR(64) NOT NULL | 归属租户（隔离） |
| `job_code` | VARCHAR(64) NOT NULL UNIQUE | 业务键（UUID） |
| `source_scope` | VARCHAR(1024) NOT NULL | 来源范围描述（如探索 run / 料源批次引用） |
| `asset_type` | VARCHAR(32) NOT NULL | 产出资产类型（`VersionedAssetType`） |
| `producer` | VARCHAR(16) NOT NULL | 生产器（API_MODEL/AGENT_TOOL/LOCAL_MODEL/MANUAL） |
| `target_pipeline` | VARCHAR(16) NOT NULL | 目标管道（PLATFORM_SOURCE/TENANT_OVERLAY） |
| `model_strategy` | VARCHAR(256) NULL | 模型策略标识（B0 为空） |
| `status` | VARCHAR(16) NOT NULL | PENDING/RUNNING/COMPLETED/FAILED/CANCELLED |
| `candidate_count` | INTEGER NOT NULL DEFAULT 0 | 已入池候选数 |
| `lineage` | VARCHAR(2048) NULL | 血缘摘要（生产器/模型模式/提示词版本/时点，JSON） |
| `created_by/at` `updated_by/at` | | 审计字段（mutable-audited） |
| `trace_id` | VARCHAR(128) NULL | 链路追踪 |

- 约束：`uk_mk_knowledge_production_job_code UNIQUE(job_code)`；`ck_..._producer`、`ck_..._pipeline`、`ck_..._status` CHECK。
- 索引：`idx_mk_knowledge_production_job_lookup (tenant_id, target_pipeline, status)`。
- 候选**不在本表存正文**：候选入既有 `KnowledgeAssetVersion`/候选池，job 仅记编排元数据 + 血缘。

### 3.2 枚举

- `TargetPipeline { PLATFORM_SOURCE, TENANT_OVERLAY }`。
- `KnowledgeProducer { API_MODEL, AGENT_TOOL, LOCAL_MODEL, MANUAL }`。
- `ProductionJobStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }`（变更类状态机：PENDING→RUNNING→COMPLETED/FAILED；任意非终态→CANCELLED）。

### 3.3 编排服务 `KnowledgeProductionOrchestrationService`

- **createJob(request)**：解析 tenant；**FR-4 隔离守卫**：
  - `PLATFORM_SOURCE` → 当前租户须为 `t-1`（`PlatformTenant.isPlatformTenant`），否则 `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`（客户禁产平台主源）。
  - `TENANT_OVERLAY` → 当前租户须**非** `t-1`（平台不产覆盖），否则同错。
  - 落 PENDING job + 血缘起点 + 审计。
- **submitCandidate(jobCode, KnowledgeAssetEnvelope)**：
  - 经 AIK-STD-01 `KnowledgeAssetSchemaValidator` 校验（无源拒收 / 候选态 / 真实 hash）。
  - **FR-4 二次隔离守卫**：候选 `orgScope` 须与 job 租户一致；`TENANT_OVERLAY` 候选 `orgScope` **禁为 `t-1`**（禁反写主源）；`assetType` 须与 job 一致。违反 → `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`。
  - **FR-3/5 候选血缘 + 计数**：job `candidate_count++`、status→RUNNING；写生产动作审计（producer/pipeline/tenant/资产身份/contentHash/时点＝血缘轨迹，FR-5）；返回校验+隔离通过的候选。
  - **边界（写给下个 AI）**：既有 `KnowledgeVersionService.classifyCandidate` 候选物化**深耦合**（需既有 `knowledge_identity` + `source_document_id`/`source_version_id` FK + 版本号 + 锚点 = 8 态去重/解析链，属 AIK-STD-04/10·P2-C）。信封（来源为字符串引用）→ 版本记录（需 FK）存在阻抗。故 PR1 **不过早耦合未建解析管道、不造平行候选表**：submitCandidate 止于校验+隔离+血缘审计+计数，**候选入既有版本/审核链的物化经 `KnowledgeCandidateIntake` 端口随解析管道（AIK-STD-04/10）落地**（PR1 定义端口，默认实现＝校验隔离 + 血缘审计；真实物化下一切片接线）。
- **getJob(jobCode) / listJobs(pipeline,status,page) / listJobCandidates(jobCode)**：进度 + 候选查询（P95 ≤2s）。
- **B0 诚实**：`MANUAL` 生产器全实现（人工/批量录入信封）；`API_MODEL`/`LOCAL_MODEL`/`AGENT_TOOL` 为框架槽位——本卡接受**已成形信封**（LLM-06 确定性产物即一例），真实模型调用经 LLM-01/08 网关 + P6 闸（本卡不解 P6）。关模型仍可经 MANUAL/确定性生产器产候选走流水线（B0 验收）。

### 3.4 DTO

- `ProductionJobRequest(@NotBlank sourceScope, @NotNull assetType, @NotNull producer, @NotNull targetPipeline, String modelStrategy)`。
- `ProductionJobResponse`（job 视图）。
- `CandidateSubmission`（jobCode + `KnowledgeAssetEnvelope`）。

### 3.5 控制器 `KnowledgeProductionController`

- `@RequestMapping("/api/v1/engine/knowledge-production") @DataScope(requireTenant=true)`。
- `POST /jobs`（建 job，`knowledge.write`）· `GET /jobs`、`GET /jobs/{jobCode}`（进度，`knowledge.read`）· `POST /jobs/{jobCode}/candidates`（提交候选，`knowledge.write`）· `GET /jobs/{jobCode}/candidates`（`knowledge.read`）。
- 错误码 `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`（越界/反写主源，结构化 422/400）。
- 权限**复用 `knowledge.write`/`knowledge.read`**（隔离靠 t-1 守卫非权限；会签角色路由 PR2）。

## 4. 配套登记

- 契约 `knowledge-production`（controller/path/permissions/audit `mk_knowledge_production_job`）。
- `DomainOwnershipCatalog` engine-knowledge tables 加 `mk_knowledge_production_job`（mutable-audited：含 updated_at/by）。
- `MigrationBaselineContractTest` V130 + 表/索引/约束 + `MUTABLE_AUDITED_TABLES` + `LIFECYCLE_FIELDS(status)` + `TENANT_TABLES`；两 `LATEST_MIGRATION_VERSION` 129→130。
- 产品功能目录重生成（新控制器）+ 前端 `productCatalog.test.ts`。

## 5. FR/AC 映射（PR1）

| 条目 | PR1 落点 |
|---|---|
| FR-1 生产 job | createJob/getJob/listJobs（建/查/进度）；重放/中止 → PR2 |
| FR-2 生产器可插拔 | producer 枚举 + MANUAL/确定性全实现；外部模型槽位（实接 PR2，P6 闸） |
| FR-3 统一候选池 | submitCandidate 消费信封：校验 + 隔离 + 血缘审计 + 计数（只产候选）；入既有版本/审核链物化经 `KnowledgeCandidateIntake` 端口随解析管道（P2-C）接线 |
| FR-4 双形态隔离 | createJob + submitCandidate 双重 t-1 守卫，越界/反写拒（§9） |
| FR-5 血缘/审计 | job 血缘标签 + 候选血缘 + 生产动作全审计；重放 → PR2 |
| AC-1（FR-1/2/3） | 四生产器 LABEL 入同一池走同一链（外部实接 PR2） |
| AC-2（FR-4） | overlay 候选反写 t-1 → VIOLATION 拒；platform 仅 t-1 |
| AC-3（FR-5） | 候选可溯 job/生产器；重放/全审计（重放 PR2） |
| B0 验收 | 关模型经 MANUAL/确定性生产器仍产候选走流水线 |

## 6. 验证清单（PR1）

- TDD 红绿：`KnowledgeProductionOrchestrationServiceTest`（建 job 隔离守卫双向 / 候选入池 / 反写 t-1 拒 / 校验闸拒无源 / 血缘 / 关模型 MANUAL 可跑）+ `KnowledgeProductionJobRepositoryIntegrationTest`（H2 落库 + 租户隔离 + 查询）+ `KnowledgeProductionControllerSecurityTest`（权限矩阵 + VIOLATION 错误码）。
- 全量 `mvn test` 不回归 + 四门禁 changed + 五方言 smoke + `git diff --check` + 前端 `productCatalog.test.ts`。
- 合并 main 逐 PR 授权（用户手动合）。

## 7. 显式不做（PR1 边界 / YAGNI）

- 不另起资产/版本表，也不造平行候选表（候选物化走既有链，经 `KnowledgeCandidateIntake` 端口随解析管道 AIK-STD-04/10 接线）。
- 不实接外部模型生产器（P6 闸；MANUAL/确定性先行）。
- 不做 job 重放/中止、会签领域路由（PR2）。
- 不做生产者工作台前端（AIK-STD-12）。
