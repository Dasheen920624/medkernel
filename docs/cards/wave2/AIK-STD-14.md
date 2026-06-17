# AIK-STD-14 · 第三方 Agent 生产接入协议（Claude Code / Codex）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)（§2 生产器②/§3 接入底座）。
> 迁移来源：wave2 域简报 §第二阶段设计校正（2026-06-13 用户决策，早期规范缺失 Agent 协助生产概念）· 核心 §8 数据最小化 · §10 不绕引擎 · §11 模型治理可重放。

## 身份
- 卡 ID：AIK-STD-14（= backlog `AIK-STD-14`）
- 域：wave2（X-AIK）
- 关联场景：S3 AI 知识工厂
- 依赖卡：[DATASVC-01](DATASVC-01.md)（MCP/CLI 受控工具底座）· [AIK-STD-13](AIK-STD-13.md)（生产编排/候选池）· [AIK-STD-01](AIK-STD-01.md)（资产 schema）· [AIK-STD-02](AIK-STD-02.md)（引用锚点）· [LLM-03](LLM-03.md)（数据最小化）· [LLM-04](LLM-04.md)（提示词/版本治理）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
定义**第三方 AI Agent（Claude Code / Codex 等，跑在生产中心，当前实例 134）协助生产知识的受控接入协议**：约定「**生产任务规格 → 结构化候选回写**」契约、引用锚点回带、**沙箱内不接触任何患者数据**、人在环（human-in-the-loop）审计。Agent 只经 [DATASVC-01](DATASVC-01.md) 的 MCP/CLI 受控工具读公开来源与回写候选，**不直连库、不绕治理**；产物只作候选，进 [AIK-STD-13](AIK-STD-13.md) 候选池走流水线。

## 现状（核查 2026-06-13 → PR1 实现 2026-06-16，以 `medkernel-backend/src` 为准）
**全新建**：早期规范无「Agent 工具协助生产」概念（域简报校正点 1）。MCP/CLI 受控工具底座由 [DATASVC-01](DATASVC-01.md) 提供；本卡＝在该底座上定义 Agent 接入协议与沙箱边界，**不新增直连后门**。

**PR1（受控候选回写，分支 `codex/wave2-knowledge-model-readiness`）**：
- 在 DATASVC 受控工具目录新增 `submitProductionCandidate`，执行仍经 `/api/v1/engine-data/tools/{toolName}:execute`；入口层允许 `engine-data.read` 或 `knowledge.write`，服务层按工具名逐项校验权限，回写工具须 `knowledge.write`。
- `AgentProductionCandidatePayload` 结构化携带 `jobCode`、`idempotencyKey`、`dataLevel` 与既有 `CandidateSubmissionRequest`；D3/D4/D5 或疑似患者字段拒绝为 `AGENT_PATIENT_DATA_FORBIDDEN`，锚点/hash/AI 标识/schema 不合格拒绝为 `AGENT_CANDIDATE_SCHEMA_INVALID`。
- 回写只调用 `KnowledgeProductionOrchestrationService.submitCandidate`，候选仍进入 AIK-STD-13 同一候选池/门禁/审核链；同 job 下相同 `contentHash` 幂等返回既有候选引用，不重复提交；`ToolExecutionEnvelope` 留 trace、数据级别、权限结果和输出 hash 审计。
- CLI 增加 `agent submit-candidate <payloadJson>`，MCP `tools/list` schema 增加结构化 `payload`，二者只调受控工具入口，不直连库。
**PR2（生产中心只读接入，分支 `codex/wave2-knowledge-model-readiness`）**：
- 知识生产 tab 已展示 `AGENT_TOOL`/模型/人工 job 的只读证据面：生产 job、候选血缘、门禁、8 态分流、影子评测、共存替换提醒；Agent 回写候选能在同一生产链路中被看见。
- **Task 22 工程验收已完成**：受控回写的 schema/锚点/hash/AI 标识、D3/D4/D5 与疑似患者字段拒绝、幂等复用、控制器权限、CLI/MCP payload 均已验证通过。
**2026-06-17 前端 Chunk7**：知识生产 tab 已补 Agent 进度与中止操作，`AGENT_TOOL` job 可见候选/门禁/8 态/影子计数，并通过 `/engine/knowledge-production/jobs/{jobCode}/cancel` 走统一生命周期中止。仍待后续补强：Agent 纠偏、会话级 prompt/tool 版本审计明细和外调最小化证据尚未完整做成操作面。

