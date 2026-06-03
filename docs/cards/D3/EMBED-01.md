# EMBED-01 · iframe / SDK / 纯 API 嵌入引擎

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · 核心 §10 集成边界 · 详规 §1.4 嵌入与 launch。

## 身份
- 卡 ID：EMBED-01（引擎卡；`EmbedLaunchToken`/嵌入会话单一归属）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[API-11](API-11.md) 对外契约 · [CDSS-01](CDSS-01.md) 推荐 · [API-01](../D2/API-01.md) 上下文 · [OPT-02](OPT-02.md) 触发点 · [INTEG-01](../D2/INTEG-01.md) 对接总线
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把嵌入做成**安全可控**：第三方 HIS/EMR 经 iframe / SDK / 纯 API 三路嵌入 CDSS，**launch token 一次性消费/过期/白名单为真**，CDS Hooks 风格事件契约，断连诚实降级、不阻断宿主主流程。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/embed/` 下 `EmbedEngineService` / `EmbedEngineController` + `EmbedLaunchToken` + `EmbedLaunchContextResponse` + `EmbedFeedbackRequest` + 安全/契约测试；前端 `clinical/EmbedLaunch.tsx`（路由 `/embed/launch`）。本卡＝把 token 一次性/过期/白名单 + 三路集成 + 事件契约框架化为引擎核心。

## 功能要求（原子可测条目）
- [x] FR-1 token 生命周期：签发→一次性消费→失效；过期/已用/撤销均拒绝。
- [x] FR-2 白名单：消费时校验 origin/宿主在白名单；越权拒绝并审计。
- [x] FR-3 三路集成：iframe / SDK / 纯 API 共享同一 token + 上下文契约。
- [x] FR-4 事件契约：CDS Hooks 风格 6 触发点（[OPT-02](OPT-02.md)）+ 反馈回调。
- [x] FR-5 降级：宿主断连/引擎不可用 → `NOT_CONNECTED`/`MODEL_DISABLED`，不阻断宿主、不伪造卡。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 对外端点契约归 [API-11](API-11.md)；本卡负责引擎内 token 签发/消费/撤销 + `EmbedLaunchToken` 状态机（签发→已消费/已过期/已撤销）+ 白名单校验。
- 幂等 / traceId：token 消费幂等（二次消费拒绝）；嵌入会话 trace（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
- N·A —— 嵌入页 `clinical/EmbedLaunch.tsx` 为宿主内启动页，体验在宿主系统；本卡只管引擎+契约。

## 数据与迁移
- 表族：`embed_launch_token`（token + 一次性标记 + 过期 + origin + 组织字段 + 审计）+ 白名单表；五方言（[BASE-05](../D0/BASE-05.md)）
- 唯一约束：token 值唯一 + 消费标记；索引：过期时间/origin

## 视角清单（11 视角逐条）
1. 产品架构：CDSS 对第三方系统的安全嵌入边界。
2. 产品体验：嵌入启动 ≤1 跳；宿主内无缝（体验契约由宿主约束）。
3. 系统与数据架构：token 验签 O(1)；换上下文 P95 ≤500ms；高并发签发。
4. 临床医疗安全：嵌入只读上下文 + 触发命中，不绕引擎直写医嘱；断连不阻断宿主。
5. 知识与数据治理：嵌入命中按 `ACTIVE` 权威版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：★token 一次性/过期/白名单 + 签发/消费审计（[BASE-04](../D0/BASE-04.md)）；越权拒绝。
7. 集团化与多租户治理：token 绑 `OrgContext`，跨租户不可复用。
8. 集成与互操作：★三路集成 + CDS Hooks 事件契约（[OPT-02](OPT-02.md)）；经 [INTEG-01](../D2/INTEG-01.md)。
9. 运维 / SRE / 国产化：嵌入失败诚实标记、可观测；离线宿主降级。
10. 质量与真实性审计：★token 不可重放、白名单不可绕、无伪造嵌入卡。
11. AI / 模型治理与可降级：模型不可用回确定性推荐 `MODEL_DISABLED`。

## 适用不变量
- 命中核心约束：**核心 §10 集成边界** · **§6 安全（token 一次性/白名单）** · **铁律 #1 真实性**。
- 本卡落点：安全可控三路嵌入 + token 生命周期，对外契约归 [API-11](API-11.md)。

## 验收 + 验证
- [x] AC-1（FR-1/2）：token 一次性、过期/越权/非白名单拒绝且审计。
- [x] AC-2（FR-3/4）：三路集成契约一致；事件/回调可达。
- [x] AC-3（FR-5）：断连/关模型诚实降级、不阻断宿主。
- 关联 A1–A9 剧本：A4 嵌入触发。
- T-GATE：后端真实性门禁全绿（token 不可重放 / 白名单不可绕）。
- B0 验收：关模型嵌入换上下文 + 确定性命中可用。

## 大卡工序（5d）
- PR1：token 生命周期 + 白名单 + 门禁 → 验收
- PR2：三路集成 + 事件/回调契约 → 验收
- PR3：降级 + 安全/重放测试 → 验收

## 完工证据
- 代码 permalink：`engine/embed` token + 三路集成 + 白名单。
- 测试：token 一次性/过期/白名单/降级 + 安全测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## PR1 证据（token 生命周期 + 白名单）

- 代码范围：`EmbedEngineService.validateAndExchange` 强制非空 `Origin` 白名单校验，校验失败写失败审计并拒绝消费；`EmbedEngineController` 将 `/launch` 的 `Origin` 请求头改为必填；`GlobalExceptionHandler` 补齐缺少请求头的 400 标准 `ProblemDetail`，避免误报 500。
- 红绿证据：新增 `validateAndExchange_MissingOriginThrowsForbiddenBeforeConsumingToken` 与 `launchExchangeWithReadRoleButMissingOrigin_ShouldReturnBadRequest`，红灯先分别失败于旧逻辑返回 `ENG_EMBED_005` 与 200；修复后目标测试转绿。
- 本地验证：`mvn -q -Dtest=EmbedEngineServiceTest,EmbedEngineControllerTest,EmbedEngineControllerSecurityTest test` 通过；`mvn -q test` 通过，H2 / PostgreSQL 15.18 / Oracle 21.3 均迁移至 V71 并二次 no-op；`npm run verify` 51 文件 / 311 测试通过；`npm run build` 通过，既有 React Router / act warning 与 `vendor-antd` 大 chunk 警告仍归 [DEFER-003](../../audit/deferred-issues.md)。
- T-GATE：脚本自测 34/34、真实性全量扫描 953 文件、配置边界 inventory 扫描 891 文件、迁移 changed 扫描 0 文件、中文注释 0 fail / 0 warn、工作区 `git diff --check` 通过；提交后复跑真实性 changed 扫描 2 文件、配置边界 changed 扫描 2 文件、迁移 changed 扫描 0 文件与 `git diff --check origin/main..HEAD`，均通过。

## PR2 证据（三路集成 + CDS Hooks 事件 / 回调契约）

- 代码范围：`EmbedEngineService` 签发 / 兑换统一复用 `ClinicalEventTriggerPoint` 作为 [OPT-02](OPT-02.md) 6 触发点单一源；`ORDER_SIGN` 等枚举名规范化为 `order-sign` wireValue，非 6 触发点或 triggerPoint 与 hook 不一致返回 `ENG_EMBED_005`、写失败审计且不保存 / 不消费 token；兑换响应返回规范化 triggerPoint / hook。`ClinicalEventTriggerPoint` 注释同步为事件 / 推荐 / 嵌入共享契约口径，避免后续另造触发点枚举。
- 红绿证据：新增 `generateToken_NormalizesTriggerPointAndDefaultHookToCdsHookWireValue`、`generateToken_RejectsUnsupportedCdsHookBeforeSavingToken`、`validateAndExchange_NormalizesRequestedCdsHookAliasBeforeConsumingToken`，红灯先分别失败于旧逻辑保留 `ORDER_SIGN`、放行 `OUTPATIENT`、把 `ORDER_SIGN` 与 `order-sign` 判不匹配；修复后目标测试转绿。新增 `validateAndExchange_AllIntegrationModesShareSameCdsHookContext` 参数化覆盖 `IFRAME` / `SDK` / `API` 三路真实服务兑换。
- 本地验证：`mvn -q -Dtest=EmbedEngineServiceTest,EmbedEngineControllerTest,EmbedEngineControllerSecurityTest,ClinicalEventContractTest test` 通过；`mvn -q test` 通过，H2 / PostgreSQL 15.18 / Oracle 21.3 均迁移至 V71 并二次 no-op；`npm run verify` 51 文件 / 311 测试通过；`npm run build` 通过，既有 React Router / act warning 与 `vendor-antd` 大 chunk 警告仍归 [DEFER-003](../../audit/deferred-issues.md)。
- T-GATE：脚本自测 34/34、真实性全量扫描 953 文件、配置边界 inventory 扫描 891 文件、迁移 changed 扫描 0 文件、中文注释 0 fail / 0 warn、工作区 `git diff --check` 通过；提交后复跑真实性 changed 扫描 2 文件、配置边界 changed 扫描 2 文件、迁移 changed 扫描 0 文件与 `git diff --check origin/main..HEAD`，均通过。
- PR3 衔接：FR-5 / AC-3 降级与安全 / 重放测试已在 PR3 收口，`docs/backlog.md` 的 `EMBED-01` 已随 PR3 本地验收标 done；远端 CI / 合并仍以 PR 门禁为准。

## PR3 证据（降级 + 安全 / 重放测试）

- 代码范围：新增 `EmbedFeedbackActionType` 受控反馈动作，`ADOPT` / `REJECT` 为有效动作，`ACCEPT` 兼容归一为 `ADOPT`；非法动作返回 `ENG_EMBED_005` 并写失败审计，不写成功反馈审计。`EmbedFeedbackResponse` 显式返回 `callbackStatus=NOT_CONNECTED`、`callbackDelivered=false`、`degradationReason=HOST_CALLBACK_NOT_CONFIGURED`，说明当前未配置宿主回调，不伪造已送达成功；前端 API 类型同步收紧为受控动作与显式降级字段。
- 红绿证据：新增 `feedback_SucceedsAndPublishesAudit` 对 `callbackDelivered=false` / `HOST_CALLBACK_NOT_CONFIGURED` 的断言先编译失败于旧响应字段缺失；新增 `feedback_RejectsUnsupportedActionTypeBeforeAuditing` 覆盖旧逻辑会接受 `CALLBACK_SUCCESS` 并写成功审计的缺口；新增 `feedback_UnusedTokenRejectsCallbackWithoutSuccessAudit` 与 `validateAndExchange_AtomicConsumeReplayLosesRaceThrowsUsedWithoutSuccessAudit` 锁定未消费令牌反馈和并发重放消费都只失败审计、不发布成功审计。实现后目标测试转绿。
- 本地聚焦验证：`mvn -q -Dtest=EmbedEngineServiceTest#feedback_SucceedsAndPublishesAudit+feedback_RejectsUnsupportedActionTypeBeforeAuditing+feedback_UnusedTokenRejectsCallbackWithoutSuccessAudit+validateAndExchange_AtomicConsumeReplayLosesRaceThrowsUsedWithoutSuccessAudit test` 通过；`mvn -q -Dtest=EmbedEngineServiceTest,EmbedEngineControllerTest,EmbedEngineControllerSecurityTest,ClinicalEventContractTest test` 通过。
- 本地全量验证：`mvn -q test` 通过，H2 / PostgreSQL 15.18 / Oracle 21.3 均迁移至 V71 并二次 no-op；`npm run verify` 首次受既有 `ConfigPackages.test.tsx` 离线导出并发 flaky 超时影响，单跑 `npm test -- src/pages/tenant/ConfigPackages.test.tsx` 12/12 通过后重跑 `npm run verify`，51 文件 / 311 测试通过；`npm run build` 通过，既有 React Router / act warning 与 `vendor-antd` 大 chunk 警告仍归 [DEFER-003](../../audit/deferred-issues.md)。
- T-GATE：脚本自测 34/34、真实性全量扫描 953 文件、配置边界 inventory 扫描 891 文件、迁移 changed 扫描 0 文件、中文注释 0 fail / 0 warn、工作区 `git diff --check` 通过；提交后复跑真实性 changed 扫描 5 文件、配置边界 changed 扫描 4 文件、迁移 changed 扫描 0 文件、中文注释 0 fail / 0 warn 与 `git diff --check origin/main..HEAD`，均通过。`docs/backlog.md` 的 `EMBED-01` 同步标 done，整卡本地收口；远端 CI / 合并仍以 PR 门禁为准。
