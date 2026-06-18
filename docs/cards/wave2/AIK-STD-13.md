# AIK-STD-13 · 知识生产编排与多生产器双形态接入

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)（§1 双产品面/§2 双形态四生产器）。
> 迁移来源：wave2 域简报 §第二阶段设计校正（2026-06-13 用户决策）· 核心 §7 唯一权威知识/平台源不可污染 · §9 客户覆盖只归本租户 · 铁律 #1/#4/#5。

## 身份
- 卡 ID：AIK-STD-13（= backlog `AIK-STD-13`）
- 域：wave2（X-AIK）
- 关联场景：S3 AI 知识工厂、S15
- 依赖卡：[LLM-01](LLM-01.md)/[LLM-08](LLM-08.md)（模型网关/provider）· [DATASVC-01](DATASVC-01.md)（MCP/CLI 底座）· [AIK-STD-01](AIK-STD-01.md)（资产 schema）· [AIK-STD-12](AIK-STD-12.md)（审核台）· [KNOW-01](../D2/KNOW-01.md)/[KNOW-02](../D2/KNOW-02.md)（料源/版本）· [SYS-08](../D2/SYS-08.md)（权威替换）
- 工作量：6d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
建**统一知识生产编排层**：不管候选来自哪个生产器（① API 大模型自主 / ② Agent 工具协助 / ③ 本地大模型 / ④ 人工录入），都进**同一候选池、走同一安全流水线**；并落**双形态生产的物理隔离**——外网平台主源管道（产物归 `t-1`）与内网院内覆盖管道（产物只归本客户租户、禁与平台主资产混、禁反写主源）。编排层负责生产任务（job）调度、生产器路由、血缘/审计/可重放，是「AI 只产候选不产事实」红线的归口。

## 现状（核查 2026-06-13 → PR1 实现 2026-06-15）
**全新建**：早期 AIK 体系只假设 Dify/Ollama 模型推理，无「统一编排层 + 多生产器 + 双形态隔离」概念（域简报早期口径校正点 1/2）。上游已具备：模型网关 B0 空壳（[LLM-01](LLM-01.md)）、料源引擎（[KNOW-01](../D2/KNOW-01.md) 已 done）、平台/客户覆盖模型（`docs/superpowers/specs/2026-06-02-platform-tenant-overlay-design.md`）+ `PlatformTenant.ID="t-1"`/`isPlatformTenant()` 守卫基座 + 既有候选审核链（`KnowledgeVersionService`/`CandidateClassification`/`ReviewAssignment`）。本卡＝建编排 + 生产器接入框架 + 双管道隔离，**不另起资产表**（候选走既有版本/审核/替换链）。设计 [`docs/superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md`](../../superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md)。

## 实现进度（PR1 = 编排核心，分支 `claude/wave2-p2b-aikstd13-production-orchestration`）
新包 `com.medkernel.engine.knowledge.production`（归 engine-knowledge 域）：
- `KnowledgeProductionOrchestrationService`：建 job（FR-1 骨架）+ **FR-4 双形态物理隔离守卫**（PLATFORM_SOURCE 仅 `t-1` 平台租户 / TENANT_OVERLAY 仅客户租户 / 覆盖候选禁反写 t-1 主源 → `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`〔ENG-KNOW-005〕）+ 提交候选（FR-3：经 AIK-STD-01 校验闸 + 隔离 + 资产类型/租户一致校验 + 血缘审计 + 计数）。
- `mk_knowledge_production_job` 表 V130 五方言（job/生产器/目标管道/状态/血缘/trace，mutable-audited）+ 枚举 `TargetPipeline`/`KnowledgeProducer`/`ProductionJobStatus`。
- `KnowledgeCandidateIntake` 端口 + PR1 默认 `StagingCandidateIntake`（暂存桩，候选物化随解析管道 AIK-STD-04/10 接线，**不造平行候选表、不伪装已物化**）。
- `KnowledgeProductionController`（建 job/列 job/查 job/提交候选，`knowledge.write`/`read`）。契约 `knowledge-production` + 域归属登记。

