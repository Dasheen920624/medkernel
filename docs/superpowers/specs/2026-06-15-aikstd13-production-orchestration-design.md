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

- **PR1（#619 已合）= 编排核心**：FR-1 job 骨架（建/查/进度）· FR-3 统一候选池（消费信封入既有候选链）· **FR-4 双形态物理隔离守卫**（§9 红线）· FR-5 血缘/审计 · FR-2 的 **MANUAL/确定性生产器**（B0）。
- **PR2（#620 已合）= job 生命周期 + 候选血缘**：FR-1 complete/cancel/replay · FR-5 候选血缘表 + 可回溯（见 §8）。
- **PR3（本设计落点 §9）= 候选会签路由 + 院内覆盖角色边界**：FR-6 候选按归属+风险+领域（含药学经 `domain=PHARMACY`）**确定性路由决策**（resolve，不建 `ReviewAssignment`——物化前不伪装已分派）· FR-7 院内覆盖角色边界（路由器保证院内候选只路由机构侧角色）。药学＝领域非类型，**不动 `VersionedAssetType`**。
- **PR4+（后续）**：FR-2 外部模型生产器实接（API/本地，经 LLM-01/08 网关，**P6 闸控**）；候选真实物化入既有版本/审核链（接 AIK-STD-04/10 解析管道，建真 `ReviewAssignment`）。

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
- 不做 job 重放/中止、会签领域路由（PR2/PR3）。
- 不做生产者工作台前端（AIK-STD-12）。

## 8. PR2 详细设计：job 生命周期 + 候选生产血缘（FR-1 中止/重放 · FR-5 血缘可回溯）

> PR1（#619）已合并入 main。PR2 直续：补 job 生命周期闭环 + 候选生产血缘可回溯。仍 B0、不碰 P6/未建解析管道。

### 8.1 数据（新表）`mk_knowledge_production_candidate`（V131 五方言 + 中文 COMMENT）

候选**生产血缘**（非资产存储——不存正文/sources 内容，仅回溯元数据）：每条提交候选记一行。

| 列 | 说明 |
|---|---|
| `id` / `tenant_id` | PK / 租户隔离 |
| `job_code` | 归属生产 job（回溯 job→生产器/管道/模型策略） |
| `asset_identity` | 候选资产身份键 |
| `content_hash` | 候选内容真实 SHA-256 |
| `candidate_ref` | intake 返回的候选引用标识 |
| `created_at` / `created_by` | 时点 / 提交人 |

- 约束：无唯一业务键（同 job 可多次提交同身份的修订）；索引 `idx_mk_knowledge_production_candidate_job (tenant_id, job_code)`。append-only（无 updated 列，非 mutable-audited）。

### 8.2 服务扩展 `KnowledgeProductionOrchestrationService`

- `submitCandidate`（改）：intake 后**持久化血缘行** + 计数（PR1 仅计数+审计，PR2 加血缘行落库）。
- `listCandidates(jobCode)`：返回该 job 血缘行（**FR-5 可回溯**）。
- `completeJob(jobCode)`：PENDING/RUNNING → COMPLETED。
- `cancelJob(jobCode)`：PENDING/RUNNING → CANCELLED；终态（COMPLETED/FAILED/CANCELLED）→ 结构化 409 拒（非法生命周期跃迁）。
- `replayJob(jobCode)`：复制 job 定义（source_scope/asset_type/producer/target_pipeline/model_strategy）建**新 PENDING job**，`lineage` JSON 记 `replayedFrom=<原 jobCode>`，返回新 job（**FR-5 可重放**；隔离守卫复用 createJob 路径，越界仍拒）。

### 8.3 控制器扩展 `KnowledgeProductionController`

- `GET /jobs/{jobCode}/candidates`（`knowledge.read`）→ 候选血缘列表。
- `POST /jobs/{jobCode}/complete` / `cancel` / `replay`（`knowledge.write`）。

### 8.4 配套

- 域归属 engine-knowledge 加 `mk_knowledge_production_candidate`；契约 `knowledge-production` 补 `mk_knowledge_production_candidate` 审计点；迁移基线 V131 + 表/索引；两 `LATEST_MIGRATION_VERSION` 130→131；产品目录重生成（新端点）。

### 8.5 PR2 验证

- TDD：服务（提交落血缘 / 列候选 / complete / cancel 终态拒 / replay 建新 job 复用隔离守卫）+ 血缘 repo 集成 + 控制器安全（新端点权限）。
- 全量 `mvn test` + 四门禁 + 五方言 smoke + 前端目录。

### 8.6 PR3+ 仍待

