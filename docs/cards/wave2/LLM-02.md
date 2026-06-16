# LLM-02 · B0/B1/B2 策略与故障切换矩阵

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：核心 §11 B0/B1/B2 + 可降级 · 详规 §降级矩阵 · 铁律 #2 诚实降级。

## 身份
- 卡 ID：LLM-02（= backlog `LLM-02`）
- 域：wave2（X-LLM）
- 关联场景：S15
- 依赖卡：[LLM-01](LLM-01.md)（路由中枢）· [LLM-08](LLM-08.md)（provider）· [DEGRADE-01](../ga/DEGRADE-01.md)（GA 降级总验收）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
定义并实现 **B0（无模型确定性）/ B1（本地）/ B2（外部）三级策略**与**故障切换矩阵**：超时 / 限流 / 结构失败 / 断网任一发生即逐级回退到 B0，全程诚实标注。

## 现状（搬迁时核查 2026-05-31）
**部分已建**（[LLM-01](LLM-01.md) 已有 B0 回退 + `baselineReason` 归因；[LLM-08](LLM-08.md) 已接 provider 注册、健康解析和外部出域闸）：provider 缺位、部署形态禁外部、出域阻断、provider 调用失败均诚实降级 B0。尚缺系统化 4×3 故障切换矩阵、限流/超时专项归因、可配 fallback order 与全组合验收。本卡＝把切换规则**矩阵化 + 可配 + 可验收**。

## 最新进度（2026-06-16 readiness 前置闸）
- 知识生产 readiness 已把“生成知识前是否允许调用模型”前置化：provider、评测、出域、能力策略、版本三元组、P6 任一不满足即结构化阻断，避免进入运行时后才失败。
- 已新增 `ModelFallbackMatrix` 与稳定触发码：`PROVIDER_TIMEOUT`、`PROVIDER_RATE_LIMITED`、`STRUCTURED_OUTPUT_FAILED`、`PROVIDER_DISCONNECTED`、`PROVIDER_UNAVAILABLE`、`EGRESS_BLOCKED` 等均归因到 B0；`ModelGatewayService` 已接 provider 缺位、出域阻断、provider 异常、429、结构化失败后 B0 降级，响应仍据实标 `mode/fallbackUsed/fallbackReason`。
- 已补 `ModelFallbackMatrixTest` 完整 4×3 组合矩阵：`PROVIDER_TIMEOUT`/`PROVIDER_RATE_LIMITED`/`STRUCTURED_OUTPUT_FAILED`/`PROVIDER_DISCONNECTED` × `BASELINE`/`LOCAL_MODEL`/`EXTERNAL_MODEL` 均有稳定归因、B0 结果与 retryable 断言。
- 仍未完成：可配置 fallback order、超时预算配置。

## 功能要求（原子可测条目）
- [ ] FR-1 三级策略：每能力码可配首选级别（B2→B1→B0 降级序）。
- [x] FR-2 切换触发：超时 / 限流 429 / 结构化校验失败 / 断网/连接错 → 自动降一级，最终 B0。
- [x] FR-3 诚实标注：响应据实标 `mode` + `fallbackUsed` + `fallbackReason`（归因到触发条件）。
- [x] FR-4 矩阵可验收：4 触发 × 3 级别组合有对应用例与期望降级结果。
- [x] FR-5 不伪造：降级后禁用上一级的模型名/置信度冒充。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 复用 [API-12](API-12.md) 提交链路；新增切换决策器（service 内）。
- 状态机：变更（任务态含降级路径）。
- 错误码：复用 `ENG_LLM_*`；新增超时/限流归因码。

## 数据与迁移
- 切换策略落 `model_capability_policy`（扩字段 fallback_order/timeout_ms/rate_limit），五方言；或独立 `model_route_matrix` 表。

## 视角清单（11 视角）
1. 产品架构：可降级是 AI 能力的可用性底线。
2. 产品体验：降级对用户透明可见（标识），不静默假装。
3. 系统与数据架构：切换延迟可控；P95 含降级路径。
4. 临床医疗安全：降级到 B0 不丢临床安全门禁（红线仍生效）。
5. 知识与数据治理：N·A。
6. 安全合规与监管：降级事件审计留痕。
7. 集团化与多租户治理：切换策略按 OrgContext 继承。
8. 集成与互操作：与 [LLM-08](LLM-08.md) provider 健康检查联动。
9. 运维 / SRE / 国产化：★断网/无外网必达 B0；国产环境主链路不依赖外部模型。
10. 质量与真实性审计：★降级诚实、不伪造上级产出。
11. AI / 模型治理与可降级：★本卡即核心降级矩阵；GA [DEGRADE-01](../ga/DEGRADE-01.md) 消费。

## 适用不变量
- 命中核心约束：**铁律 #2 诚实降级** · **#4 B0 先于模型** · **#1 真实性**。
- 本卡落点：4 触发自动逐级回退 B0、全程诚实标注、可矩阵验收。

## 验收 + 验证
- [ ] AC-1（FR-1~3）：4 触发条件分别触发正确降级 + 诚实归因。（FR-1 可配置 fallback order/预算仍未收口，暂不闭）
- [x] AC-2（FR-4）：12 组合矩阵用例全过。
- 关联 A1–A9 剧本：降级链相关剧本。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★断网/超时/限流/结构失败任一 → 主链路 B0 可运行。

## 完工证据
- 代码 permalink：切换决策器 + 矩阵配置。
- 测试：4×3 矩阵 + 诚实归因 + 断网。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