**PR2（生命周期 + 候选血缘，分支 `claude/wave2-p2b-aikstd13-pr2-lifecycle-lineage`）**：
- **FR-5 候选生产血缘**：新表 `mk_knowledge_production_candidate` V131 五方言（append-only，每条提交候选落一行回溯 job/身份/指纹/候选引用/时点）；`submitCandidate` 落血缘行；`GET /jobs/{code}/candidates` 列血缘（**非资产存储**，候选物化仍走既有链）。
- **FR-1 job 生命周期 + FR-5 可重放**：`completeJob`（PENDING/RUNNING→COMPLETED）、`cancelJob`（→CANCELLED，终态结构化 409 拒）、`replayJob`（复制 job 定义建新 PENDING job，lineage 记 replayedFrom，隔离守卫复用建 job 路径）；控制器加 `complete`/`cancel`/`replay` 端点。
**PR3（候选会签路由 + 院内覆盖角色边界，分支 `claude/wave2-p2b-aikstd13-pr3-review-routing`）**：
- **FR-6 候选会签路由**：新枚举 `KnowledgeDomain`（临床/药学/术语报告/评估医保/通用）+ `CandidateReviewRouter`（@Service，**纯确定性 B0** `resolve(管道,领域,风险)`）+ `ReviewRoutingDecision`（归口角色 + 领域会签角色 + 是否双签 + 领域）。归口按管道（平台主源→平台知识治理员 / 院内覆盖→机构知识治理员）；领域会签按 domain（临床→临床治理负责人、**药学→药事安全人员**、术语报告→医技协同人员、评估医保→质量与医保治理员、通用→同归口）；HIGH→双签。`submitCandidate` 提交即返回 `CandidateSubmissionResponse(候选引用+路由)`；`listCandidates` 每条血缘附 `ProductionCandidateView(血缘+路由)`（FR-5/6 可回溯，只读 resolve 不存派生列）。
- **药学＝领域非资产类型**：原地改 V130 加 `domain VARCHAR(24) NOT NULL`+CHECK（5 方言）、V131 加 `risk_level VARCHAR(16) NOT NULL`+CHECK（5 方言），**不新建 V132、不动 `VersionedAssetType`**（说明书走 `KNOWLEDGE`、DDI 走 `RULE`，经 domain 区分领域）；job `domain` 应用层 `@NotNull` 必填（须显式申报方能正确路由）。
- **FR-7 院内覆盖角色边界**：路由器保证 `TENANT_OVERLAY` 候选归口恒为机构知识治理员，**永不路由平台归口**（定向测试锁定）；叠加 PR1 `guardPipelineOwnership` 硬隔离，**不新增权限码/不建 `ReviewAssignment`**（物化前不伪装已分派，待 P2-C）。
**PR4（候选真实物化入版本/审核链，分支 `claude/wave2-p2b-aikstd13-pr4-candidate-materialization`）**：
- **候选真实物化**（替换 PR1 暂存桩，使能 FR-3 统一流水线 + FR-5 血缘）：`MaterializingCandidateIntake` 替换 `StagingCandidateIntake`——`SourceReferenceResolver` 解析受控源串引用 `源编码:版本:锚点`→源 FK（**B0 解析不出诚实拒收**）+ `MaterializationTarget`（现有身份 **异或** 新建身份壳 `NewIdentitySpec`，二选一校验，新建 find-or-create + ACTIVE 保守默认）→ 信封经 `KnowledgeVersionService.classifyCandidate` 真实落版本（`PENDING_REPLACEMENT_REVIEW` 待审）/ `CandidateClassification` + **据 PR3 路由建真 `ReviewAssignment`**（归口 ∪ 领域，`LinkedHashSet` 去重）；`classifyCandidate` 接 `ReviewAssignmentPlan`（null 零回归）。
- **服务端编排合成诚实 API-03 上下文**：编排无 HTTP 入参，`KnowledgeApiContext.validateTenant` 非空闸 → 合成 request_id=`kpm:uuid`、user_id=会话 actor、**package_version=job 编码（真实溯源）**、role_codes=PR3 归口治理角色。真实 H2 端到端集成测试 `CandidateMaterializationIntegrationTest` 锁定（候选→版本/分类/多角色分派全链）。仅覆盖可解析受控源（discovery-origin）。
**PR5（模型生成 readiness 闸，分支 `codex/wave2-knowledge-model-readiness`）**：
- 新增 `KnowledgeProductionReadinessService`：正式模型生成知识前只聚合真实前置事实，不调用模型、不造候选；缺文献资料库根地址、部署形态不符、provider 缺失/类型无效/不健康、医学回归基准集为空、**当前能力码与当前完整基准集**的 provider/模型版本未通过评测、外部出域白名单缺失或策略不可执行、能力策略不匹配、当前能力唯一 ACTIVE prompt/tool/model 版本包缺失或与 provider 模型版本不一致、P6 独立验收未放行时均结构化阻断。评测运行保存启用题全部裁决字段的 SHA-256 指纹，readiness 同时复核能力码与指纹；其他能力或题数相同但内容/来源/版本已变化的 `PASSED` 均不能串线放行。外部知识生产白名单必须允许 `prompt`，版本包三个内容 hash 必须是 64 位 SHA-256；调用方自由文本不能替代治理事实。网关复用版本包完整性校验，缺失或畸形时在 provider 解析前 B0，并与三类适配器双层拒绝空补全或响应模型漂移；V150 进一步以规范化作用域键等值 CHECK + UNIQUE 保证每租户每能力最多一个 ACTIVE 版本包。
- `GET /api/v1/engine/knowledge-production/readiness`（`knowledge.read`）：供知识生产中心/运维只读查看 readiness 阻断项；`LOCAL_MODEL` 不要求外部部署形态和出域白名单，但仍要求受控文献根、健康本地 provider、回归评测、能力策略、版本三元组和 P6 放行。
- 新配置中心项 `medkernel.knowledge.production.p6-independent-acceptance` 默认 `false`、高风险受保护，仅作为 readiness 阻断事实源，不能替代文献库、provider、评测、出域与审核证据。
**PR6（模型生产器后端接入，分支 `codex/wave2-knowledge-model-readiness`）**：
- 新增 `ModelKnowledgeProducer`：仅经 `ModelGatewayService` 调用模型；先查 job + readiness，readiness 未齐直接返回结构化阻断项，不调用模型；provider 成功输出先转 `KnowledgeAssetEnvelope`，写入 AI 标识、模型任务 id、模型模式、prompt/tool/model 版本三元组和真实 payload hash，再走同一 `CandidateSafetyGateService → KnowledgeGenerationTriageService → KnowledgeShadowEvaluationService → submitCandidate` 候选流水线。
- `POST /api/v1/engine/knowledge-production/jobs/{jobCode}/model-candidates`（`knowledge.write`）：API 大模型 / 本地模型 job 产出模型候选；模型输出非 JSON 对象、出域阻断/B0 降级时不产伪候选，分别返回 `MODEL_OUTPUT_SCHEMA` 阻断或跳过原因。
**PR7（Agent 受控回写后端接入，分支 `codex/wave2-knowledge-model-readiness`）**：
- DATASVC 受控工具新增 `submitProductionCandidate`，Agent 只能以 `knowledge.write` 经受控工具入口提交 `AgentProductionCandidatePayload`；候选必须带 `jobCode`、幂等键、D1/D2 数据级别、来源锚点、真实内容 hash 与 AI 标识，D3/D4/D5 或疑似患者字段拒绝。
- 回写调用同一 `KnowledgeProductionOrchestrationService.submitCandidate`，相同 job + `contentHash` 幂等返回既有候选引用；CLI `agent submit-candidate` 与 MCP `payload` schema 完成接线。
- **Task 22 工程验收已完成**：配置齐全路径可由模型生产器生成 AI 候选并进入同一候选池/门禁/分流/影子/审核链；缺模型、文献根、评测、白名单、三元组、P6 验收等缺口均结构化阻断，不调用模型、不造假候选。真实外部 provider 现场与 P6 独立放行仍按运行环境另验；Agent 任务中止/纠偏等协同控制继续按 AIK-STD-14 收口。
**PR8（生产中心只读证据前端，分支 `codex/wave2-knowledge-model-readiness`）**：
- `知识审核与发布` 页面新增 `知识生产` tab：展示模型生产 readiness、生产 job、候选血缘、AIK 门禁、8 态分流、影子评测和共存替换提醒；默认选中第一页首个 job，所有数据走 `/engine/knowledge-production/*` 真实只读接口和服务端分页 hook。
- 页面只展示候选生产证据，不提供 AI 生成/创建候选按钮；readiness 未过时明确提示不得调用模型或伪造候选。
- **Task 22 验证记录**：后端全量 `mvn test` 2722 通过、前端全量 95 文件/740 用例通过，CLI/MCP 与 changed 门禁均通过；卡片仍不勾 Agent 中止/纠偏等未完成协同控制。

