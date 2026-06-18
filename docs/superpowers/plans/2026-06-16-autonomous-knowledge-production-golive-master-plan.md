# 自主公域知识生产 + AI 工厂收尾 + 整体上线 · 主计划

> **For agentic workers:** REQUIRED SUB-SKILL：本文件是**主计划（master plan）**，把"从当前到整体上线"的全部剩余工作分解为 12 个阶段、4 大执行块。**每个阶段是一个独立子计划**，执行时用 `superpowers:subagent-driven-development` 把该阶段展开为 bite-sized TDD 任务（写失败测试→验红→实现→验绿→提交）后实现。步骤用 checkbox（`- [ ]`）跟踪。
>
> **质量准绳（恒守）**：质量最高、体验最好；首发知识包目标 = **全医疗专业领域全量覆盖 + 全专科深度**（[_brief §10](../../cards/wave2/_brief.md)）。不降真实性标准、不交 B0 桩冒充完工。
>
> **开工先读**：[CONSTITUTION](../../CONSTITUTION.md) > [wave2 _brief](../../cards/wave2/_brief.md) > 本计划 > 对应卡 > 既有代码。

**Goal:** 把第二阶段从"模型可接入但被阻断"推进到"整体上线、自主从公域+院内持续生产真实医学知识、专家审核上线"的完整产品状态。

**Architecture:** 三条进料口（公域搜集 / 院内上传解析 / 人工维护）→ 共用「文档原件资料库存储层」→ 统一解析/候选/门禁/triage/影子/审核管线（单一候选池统一治理）→ 双形态物理隔离落库（平台主源 t-1 / 院内覆盖）→ 原子替换 → 知识包。全部建在既有地基上，禁重复造表/造控制器。

**Tech Stack:** Spring Boot + Java records + Spring Data JDBC + Flyway 五方言（h2/postgres/oracle/dm/kingbase）+ JUnit5/AssertJ/Mockito + React/Vitest + 产品级 CLI（node:test）+ MCP（node:test）+ 真实性/配置/迁移/中文注释门禁 + T-GATE。

---

## 0. 核心顺序铁律（先读这条，别再排反）

> **134 = 生产中心 = 生产知识的机器本身。** 首发知识包是这台机器**部署上线后"跑出来"的运营产物**，不是部署前的开发产物。

正确顺序 = **建机器（代码）→ 部署 134 上线生产中心 → 在 134 上跑出知识 → 总验收 + 试点医院上线**。全程**两次上线**：
- **第一次 = 生产中心(134)上线**（产出侧 / 外网）：部署机器 + 配真前置 + 超管翻 P6 → 134 具备自主生产能力。
- **第二次 = 试点医院上线**（临床侧 / 内网）：134 跑出的知识包 v1.0 经总验收后同步到试点医院运行侧。

KNOWGEN 内容产出**夹在两次上线之间**，是第一次上线之后、第二次上线之前的**运营产物**。

---

## 1. 约束分层（全程恒守 vs 本计划要消除）

> 给所有执行 AI 的**绑定契约**。动任何代码前先认这张表。

### 1.1 永久必控（不分上线前后，永远在，绝不让步）

| 控制 | 落点 / 为何永久 |
|---|---|
| **真实性（铁律 #1）** | 禁伪造医学事实/引文/置信度/评测结果/基准答案；候选必来自真实来源锚点、可溯源；`authenticity --mode=changed` 永绿 |
| **AI 只产候选不产事实（铁律 #5/#6）** | AI/抓取/模型全部只产 DRAFT 候选，入库走审核台 + 原子替换链（SYS-08），关系库唯一权威 |
| **专家审核上线** | 任何候选（公域/院内/人工）激活前必经审核台人工审核署名；AI 永不自动发布权威知识 |
| **临床安全红线** | OPT-04 红线（DDI/危急值/剂量/抗菌/特殊人群）生效；医师确认才进病历；高危近似禁批量自动确认 |
| **凭据安全** | 密钥永不入仓库/对话/日志；只存 `credential_ref` 引用 |
| **数据分级 D0–D5** | D5 禁入模型/CLI/MCP；D3/D4 字段级加密；出域最小化脱敏；公域生产中心**不接触任何患者数据** |
| **双形态物理隔离（核心 §9）** | 公域→平台主源 t-1；院内→院内覆盖；**院内禁反写平台主源**；运行侧只本地模型/B0/不出网 |
| **换模型/版本必重评** | provider 或 prompt/tool/model 三元组变更，必须重跑 PASSED 医学回归评测才放行 |
| **降级路径（GA 门禁 6 / DEGRADE-01）** | 模型/Dify/图/外网/MCP 任一断，主链路仍以 B0/本地/NOT_CONNECTED 诚实运行——**永久产品功能，必须实现** |
| **审计留痕** | 抓取/外调/生成/审核/替换/导出全留痕，合规审计独立只读可查 |

### 1.2 临时开发期姿态（本计划要逐项消除）

| 姿态 | 消除动作（哪阶段） |
|---|---|
| "B0 先跑、模型/外联后置"的**开发分期** | 取消分期，全部真实实现（模型增强 + 降级路径都建，见 1.3） |
| 候选只产 **B0 模板桩 / 逻辑留白**（AIK-STD-04 现状） | P5 用真实来源 + 模型增强生成真实候选内容 |
| **P6 = OFF（被阻断状态）** | P9 真前置备齐 → 超管在配置中心翻 `true` |
| **医学回归基准集为空** | P2 默认自带真实基准集（OPT-04 投影）+ 可维护更新 |
| **公域获取从没建** | P1+P4 建文档原件存储层 + 自主公域获取引擎 |
| **KNOWGEN 首发资产未产出** | P10 在 134 上运营产出，专家审核上线 |
| 前端 / AIK-STD-07/08 / X-DOMAIN 未做 | P3/P7/P8 收尾 |

### 1.3 "不需要 B0 先跑"的准确含义

