# SVC-CLINICAL-03 · 临床协同服务包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · 详规 待办/通知协同 · 落地规划 §服务包。

## 身份
- 卡 ID：SVC-CLINICAL-03（服务包卡；待办/通知协同单一归属）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行 · S12 智能随访
- 依赖卡：[FOLLOW-01](FOLLOW-01.md) 随访 · [MED-C3](MED-C3.md) 复核任务 · [CDSS-01](CDSS-01.md) 提醒 · 页 [TODO-01](TODO-01.md)/[NOTIFY-01](NOTIFY-01.md)
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把**待办 / 通知 / 护理协同 / 报告解读 / 床旁知识 / 随访触发**编排为临床协同服务包：把各引擎产生的任务/事件汇成统一待办与通知，闭环可追、低打扰、按角色作用域。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
部分基础：随访任务（`engine/followup`）、提醒（`engine/recommendation`）、安全复核任务（[MED-C3](MED-C3.md)）各有来源；**缺统一待办/通知汇聚层**。本卡＝建统一待办/通知聚合 + 协同编排（护理/报告解读/床旁知识/随访触发），各源任务单一注入、不重造。

## PR1 实施边界（2026-06-04，本地完整验证）
- 已实现：新增 `engine/workflow` 统一协同服务包、五方言 V77 迁移、权限 / 服务契约 / 领域归属；`GET /api/v1/engine/workflow/todos`、`POST /workflow/todos/{id}/complete`、`GET /api/v1/engine/notifications`、`POST /notifications/{id}/read` 均走真实关系库。
- 已覆盖来源：随访任务（`FOLLOWUP_TASK`）、安全撤回复核任务（`SAFETY_REVIEW`）、待处理 / 已查看 / 延后处理的推荐卡（`RECOMMENDATION_CARD`）投影为统一待办；随访异常通知事件投影为通知并按 `dedupeKey` 去重，收件人来自随访任务执行人而非页面查询用户；待办仓储已覆盖 `patientId` 过滤与随访投影。
- 已覆盖闭环：待办完成持久化 `completionReason/completedBy/completedAt`；通知单条已读与当前页全部已读均回写后端；待办分页层安全复核 + 高风险优先排序，前端再做同口径展示保护。
- 未冒领范围：护理任务、报告解读、床旁知识卡、通知免打扰设置与外发投递仍属于本卡后续 PR 或 X-DOMAIN/wave2 接入点；不是外部环境阻塞，后续必须继续实现，不得写成已完成。

## PR2 实施边界（2026-06-04，PR #371 已合并）
- 已实现：新增待办转交端点 `POST /api/v1/engine/workflow/todos/{todoId}/transfer`，待办状态可从 `PENDING` / `IN_PROGRESS` 流转到 `TRANSFERRED`，并持久化 `transferredTo`、`transferReason`、新责任人 / 角色、traceId 与更新时间；新增通知偏好 `GET/PUT /api/v1/engine/notifications/settings`，复用 `mk_experience_user_pref` 存取个人免打扰与通道偏好，不新增伪投递表。
- 已覆盖闭环：前端待办中心提供转交弹窗并回写后端；通知设置页从后端加载 / 保存偏好，明确 CRITICAL / HIGH 不被免打扰静默，外部通道当前只保存个人偏好，不伪造短信 / 院内消息投递成功。
- 验证证据：本地 `npm run verify`、`mvn -q clean test`、`npm run build`、`npm audit --omit=dev --audit-level=moderate`、changed-mode T-GATE 与远端 CI 8/8 通过；PostgreSQL + Oracle 校验并应用到 V80。达梦 / 人大金仓真实运行按当前阶段边界后置，不作为本 PR 阻塞。
- 未冒领范围：护理任务、报告解读、床旁知识卡、待办派生通知、同步事件通知与真实外发投递仍待后续 PR；缺上游真实来源时登记待处理问题后继续下一可测闭环。

## PR3 实施边界（2026-06-04，本地验证）
- 已实现：待办完成 / 转交成功后派生 `WORKFLOW_TODO` 站内通知，使用 `todo:<todoId>:completed` 与 `todo:<todoId>:transferred:<recipient>` 去重，全部写入 `mk_engine_notification`；完成回执为 `INFO` 低打扰通知，转交通知按待办优先级映射为 CRITICAL/HIGH/MEDIUM/LOW，安全优先级不被静默。
- 已覆盖闭环：完成通知发给当前责任人（缺责任人时回退当前操作者），转交通知发给新责任人；通知保留患者 / 就诊 / deepLink / traceId，可在通知中心以“协同待办”来源展示并走既有已读回执。
- 未冒领范围：本 PR 只做站内通知派生，不接短信 / 邮件 / Webhook / 院内消息外发，不声明同步事件通知、护理任务、报告解读、床旁知识卡已接通。

