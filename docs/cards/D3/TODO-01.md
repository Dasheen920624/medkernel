# TODO-01 · 待办中心页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · 详规 待办协同 · 体验规范 §3 低打扰。
> 实化映射：占位 `D3-PAGE-待办中心` → 本卡 **TODO-01**。

## 身份
- 卡 ID：TODO-01（页面卡；= backlog `D3-PAGE-待办中心` 实化）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[SVC-CLINICAL-03](SVC-CLINICAL-03.md)（待办聚合后端）· [MED-C3](MED-C3.md)（安全复核任务）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) · [API-13](../D0/API-13.md) · [INFRA-09](../D1/INFRA-09.md)
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把待办中心页**真实化**：统一呈现多源待办（CDSS 复核/随访/安全复核），按责任人/截止/来源闭环处理，**不前端造待办**，低打扰。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/clinical/WorkflowTodos.tsx`（路由 `/workflow/todos` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接 [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 统一待办 API + 六态/五维 RBAC 齐全。

## PR1 实施边界（2026-06-04，本地完整验证）
- 已实现：`WorkflowTodos.tsx` 删除旧“接口尚未接入”占位，改为消费 `useWorkflowTodos` / `useCompleteWorkflowTodo`，展示来源、患者、责任人、截止、优先级、状态、来源跳转和完成弹窗。
- 已覆盖：随访任务、安全复核、推荐卡三类真实来源；安全复核与高风险待办在后端分页层置顶，前端当前页再次按同口径排序；完成说明必填并通过后端持久化；列表请求使用后端 1 基分页并支持 `patientId` 过滤。
- 未冒领：转交操作、护理 / 报告 / 床旁知识来源展示仍需后续 PR 接入真实来源；当前页面不造待办，不用本地示例兜底。

## PR2 实施边界（2026-06-04，PR #371 已合并）
- 已实现：待办中心新增转交入口，弹窗收集接收人、接收角色和转交说明，调用 `POST /api/v1/engine/workflow/todos/{todoId}/transfer` 后刷新后端分页数据；转交说明必填，不能在页面本地伪改状态。
- 已覆盖：`PENDING` / `IN_PROGRESS` 待办可转交为 `TRANSFERRED`，后端持久化 `transferredTo` / `transferReason` 并更新责任人 / 角色；列表可展示已转交给谁和转交原因，仍保留安全复核 / 高风险排序保护。
- 未冒领：护理 / 报告 / 床旁知识真实来源仍未接入；转交不代表外发通知已投递。

## PR3 实施边界（2026-06-04，本地验证）
- 已实现：完成 / 转交待办后由后端生成 `WORKFLOW_TODO` 站内通知，前端待办中心仍只通过后端状态刷新，不在浏览器本地追加通知。
- 已覆盖：完成回执低打扰通知发给当前责任人；转交通知按待办优先级发给新责任人，保留来源链接与 traceId，供通知中心继续处理已读回执。
- 未冒领：站内通知不等于短信 / 邮件 / Webhook 或院内消息已投递；护理 / 报告 / 床旁知识来源仍待真实上游接入。

## PR5 实施边界（2026-06-04，本地验证）
- 已实现：待办中心可展示由真实推荐卡类型细分出的护理任务、报告解读、床旁知识来源；后端按 `recommendation_card.card_type` 和报告触发语义投影 `NURSING_TASK` / `REPORT_INTERPRETATION` / `BEDSIDE_KNOWLEDGE`，前端来源列与筛选项均显示中文。
- 已覆盖：推荐卡派生来源按 `cardId` 去重，避免历史普通推荐卡待办与新细分来源重复；页面不暴露 `FOLLOWUP_TASK`、`NURSING_TASK` 等裸枚举，仍只依赖统一待办 API 刷新。
- 未冒领：本 PR 不接护理站、LIS / PACS 或外部床旁知识系统直连；若没有落库为推荐卡类型，不在前端造来源待办。外发通知投递仍未接通。

## 功能要求（原子可测条目）
- [ ] FR-1 统一待办：列多源待办（来源/责任人/截止/优先级），数据真实（[API-13](../D0/API-13.md) 分页）。
- [ ] FR-2 安全优先：安全复核任务（[MED-C3](MED-C3.md)）置顶高优先、不可忽略。
- [ ] FR-3 闭环处理：待办流转到完成/转交，状态机闭环。
- [ ] FR-4 跳转来源：每条待办可跳来源页（提醒/患者/路径）。
- [ ] FR-5 六态 + 五维 RBAC：齐全；按责任人/科室作用域；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 待办 API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `clinical-run` / menuKey `workflow-todos` / menuLabel `待办中心` / path `/workflow/todos` / requiredPermissions 待办处理 / requiredRoles 临床医生·护理·科主任。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 待办列表（优先级排序、安全置顶）+ 处理抽屉 + 六态。
- 主按钮 ≤1（完成待办）/ 默认筛选 ≤3（我的/今日/高优先）/ 默认角色视图（本人）。
- 五维 RBAC：菜单 / 动作（完成/转交）/ 数据（org/本人）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：临床各源任务的"统一处理入口"。
2. 产品体验：★一处理多源、安全置顶、低打扰；国产浏览器可读。
3. 系统与数据架构：待办分页 P95 ≤1s；优先级排序稳定。
4. 临床医疗安全：★安全复核任务（[MED-C3](MED-C3.md)）高优先不漏。
5. 知识与数据治理：待办来源可追溯。
6. 安全合规与监管：待办完成/转交留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按责任人/科室 + `OrgContext` 作用域。
8. 集成与互操作：跳转来源页深链。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端造待办、去重正确、闭环可审计；无演示路由（[INFRA-09](../D1/INFRA-09.md)）。
11. AI / 模型治理与可降级：N·A（确定性页面；智能摘要为 wave2 挂点）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §13 低打扰** · **§5 状态机闭环** · **§9 多租户作用域**。
- 本卡落点：把待办中心页变为接真实多源待办、安全置顶、闭环可审计的处理入口。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：多源待办真实；安全复核置顶。
- [ ] AC-2（FR-3/4）：闭环处理；跳转来源正确。
- [ ] AC-3（FR-5）：六态齐全；越权不可处理他人待办。
- 关联 A1–A9 剧本：A6 协同待办 · A8 安全复核。
- T-GATE：前端真实性门禁全绿（no-page-mock、无造待办、无演示路由）。
- B0 验收：N·A（确定性页面）。

## 完工证据
- 代码 permalink：PR 创建后补充 `pages/clinical/WorkflowTodos` 真实化链接。
- 测试（PR1 本地）：`npm run verify`；`npm run build`；Browser 复验 `/workflow/todos` 未登录重定向 `/login` 且控制台 error 为空；`WorkflowTodos.test.tsx` 覆盖真实 API 渲染、安全复核置顶、来源跳转、1 基分页和完成回写；`pages.smoke.test.tsx` 覆盖页面空态。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
