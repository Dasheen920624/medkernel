# PROVIDER-01 · 运行状态页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D5 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S14 用户、权限与合规 · 核心 §11 B0 / 诚实降级 · 详规 Provider/模型状态。
> 实化映射：原占位 `D5-PAGE-Provider 状态` → 当前 **PROVIDER-01 运行状态**。

## 身份
- 卡 ID：PROVIDER-01（页面卡；运行状态）
- 域：D5 合规运维
- 关联场景：S14 用户、权限与合规
- 依赖卡：[SVC-COMPLIANCE-02](SVC-COMPLIANCE-02.md)（运行状态后端）· [CONFIG-01](../D0/CONFIG-01.md)（运行配置）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：2d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把运行状态页**真实化**：普通视图呈现依赖健康、备份恢复就绪和国产化摘要；技术路径、方言、脚本、配置来源与风险枚举只在授权专家模式展示。外部依赖无连接诚实显示 `NOT_CONNECTED`/`MODEL_DISABLED`，绝不伪造绿灯。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面实现为 `pages/compliance/SystemProviders.tsx`（路由 `/system/providers`）。本卡收口真实运行状态、普通/专家视图边界、六态和五维 RBAC。

## 功能要求（原子可测条目）
- [ ] FR-1 状态列表：列数据库、备份恢复、模型工作流、图投影等依赖真实健康状态。
- [ ] FR-2 诚实降级：未连接/不可用标 `NOT_CONNECTED`/`MODEL_DISABLED`，**不伪造绿灯**。
- [ ] FR-3 视图分层：普通视图只呈现可行动摘要；授权专家模式显示部署、迁移、脚本和配置来源。
- [ ] FR-4 六态：加载/空/错误/无权限/部分成功/正常齐全（[BASE-08](../D0/BASE-08.md)）。
- [ ] FR-5 五维 RBAC：信息科/管理员可见；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 `GET /api/v1/system/operations`。
### 页面契约（页面卡）
- 路由元数据：sectionKey `compliance-ops` / menuKey `system-providers` / menuLabel `运行状态` / path `/system/providers`。
- 结构：PageExperienceShell + 健康摘要 + 依赖列表 + 备份恢复就绪 + 国产化摘要 + 授权专家模式。
- 主按钮 ≤1（重新探测）/ 默认筛选 ≤3（全部/异常/模型）/ 默认角色视图（信息科）。
- 五维 RBAC：菜单 / 动作（探测）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px（状态色用 token）。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-COMPLIANCE-02](SVC-COMPLIANCE-02.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：外部依赖健康的"运维看板"页。
2. 产品体验：状态一目了然、异常醒目；国产浏览器可读。
3. 系统与数据架构：状态探测异步；P95 ≤1s。
4. 临床医疗安全：模型不可用诚实显示，避免临床误信"在线"。
5. 知识与数据治理：N·A。
6. 安全合规与监管：状态/探测留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按院/平台作用域。
8. 集成与互操作：★各外部 Provider 真实健康探测。
9. 运维 / SRE / 国产化：★无连接诚实 `NOT_CONNECTED`；国产化离线场景。
10. 质量与真实性审计：★绝不伪造连接/绿灯、状态真实探测；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：★模型 Provider 不可用诚实 `MODEL_DISABLED`。

## 适用不变量
- 命中核心约束：**铁律 #1/#2 真实性（不伪造连接）** · **核心 §11 B0 诚实降级** · **§运维/国产化**。
- 本卡落点：把运行状态页变为异常优先、技术细节分层、无连接诚实标记的运维看板。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：状态真实；未连接标 `NOT_CONNECTED`、不伪造绿灯。
- [ ] AC-2（FR-3）：健康详情真实。
- [ ] AC-3（FR-4/5）：六态齐全；按作用域。
- 关联 A1–A9 剧本：A9 Provider 运维。
- T-GATE：前端真实性门禁全绿（no-page-mock、无伪造连接）。
- B0 验收：Provider 不可用诚实降级展示。

## 完工证据
- 代码 permalink：`pages/compliance/SystemProviders` 真实化 + 接 [SVC-COMPLIANCE-02](SVC-COMPLIANCE-02.md) + 六态。
- 测试：状态/NOT_CONNECTED/健康详情 + 六态 + RBAC + no-page-mock 门禁（含 `SystemProviders.test.tsx`）。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