**2026-06-17 T5.7 候选真实化复核（分支 `codex/knowledge-fullflow-audit-production`）**：
- `ModelKnowledgeProducer` 的模型候选 payload 已只落 `promptInputHash`，不落生产提示正文；候选 payload 保留 `aiGenerated/modelTaskId/modelMode/modelVersion/promptVersion/toolVersion/sourceCitations/modelOutput` 与真实内容 hash，满足 AI 标识、三元组、锚点与最小化证据。
- B2 外部失败但 B1 本地模型真实成功时不再被误判为 B0 跳过；候选继续进入同一 `CandidateSafetyGateService → triage → shadow → submitCandidate` 链，并在 payload 中保留 `fallbackUsed/fallbackReason` 降级证据。B0、非 `SUCCEEDED`、readiness 未齐或模型输出非 JSON 对象仍阻断/跳过，不产伪候选。
- 本轮仅收口模型候选真实化语义，不宣称真实 provider 现场、P6 独立放行、Agent 中止/纠偏或 AIK-STD-13 整卡全部完成。

**2026-06-17 T5.8 降级路径预验（分支 `codex/knowledge-fullflow-audit-production`）**：
- `ModelKnowledgeProducer` 对非成功 B2/B1 模型任务返回 `模型网关未成功(status=..., mode=...)` 跳过原因，保留 provider 失败码与模型模式；只有真实 B0 模式才写“降级 B0”，避免把 provider 断连/超时伪装成 B0 成功模板链。
- 知识生产中心在 readiness/job 主证据可读取时，候选血缘、门禁结果、8 态分流、影子评测、共存替换提醒任一下游 evidence 查询失败，会显示“生产证据部分读取失败”和分项错误；页面不展示 AI 生成按钮，也不以空表掩盖断连。
- 本轮为 DEGRADE-01 预验收口，仍不宣称真实 provider 现场、P6 独立放行、Agent 中止/纠偏或 AIK-STD-13 整卡全部完成。

