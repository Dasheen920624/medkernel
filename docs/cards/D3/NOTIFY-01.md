# NOTIFY-01 · 通知中心页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · 详规 通知协同 · 体验规范 §3 低打扰。
> 实化映射：占位 `D3-PAGE-通知中心` → 本卡 **NOTIFY-01**。

## 身份
- 卡 ID：NOTIFY-01（页面卡；= backlog `D3-PAGE-通知中心` 实化）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[SVC-CLINICAL-03](SVC-CLINICAL-03.md)（通知后端）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [API-13](../D0/API-13.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：2d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把通知中心页**真实化**：聚合各源通知（待办/异常/同步/安全），去重、低打扰、已读回执，**不前端造通知**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/clinical/Notifications.tsx`（路由 `/notifications` 已注册 `app/router.tsx`；另有 `/notifications/settings` 设置页）。本卡＝去占位/mock + 接 [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 通知 API + 六态/五维 RBAC 齐全。

## PR1 实施边界（2026-06-04，本地完整验证）
- 已实现：`Notifications.tsx` 删除旧“通知接口尚未接入”占位，改为消费 `useWorkflowNotifications` / `useReadWorkflowNotification`，展示真实通知、级别、已读状态、患者 / 就诊上下文、来源跳转、单条已读和当前页全部已读。
- 已覆盖：随访异常通知事件从后端真实关系库投影，按 `dedupeKey` 去重，已读回执持久化；页面默认未读视图 + 级别筛选，低打扰原则下不展示前端造通知；列表请求使用后端 1 基分页。
- 未冒领：通知免打扰时段设置仍在 `/notifications/settings` 后续收口；待办派生通知、外发投递、同步事件通知需要后续 PR 按真实来源接入，不得伪造投递成功。

## PR2 实施边界（2026-06-04，PR #371 已合并）
- 已实现：`/notifications/settings` 删除静态假默认，改为通过 `GET/PUT /api/v1/engine/notifications/settings` 读取 / 保存个人通知偏好；偏好复用 `mk_experience_user_pref`，不新增外发投递假表。
- 已覆盖：免打扰启用、时段、CRITICAL/HIGH 绕过、院内 / 邮件 / 短信 / Webhook 通道偏好均回写后端；页面明确外部通道当前只保存个人偏好，真实投递需后续接入。
- 未冒领：待办派生通知、同步事件通知和真实外发投递仍需后续 PR；安全 / 危急通知不得被免打扰静默。

## PR3 实施边界（2026-06-04，本地验证）
- 已实现：通知中心可展示待办完成 / 转交派生的 `WORKFLOW_TODO` 通知，来源文案显示为“协同待办”，不再暴露裸枚举。
- 已覆盖：待办派生通知沿用既有列表、筛选、单条已读与当前页全部已读回执；完成回执为低打扰 `INFO`，转交事项保留原待办优先级。
- 未冒领：真实外发投递、同步事件通知、护理 / 报告 / 床旁知识来源通知仍未接通。

## PR4 实施边界（2026-06-04，本地验证）
- 已实现：通知中心可展示临床事件处理完成后的 `SYNC_EVENT` 站内通知，来源文案显示为“同步事件”，不暴露裸枚举。
- 已覆盖：同步事件通知来自真实 `clinical_event` 的 `PROCESSED` 状态，按 `clinical-event:<eventId>` 去重；页面沿用既有级别筛选、单条已读和当前页全部已读回执，通知内容保留患者 / 就诊 / traceId 与来源深链。
- 未冒领：同步事件站内通知不等于外部 Webhook / 短信 / 邮件 / 院内消息已投递；护理 / 报告 / 床旁知识来源通知仍未接通。

## 功能要求（原子可测条目）
- [ ] FR-1 通知聚合：列多源通知（来源/级别/时间），去重、真实（[API-13](../D0/API-13.md) 分页）。
- [ ] FR-2 已读回执：标记已读/全部已读，回执真实回写。
- [ ] FR-3 低打扰：按级别/类型筛选，免打扰时段设置（settings 页）。
- [ ] FR-4 跳转：通知可跳来源（待办/患者/路径）。
- [ ] FR-5 六态 + 五维 RBAC：齐全；按本人/科室作用域；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 通知 API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `clinical-run` / menuKey `notifications` / menuLabel `通知中心` / path `/notifications`（设置 `/notifications/settings`）/ requiredPermissions 通知查看 / requiredRoles 全临床角色（本人范围）。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 通知列表（级别色阶）+ 筛选 + 已读操作 + 六态。
- 主按钮 ≤1（全部已读；设置页为保存设置）/ 默认筛选 ≤3（未读/今日/高优先）/ 默认角色视图（本人）。
- 五维 RBAC：菜单 / 动作（已读/设置）/ 数据（本人/org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：临床通知的"统一收件箱"。
2. 产品体验：★低打扰、去重、免打扰时段；国产浏览器/老年模式可读。
3. 系统与数据架构：通知分页 P95 ≤1s；已读批量。
4. 临床医疗安全：安全/危急通知高级别不被免打扰静默。
5. 知识与数据治理：N·A。
6. 安全合规与监管：通知/已读留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按本人/科室 + `OrgContext` 作用域。
8. 集成与互操作：外发通知经 [INTEG-01](../D2/INTEG-01.md)（页面只读结果）。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端造通知、去重正确；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A（确定性页面）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §13 低打扰** · **§9 多租户作用域**。
- 本卡落点：把通知中心页变为接真实多源通知、去重低打扰、安全不静默的收件箱。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：通知真实去重；已读回执回写。
- [ ] AC-2（FR-3/4）：低打扰筛选/免打扰；跳转来源正确；安全通知不被静默。
- [ ] AC-3（FR-5）：六态齐全；越权不可见他人通知。
- 关联 A1–A9 剧本：A6 协同通知。
- T-GATE：前端真实性门禁全绿（no-page-mock、无造通知、无演示路由）。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码 permalink：PR 创建后补充 `pages/clinical/Notifications` 真实化链接。
- 测试（PR1 本地）：`npm run verify`；`npm run build`；Browser 复验 `/notifications` 未登录重定向 `/login` 且控制台 error 为空；`Notifications.test.tsx` 覆盖真实通知渲染、1 基分页、单条已读、当前页全部已读；`hooks.test.ts` 覆盖接口路径。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