- ✅ **取消** "先交 B0 桩、模型留到 P6" 的开发分期——一步到位实现真实功能。
- ⚠️ **不取消** 降级/本地路径作为**永久功能**：医院内网运行侧按设计无外部模型，模型不可用时全链必须仍可跑（GA 门禁 6）。"全部实现" = 模型增强路径 + 降级/本地路径，两条都真实建。

---

## 2. 既有地基清单（建其上，禁重复造表/造控制器）

> 落卡前核既有 infra，命中"别建重复表"已 ≥3 次。下列均已存在，**复用，不重建**。

**来源与资料（KNOW-01 / AIK-STD-02）**：`engine.knowledge.{SourceDocument, SourceVersion(已有 file_uri + content_hash), SourceFragment, SourceAuthorityLevel(A–E), SourceRegisterRequest, SourceReferenceResolver}`；解析 `engine.knowledge.parsing.{DocumentParser, StructuredText/Pdf/WordDocumentParser, DocumentSectionizer, ParsedDocumentMaterializer, DocumentParseOrchestrationService, DocumentParseController(documents:parse)}` + `mk_doc_parse_job`。

**探索（LLM-06，仅受控源、不开放全网）**：`engine.knowledge.discovery.{DiscoveryOrchestrationService, ControlledSourceSearchRepository, DiscoveryController}` + `mk_knowledge_discovery_run`。

**生产编排与候选管线**：`engine.knowledge.production.{KnowledgeProductionOrchestrationService, CandidateGenerationOrchestrationService, SourceCandidateGenerator, MaterializingCandidateIntake, MaterializationTarget, CandidateCoexistenceService, KnowledgeProductionReadinessService, KnowledgeProductionController}`；门禁 `production.gate.{CandidateGate, CandidateSafetyGateService, SourcePresentGate, AnchorCompleteGate, AuthorityLevelGate, ContentFormatGate, ReviewElementsGate, ApplicableScopeGate, SourceLicenseGate, ClinicalRedlineReadinessGate, AuthorityConflictGate}` + `mk_aik_gate_result`；分流 `production.triage.KnowledgeGenerationTriageService` + `mk_knowledge_generation_triage`；影子 `production.shadow.KnowledgeShadowEvaluationService` + `mk_knowledge_shadow_run`；模型生产器 `production.model.ModelKnowledgeProducer`。

**模型网关/评测/出域/provider/版本治理（X-LLM）**：`engine.llm.{ModelGatewayService, ModelGatewayController, ModelCapabilityPolicy, ModelFallbackMatrix, ModelVersionGovernanceService}` + `mk_llm_model_version_bundle`；评测 `engine.llm.eval.{MedicalRegressionCase, ModelEvalService, ModelEvalController, MedicalRegressionEvaluator}` + `mk_llm_regression_case`/`mk_llm_eval_run`；出域与数据最小化 `engine.llm.egress.{ModelEgressGovernanceService, ModelEgressGuard, ModelEgressWhitelist, ModelEgressController, DataMinimizationPolicyController}` + `mk_llm_egress_whitelist` OPT-09 策略字段；provider `engine.llm.provider.{ModelProviderConfig, ModelProviderHttpClient, RestClientModelProviderHttpClient, DeploymentFormService, ProviderType, DeploymentForm}`。

**知识版本/审核/替换（KNOW-02 / SYS-08 / MED-C3 / AIREVIEW-01）**：`KnowledgeVersionService`（reviewCandidate APPROVE/REJECT/RETURN、classifyCandidate、activate）+ SYS-08 原子替换 + MED-C3 旧版隔离；前端 `frontend/src/pages/quality/KnowledgeGovernance.tsx` + `shared/api/hooks.ts`。

**配置/安全/包/接入底座**：`shared.config.{SystemConfigService, SystemConfigSeeder, SystemConfigController}`；`engine.safety.ClinicalRedlineService`（OPT-04）；PKG-01 包发布；DATASVC-01 MCP 动态工具目录 + CLI + 引擎数据服务层。
**注意**：`shared.observability.{PayloadStoragePort, DbPayloadStorage}` 是 OBS-01 可观测性 payload（存进 DB）——**不是**文档原件存储，勿误用。

---

## 3. 目标架构（数据流）

```
[公域搜集]   [院内上传解析]   [人工维护]
  ①②             ④/③            ④
    \             |             /
     v            v            v
   ┌─────────────────────────────┐
   │ 文档原件资料库存储层 (P1 新建) │  原件→受管资料库后端(file/COS/OSS/S3/MinIO/HTTPS)→file_uri
   └─────────────────────────────┘  (按 scope 隔离: t-1 / 各租户)
                 |
                 v
   解析(AIK-STD-02) → 候选生成(AIK-STD-04) → 安全门禁(AIK-STD-05)
                 → 8态分流(AIK-STD-10) → 影子评测(AIK-STD-06)
                 → 审核台(AIREVIEW/AIK-STD-12) → 【专家审核署名】
                 → 原子替换(SYS-08/AIK-STD-09/11) → 知识包(PKG-01/AIK-STD-07)
                 |
        ┌────────┴─────────┐
        v                  v
  平台主源 t-1        院内覆盖(本租户)   ← 双形态物理隔离(核心§9/AIK-STD-13)
  (客户只读订阅)     (禁反写平台主源)
```

---

## 4. 执行顺序（4 大块 · 两次上线）