**2026-06-17 T6.1 院内上传覆盖接线（分支 `codex/knowledge-fullflow-audit-production`）**：
- `DocumentParseController` 院内 multipart 上传入口复用 AIK-STD-02 解析和 P1 受管资料库；成功解析后可选生成计划只声明领域与物化目标，服务端用解析出的真实 `SourceVersion` 固定构造 `CandidateGenerationRequest(..., TENANT_OVERLAY, ...)`。
- T6.1 未新增候选表、不新增平台主源写入口；院内上传候选仍走 `CandidateGenerationOrchestrationService → KnowledgeProductionOrchestrationService.submitCandidate`，继续受双形态隔离、门禁、8 态、影子评测和会签路由约束。

**2026-06-17 T6.2 本地模型生产器收口（分支 `codex/knowledge-fullflow-audit-production`）**：
- `ModelKnowledgeProducer` 对 `LOCAL_MODEL` job 增加 producer 层管道守卫：本地模型只允许生成 `TENANT_OVERLAY` 院内覆盖候选，发现平台主源管道在 readiness 和模型网关调用前即返回 `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`。
- `ModelTaskRequest` 增加可选 `requiredRouteStrategy/providerCode`，知识生产器按 job 类型传入 `LOCAL_MODEL` 或 `EXTERNAL_MODEL`；`ModelGatewayService` 在 provider 解析、脱敏摘要落库和真实调用前校验当前能力策略必须匹配必需路由，策略漂移时拒绝越界调用，不落任务、不外调。
- `ModelProviderRegistry` 支持按 `routeStrategy + providerCode` 解析指定 provider，并复用部署形态、类型匹配与健康检查；`LOCAL_MODEL` 指定外部 providerCode 时解析为空并回到既有诚实降级/阻断路径，不伪造 B1/B2。