- FR-6 候选按归属+风险+领域路由会签（接审核分派）· FR-2 外部模型生产器实接（P6 闸）· 候选真实物化入既有版本/审核链（接 AIK-STD-04/10 解析管道）· FR-7 院内覆盖角色边界细化。

## 9. PR3 详细设计：候选会签路由（FR-6）+ 院内覆盖角色边界（FR-7）

> PR1（#619）+ PR2（#620）已合并入 main。PR3 直续：候选**确定性会签路由决策** + 院内覆盖角色边界。仍 B0、不碰 P6/未建解析管道。

### 9.0 关键约束（写给下个 AI）

候选**尚未物化**进版本链（物化属 P2-C 解析管道，本卡留缝）。既有 `ReviewAssignment` 强依赖 `candidate_classification_id`/`identity_id`/`candidate_version_id`（均物化产物）。故 PR3 **不建 `ReviewAssignment` 行**，只产**确定性路由决策（resolve）**：算「该候选送谁会签 / 是否双签 / 领域」，留待物化时（P2-C）据此真正建分派。**诚实分寸：不伪装已分派**（铁律 #1）。

路由是**纯确定性函数**（B0，无上游、无模型）：输入 = `targetPipeline`（job）+ `domain`（job）+ `riskLevel`（候选 envelope），输出 = 归口审核角色 + 领域会签角色 + 是否双签。

**领域 vs 资产类型（正交，药学＝领域不是类型）**：医学**领域**（临床/药学/术语报告/评估医保）与**结构资产类型**（RULE/PATHWAY/KNOWLEDGE…）正交——一条「药学 DDI 规则」与「临床危急值规则」结构同为 `RULE`，单看类型分不出领域。**药学本身就是知识的一部分**，不另起资产类型（`DRUG_LABEL`/`DRUG_INTERACTION` 会把领域与结构混淆、且与 `domain=PHARMACY` 重复）：药品说明书＝`KNOWLEDGE` 资产、DDI＝`RULE` 资产（KNOWGEN-04 自身亦归 DDI 入「临床规则」），二者经 `domain=PHARMACY` 区分领域、路由药事安全人员。故本卡**只引入 `domain` 维度**填药学缺口，**不动 `VersionedAssetType`/资产表约束**。

### 9.1 数据列补全（原地改 V130/V131，五方言 + 中文 COMMENT，**不新建 V132**）

用户决策**原地改已合迁移**（greenfield 无兼容包袱，最直接、零 ALTER 碎片，靠新建库生效）——直接在 `CREATE TABLE` 中加列：

| 迁移 | 变更 | 说明 |
|---|---|---|
| `V130`（job，5 方言） | `CREATE TABLE` 加列 `domain VARCHAR(24) NOT NULL` + `CHECK domain IN (5 域)` + COMMENT | 生产领域（`KnowledgeDomain`），应用层 `@NotNull` 强制申报 |
| `V131`（候选血缘，5 方言） | `CREATE TABLE` 加列 `risk_level VARCHAR(16) NOT NULL` + `CHECK risk_level IN (LOW,MEDIUM,HIGH)` + COMMENT | 候选风险级（`KnowledgeRiskLevel`），提交时存真实 envelope 风险级 |

- 路由结论（归口/领域角色/双签）**不落库**——纯函数派生，只读 resolve（不存派生数据）；只持久化非派生输入 `domain`（job）+ `risk_level`（候选）。
- **`LATEST_MIGRATION_VERSION` 保持 131**（原地改 CREATE 内容，无新版本号）；迁移基线测试 `REQUIRED_*` 补 job.domain / candidate.risk_level 列。

### 9.2 枚举 `KnowledgeDomain`（新）

`{ CLINICAL, PHARMACY, TERMINOLOGY_REPORT, EVALUATION_INSURANCE, GENERAL }`——候选生产领域（核心 §6 会签领域归类）。

### 9.3 路由器 `CandidateReviewRouter`（@Service，纯确定性 B0）

`ReviewRoutingDecision resolve(TargetPipeline pipeline, KnowledgeDomain domain, KnowledgeRiskLevel risk)`：

