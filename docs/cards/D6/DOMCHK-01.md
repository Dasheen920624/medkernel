# DOMCHK-01 · 国产化自检页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D6 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 国产化 · 核心 §运维/国产化交付 · 体验规范 §3。
> 实化映射：占位 `D6-PAGE-国产化自检` → 本卡 **DOMCHK-01**。

## 身份
- 卡 ID：DOMCHK-01（页面卡；= backlog `D6-PAGE-国产化自检` 实化）
- 域：D6 高级工具
- 关联场景：运维 / 国产化交付
- 依赖卡：[CONFIG-01](../D0/CONFIG-01.md)（配置）· [SVC-COMPLIANCE-02](../D5/SVC-COMPLIANCE-02.md)（运行状态）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：2d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把国产化自检页**真实化**：自检国产数据库/中间件/浏览器/CA/操作系统兼容性，**结果真实、不假绿灯**，给出不兼容项与建议。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/advanced/DomesticCheck.tsx`（路由 `/advanced/domestic` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接国产化自检 API（待建为主）+ 六态/RBAC；**自检结果真实探测，不假绿灯**。

## 功能要求（原子可测条目）
- [ ] FR-1 自检项：库（达梦/人大金仓等）/中间件/国产浏览器/CA/OS 自检项受控。
- [ ] FR-2 真实探测：每项真实探测连通/版本/兼容，**不假绿灯**。
- [ ] FR-3 不兼容提示：不兼容项明确原因 + 建议，可导出报告。
- [ ] FR-4 六态：加载/空/错误/无权限/部分成功/正常齐全（[BASE-08](../D0/BASE-08.md)）。
- [ ] FR-5 RBAC：信息科/运维可见；数据按 `OrgContext`；不入客户主菜单。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费国产化自检 API（[SVC-COMPLIANCE-02](../D5/SVC-COMPLIANCE-02.md) 运行状态相邻）。
### 页面契约（页面卡）
- 路由元数据：sectionKey `advanced` / menuKey `domestic-check` / menuLabel `国产化自检` / path `/advanced/domestic` / requiredPermissions 国产化自检 / requiredRoles 信息科·运维。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 自检项列表（状态色阶 token）+ 不兼容详情 + 报告导出 + 六态。
- 主按钮 ≤1（重新自检）/ 默认筛选 ≤3（全部/不兼容/警告）/ 默认角色视图（信息科）。
- 五维 RBAC：菜单 / 动作（自检/导出）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费自检后端。

## 视角清单（11 视角逐条）
1. 产品架构：国产化交付的"自检"工具页。
2. 产品体验：自检结果直观、不兼容醒目；★国产浏览器/老年模式可读。
3. 系统与数据架构：自检异步；P95 ≤2s。
4. 临床医疗安全：N·A（运维工具）。
5. 知识与数据治理：N·A。
6. 安全合规与监管：自检报告作交付证据（[BASE-04](../D0/BASE-04.md) 审计）。
7. 集团化与多租户治理：按院/平台作用域。
8. 集成与互操作：探测国产库/中间件/CA 真实连通。
9. 运维 / SRE / 国产化：★国产化交付核心自检，离线可跑。
10. 质量与真实性审计：★真实探测、**不假绿灯**、不兼容有据；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（不假绿灯）** · **核心 §国产化** · **技术对象不入主路径**。
- 本卡落点：把国产化自检页变为接真实探测、不假绿灯、不兼容有据的自检工具。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：自检项真实探测、不假绿灯。
- [ ] AC-2（FR-3）：不兼容有原因 + 建议、可导出。
- [ ] AC-3（FR-4/5）：六态齐全；信息科可见、不入客户主菜单。
- 关联 A1–A9 剧本：A9 国产化自检。
- T-GATE：前端真实性门禁全绿（no-page-mock、不假绿灯）。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码 permalink：`pages/advanced/DomesticCheck` 真实化 + 接自检 API + 六态。
- 测试：自检/真实探测/不兼容/报告 + 六态 + RBAC + no-page-mock 门禁。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