**2026-06-17 T6.3 双形态隔离强化（分支 `codex/knowledge-fullflow-audit-production`）**：
- `KnowledgeProductionOrchestrationService.submitCandidate` 对 `PLATFORM_SOURCE` job 增加对称守卫：非 `t-1` 候选进入平台主源管道直接返回 `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`，不再退化为泛化租户不一致错误；`TENANT_OVERLAY` 反写 `t-1` 守卫保持不变。
- 新增平台主源正向提交测试：`t-1` 候选进入平台主源管道时保持平台归属并路由 `PLATFORM_KNOWLEDGE_GOVERNOR`；院内覆盖候选仍路由机构知识治理员，FR-7 不路由平台归口。
- `KnowledgeVersionService` 补客户只读平台主源测试：客户租户可读取平台 effective identity，但不能把平台 `identityId` 当写入目标提交候选；写入必须命中本租户 identity，否则 `NOT_FOUND` 且不读取来源、不保存版本、不触碰平台版本链。

## 功能要求（原子可测条目）
- [ ] FR-1 生产任务（job）：可定义 job＝来源范围 + 资产类型 + 生产器 + **目标管道（平台主源 / 院内覆盖）** + 模型策略；可调度、可查进度、可重放、可中止。
- [ ] FR-2 四生产器可插拔：① API 大模型自主（B2 外部，经 [LLM-01](LLM-01.md) 网关）② Agent 工具协助（经 [DATASVC-01](DATASVC-01.md) MCP/CLI 回写，契约见 [AIK-STD-14](AIK-STD-14.md)）③ 本地大模型（B1 本地/国产化）④ 人工录入/批量导入；新增生产器不破坏框架。
- [ ] FR-3 统一候选池：四生产器产物统一进候选池，走同一流水线（解析→候选→门禁→评测→去重→审核），**只产候选不产事实**（核心 §7 / 铁律 #5）。
- [ ] FR-4 双形态物理隔离：**外网管道**产物归 `t-1` 平台主源发布账本；**内网管道**产物只生成本客户租户覆盖/新增资产，**禁与平台主资产混、禁反写平台主源**（核心 §9）；每候选带「归属管道 + 租户 + 生产器」血缘标签，越界即拒。
- [ ] FR-5 血缘/审计/可重放：每候选可回溯来源 job/生产器/模型模式/提示词版本/时点；生产动作全审计；可按 job 重放（核心 §11 可重放）。
- [x] FR-6 候选按归属+风险+**领域**路由会签（PR3）：平台主源候选→平台知识治理员、院内覆盖候选→机构知识治理员；按领域会签——临床(规则/路径/危急值)→临床治理负责人、药学(说明书/DDI)→药事安全人员、术语/报告→医技协同人员、医保病案→质量与医保治理员；高危走双签（核心 §6）。**纯确定性 resolve，不建 ReviewAssignment（物化前不伪装已分派）**。
- [x] FR-7 院内覆盖角色边界（PR3）：机构侧角色（知识治理员/护理/药事/医技经各自治理）可产**本租户院内覆盖**候选，**对平台主源只读、禁反写**（五维资产权限落点，核心 §9）；路由层一致性＝院内候选归口恒为机构知识治理员，永不平台归口。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`POST /api/v1/engine/knowledge-production/jobs`（建 job）、`GET /jobs[/{id}]`（进度）、`POST /jobs/{id}/{cancel,replay}`、`GET /jobs/{id}/candidates`（候选）、`GET /readiness`（模型生产前置闸只读查询）、`POST /jobs/{jobCode}/model-candidates`（模型生产器生成候选）。
- DTO：Record DTO + Bean Validation；job 必带 `targetPipeline`(PLATFORM_SOURCE/TENANT_OVERLAY) + `producer` + `assetType` + `tenantId`。
- 响应信封：`ApiResult`/`ProblemDetail`；幂等键 `Idempotency-Key`；traceId 透传。
- 状态机：变更类（job：待发布→进行→完成/失败/已取消）；候选入既有配置类版本状态机（[KNOW-02](../D2/KNOW-02.md)）。
- 错误码：`KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`（越界/反写主源）、生产器不可用降级。
### 页面契约（页面卡）
- 生产者工作台（下任务/看进度/审候选）承载于 [AIK-STD-12](AIK-STD-12.md) 审核台 + 知识生产侧一级域（核心 §2.0）；**E2/E4 体验落点**：审核台模式（队列+左右对照+影响+结论）、双形态颜色/徽标/分区物理可辨、客户对主源只读、高风险不可批量自动通过。