```
■ 第一大块 · 建工厂机器（代码，部署前在本地/CI 验证）
  P0 安全基线 → P1 原件存储层 → P2 上线就绪地基 → P3 工厂收尾
  → P4 自主公域获取引擎 → P5 模型增强 → P6 院内管道
  → P7 领域门面代码 + KNOWGEN 资产类型专用代码 → P8 前端体验
     (机器层部署前用 fixtures 预验：无模型可运行组/审核台/降级 = GA门禁3机制 + DEGRADE-01)

■ 第二大块 · 生产中心(134)上线 ← 第一次上线
  P9 部署机器到 134 + 配真前置(受管文献资料库根/provider/凭据/真跑PASSED评测/allowlist/版本三元组)
     + 超管翻 P6 → 134 生产中心 live，具备自主生产能力

■ 第三大块 · 在 134 上生产首发知识（运营，机器跑出来的）
  P10 自主公域获取 + 模型增强 → KNOWGEN-01~25 候选 → 专家审核上线 → 打包知识包 v1.0
     (持续批次：起步集 → 扩充 → 全量；每批同链不降标)

■ 第四大块 · 总验收 + 试点医院上线 ← 第二次上线
  P11 KNOWGEN-15 总验收 + GA门禁 3/8/10 实跑点亮 + 知识包同步到试点医院(运行侧/内网)
```

**依赖要点**：P1 是 P4/P6 文档进料的物理地基，必须最先；P2 基准集/配置是 P5/P9 前置；建机器块(P0–P8)全绿才 P9 部署；**P9 上线后才能 P10 产内容**（机器先就位）；P10 产出知识包后才能 P11 总验收 + 试点同步。

---

## 5. 各阶段方案（目标 / 复用 / 任务 / 验收）

> 任务为 FR 级 + 文件落点 + 测试意图 + 验收；执行 AI 用 subagent-driven-development 展开成 bite-sized TDD。每个 PR 必过 §6 门禁。

### 〈第一大块 · 建工厂机器〉

#### Phase 0 · 安全基线锁定（doc，0.5d）
- **目标**：把 §0 顺序铁律 + §1 约束分层固化为执行契约，写入 `_HANDOFF` 与 backlog，后续 PR 描述引用。
- [ ] T0.1 `docs/_HANDOFF.md` 顶部新增"整体上线主计划"工作线段（活跃分支/状态/下一步/契约链接）。
- [ ] T0.2 `docs/backlog.md` wave2 区标注各卡归属本计划哪个 Phase（不改 done/pending 口径）。

#### Phase 1 · 文档原件资料库存储层（地基，3d）
- **目标**：多进料口共用的原件资料库存储——原件落现场明确配置的受管资料库根 → `file_uri`，可取回/重解析/审计；按 scope 隔离；未配根诚实阻断，不偷偷回退到 tmp/工作目录。
- **复用**：`SourceVersion.file_uri`、`SystemConfigService.runtimeKnowledgeLiteratureMaterialRootUri()`、根 URI 校验。
- **新建包** `engine.knowledge.material`
  - [x] T1.1 `DocumentMaterialStoragePort`：`store(scope,bytes,contentType,sha256)->fileUri` / `fetch(fileUri)->bytes` / `exists/delete`；scope 决定路径前缀（t-1 / 租户 id）。
  - [x] T1.2 `ManagedDocumentMaterialStorage` 当前支持现场受管 `file://` 本地资料库；根未配/不可达/协议未接入→结构化阻断，不自动选择隐式本地路径，不写死对象存储。
  - [x] T1.3 `mk_knowledge_material_object` 账本（五方言 append-audited）：scope/file_uri/sha256/content_type/byte_size/stored_at/stored_by/source_channel。**已核无等价表并纳入 V3 基线**。
  - [x] T1.4 接 `DocumentParseOrchestrationService`：上传原件先 store→登记 `SourceVersion(file_uri,hash)`→解析；成功 job 可从 fetch 取原件创建重解析 job。
  - [x] T1.5 只读取回端点 `GET .../knowledge/materials/{ref}`（`knowledge.read`，审计下载）。
  - [ ] T1.6 按现场配置接入 COS/OSS/OBS/MinIO/HTTPS 网关等其他受管资料库后端；凭据走 `credential_ref`，未接入时继续诚实阻断。
- **验收**：原件存取 hash 一致 + scope 隔离 + 根未配阻断 + 解析接通。

#### Phase 2 · 上线就绪地基（3d）
- **目标**：默认自带**真实**医学回归基准集 + 可维护；门禁配置收编超管管理面；readiness 前端六态。
- **复用**：`mk_llm_regression_case`、`ModelEvalController/Service`、`ClinicalRedlineService`、`SystemConfigController`、`/readiness`。
  - [x] T2.1 `RegressionBaselineSeeder`：从 OPT-04 **已审红线库**投影首发回归用例（capability+input+expected_phrase+red_line_type+引用+citation=Y）；**禁凭空编题/编答案**，只投影已审内容。已实现 `rule.draft` ACTIVE 红线投影、题干去重、长 DSL 有界摘录且保留证据锚点。
  - [x] T2.2 基准集维护：确认/补 `ModelEvalController` 新增/启停/版本/批量导入真实题。已新增结构化 `source_reference` 五方言基线、列表/新增/批量导入/启停端点，真实来源引用必填且占位来源拒收。
  - [x] T2.3 配置中心管理面（前端）：文献库根 URI / 部署形态 / P6 / provider / 出域白名单 / 能力策略 归超管"安全基线与系统配置"页，默认关、高危二次确认、审计可见。已修正资料库根配置提示，明确受管本地磁盘、对象存储和 HTTPS 网关均为正式后端，不再暗示只能使用对象存储；系统配置变更仍要求高危确认与审计。
  - [x] T2.4 readiness 前端：生产中心页展示 9 闸逐项 PASS/BLOCK + 阻断原因 + 去配置去处；六态齐。已接 `/engine/knowledge-production/readiness`，把 LITERATURE_ROOT / DEPLOYMENT_FORM / MODEL_PROVIDER / REGRESSION_BASELINE / MODEL_EVALUATION / EGRESS_GOVERNANCE / MODEL_POLICY / VERSION_TRIPLE / P6_ACCEPTANCE 并入验收自检表，保留 loading/error/empty/forbidden/partial/正常视图。
- **验收**：自带真实基准集且可维护 + 6 项配置超管默认关可管 + readiness 前端逐项可见（注：`MODEL_EVALUATION` 仍要求真跑 PASSED，不被种子绕过）。