**2026-06-17 Phase4 首片（公域取数后端）**：
- 新建 `engine.knowledge.acquisition`：`AcquisitionOrchestrationService` 只允许 `PRODUCTION_CENTER` 手动触发，URL 必须命中已审批 allowlist、HTTPS、许可 `PERMITTED` 且 robots 策略允许。
- V142 五方言新增 `mk_knowledge_acquisition_source` / `mk_knowledge_acquisition_run`，记录域名、A-E 权威、许可、robots 策略、审批人、真实 URL、抓取时点、原文字节数、sha256、资料 URI、解析 job 和状态。
- `WebContentFetcher`/`RestWebContentFetcher` 负责真实 HTTP 获取；资料进入 AIK-STD-02 解析链路，由 P1 受管资料库存储决定落 `file://` 本地磁盘、对象存储或 HTTPS 网关，不写死对象存储。
- 新增 `POST /api/v1/engine/knowledge/acquisition/runs`、`GET /api/v1/engine/knowledge/acquisition/{sources,runs}`，复用 `knowledge.write/read` 与服务契约。当前完成“手动公域资料→资料库→SourceVersion/fragment”链路；MCP/CLI `fetchPublicMaterial`、自动调度和候选生成触发仍待后续。

## 功能要求（原子可测条目）
- [ ] FR-1 生产任务规格：Agent 收到结构化任务（来源范围 + 目标资产类型 + 目标管道 + 输出 schema + 约束）；任务由 [AIK-STD-13](AIK-STD-13.md) 编排层下发。
- [ ] FR-2 结构化候选回写（PR1 后端/CLI/MCP 已接线）：Agent 经 MCP/CLI 回写候选，必带**引用锚点**（来源片段 + 偏移，[AIK-STD-02](AIK-STD-02.md)）+ 内容 hash + AI 生成标识；不合 schema 拒收。
- [ ] FR-3 沙箱无患者数据（PR1 入站硬闸 + Phase4 后端公域取数门禁已接线）：Agent 运行沙箱**只可见公开医学资料，禁触患者数据 / D5 重要个人信息**（[LLM-03](LLM-03.md) 数据最小化强制）；外网管道（核心 §8 无个人数据出境）。MCP/CLI `fetchPublicMaterial` 仍待接入。
- [ ] FR-4 不绕治理（PR1 受控工具已接线）：Agent 只调 [DATASVC-01](DATASVC-01.md) 受控 MCP/CLI 工具，**不直连库、不读原始病历、不绕身份/权限/脱敏/审计**（核心 §10）。
- [ ] FR-5 人在环 + 可重放：Agent 任务进度/产出可视、可中止/纠偏/审批（E3 体验）；调用方/工具/用途/提示词版本/输出 hash 全审计、可重放（核心 §11、[LLM-04](LLM-04.md)）。
- [ ] FR-6 外调最小化合规证据：发往外部模型/Agent 的内容须留**可审计证据**（字段白名单 + 脱敏策略 + 发送摘要 hash），供合规审计**证明无患者数据出境**（[LLM-03](LLM-03.md)/[OPT-09](OPT-09.md)，核心 §8）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- Agent 侧不新增直连 API，**复用 [DATASVC-01](DATASVC-01.md) MCP 工具**（`searchKnowledge`/`checkKnowledgeExistence`/…）读来源 + 新增受控回写工具 `submitProductionCandidate`（带 schema 校验、锚点必填、归属管道、幂等键）。
- 公域取数后端入口：`POST /api/v1/engine/knowledge/acquisition/runs` 手动触发 allowlisted 公开资料获取；`GET /api/v1/engine/knowledge/acquisition/sources` 与 `/runs` 查询白名单和运行账本。该入口服务 MCP/CLI `fetchPublicMaterial` 的后续接线，不允许绕过解析和候选审核链。
- CLI：`medkernel agent submit-candidate '<payloadJson>' --purpose 'Agent 受控回写'`，payload 为 `AgentProductionCandidatePayload` JSON。
- MCP：`tools/list` 暴露 `payload` object schema，`tools/call` 以 `{purpose,payload}` 调 `submitProductionCandidate`。
- 任务下发/回写经 [AIK-STD-13](AIK-STD-13.md) 编排层 job 接口。
- 响应信封：`ApiResult`/`ProblemDetail`；错误码：`AGENT_CANDIDATE_SCHEMA_INVALID`、`AGENT_PATIENT_DATA_FORBIDDEN`、越权拒绝；traceId 透传。
### 页面契约（页面卡）
- Agent 协同体验（E3）承载于知识生产侧一级域 / [AIK-STD-12](AIK-STD-12.md) 审核台：任务进度可视（读哪些来源、产几条候选）+ 中止/纠偏/审批 + 低打扰不刷屏 + 每候选标 AI 生成/来源锚点/模型模式（E5）。技术对象（提示词/工具 schema）入专家模式（核心 §14）。

