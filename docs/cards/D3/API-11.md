# API-11 · 嵌入 API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · 核心 §10 集成边界 · 详规 §1.4 嵌入入参。

## 身份
- 卡 ID：API-11（引擎/API 卡）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[EMBED-01](EMBED-01.md) 嵌入引擎（单一归属）· [API-07](API-07.md) 推荐 · [BASE-03](../D0/BASE-03.md) API 契约 · [OPT-02](OPT-02.md) 触发点
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把嵌入能力**契约化**：iframe / SDK / 纯 API 三路嵌入第三方 HIS/EMR，launch token 一次性消费、过期、白名单为真，CDS Hooks 风格事件契约，断连诚实降级。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/embed/` 下 `EmbedEngineController` / `EmbedEngineService` + `EmbedLaunchToken` + `EmbedLaunchContextResponse` + `EmbedFeedbackRequest` + 安全/契约测试。本卡＝把 launch token 一次性/过期/白名单 + 三路嵌入 + 回调契约化，token 归 [EMBED-01](EMBED-01.md)。

## 功能要求（原子可测条目）
- [x] FR-1 launch token：签发 → 一次性消费换取上下文（`EmbedLaunchContextResponse`），重复使用拒绝。
- [x] FR-2 过期/白名单：token 过期拒绝；来源 origin 不在白名单拒绝。
- [x] FR-3 三路嵌入：iframe / SDK / 纯 API 三种集成方式契约一致。
- [x] FR-4 事件/回调：CDS Hooks 风格事件（[OPT-02](OPT-02.md)）+ 反馈回调（`EmbedFeedbackRequest`）。
- [x] FR-5 降级：引擎/模型不可用诚实返回 `MODEL_DISABLED`/`NOT_CONNECTED`，不伪造嵌入卡。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`POST /api/v1/engine/embed/launch-tokens`（签发）· `POST .../embed/launch`（消费换上下文）· `POST .../embed/feedback`（反馈）
- DTO：`EmbedLaunchTokenRequest` / `EmbedLaunchTokenResponse` / `EmbedLaunchRequest` / `EmbedLaunchContextResponse` / `EmbedFeedbackRequest` / `EmbedFeedbackResponse`（Record + Bean Validation）
- 反馈降级：`EmbedFeedbackResponse.callbackStatus=NOT_CONNECTED` 时必须同步返回 `callbackDelivered=false` 与真实 `degradationReason`，不得把未配置宿主回调伪造成送达成功。
- 响应信封：`ApiResult` / `ProblemDetail`；状态机：`UNUSED` → `USED` / `EXPIRED` / `REVOKED`
- 幂等 / 错误码 / traceId：token 一次性（消费即失效）；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 复用 `EmbedLaunchToken` 表族（[EMBED-01](EMBED-01.md) 归属）+ 白名单/origin 配置；V70 五方言补 `integration_mode` / `hook` / `hook_instance` / `consumed_at`、状态过期索引和 Hook 索引（[BASE-05](../D0/BASE-05.md)）

## 视角清单（11 视角逐条）
1. 产品架构：第三方系统嵌入的统一入口契约。
2. 产品体验：N·A（嵌入页 `clinical/EmbedLaunch.tsx`，体验在宿主系统内）。
3. 系统与数据架构：token 验签 O(1)；换上下文 P95 ≤500ms。
4. 临床医疗安全：嵌入只读上下文 + 触发命中、不绕引擎直写；断连不阻断宿主主流程。
5. 知识与数据治理：嵌入命中仍按 `ACTIVE` 权威版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：★launch token 一次性/过期/白名单为真；签发/消费留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：token 绑定 `OrgContext`，跨租户不可复用。
8. 集成与互操作：★CDS Hooks 风格事件契约（[OPT-02](OPT-02.md)）；iframe/SDK/纯 API 三路。
9. 运维 / SRE / 国产化：嵌入失败诚实标记可观测。
10. 质量与真实性审计：★无伪造嵌入卡、token 不可重放；白名单不可绕。
11. AI / 模型治理与可降级：模型不可用回确定性推荐 `MODEL_DISABLED`。

## 适用不变量
- 命中核心约束：**核心 §10 集成边界** · **铁律 #1 真实性** · **§6 安全（token 一次性）** · **§1.4 统一入参**。
- 本卡落点：安全可控的三路嵌入契约，token 归 [EMBED-01](EMBED-01.md)。

## 验收 + 验证
- [x] AC-1（FR-1/2）：token 一次性消费；过期/非白名单拒绝。
- [x] AC-2（FR-3/4）：三路嵌入契约一致；事件/回调可达。
- [x] AC-3（FR-5）：断连/关模型诚实降级，无伪造卡。
- 关联 A1–A9 剧本：A4 嵌入触发。
- T-GATE：后端真实性门禁全绿（token 不可重放 / 白名单不可绕）。
- B0 验收：关模型嵌入换上下文 + 确定性命中仍可用。

## 完工证据
- 代码 permalink：`engine/embed` launch token + 三路嵌入契约（PR 合并后回填）。
- 测试：红灯先失败（缺 `EmbedLaunchRequest` / 三路模式 / 降级响应 / `ENG-EMBED-005` / 原子消费仓储方法）；绿灯已跑 `mvn -q -Dtest=EmbedEngineServiceTest,EmbedEngineControllerTest,EmbedEngineControllerSecurityTest test`、迁移/契约套件、后端全量 `mvn -q test`、前端 `npm run verify`。
- T-GATE：提交前后 changed-mode 真实性 / 配置边界 / 迁移规约、中文注释、diff 检查和脚本自测（PR 前后以最新命令为准）。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