#### Phase 3 · AI 工厂收尾（5d）
  - [x] T3.1 **AIK-STD-05 深临床逻辑**：红线/剂量/高危**逐条命中**结构化 payload（接 `ClinicalRedlineService` 真实匹配）+ 冲突仲裁逐条留证；去重由 AIK-STD-10 生成期分流跳过重复入审，冲突/升级/降级接 AIK-STD-09 原子替换、影响任务和回滚链，当前切片已收口。
  - [x] T3.2 **AIK-STD-08 差异检测 + 过期治理**（新建）：`mk_knowledge_diff` + `mk_knowledge_expiry_task` 五方言；接 `DiscoveryOrchestrationService`；不自动替换只提候选；无更新诚实空。已实现 `KnowledgeDiffDetectionService`、`DiscoveryRequest.targetIdentityId` / 响应 `diffs[]`，来源废止建 `SOURCE_DEPRECATED` 任务，复审超期建 `REVIEW_OVERDUE` 任务；同指纹超期不落伪 `REVISED` 差异。
  - [x] T3.3 **AIK-STD-07 知识包生成 + 院内同步**（新建）：接 PKG-01，ACTIVE 资产打包→校验→灰度/全量→同步（无通道 NOT_SYNCED 不伪造）。已新增 `POST /api/v1/engine/pkg/packages/aik`、`mk_aik_pack_job` V141 五方言、manifest hash 证据；发布仍走 PKG-01，空 `adapterIds` 真实返回 `NOT_SYNCED` 且不触发发布端口。
  - [x] T3.4 **AIK-STD-03 术语勾卡**（已实质建成 TERM-01+`TerminologyCandidateGenerationJob`，本轮补前端生成入口、任务追踪、服务契约审计点并勾卡）。
  - [x] T3.5 **前端 Chunk7**：triage 8 态队列 + 影子展示 + 共存左右对照高亮 + Agent 进度可视/可中止 + 审后任务化提醒。
- **验收**：各卡 FR 真实勾全（无虚勾）；差异/过期不自动替换、诚实空；包无通道 NOT_SYNCED。

#### Phase 4 · 🌟 自主公域知识获取引擎（灵魂，6d）
- **目标**：生产中心**自主从公域权威源持续获取资料→落资料库→进统一管线→专家审核上线**；补 AIK-STD-14 Agent 取数工具。原设计预留槽位（生产器①②）补全。
- **复用**：P1 存储层、来源登记、解析管线、`ModelEgressGovernanceService`、`DeploymentFormService`（仅 PRODUCTION_CENTER 可外联）、`CandidateGenerationOrchestrationService`、DATASVC MCP/CLI。
- **新建包** `engine.knowledge.acquisition`
  - [x] T4.1 公域源 allowlist 治理 `mk_knowledge_acquisition_source`（域名/A–E 权威(OPT-07)/license/robots 策略/enabled/审批人/时点）五方言；启用必须审批，许可和 robots 策略不允许时运行阻断。配置中心管理面仍留 P8/P9 配置体验收口。
  - [x] T4.2 合规抓取器 `WebContentFetcher` + `RestWebContentFetcher`：本轮完成仅 PRODUCTION_CENTER + allowlisted HTTPS 域 + license/robots 策略裁决 + 真实 URL/时点/字节/sha256 留证；资料落 P1 解析链路，受管 `file://` 本地磁盘、对象存储或 HTTPS 网关均可承载。调度限速和更细出域审批证据留 T4.5/P5。
  - [x] T4.3 获取编排 `AcquisitionOrchestrationService`：手动触发→抓取→同 hash 去重→解析链路→`SourceVersion(file_uri,hash,authority,license)`；请求可携带显式 `generation` 计划，把新解析或重复复用的来源版本接入统一候选生成/审核池，不新造候选表、不绕门禁。
  - [x] T4.4 抓取账本 `mk_knowledge_acquisition_run`（域名/url/fetched_at/sha256/bytes/license/status/触发方式）五方言；合规审计可查。
  - [x] T4.5 自主调度：V143 为公域来源加入调度开关、间隔、下次/上次检查、默认格式和候选生成计划 JSON；`AcquisitionScheduleScheduler` 动态读取配置中心间隔，`AcquisitionScheduleWorker` 原子推进到期来源并按租户提交 SYS-05 `KNOWLEDGE_ACQUISITION_DISCOVERY` 批任务；handler 调 `runScheduled`，失败项走 SYS-05 重试/死信证据，不另造队列。
  - [x] T4.6 **AIK-STD-14 Agent 取数工具**：DATASVC 新增 `fetchPublicMaterial` 受控工具（D1 / `knowledge.write`），只把 `AgentPublicMaterialFetchPayload` 转入既有 `AcquisitionOrchestrationService.run`；MCP 通过动态工具目录暴露，CLI 接 `agent fetch-public-material`；D3/D4/D5 结构化拒绝，产候选不产事实。
  - [x] T4.7 端点：`POST .../knowledge/acquisition/runs`（手动触发，write）+ `GET .../knowledge/acquisition/{sources,runs}`（read）。
- **验收**：自主抓 allowlisted 公域→资料库→候选入审核链；形态/出域/license/robots 合规留证；Agent 工具越权（患者数据/D5/非公域）拒；全程 AI 只产候选、专家审核、不臆造来源。