## 数据与迁移
- 复用 [AIK-STD-13](AIK-STD-13.md) `knowledge_production_job` + 候选血缘（生产器=AGENT）；Agent 会话审计（调用方/工具/用途/提示词版本/输出 hash）复用 [BASE-04](../D0/BASE-04.md) + [LLM-04](LLM-04.md) 版本治理。
- Phase4 公域取数新增 `mk_knowledge_acquisition_source` / `mk_knowledge_acquisition_run`（V142 五方言），只记录公开来源白名单和获取运行账本，不成为权威知识表。

## 视角清单（11 视角逐条）
1. 产品架构：Agent 作为生产器②接入编排层的受控协议。
2. 产品体验：★E3 人机协同——进度可视/可中止纠偏/审批/低打扰。
3. 系统与数据架构：回写经受控工具，schema + 锚点 + hash 校验；异步任务。
4. 临床医疗安全：Agent 不产事实、产物作候选；高危候选走门禁（[AIK-STD-05](AIK-STD-05.md)）；不自动入病历。
5. 知识与数据治理：候选必带引用锚点 + 来源血缘；只进外网平台主源管道（公开资料）。
6. 安全合规与监管：★沙箱无患者数据 + 无个人数据出境 + 全审计（核心 §8）。
7. 集团化与多租户治理：Agent 外网管道产物归 `t-1`；不触客户患者数据。
8. 集成与互操作：★只经 MCP/CLI 受控工具，不绕引擎、不直连库（核心 §10）。
9. 运维 / SRE / 国产化：Agent 跑在生产中心（逻辑角色，当前 134，可迁移）；外网形态；关 Agent 其余生产器不受影响。
10. 质量与真实性审计：★候选必带真实锚点 + hash，禁伪造来源/无源产物（铁律 #1）。
11. AI / 模型治理与可降级：★Agent 是受控生产器、产物只作候选；提示词/工具/版本可重放可回滚（核心 §11、[LLM-04](LLM-04.md)）。

## 适用不变量
- 命中核心约束：**核心 §8 数据最小化/无出境** · **§10 不绕引擎** · **§11 可重放可审计** · **铁律 #1 真实性** · **#5 关系库权威（候选不入权威库）** · **§7 平台源不可污染**。
- 本卡落点：Agent 经受控 MCP/CLI 协助生产候选，沙箱无患者数据、不绕治理、人在环可审计可重放。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：Agent 收任务、经受控工具回写候选，锚点/hash/AI 标识齐全；不合 schema 拒收。
- [ ] AC-2（FR-3/4）：沙箱触患者数据/D5 → `AGENT_PATIENT_DATA_FORBIDDEN` 拒；Agent 直连库/绕治理被阻断。当前后端公域取数已阻断非生产中心、非白名单域、未许可/robots 不允许来源；MCP/CLI `fetchPublicMaterial` 未接线，故本 AC 未完全勾满。
- [ ] AC-3（FR-5）：任务进度可视、可中止/纠偏/审批；调用全审计、可重放。当前前端已补进度可视和中止；纠偏、会话级 prompt/tool 版本审计与可重放仍未勾满。
- 关联 A1–A9 剧本：A9 AI 知识审核（Agent 候选入审）。
- T-GATE：后端真实性门禁全绿（候选真实锚点、无伪造）。
- B0 验收：★Agent 是可选生产器；关 Agent，API/本地模型/人工生产器与流水线不受影响（铁律 #4）。

## 完工证据
- 代码 permalink：Agent 接入协议 + 受控回写工具 + 沙箱无患者数据门禁 + 人在环审计/可重放。
- 测试：候选 schema/锚点校验、患者数据禁触、绕治理拒绝、进度可视/中止、审计可重放、关 Agent 降级；`KnowledgeGovernance.test.tsx` 覆盖前端 Agent 进度与中止。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