## 数据与迁移
- `knowledge_production_job`（job/生产器/目标管道/状态/血缘/trace）+ 候选血缘标签（归属管道+租户+生产器）；按 `tenant_id`+`target_pipeline`+状态索引。
- 不重造资产/版本表，复用 [KNOW-01](../D2/KNOW-01.md)/[KNOW-02](../D2/KNOW-02.md)。5 方言迁移 + 中文 COMMENT + 约束。

## 视角清单（11 视角逐条）
1. 产品架构：多生产器→统一候选池→流水线的编排中枢；双管道分流。
2. 产品体验：E2 生产者工作台 + E4 双形态一眼可辨（防客户误编辑主源）。
3. 系统与数据架构：job 异步调度、可重放；候选血缘按租户+管道索引；P95 进度查询 ≤2s。
4. 临床医疗安全：候选只产不执行；高危候选标风险、禁批量自动确认（[AIK-STD-05](AIK-STD-05.md) 门禁）。
5. 知识与数据治理：★主战场——只产候选、双管道隔离、血缘可溯（核心 §7）。
6. 安全合规与监管：生产动作全审计；外网管道无患者数据。
7. 集团化与多租户治理：★平台主源 `t-1` 不可污染 + 客户覆盖只归本租户不反写（核心 §9）。
8. 集成与互操作：生产器经网关/MCP 解耦；Agent 接入见 [AIK-STD-14](AIK-STD-14.md)。
9. 运维 / SRE / 国产化：内网管道用本地模型/国产化；关模型人工生产器仍可跑。
10. 质量与真实性审计：★候选必带真实来源 + 生产血缘，禁无源/伪造产物（铁律 #1）。
11. AI / 模型治理与可降级：★生产器经统一网关路由 B0/B1/B2；关模型退人工/B0 生产器（铁律 #4）。

## 适用不变量
- 命中核心约束：**核心 §7 唯一权威/平台源不可污染** · **§9 客户覆盖只归本租户/不反写主源** · **铁律 #1 真实性** · **#4 B0 先于模型** · **#5 关系库权威（候选不入权威库）**。
- 本卡落点：统一编排 + 多生产器 + 双形态物理隔离，候选走既有审核/替换链，红线「只产候选」有归口。

## 验收 + 验证
- [ ] AC-1（FR-1/2/3）：四生产器各产候选均进同一池、走同一流水线；新增生产器不破框架。
- [ ] AC-2（FR-4）：内网管道候选写成平台主源/反写 `t-1` → `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION` 拒；外网管道归 `t-1`、客户对主源只读。
- [ ] AC-3（FR-5）：候选血缘可回溯 job/生产器/模型模式/时点；job 可重放、全审计。
- T-GATE：后端真实性门禁全绿（候选必带真实来源血缘）。
- B0 验收：★关模型→人工/确定性生产器仍可产候选并走流水线；双管道隔离与模型无关。

## 完工证据
- 代码 permalink：编排服务 + 四生产器接入框架 + 双管道隔离守卫 + 血缘/审计 + 5 方言迁移。
- 测试：四生产器入池、双形态越界拒绝、反写主源拒绝、血缘可溯、job 重放、关模型降级。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