#### Phase 5 · 模型增强全实现（X-LLM 收口，5d）
  - [x] T5.1 **LLM-01** 固化 provider 无关网关契约，修正"未接 provider"陈旧口径；B0 空候选不写死医学事实。已补 `model_capability_policy` 作用域化 clean baseline、当前组织链继承解析、readiness 同源策略解析和前端策略来源证据列。
  - [x] T5.2 **LLM-02** provider 缺位/断连/限流/结构化失败/出域阻断→B0 降级矩阵验收（接 `ModelFallbackMatrix`）。已补 `fallback_order_json`/`timeout_ms`/`rate_limit_per_minute` clean baseline、发布前顺序校验、B2→B1→B0 逐级尝试、provider HTTP 超时预算、运行时 provider 调用限流和前端降级顺序证据列。
  - [x] T5.3 **LLM-04** prompt/tool/model 版本包 + 三元组绑定 + 重放/回滚/导出（只出 hash）；模型候选必带真实三元组。已完成 `mk_llm_model_version_bundle` V139 clean baseline、任务 `tool_version` 绑定、provider 成功任务真实 prompt/tool/model 三元组记录、B0 脱敏摘要重放、历史版本回滚、hash-only 导出，以及服务层发布前载荷校验，避免绕过 Controller 写入空版本或正文空 hash。
  - [x] T5.4 **OPT-06** AI 质量评测中心（字典/规则/路径/推荐/解释/术语回归集 + 幻觉拦截）。已复用 LLM-07 同平台扩展 V126 clean baseline：`case_domain`、术语期望、禁用断言、最低分、质量/术语分、幻觉标记、case summary 和 prompt/tool/model 版本趋势；新增 `/api/v1/ai-eval/runs`、`/api/v1/ai-eval/trends`，支持离线 B0 输出或真实 provider 输出评测；真实领域题库只允许后续由真实来源导入，不预置伪医学题。
  - [x] T5.5 **OPT-09** 数据最小化策略引擎（字段白名单+脱敏+审批）。已补 V144 五方言策略字段（脱敏规则、审批阈值、不可关闭护栏）、`/api/v1/data-minimization/policies/model-egress/*` 正式入口、字段级 `MASK_ALL/MASK/GENERALIZE/NULLIFY/NONE` 脱敏算子、可配置审批阈值与 LLM-03 统一消费；策略缺失仍按最严阻断并降级 B0。
  - [x] T5.6 **API-12** 模型能力网关 API 收口勾卡。已补前端共享 API 契约 `toolVersion` 与 B0 replay hook，确认 `status/catalog/tasks/retry/replay/policies` 端点、权限和三元组口径一致；新增任务查询/重试跨租户拒绝证据，API-12 与 LLM-01 backlog 收口为 done。
  - [x] T5.7 **候选真实化**：`SourceCandidateGenerator`/`ModelKnowledgeProducer` 用真实锚点 + 模型增强填充逻辑字段（带 AI 标识+锚点+hash+三元组），替换 B0 留白；readiness 未过/provider 失败/schema 不合格→诚实降级不产伪候选。已修正模型候选 payload 不落生产提示正文、仅留 `promptInputHash`；B2→B1 真实本地模型成功不再被误判为 B0 跳过，并随候选记录 fallback 证据。
  - [x] T5.8 **降级路径实现 + 验收**：模型 off→B0；运行侧只本地/B0；六态齐（DEGRADE-01 预验）。已补非成功 B2/B1 模型任务的诚实 `status/mode` 跳过原因，避免误写为 B0；知识生产中心对候选血缘/门禁/8 态/影子/共存下游 evidence 局部失败显示分项告警，不以空表掩盖断连。
- **验收**：配齐前置后模型真产候选进统一链 + 缺任一前置结构化阻断 + 降级矩阵全绿 + 候选无伪造。

#### Phase 6 · 院内覆盖管道全实现（4d）
  - [x] T6.1 院内上传增强：`DocumentParseController` 上传接 P1 存储层（原件落本租户 scope）；候选归院内覆盖。已新增 multipart `documents:upload-parse`，上传原件复用 P1 资料库存储与解析，`scopeKey=tenantId`；上传后的生成计划不暴露平台主源管道，后端固定转为 `TENANT_OVERLAY` 后进入统一候选生成/门禁/分流/影子/审核链。
  - [x] T6.2 本地模型生产器（生产器③）：`ModelKnowledgeProducer` 本地 provider 路径（Ollama/国产化不出网），归院内覆盖，运行侧可用。已补 `ModelTaskRequest.requiredRouteStrategy/providerCode` 内部约束，`LOCAL_MODEL` job 强制 `TENANT_OVERLAY` 后才进入 readiness；网关在 provider 解析前校验必需路由并按指定本地 provider 解析，策略漂移时拒绝越界调用、不落任务、不外调。
  - [x] T6.3 双形态隔离强化测试：院内候选禁反写 t-1；客户对主源只读；复核 AIK-STD-13 FR-4/FR-7。已补平台主源提交双向测试：客户候选进入平台主源管道返回 `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`，平台候选进入平台主源保持 `t-1` 归属并路由平台知识治理员；补客户租户不能把平台 identityId 当写入目标提交候选的只读证据。
  - [x] T6.4 **DATASVC-01 字段级加密收口**：D3/D4 字段级加密落地 + AC 收口；MCP/CLI 不绕治理复核。已补 V145 字段级加密密文表与字段策略表、独立字段密钥解析、SM4 密文/SM3 检索 hash、审计凭证与 D3/D4 `requiresFieldEncryption` 策略；Agent 写工具仍拒绝 D3/D4/D5，CLI/MCP 测试确认不绕治理。
- **验收**：院内上传→覆盖候选全链 + 本地模型不出网 + 隔离硬保证 + D3/D4 加密。

