# API-12 · 模型能力网关 API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：落地规划 §模型网关 · 详规 §AI 能力码 · 核心 §11 B0 先于模型。

## 身份
- 卡 ID：API-12（= backlog `API-12`）
- 域：wave2（X-LLM）
- 关联场景：S15 AI 验证与验收
- 依赖卡：[LLM-01](LLM-01.md)（网关引擎，本 API 的实现体）· [BASE-03](../D0/BASE-03.md)（信封）· [BASE-04](../D0/BASE-04.md)（审计）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
对外暴露**统一模型能力网关 API**：能力代码路由、数据脱敏、结构化输出校验、审计留痕、B0 诚实降级——上层业务只调能力码、不直连任何 provider。

## 现状（搬迁时核查 2026-05-31，以 `medkernel-backend` 为准）
**MVP 已建**：`engine/llm/ModelGatewayController`（端点 `/api/v1/model-capabilities/{status,catalog,catalog/{capabilityCode},tasks,tasks/{id},tasks/{id}/retry,policies/validate,policies/{capabilityCode}}`，perm `llm.read`/`llm.execute`/`llm.manage`/`system.manage`，`@DataScope(requireTenant)`）。能力代码、中文名称、说明、分类、顺序和启停统一由关系库 `model_capability_definition` 管理，前端不保留能力目录副本。

## 功能要求（原子可测条目）
- [ ] FR-1 能力状态：`GET /status` 返回租户全部能力码 + 路由策略 + 可用性，无策略时诚实 B0。
- [ ] FR-2 提交任务：`POST /tasks` 入参校验（Bean Validation），返回 `task_id` + mode + fallback + traceId。
- [ ] FR-3 任务追溯：`GET /tasks/{id}` 跨租户访问拒绝（`TENANT_FORBIDDEN`）。
- [ ] FR-4 重试：`POST /tasks/{id}/retry` 以原输入重发，留审计。
- [ ] FR-5 策略校验：`POST /policies/validate` 非法能力码/非法 schema 显式报错。
- [ ] FR-6 能力码目录：能力码可配（不再硬编码），新增能力码不改本契约。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：见 FR；响应信封 `ApiResult`/`ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- DTO：`ModelTaskRequest`(capabilityCode/inputData/desensitizeStrategy/expectedSchema/timeout) · `ModelTaskResponse`(taskId/status/mode/fallbackUsed/riskLevel/traceId…)。
- 状态机：变更（任务 PENDING→DEGRADED/SUCCESS/FAILED）。
- 幂等 / 错误码 / traceId：`ENG_LLM_001` 能力禁用 · `002` schema 校验失败 · `004` 任务不存在 · `TENANT_FORBIDDEN`；traceId 全链路透传。

## 数据与迁移
- 复用 `model_capability_task` / `model_capability_policy`（五方言 `V18`，单一归属 [LLM-01](LLM-01.md)）；能力码目录如需落库另立 `model_capability_catalog`（五方言）。

## 视角清单（11 视角）
1. 产品架构：业务只调能力码的统一门面，provider 解耦。
2. 产品体验：N·A（后端 API；前端在 [PROVIDER-01](../D5/PROVIDER-01.md)/[AIFLOW-01](../D6/AIFLOW-01.md)）。
3. 系统与数据架构：提交 P95 ≤2s（B0）；任务表按 `(tenant,capability)` 索引；大列表走 [API-13](../D0/API-13.md)。
4. 临床医疗安全：高风险能力（用药/剂量）输出标 `riskLevel`，禁自动入病历。
5. 知识与数据治理：输出候选不入权威库，走审核链（见简报）。
6. 安全合规与监管：入参脱敏 + `input_hash` 存证；调用全审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：`@DataScope` 强租户隔离；策略按 OrgContext 继承。
8. 集成与互操作：provider 适配在 [LLM-08](LLM-08.md)；本 API 与之解耦。
9. 运维 / SRE / 国产化：能力状态可监控；国产环境无外网仍 B0 可用。
10. 质量与真实性审计：★禁伪造 mode/置信度；B0 时 `fallbackUsed=true` 据实。
11. AI / 模型治理与可降级：★能力禁用/无 provider → 诚实 B0；降级矩阵见 [LLM-02](LLM-02.md)。

## 适用不变量
- 命中核心约束：**铁律 #4 B0 先于模型** · **#1 真实性** · **核心 §5 五维权限**（perm 点位）。
- 本卡落点：统一能力网关契约，provider 解耦、降级诚实、调用可审计。

## 验收 + 验证
- [ ] AC-1（FR-1~5）：契约测试覆盖五端点 + 错误码 + 跨租户拒绝。
- [ ] AC-2（FR-6）：新增能力码不改契约（目录驱动）。
- T-GATE：后端真实性门禁全绿（无伪造 mode/置信度）。
- B0 验收：★无 provider 时全能力诚实降级 B0 可调通。

## 完工证据
- 代码 permalink：`engine/llm/ModelGatewayController` + DTO + 能力码目录。
- 测试：契约 + 跨租户 + 错误码 + B0 降级。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
