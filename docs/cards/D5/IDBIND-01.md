# IDBIND-01 · 身份绑定页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D5 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S14 用户、权限与合规 · 详规 身份绑定/SSO · 体验规范 §3。
> 实化映射：占位 `D5-PAGE-身份绑定` → 本卡 **IDBIND-01**。

## 身份
- 卡 ID：IDBIND-01（页面卡；= backlog `D5-PAGE-身份绑定` 实化）
- 域：D5 合规运维
- 关联场景：S14 用户、权限与合规
- 依赖卡：[SVC-COMPLIANCE-01](SVC-COMPLIANCE-01.md)（身份绑定后端）· [AUTH-01](../D0/AUTH-01.md)（双模身份）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：2d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把身份绑定页**真实化**：配置/解绑外部身份（SSO/工号/CA），绑定关系真实可审计，全部接 [SVC-COMPLIANCE-01](SVC-COMPLIANCE-01.md)，**不前端伪造绑定**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/compliance/IdentityBinding.tsx`（路由 `/security/identity-binding` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接身份绑定 API（[SVC-COMPLIANCE-01](SVC-COMPLIANCE-01.md)）+ 六态/五维 RBAC 齐全。

## 功能要求（原子可测条目）
- [ ] FR-1 绑定列表：列用户↔外部身份绑定关系，真实。
- [ ] FR-2 绑定/解绑：配置 SSO/工号/CA 绑定，解绑需校验，操作留审计。
- [ ] FR-3 冲突检测：一外部身份不可重复绑多账号（或按策略），冲突提示。
- [ ] FR-4 六态：加载/空/错误/无权限/部分成功/正常齐全（[BASE-08](../D0/BASE-08.md)）。
- [ ] FR-5 五维 RBAC：仅管理员可配；数据按 `OrgContext` 隔离。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [SVC-COMPLIANCE-01](SVC-COMPLIANCE-01.md) 身份绑定 API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `compliance-ops` / menuKey `identity-bindings` / menuLabel `身份绑定` / path `/security/identity-binding` / requiredPermissions `menu.identity-bindings + org.read` / requiredRoles 信息科与平台·集团·医院管理员；绑定/解绑另需 `org.write`。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 绑定列表 + 绑定/解绑表单 + 冲突提示 + 六态。
- 主按钮 ≤1（新增绑定）/ 默认筛选 ≤3 / 默认角色视图（管理员）。
- 五维 RBAC：菜单 / 动作（绑定/解绑）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-COMPLIANCE-01](SVC-COMPLIANCE-01.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：外部身份接入的"绑定管理"页。
2. 产品体验：绑定/解绑清晰、冲突提示；国产浏览器可读。
3. 系统与数据架构：绑定查询 P95 ≤1s。
4. 临床医疗安全：N·A。
5. 知识与数据治理：N·A。
6. 安全合规与监管：★绑定/解绑留审计（[BASE-04](../D0/BASE-04.md)）；解绑校验防越权。
7. 集团化与多租户治理：绑定按租户隔离。
8. 集成与互操作：★对接外部 SSO/工号/CA（[AUTH-01](../D0/AUTH-01.md) 双模）。
9. 运维 / SRE / 国产化：国产 CA/SSO 兼容。
10. 质量与真实性审计：★无前端伪造绑定、冲突真实检测；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §6 安全** · **§9 多租户隔离** · **依赖 [SVC-COMPLIANCE-01](SVC-COMPLIANCE-01.md)**。
- 本卡落点：把身份绑定页变为接真实绑定/解绑、冲突检测、可审计的管理页。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：绑定/解绑真实、留审计。
- [ ] AC-2（FR-3）：冲突真实检测。
- [ ] AC-3（FR-4/5）：六态齐全；越权不可配、按租户隔离。
- 关联 A1–A9 剧本：A1 身份绑定。
- T-GATE：前端真实性门禁全绿（no-page-mock、无伪造绑定）。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码 permalink：`pages/compliance/IdentityBinding` 真实化 + 接 [SVC-COMPLIANCE-01](SVC-COMPLIANCE-01.md) + 六态。
- 测试：绑定/解绑/冲突/隔离 + 六态 + RBAC + no-page-mock 门禁。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