#### Phase 7 · 领域门面代码 + KNOWGEN 资产类型专用代码（6d）
- **目标**：建领域门面（代码组合）+ KNOWGEN 中需**代码支撑**的部分（计算器/模板/规则结构），为 P10 内容产出备好机器。**注意：这里只建代码，不产内容**。
  - [x] T7.1 **领域门面 X-DOMAIN 17 卡代码**（[NURSING-01](../../cards/wave2/NURSING-01.md)/REPORT/POC-KNOW/PHARMACY/CRITICAL/SPECIAL-POP/PERIOP/ONCO-RENAL/ALLIED-CARE/TCM-HEALTH/INFECTION-PH/PRIMARY-CARE/REGION-COLLAB/SPECIALTY-EXT/RWD + SVC-DOMAIN-01/02）：规则+路径+知识+CDSS+嵌入+评估+随访 领域组合，**复用同一引擎链路不另起业务实现**。已补 17 卡只读组合目录 API、权限、服务契约、产品目录与 B0 fixture 证据 API：逐门面证明共享处理器/确定性路由可解析、模型非必需、不预填医学内容、服务包成员可解析，扩展专科缺真实资产时诚实空态。
  - [x] T7.2 **KNOWGEN 资产类型专用代码**：KNOWGEN-16 评分量表/计算器**算法物理化可复算**、KNOWGEN-04/18/20 规则结构+测试病例骨架、KNOWGEN-19 PGx 剂量结构、各类专用模板（复用 AIK-STD-12 全专业模板，缺则补）。**不预填医学内容**（内容 P10 产）。已补 `FORMULA` 模板、RULE 测试病例结构、KNOWGEN-16/04/18/20/19 专用骨架目录、payload 结构校验器与传入公式定义驱动的确定性计算服务；只提供生成/校验/计算代码骨架，不内置医学常量。
- **验收**：各领域门面 B0 主链路 E2E（用 fixtures）；KNOWGEN 各资产类型有可运行的生成/校验/计算代码骨架。

#### Phase 8 · 前端体验完美化（贯穿，定稿）
- **目标**：体验契约 E1–E9 全过（[_brief §5](../../cards/wave2/_brief.md)）；生产中心全页齐。
  - [x] T8.1 菜单 IA 双产品面（生产侧"知识生产"一级域，临床客户不可见）。已把审核台、机构知识、知识生产拆为独立入口；模型能力迁入知识生产域；后端菜单权限、五方言 V146、14 角色菜单快照、产品目录和 IA 矩阵同步。
  - [x] T8.2 生产者工作台（下任务→看进度→审候选→批处置；队列+左右对照+影响+结论）。已补 `useCreateKnowledgeProductionJob`，知识生产入口新增生产者工作台表单、候选队列单选、左右对照随候选切换、结论区批处置预案；高风险/双签候选批量通过明确锁定，最终通过/退修/驳回仍归审核台。
  - [x] T8.3 双形态一眼可辨（主源 vs 院内覆盖 颜色/徽标/分区）。已补平台主源/院内覆盖元数据与常驻分区卡：机构知识页空数据态也显示“平台主源只读 / 院内覆盖可治理”，生产中心按平台主源只读发布账本与院内覆盖本机构治理分区展示 job 管道、审计溯源和候选证据；同类扫描确认知识审核台未再承载机构维护或生产工作台，跨域 `/clinical/followup` 模板治理混放保持后续拆分项。
  - [x] T8.4 可信解释贯穿（每条 AI 产物标 AI生成/来源锚点/版本/模型模式/置信降级）。已补候选生产血缘 `explain_json` 最小化解释元数据：提交候选只白名单保存模型任务 ID、模型模式、prompt/tool/model 版本、来源引用、置信和降级原因，不保存提示词原文或候选正文；溯源接口和审核台候选来源列/AI 生产来源抽屉贯穿展示 AI 标识、模型模式、版本、来源引用、置信和降级状态，坏 JSON 诚实降级为空解释；V147 五方言和迁移契约同步。
  - [x] T8.5 反馈回流闭环（采纳/不采纳/误报/空白→新候选或治理任务）。已补 `mk_knowledge_review_assignment.feedback_type/followup_action` V148 五方言与 CHECK：审核台仅登记结构化反馈和建议后续动作，不直接生产新知识；服务端为历史调用兜底映射 `APPROVE→ACCEPTED/NONE`、`RETURN→CONTENT_GAP/CREATE_REVISION_CANDIDATE`、`REJECT→NOT_ADOPTED/ARCHIVE_REJECTED`，并支持 `SOURCE_BLANK/REQUEST_SOURCE_EVIDENCE`、`FALSE_POSITIVE/MARK_FALSE_POSITIVE`；前端审核抽屉提交结构化反馈。同步审计：知识域入口已拆出审核台、机构知识、诊断知识维护和知识生产；剩余代码层多模式组件与跨域 `/clinical/followup` 运行/模板治理混放列入后续 IA 拆分，不并入本次知识生产链路提交。
  - [x] T8.6 降级六态 + 医院语言文案（不暴露黑话）+ 老年≥16pt + 5 主题 + 国产浏览器 + 移动端 + 技术对象专家模式默认折叠。知识审核 AI 溯源普通模式只展示医院语言，技术字段仅在授权专家模式显示；共享六态错误、部分成功、异步导出和工作台等入口统一脱敏原始接口/连接/异常文本。规则/路径 L3 DSL 与全局专家模式语义分离，条件层级和窄屏边界清晰。前端 lint 固化零 warning，全量生产 CSS 纳入 token/固定 px 门禁；elder 实测 `--ant-font-size >= 21.333px`、`--ant-font-size-sm >= 20px`，表单实际字号不低于 16pt。新增只读浏览器能力预检，按 Web 能力给出通过/警告/失败，不以 User-Agent 冒充国产认证，报告不含凭据、令牌或患者数据；5 主题、390px 无根节点横向溢出、Chromium 与“国产 Chromium 内核仿真（非现场认证）”自动化均已通过。真实目标国产浏览器现场确认保留在 P9 部署验收，不作为软件门禁的伪造证据。
- **验收**：体验门禁通过；INFRA-07 全页可打开 E2E（机器层，部署前）。

### 〈第二大块 · 生产中心(134)上线〉