## 功能要求（原子可测条目）
- [ ] FR-1 统一待办：CDSS 复核/随访/安全复核（[MED-C3](MED-C3.md)）任务统一进待办，带责任人/截止/来源。
- [ ] FR-2 通知：待办/异常/同步事件转通知，去重 + 低打扰 + 已读回执。（待办/随访异常已覆盖，外发与同步事件待后续）
- [ ] FR-3 协同：护理任务、报告解读、床旁知识卡按上下文呈现。
- [ ] FR-4 随访触发：随访（[FOLLOW-01](FOLLOW-01.md)）任务汇入待办、异常回院转通知。
- [ ] FR-5 闭环：每条待办可流转到完成，状态机闭环可审计。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET /api/v1/engine/workflow/todos` · `POST .../workflow/todos/{id}/complete` · `POST .../workflow/todos/{id}/transfer` · `GET .../notifications` · `POST .../notifications/{id}/read` · `GET/PUT .../notifications/settings`
- DTO：待办/通知 Record（来源/责任人/截止/状态/组织字段）；信封 `ApiResult`/`ProblemDetail` + 大列表 [API-13](../D0/API-13.md)
- 状态机：待办类（待处理→进行中→完成/转交）
- 幂等 / traceId：任务注入幂等键；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 表族：`workflow_todo` + `notification`（来源引用 + 责任人 + 状态 + 组织字段 + 审计）；五方言（[BASE-05](../D0/BASE-05.md)）
- 唯一约束：来源+对象去重防重复待办；索引：责任人/状态/截止

## 视角清单（11 视角逐条）
1. 产品架构：临床各源任务/事件的"统一协同枢纽"。
2. 产品体验：低打扰、去重、一处理多源（页 [TODO-01](TODO-01.md)/[NOTIFY-01](NOTIFY-01.md)）。
3. 系统与数据架构：待办聚合 10万级；通知去重；P95 ≤1s。
4. 临床医疗安全：安全复核任务（[MED-C3](MED-C3.md)）高优先、不漏不重。
5. 知识与数据治理：床旁知识卡追溯版本（[KNOW-01](../D2/KNOW-01.md)）。
6. 安全合规与监管：待办/通知/完成留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：待办/通知按角色 + `OrgContext`/科室作用域。
8. 集成与互操作：通知可经 [INTEG-01](../D2/INTEG-01.md) 外发（短信/院内消息）。
9. 运维 / SRE / 国产化：通知投递可观测、可重试。
10. 质量与真实性审计：★无伪造待办、去重正确、闭环可审计。
11. AI / 模型治理与可降级：N·A（协同确定性；智能摘要为 wave2 挂点）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §13 低打扰** · **§5 状态机闭环** · **§9 多租户作用域**。
- 本卡落点：统一待办/通知协同枢纽，各源任务单一注入。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：多源任务统一进待办；通知去重 + 已读回执。
- [ ] AC-2（FR-3/4）：协同卡按上下文；随访/异常汇入。
- [ ] AC-3（FR-5）：待办闭环可审计。
- 关联 A1–A9 剧本：A6 协同待办 · A8 安全复核。
- T-GATE：前后端真实性门禁全绿（无伪造待办 / 去重正确）。
- B0 验收：关模型待办/通知协同全可用。

## 完工证据
- 代码 permalink：PR 创建后补充统一待办/通知聚合 + TODO/NOTIFY 页面真实化链接。
- 测试（PR1 本地）：`mvn -q test`；`npm run verify`；`npm run build`；`npm audit --omit=dev --audit-level=moderate`（0 漏洞）；Browser 复验 `/workflow/todos`、`/notifications` 未登录均重定向 `/login` 且控制台 error 为空。目标用例覆盖 `WorkflowCollaborationServiceTest`、`WorkflowTodoRepositoryTest`、`RecommendationRepositoryTest`、`MigrationBaselineContractTest`、`H2BaselineMigrationTest`、`FlywayMultiDialectSmokeTest`、前端 `WorkflowTodos.test.tsx` / `Notifications.test.tsx` / `hooks.test.ts`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
