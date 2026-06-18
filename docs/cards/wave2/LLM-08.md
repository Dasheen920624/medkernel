# LLM-08 · provider 真实接入

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：核心 §11 B1/B2 · 详规 §provider 接入 · 铁律 #2 诚实降级。

## 身份
- 卡 ID：LLM-08（= backlog `LLM-08`）
- 域：wave2（X-LLM）
- 关联场景：S15
- 依赖卡：[LLM-01](LLM-01.md)（路由）· [LLM-02](LLM-02.md)（切换矩阵）· [LLM-03](LLM-03.md)（出域安全）· [LLM-07](LLM-07.md)（上线评测）
- 工作量：6d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**provider 真实接入**：B1 本地 + B2 外部 + Dify 可选——把真实模型接进网关，缺位诚实降级 B0；这是把 [LLM-01](LLM-01.md) 的「无 provider B0」升级为「有 provider 真增强、无则 B0」的关键卡。

## 现状（搬迁时核查 2026-05-31）
**待建**：[LLM-01](LLM-01.md) 注释明确 provider 由本卡（`GA-ENG-LLM-02`/本卡）落地，当前一律 B0。本卡＝实现 provider 适配（B1/B2/Dify）+ 健康检查 + 缺位降级；接入前过 [LLM-07](LLM-07.md) 评测、出域过 [LLM-03](LLM-03.md)。**2026-06-13 校正**：B2 外部明确含 **Claude API / OpenAI 兼容 API**，按双形态——生产侧（外网生产中心，公开资料）可用，运行侧（内网医院，患者数据）禁外部 provider、只 B1 本地/B0（见 [_brief](_brief.md) §第二阶段设计校正）。

## 功能要求（原子可测条目）
- [x] FR-1 provider 适配：B1 本地（Ollama/国产化）/ B2 外部（**Claude API · OpenAI 兼容 API** 等）/ Dify 三类可插拔适配器（统一接口）；**生产侧外网可用、运行侧内网禁外部 provider**（双形态）。（`ModelProvider` 抽象+三适配器 Ollama/OpenAI兼容/Claude，HTTP 经 `ModelProviderHttpClient` 注入；`DeploymentFormService` 双形态门禁 `ENG-LLM-009`）
- [x] FR-2 健康检查：provider 连通性探活；不可用标 `NOT_CONNECTED`（呼应 [PROVIDER-01](../D5/PROVIDER-01.md)）。（`ModelProviderRegistry` 实时探测；`POST /api/v1/model-providers/{providerCode}/health-check` 将 `HEALTHY/NOT_CONNECTED` 持久化到唯一状态源并审计）
- [x] FR-3 缺位降级：无 provider/断连 → 诚实 B0（[LLM-02](LLM-02.md) 矩阵），不伪造产出。（`submitTask` 缺位/断连/形态禁外部→诚实 B0）
- [x] FR-4 真实产出：接入后产出标真实 `model_version`/置信度/来源（不再恒 B0）。（`submitTask` 过出域闸→真实产出）
- [x] FR-5 上线门禁：provider/版本上线前过 [LLM-07](LLM-07.md) 医学回归。（`upsertProvider` 启用须过 `isClearedForGoLive` 否则 `ENG-LLM-008`）

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 适配器内嵌 [LLM-01](LLM-01.md) 路由；provider 配置端点 + 健康检查端点。
- 状态机：配置（provider 启停）+ 变更（任务态）。

## 数据与迁移
- `model_provider`（类型/端点/凭证引用/状态）+ 复用 `model_capability_task`（真实 model_version），五方言；凭证走密钥管理不落明文。

## 视角清单（11 视角）
1. 产品架构：模型真实接入层，与网关解耦。
2. 产品体验：Provider 状态在 [PROVIDER-01](../D5/PROVIDER-01.md) 诚实显示。
3. 系统与数据架构：超时/重试/熔断；P95 含外调。
4. 临床医疗安全：真实产出仍标识 + 医师确认才入病历。
5. 知识与数据治理：真实产出仍为候选、走审核链。
6. 安全合规与监管：凭证不落明文；出域过 [LLM-03](LLM-03.md)。
7. 集团化与多租户治理：provider 按 OrgContext 配。
8. 集成与互操作：★Dify/外部模型对接（可选、缺位不影响）。
9. 运维 / SRE / 国产化：★国产环境无外网 → B1 本地或 B0；不强依赖外部。
10. 质量与真实性审计：★缺位诚实 B0、不伪造模型名/置信度。
11. AI / 模型治理与可降级：★本卡即「真接入 + 诚实降级」核心。

## 适用不变量
- 命中核心约束：**铁律 #2 诚实降级** · **#1 真实性** · **#4 B0** · **核心 §11 B1/B2**。
- 本卡落点：B1/B2/Dify 可插拔真实接入 + 健康检查 + 缺位诚实 B0 + 上线过评测。

## 验收 + 验证
- [x] AC-1（FR-1/2/4）：接真实 provider 产出真实 + 健康检查准确。（适配器经 mock HTTP 测；`ModelProviderGovernanceServiceTest` 覆盖）
- [x] AC-2（FR-3/5）：断连降级 B0 不伪造；上线过 [LLM-07](LLM-07.md)。（缺位 B0 测 + 启用门禁 `ENG-LLM-008` 测）
- 关联 A1–A9 剧本：有/无 provider 双向。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★拔掉全部 provider 主链路仍 B0 可运行。

## 完工证据
- 代码 permalink：provider 适配器 + 健康检查 + 降级。
- 测试：B1/B2/Dify 接入 + 断连降级 + 不伪造。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