#### Phase 9 · 生产中心(134)上线（第一次上线 · ops+治理，非纯代码）
> 代码无法伪造的真实外部前置——这一步 = "生产中心上线"本身。用户已明确按全新项目发布：发布前停服务并清空数据库、旧制品和旧运行数据，从最新迁移基线全新初始化，不迁就历史数据、不保留旧包袱，发布留痕只记录清库初始化和新版本证据。
  - [x] T9.0 清库发布准备：已在 134 完成“备份→隔离恢复验证→停服→重建空库→清旧运行物/旧备份→显式候选发布→独立验收”。数据库从空库迁移至 V148 / 206 张 public 基表，知识身份、版本、包、生产任务、候选和公域获取运行数据均为 0；两条平台用户/角色记录来自 V25/V96 clean baseline，非历史回灌。运行 `8ef5103d6227d371baed829a48f5dda9774071ce` 的 manifest、候选/运行 JAR SHA-256、HTTP/HTTPS readiness、bootstrap 未接管状态和数据库 owner 已一致留证。发布脚本同时补生产密钥独立预检，避免清库后才发现启动配置缺口；T9.7 最新本地提交重发仍单独保持未完成。
  - [x] T9.1 运维准备受管资料库后端（现场本地磁盘、COS/OSS/MinIO 或 HTTPS 网关）→ 配 `文献库根 URI`（受管、含 `/platform-knowledge/t-1/literature-materials/`、非 tmp/非明文 HTTP）。134 已采用受管本地磁盘，目录权限/服务用户写入探针、配置中心高危变更审计和 readiness 均通过。
  - [x] T9.2 集成运维员配真 provider（Claude/OpenAI 协议型/Ollama）+ 凭据（`credential_ref`）+ 健康检查 HEALTHY。134 已完成固定版本 Ollama `0.30.9`、回环监听、受管模型目录和确定性 `medkernel-qwen25:1.5b-v1`，应用内 `ollama-qwen25-15b` 为 HEALTHY；外部 OpenAI 协议 provider `external-mimo-v25` 通过服务器真实 TLS、模型目录和 3 次补全验证，凭据只以环境变量引用，应用健康状态为 HEALTHY。两者均按医学闸门保持停用，待 T9.3 独立专家签署后才能启用。
  - [ ] T9.3 质量医保治理员复核真实医学基准集 + 专家签字；**实跑一次 PASSED 医学回归评测**（精确覆盖当前启用题）。134 已从 WHO 2024 慢性乙肝指南导入 1 条来源化高风险用例；旧运行 `1`、`2` 的逐例证据数为 0，V151 已明确判定 `evidenceComplete=false`、`reviewable=false`。V151 发布后新运行 `3`（本地）与 `4`（外部）均真实得到 1/1 和 `PENDING_REVIEW`，各保存 1 条逐例不可变证据，且 `evidenceComplete=true`、`baselineCurrent=true`、`reviewable=true`；两 provider 仍为 HEALTHY、停用。剩余步骤只能由真实独立医学专家在专用复核页逐例核对输入、期望、模型输出、来源引用和红线结果后留意见签字；自动化不得冒充，因此本项保持未完成。
  - [x] T9.4 平台治理管理员配 部署形态=PRODUCTION_CENTER + 出域白名单 + 能力策略 + prompt/tool/model 版本三元组。134 已配置只允许 `prompt` 的 MEDIUM 出域策略、`MASK_ALL` 脱敏与锁定护栏；`rule.draft` 使用 `EXTERNAL_MODEL → LOCAL_MODEL → BASELINE`、60 秒超时和每分钟 10 次限流；ACTIVE 版本包 `2` 精确绑定 `mimo-v2.5` 及 64 位 prompt/tool/model hash。V150 关系库 CHECK + UNIQUE 已真实迁移并复核。
  - [x] T9.5 公域源 allowlist 起步集审批生效（§7 确认后）。WHO IRIS `10665/376353` 官方 PDF、许可、robots 与 SHA-256 已核验；知识治理员登记停用草稿，同人审批负测 403，平台治理管理员经 MFA 独立审批。停用抓取返回结构化 `BLOCKED` 且无获取副作用，重新编辑后再次独立审批，来源 `WHO-CHB-GUIDELINE-2024` 当前已启用生效。
  - [ ] T9.6 **超管在配置中心翻 `medkernel.knowledge.production.p6-independent-acceptance = true`**（上线放行，高危二次确认 + 审计）。
  - [x] T9.7 按全新项目发布流程部署最新版到 134（`mk-publish.sh --source` 当前 HEAD 全哈希并强制从干净工作树重建；不得从旧库回灌历史业务数据，不保留旧部署过渡路径）。2026-06-18 17:52 从干净工作树发布完整提交 `09306b0531309bee48978dab09c02f649d3482e6`；manifest 的 source/commit 均与该全哈希一致，本地与运行 JAR SHA-256 均为 `67ae7820448d8d50c76c230d4c99da70fe46b68685f89d4b95758780b2c1505d`。启动日志与关系库均确认 Flyway V151；HTTP/HTTPS readiness `UP`，服务 active/enabled，`NRestarts=0`。
  - [ ] T9.8 验证 `/readiness` 9 闸全绿 → 跑一条真实自主获取→候选→审核→激活小样本闭环留证。
- **验收**：readiness 全绿 + 一条真实知识端到端上线留证 + 清库初始化记录 + 134 运行 manifest 哈希一致。

### 〈第三大块 · 在 134 上生产首发知识〉

#### Phase 10 · 首发知识资产真产出（运营，在 live 的 134 上跑出来）
- **目标**：用 P4 公域引擎 + P5 模型增强**自主获取并生产** KNOWGEN-01~25 候选 → 专家审核上线，达"全景分类全覆盖 + 全专科深度"。**起步集 v1.0 → 持续扩充 → 全量**，每批同链不降标。**这是运营产物，发生在 P9 之后**。
  - [ ] T10.x 逐资产域跑产线：KNOWGEN-01 术语 / 02 药品说明书 / 03 指南 / 04 临床规则(带测试病例) / 05 专病路径 / 06 CDSS / 07 评估指标 / 08 随访 / 09 护理 / 10 医技报告 / 11 床旁 / 12 中医药 / 13 医保病案 / 14 公卫院感 / 16 评分量表 / 17 鉴别诊断 / 18 检查检验适当性 / 19 特殊人群剂量+PGx / 20 18项核心制度 / 21 罕见病 / 22 急救生命支持 / 23 围术期输血 / 24 患教知情同意 / 25 证据分级。
