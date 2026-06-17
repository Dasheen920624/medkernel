# LLM-01 · 模型能力网关（引擎）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：落地规划 §模型网关 · 核心 §11 B0 先于模型 · 铁律 #1 真实性。

## 身份
- 卡 ID：LLM-01（= backlog `LLM-01`）
- 域：wave2（X-LLM）
- 关联场景：S15
- 依赖卡：[API-12](API-12.md)（对外契约）· [BASE-01](../D0/BASE-01.md)（OrgContext）· [BASE-04](../D0/BASE-04.md)（审计）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
模型能力网关**引擎**：provider 无关契约 + 路由策略持久化 + 组织继承 + B0 诚实空候选（不写死病种）——所有 AI 增强能力的统一入口与降级裁决中枢。

## 现状（搬迁时核查 2026-05-31，以 `medkernel-backend` 为准）
**MVP 已建**：`engine/llm/ModelGatewayService` 实现能力状态、`submitTask`（脱敏→hash→路由→Schema 校验→B0 回退→审计）、`getTask`/`retryTask`/`validatePolicy`；`ModelCapabilityPolicy`(scope_type/scope_ref/route_strategy/desensitize_strategy/expected_schema) + `ModelCapabilityTask` 实体；五方言 `V18`。**LLM-08 已落 provider 机制**：B1/B2 通过 `ModelProviderRegistry` 解析健康 provider，外部 provider 先过出域白名单/审批闸；缺 provider、部署形态禁外部、出域阻断或 provider 调用失败均诚实降级 B0。当前 B0 回退返回统一空候选信封，不写死医学事实。剩余：完整故障矩阵仍待 [LLM-02](LLM-02.md) 收口，候选模型真实化仍待 Phase 5 后续切片。

## 最新进度（2026-06-16 readiness 前置闸）
- 知识生产侧已新增 `KnowledgeProductionReadinessService`，在真实模型生成知识前校验 provider 可用、评测通过、出域治理、能力策略、prompt/tool/model 三元组和 P6 独立验收；未通过时不进入模型调用。
- 本卡的“网关可调用”不等于“知识生产可正式模型生成”：P6、文献资料库、真实基准集、凭据引用和独立验收仍是 readiness 的强阻断项。
- LLM-02 降级矩阵已把 provider 缺位、限流、超时、结构化失败、断连、出域阻断归因到稳定 `fallbackReason`；LLM-04 版本包已让 provider 成功任务绑定 prompt/tool/model 三元组。
- 2026-06-17 T5.1：模型策略改为 `scope_type/scope_ref` clean baseline（134 清库初始化，不保留旧 `tenant+capability` 唯一策略），`ModelPolicyScope` 按当前组织链由近到远继承到租户；`getStatus` 返回策略来源和是否继承，知识生产 readiness 使用同一解析器，前端 AI 工作流页展示策略来源。

## 功能要求（原子可测条目）
- [x] FR-1 路由裁决：按策略 `BASELINE/LOCAL_MODEL/EXTERNAL_MODEL/DISABLED` 选路；无 provider → B0。
- [x] FR-2 策略持久化 + 组织继承：策略按 租户→集团→医院→院区→站点→科室→病区 继承覆盖（呼应核心 §9）。
- [x] FR-3 B0 诚实空候选：无 provider 时返回**确定性来源**（既有规则/字典/路径事实）或诚实空态，**不写死病种**。
- [x] FR-4 脱敏 + 存证：调用前脱敏 + `input_hash` SHA-256 存证。
- [x] FR-5 不伪造：禁伪造 B1/B2 模型名/置信度/来源引文；`fallbackUsed`/`mode` 据实。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 对外契约见 [API-12](API-12.md)；本卡为其 service 实现。
- 状态机：变更（任务态）+ 配置（策略态）。
- 错误码：`ENG_LLM_001/002/004`；traceId 透传。

## 数据与迁移
- 表族 `model_capability_policy`/`model_capability_task`（五方言 `V18`，本卡单一归属）；策略字段为 `scope_type/scope_ref`，唯一键为 `tenant_id+capability_code+scope_type+scope_ref`，从当前组织链继承解析。

## 视角清单（11 视角）
1. 产品架构：AI 能力统一中枢，provider 可插拔。
2. 产品体验：N·A（引擎）。
3. 系统与数据架构：B0 P95 ≤2s；任务/策略按租户+能力索引。
4. 临床医疗安全：高危能力输出标风险、禁自动入病历。
5. 知识与数据治理：B0 候选引既有权威事实，不写死。
6. 安全合规与监管：脱敏 + hash 存证 + 全审计。
7. 集团化与多租户治理：★策略组织继承（核心 §9 七层）。
8. 集成与互操作：provider 适配解耦至 [LLM-08](LLM-08.md)。
9. 运维 / SRE / 国产化：无外网纯 B0 可运行。
10. 质量与真实性审计：★去 `executeB0Fallback` 硬编码病种；不伪造模型产出。
11. AI / 模型治理与可降级：★路由 + 降级裁决中枢；矩阵见 [LLM-02](LLM-02.md)。

## 适用不变量
- 命中核心约束：**铁律 #4 B0 先于模型** · **#1 真实性** · **#5 关系库权威**（候选不入权威库）· **核心 §9 组织继承**。
- 本卡落点：provider 无关网关引擎，策略组织继承、B0 诚实不写死、产出可审计不伪造。

## 验收 + 验证
- [x] AC-1（FR-1/2）：策略选路 + 组织继承覆盖单测。
- [x] AC-2（FR-3/5）：无 provider B0 不写死病种、不伪造模型名（真实性门禁）。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★关 provider 全能力码可调通、产出诚实。

## 完工证据
- 代码 permalink：`engine/llm/ModelGatewayService` + 策略继承 + 去硬编码 B0。
- 测试：`mvn -q -Dtest=ModelGatewayServiceTest,KnowledgeProductionReadinessServiceTest,ModelGatewayControllerTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`；`cd frontend && npm test -- AiWorkflows.test.tsx`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