- **归口审核角色（按管道归属，FR-6/FR-7）**：`PLATFORM_SOURCE → PLATFORM_KNOWLEDGE_GOVERNOR`；`TENANT_OVERLAY → KNOWLEDGE_GOVERNOR`（机构侧，**永不平台归口**＝FR-7 边界）。
- **领域会签角色（按领域，FR-6）**：`CLINICAL → CLINICAL_GOVERNOR`；`PHARMACY → MEDICATION_SAFETY_USER`；`TERMINOLOGY_REPORT → DIAGNOSTIC_SERVICE_USER`；`EVALUATION_INSURANCE → QUALITY_GOVERNOR`；`GENERAL →` 同归口角色（无独立专科会签）。
- **是否双签（按风险，FR-6「高危走双签」）**：`risk == HIGH → true`（归口 + 领域两签）；否则 `false`（单签）。`GENERAL` 领域时领域角色＝归口角色，双签即归口角色**双人会签**（核心 §6 高危双人）。
- 输出 `ReviewRoutingDecision(ownerReviewerRole, domainReviewerRole, requiresDualSign, domain)`（record，角色用 `RoleCode`）。PR3 只产此决策记录，**不执行分派**（消费者＝P2-C 物化链 / AIK-STD-12 审核台）。

### 9.4 服务 / DTO / 控制器扩展

- `ProductionJobRequest` 加 `domain`（`KnowledgeDomain` `@NotNull`，**必填**——无任何资产类型隐含药学，领域须由生产方显式申报方能正确路由）；`createJob` 持久化 job.domain；`replayJob` 沿用原 job 领域。
- `submitCandidate`：落血缘行时存 `risk_level`（envelope 风险级）；返回值由 `String candidateRef` 升为 `CandidateSubmissionResponse(candidateRef, ReviewRoutingDecision)`（提交即返回路由，FR-6）。
- `listCandidates`/`GET /jobs/{jobCode}/candidates`：每条血缘行附 resolve 出的 `ReviewRoutingDecision`（只读计算，FR-5/6 可回溯）；新 DTO `ProductionCandidateView(血缘字段 + routing)`。
- 控制器**无新增端点**（submit/列候选既有端点，仅响应体扩展）。

### 9.5 配套登记

- 迁移基线：见 9.1（不升版本号，补 job.domain / candidate.risk_level 列）；域归属 engine-knowledge 不变（仅加列）；契约 `knowledge-production` 审计点不变（无新表/端点）；产品目录：控制器端点数不变（响应体扩展不计端点），`--check` 无漂移则不重生成，仍本地跑 `productCatalog.test.ts` 兜底。

### 9.6 FR-7 院内覆盖角色边界

FR-4 租户守卫（PR1 `guardPipelineOwnership`）已硬隔离：客户租户禁产平台主源、平台租户不产覆盖。PR3 增量＝**路由层一致性**：`TENANT_OVERLAY` 候选归口角色恒为 `KNOWLEDGE_GOVERNOR`（机构侧），**永不路由到 `PLATFORM_KNOWLEDGE_GOVERNOR`**；定向测试锁定（机构候选不会升格到平台归口）。**不新增权限码/机制**（避免与 FR-4 重复造轮子）。

### 9.7 FR/AC 映射（PR3）

- FR-6 ✅：候选按归属（管道→归口角色）+ 风险（HIGH→双签）+ 领域（domain→专科会签角色，药学经 `domain=PHARMACY`→药事安全人员）确定性路由；提交即返回 + 列候选可回溯。
- FR-7 ✅：路由器保证院内覆盖候选只路由机构侧角色，平台归口不可达。
- AC-3（FR-5 延伸）：候选血缘 + 路由决策可回溯（risk_level 持久 + 路由只读 resolve）。
- 仍 pending（PR4+）：FR-2 外部生产器实接（P6）、候选真实物化建 `ReviewAssignment`（AIK-STD-04/10）。

### 9.8 验证清单（PR3）

- TDD：`CandidateReviewRouter` 全分支矩阵（2 管道 × 5 领域 × 3 风险，含 FR-7 边界 + 药学→药事安全人员）；`submitCandidate` 存 risk_level + 返回路由；`createJob` 必填 domain 校验；`listCandidates` 附路由；控制器响应体；迁移基线（job.domain/candidate.risk_level 列）。
- 全量 `mvn test`（基线 2496 + 新增）+ 四门禁 changed + **五方言 Flyway smoke（原地改 V130/V131，含 Oracle/DM/Kingbase 真实容器须全绿）** + `git diff --check` + 前端 `productCatalog.test.ts`。

### 9.9 显式不做（PR3 边界 / YAGNI）

- 不建 `ReviewAssignment` 行（物化前不伪装已分派，待 P2-C）。
- 不存路由结论派生列（纯函数 resolve）。
- **不动 `VersionedAssetType`**：药学＝领域（`domain=PHARMACY`），非结构资产类型；说明书走 `KNOWLEDGE`、DDI 走 `RULE`，不另起 `DRUG_*` 类型（避免领域/结构混淆 + 与 domain 重复）。
- 不接外部模型生产器（P6 闸，PR4+）。
- 不做生产者工作台前端（承载于 AIK-STD-12）。