- **铁律**：内容必来自真实抓取/权威来源 + 专家审核署名，**绝不 AI 编造医学事实**；高危（剂量/输血/配型）双签。
- **验收**：各资产域候选真实产出 + 每条可溯源 + 专家审核上线 + 红线生效。

### 〈第四大块 · 总验收 + 试点医院上线〉

#### Phase 11 · 总验收 + GA 门禁 + 试点医院上线（第二次上线）
  - [ ] T11.1 **KNOWGEN-15 总验收**：24 类合并"试点医院首发知识包 v1.0"；A1–A9 通过；OPT-04 红线生效；PKG-01 可同步。
  - [ ] T11.2 **GA 总验收卡**：QA-01 引擎 E2E / QA-02 五方言+性能+备份 / QA-03 医疗安全 / QA-04 无模型/无Dify/无图 B0 / QA-05 上线评审 / QA-06 产品体验 / QA-07 代码净化 / QA-08 第三方对接 / DEGRADE-01 / SYS-07 / INFRA-07 / INFRA-10。
  - [ ] T11.3 **GA 门禁 3/8/10 实跑点亮**：3（AI 工厂 AIK-STD-01~14 + DATASVC-01 通过、审核台真实可审可发、CLI/MCP 不绕治理）/ 8（15 领域门面）/ 10（KNOWGEN-15 首发包 A1–A9 + 同步试点）。
  - [ ] T11.4 **试点医院上线**：知识包 v1.0 同步到试点医院运行侧（内网，PKG-01 灰度→全量）；运行侧只本地模型/B0；医师确认才进病历；反馈回流。
- **验收**：GA 门禁 3/8/10 真实点亮 + 知识包同步试点留证 + 运行侧 B0 主链路真实可跑。

---

## 6. 每个 PR 必过门禁（执行 AI 推送前自检）

- [ ] 后端全量 `cd medkernel-backend && MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`（0 fail 0 error）。
- [ ] 新增迁移表/控制器：`MigrationBaselineContractTest`（REQUIRED_INDEXES=idx / COMMON_CONSTRAINTS=uk·ck **别混集** + 2 处 `LATEST_MIGRATION_VERSION`）+ `DomainOwnershipCatalog` 登记 + `ServiceContractCatalog` 登记新控制器。
- [ ] `ModuleBoundaryArchTest`（engine/shared 不得依赖 compliance；跨域复用抽象放 shared）。
- [ ] 五方言 Flyway smoke 真实容器（h2/postgres/oracle/dm/kingbase）。
- [ ] 四门禁 `--mode=changed`：真实性 / 配置 / 迁移 / 中文注释（**多行 Javadoc，单行 fail**；禁 模拟/仿真/演示/占位/placeholder）。
- [ ] 改控制器/route/menu：重生成 `product-function-catalog` + 前端 `productCatalog.test.ts`。
- [ ] 改角色 MENU 白名单：同步 `PermissionDimensionModelTest` + `DefaultPermissionPolicyTest` 两处断言。
- [ ] 前端 `cd frontend && npm run verify`（vitest + tsc + eslint + **Prettier format:check**）。
- [ ] `git diff --check && git diff --cached --check` 干净。

---

## 7. 决策点（开工前需用户确认）

1. **公域源 allowlist 起步集**：建议起步 = 国家级官方（卫健委/药监局 NMPA/疾控）+ 主要学会指南 + 标准术语机构 + 公开说明书库；国际源（PubMed/WHO）按合规口径。**新增域名一律超管审批**。
2. **公域内容许可/版权口径**：建议保守——原件内部存供锚点抽取+审计，对外只呈现引用/锚点+溯源（不整文转载），每源记 license，`SourceLicenseGate` 强制。需用户/法务确认。
3. **境外大模型数据出境合规**：生产中心只吃公开资料不碰患者数据（满足无个人数据出境）；生成式 AI 备案登记口径需合规审计员确认（O13）。

---

## 8. 执行交接

- **分支**：每 Phase 独立分支（如 `codex/golive-p1-material-storage`），从最新 `origin/main` 起；Phase 内多 PR 增量；合并 main 逐 PR 授权。
- **执行方式**：每 Phase 用 `superpowers:subagent-driven-development` 展开 bite-sized TDD + 每任务两段式 review。
- **收尾**：每 Phase 完更新 `_HANDOFF` 对应工作线；过 §6 门禁 + owner≠reviewer 签字。
- **防中断**：只用透明工程手段（本地分支/小提交/计划/接力/验证证据）。

---

## 9. Self-Review（spec 覆盖核对）

- ✅ **顺序修正**：134 部署(P9) 在 KNOWGEN 内容产出(P10) **之前**；两次上线（P9 生产中心 / P11 试点医院）。
- ✅ wave2 全 68 卡映射：X-LLM(P5)、X-AIK(P3/P4/P6)、DATASVC(P6)、X-DOMAIN(P7)、X-KNOWGEN 代码(P7)/内容(P10)、GA(P11)。
- ✅ 灵魂三新地基：文档原件存储(P1)、公域获取引擎(P4)、AIK-STD-14 取数工具(P4.6)。
- ✅ 三进料口 + 单一候选池 + 双形态隔离：架构 §3 + P1/P4/P6。
- ✅ 永久必控全程恒守 + 临时姿态逐项消除：§1。
- ✅ KNOWGEN 拆分：资产类型代码(P7，部署前) vs 内容产出(P10，部署后运营)。
- ⏳ 决策点 3 项待用户确认（§7）后，各 Phase 由执行 AI 展开 bite-sized 实现。
