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
- 已覆盖闭环：前端待办中心提供转交弹窗并回写后端；通知设置页从后端加载 / 保存偏好，明确 CRITICAL / HIGH 不被免打扰静默；PR2 阶段仅保存外部通道个人偏好，不伪造短信 / 邮件 / 移动推送投递成功。
- 验证证据：本地 `npm run verify`、`mvn -q clean test`、`npm run build`、`npm audit --omit=dev --audit-level=moderate`、changed-mode T-GATE 与远端 CI 8/8 通过；PostgreSQL + Oracle 校验并应用到 V80。达梦 / 人大金仓真实运行按当前阶段边界后置，不作为本 PR 阻塞。
- 未冒领范围：护理任务、报告解读、床旁知识卡、待办派生通知、同步事件通知与真实外发投递仍待后续 PR；缺上游真实来源时登记待处理问题后继续下一可测闭环。

## PR3 实施边界（2026-06-04，本地验证）
- 已实现：待办完成 / 转交成功后派生 `WORKFLOW_TODO` 站内通知，使用 `todo:<todoId>:completed` 与 `todo:<todoId>:transferred:<recipient>` 去重，全部写入 `mk_engine_notification`；完成回执为 `INFO` 低打扰通知，转交通知按待办优先级映射为 CRITICAL/HIGH/MEDIUM/LOW，安全优先级不被静默。
- 已覆盖闭环：完成通知发给当前责任人（缺责任人时回退当前操作者），转交通知发给新责任人；通知保留患者 / 就诊 / deepLink / traceId，可在通知中心以“协同待办”来源展示并走既有已读回执。
- 未冒领范围：本 PR 只做站内通知派生，不接短信 / 邮件 / Webhook / 院内消息外发，不声明同步事件通知、护理任务、报告解读、床旁知识卡已接通。

## PR4 实施边界（2026-06-04，本地验证）
- 已实现：通知中心接入临床同步事件站内通知来源 `SYNC_EVENT`；查询通知前从真实 `clinical_event` 表投影 `PROCESSED` 事件，使用 `clinical-event:<eventId>` 去重并写入 `mk_engine_notification`，V81 迁移扩展通知来源约束。
- 已覆盖闭环：同步事件通知为 `INFO` 低打扰、组织范围通知（不错误归属给首个查询人），保留患者 / 就诊 / traceId / 来源系统 / 触发点文案与 `/rule/validate?eventId=...` 深链；通知中心来源显示“同步事件”且不暴露裸枚举。
- 未冒领范围：本 PR 只做站内通知，不声明 Webhook / 短信 / 邮件 / 院内消息真实投递；临床事件回调外发继续由集成层登记并诚实返回 `NOT_CONNECTED`，护理任务、报告解读、床旁知识卡仍待真实上游接入。

## PR5 实施边界（2026-06-04，本地验证）
- 已实现：统一待办从真实 `recommendation_card.card_type` 与触发点 / 场景码细分协同来源：`NURSING` 推荐卡投影为 `NURSING_TASK`，`KNOWLEDGE` 推荐卡投影为 `BEDSIDE_KNOWLEDGE`，`EXAM` / `LAB` 且触发点或场景明确为报告 / 结果 / 诊断报告语义时投影为 `REPORT_INTERPRETATION`；普通推荐卡仍为 `RECOMMENDATION_CARD`。
- 已覆盖闭环：推荐卡来源细分仍复用同一待办状态机、完成 / 转交 / 通知派生和 traceId；新增推荐卡派生来源去重查询，避免同一 `cardId` 在旧 `RECOMMENDATION_CARD` 与新细分来源之间重复生成待办；待办中心来源列和筛选项显示中文，不暴露裸枚举。
- 未冒领范围：本 PR 不声明护理站任务系统、LIS / PACS 报告解读外部系统或独立床旁知识外部通道已直连；只接入已在关系库落地的推荐卡真实类型。外发投递仍只由集成层登记并诚实 `NOT_CONNECTED`，短信 / 邮件 / Webhook / 院内消息真实投递未接通。

## PR6 实施边界（2026-06-04，本地验证）
- 已实现：待办完成 / 转交派生通知与随访异常新通知保存成功后，按通知接收人的个人偏好登记短信 / 邮件 / 移动推送出站补偿消息，统一调用 `IntegrationService.enqueueOutboundMessage`，消息 ID 为 `notify-out-<channel>-<notificationId>`，不新增真实发送连接器。
- 已覆盖闭环：外发补偿 payload 只保留通知 ID、来源、级别、接收人、深链与 traceId，不写入患者号或通知正文；INFO 级别在接收人免打扰命中时不登记外发补偿，HIGH / CRITICAL 按既有绕过级别继续允许登记。集成层无连接器时诚实落库 `NOT_CONNECTED`、`compensationRequired=true`、`blocksMainFlow=false`，供集成日志 / 死信重放继续追踪。
- 未冒领范围：本 PR 不声明短信 / 邮件 / 移动推送 / Webhook / 院内消息已真实投递，不接护理站、LIS / PACS 或独立床旁知识外部系统；达梦 / 人大金仓真实运行仍按当前阶段边界后置。

## PR7 实施边界（2026-06-04，本地验证）
- 已实现：真实来源首次投影为统一待办、或历史已投影待办缺少创建通知时，按 `todo:<todoId>:created` 幂等补齐 `WORKFLOW_TODO` 站内“待办待处理”通知；覆盖随访任务、安全复核任务、推荐卡及其护理 / 报告解读 / 床旁知识细分来源。
- 已覆盖闭环：待办创建通知沿用待办优先级、责任人 / 角色、患者 / 就诊上下文、deepLink 与 traceId；已有去重键时不重复通知。存在接收人且其通道偏好开启时，继续复用 PR6 外发补偿登记；无接收人的组织型推荐卡通知只留站内，不伪造外部投递。
- 未冒领范围：本 PR 不接护理站、LIS / PACS、独立床旁知识系统，也不声明短信 / 邮件 / 移动推送 / Webhook / 院内消息真实送达；外部连接器仍只能诚实 `NOT_CONNECTED`。

## PR8 实施边界（2026-06-05，本地验证）
- 已实现：待办 / 通知列表默认不再按租户全量返回，改为“当前用户个人项 + 未指定个人责任人 / 接收人的组织范围项”；显式传 `assigneeId` / `recipientId` 时仍按显式条件查询。
- 已覆盖闭环：完成 / 转交待办、标记通知已读前校验个人可见性；他人个人待办 / 通知按 `notFound` 处理且不保存状态、不生成通知、不写外发补偿，避免同租户串读或误处理。
- 未冒领范围：本 PR 仍未实现科室 / 组织树闭包级筛选，也不声明护理站、LIS / PACS、独立床旁知识系统或真实外发连接器已接通；组织范围项仅指当前表中未绑定个人责任人的站内项。

## PR9 实施边界（2026-06-05，本地验证）
- 已实现：待办完成、待办转交、通知标记已读在真实状态保存后统一调用 [BASE-04](../D0/BASE-04.md) `AuditRecorder` 写入 `UPDATE` 审计事件，目标分别为 `workflow_todo` / `workflow_notification`。
- 已覆盖闭环：审计 before / after 快照只记录协同元数据（状态、来源类型、责任人 / 接收人、traceId、转交说明等），不写入患者 ID、通知正文、待办标题 / 摘要；PR8 的越权拒绝路径仍不保存状态、不派生通知、不登记外发补偿，也不写成功审计。
- 未冒领范围：本 PR 不新增审计查询页、不接短信 / 邮件 / 移动推送 / Webhook / 院内消息真实投递，不实现科室 / 组织树闭包级筛选；达梦 / 人大金仓真实运行继续按当前范围后置。

### PR10 组织闭包级待办 / 通知池（2026-06-04）
- 已实现：`workflow_todo` 与 `notification` 增加 `orgUnitId` 组织池归属字段；列表与完成 / 转交 / 已读操作均通过数据库侧可见性查询收敛为当前用户个人项 + 未指定个人且在当前 `OrgContext` 祖先 / 后代闭包内的组织项，`orgUnitId` 为空时保留为租户级组织项。
- 已覆盖闭环：同租户兄弟科室的组织型待办 / 通知不再默认可见，单条操作返回 `notFound` 且不保存状态、不派生通知、不登记外发补偿、不写成功审计；新增组织作用域索引支撑待办 / 通知分页。
- 未冒领范围：页面本 PR 不新增科室树筛选或外部投递回执，不声明护理站、LIS / PACS、独立床旁知识系统、短信 / 邮件 / 移动推送 / Webhook / 院内消息真实接通；达梦 / 人大金仓真实运行继续按当前阶段边界后置。

### PR11 显式组织范围筛选（2026-06-05，本地验证）
- 已实现：待办与通知列表接口新增可选 `orgUnitId` 查询参数；传入组织时在 PR10 的当前用户 / 当前 `OrgContext` 可见性基础上，再按所选组织的 `org_closure` 子树收窄，`orgUnitId` 为空时保持 PR10 默认可见范围。
- 已覆盖闭环：后端服务、仓储与控制器均接入显式组织筛选；前端待办中心与通知中心使用真实组织 API 加载组织选项，把选择值传给统一待办 / 通知查询，不在页面本地造组织树或过滤结果。
- 未冒领范围：本 PR 只是显式查询收窄，不接护理站、LIS / PACS、独立床旁知识系统，不新增短信 / 邮件 / 移动推送 / Webhook / 院内消息真实投递或回执；达梦 / 人大金仓真实运行继续后置。

### PR12 通知中心免打扰感知（2026-06-05，本地验证）
- 已实现：通知中心读取真实 `GET /api/v1/engine/notifications/settings` 个人偏好，展示当前免打扰窗口与绕过级别；`quietActiveNow=true` 时，低打扰级别通知标记“免打扰中”，CRITICAL / HIGH 标记“安全绕过”且仍在列表可见。
- 已覆盖闭环：页面只展示免打扰状态与安全绕过证据，不隐藏通知、不做浏览器本地过滤、不改变服务端分页；设置接口不可用时显示“免打扰状态暂不可确认”诚实降级，不把失败伪装成无配置；设置入口跳转到已有 `/notifications/settings`，继续复用后端偏好。
- 未冒领范围：本 PR 不新增短信 / 邮件 / 移动推送 / Webhook / 院内消息真实发送连接器，不展示外部投递回执；免打扰只影响站内可读提示与既有外发补偿判断，达梦 / 人大金仓真实运行继续后置。

### PR13 来源跳转安全降级（2026-06-05，本地验证）
- 已实现：待办中心与通知中心复用统一站内来源链接判定，仅允许后端返回的单斜杠应用内路径作为“打开来源”跳转；外部 URL、协议相对 URL、`javascript:`、含空白 / 控制字符或反斜杠的 deepLink 均不渲染为可点击链接。
- 已覆盖闭环：有安全站内 deepLink 时继续保留来源跳转；有 deepLink 但不可安全跳转时显示“来源暂不可跳转”，让用户知道来源存在但当前页面不能打开，不在浏览器本地改写为伪路由或打开外部地址。
- 未冒领范围：本 PR 不新增任何来源系统直连、不校验外部护理站 / LIS / PACS / 床旁知识系统可达性，也不展示短信 / 邮件 / 移动推送 / Webhook / 院内消息投递回执；deepLink 来源仍以服务端真实落库值为准。

### PR14 协同来源证据呈现（2026-06-05，本地验证）
- 已实现：待办中心与通知中心把后端 DTO 已返回的 `sourceId` 显示为“来源对象”，把存在的 `traceId` 显示为“追踪链路”，用于页面内直接核对协同来源和审计链路。
- 已覆盖闭环：来源证据与原有来源类型、患者 / 就诊上下文、来源跳转、完成 / 转交 / 已读操作同屏呈现；`traceId` 缺失时不生成占位链路或假追踪值。
- 未冒领范围：本 PR 不新增外部护理站、LIS / PACS、独立床旁知识系统或外发连接器，也不声明外部来源系统可达；仅展示统一待办 / 通知 API 已落库返回的证据字段。

### PR15 缺失来源跳转诚实提示（2026-06-05，本地验证）
- 已实现：待办中心与通知中心在后端未提供 `deepLink` 时显示“来源未提供跳转”，与有 `deepLink` 但不安全时的“来源暂不可跳转”区分。
- 已覆盖闭环：无来源跳转的待办 / 通知仍展示来源对象、来源类型、患者 / 就诊上下文和追踪链路，不在浏览器本地生成伪 deepLink，也不影响完成 / 转交 / 已读主链路。
- 未冒领范围：本 PR 不校验外部来源系统可达性，不新增护理站、LIS / PACS、床旁知识系统或外发连接器；仅补页面对缺失来源跳转的诚实可读状态。

### PR16 缺失追踪链路诚实提示（2026-06-05，本地目标红绿）
- 已实现：待办中心与通知中心在后端未提供 `traceId` 时显示“追踪链路未提供”，与真实 `traceId` 的“追踪链路 <id>”区分。
- 已覆盖闭环：来源对象、来源类型、患者 / 就诊上下文、来源跳转和完成 / 转交 / 已读主链路均保持不变；缺 `traceId` 时不在浏览器本地生成占位追踪号或伪审计链。
- 未冒领范围：本 PR 不新增审计查询页、不声明外部来源系统或投递连接器可达；仅补统一待办 / 通知 API 已落库来源证据的缺失追踪状态。

### PR17 通知外发补偿状态读回（2026-06-05，本地目标红绿）
- 已实现：通知响应新增 `externalDeliveries`，按 PR6 的 `notify-out-<channel>-<notificationId>` 消息 ID 从真实 `integration_message_log` 读回短信 / 邮件 / 移动推送补偿日志；通知中心在已有日志时展示“外发状态”、通道、状态与补偿原因。
- 已覆盖闭环：`NOT_CONNECTED` 等非 `SUCCESS` 状态显示为“需补偿”，错误原因直接来自集成日志；列表查询和标记已读响应口径一致，缺日志时不生成假外发状态。
- 未冒领范围：本 PR 不新增短信 / 邮件 / 移动推送 / Webhook / 院内消息真实发送连接器，不声明外部已送达；只展示已登记的外发补偿事实。

### PR18 通知外发补偿通道补齐（2026-06-05，本地目标红绿）
- 已实现：通知偏好设置新增 `webhookEnabled` 与 `inHospitalMessageEnabled`，默认关闭；外发补偿登记通道从短信 / 邮件 / 移动推送补齐为短信 / 邮件 / 移动推送 / Webhook / 院内消息五类声明通道，消息 ID 仍为 `notify-out-<channel>-<notificationId>`。
- 已覆盖闭环：五类通道均按接收人个人偏好判断是否登记补偿；免打扰命中的低打扰通知仍不登记外发补偿，CRITICAL / HIGH 按安全绕过级别继续允许登记；设置页可保存五类通道偏好，并继续提示当前无真实发送连接器、状态为 `NOT_CONNECTED`。
- 未冒领范围：本 PR 不新增真实短信 / 邮件 / 移动推送 / Webhook / 院内消息连接器，不生成外部投递回执，不声明院内消息系统已接通；只把未接通通道纳入诚实补偿登记。

### PR19 随访异常回院证据页内呈现（2026-06-05，本地目标红绿）
- 已实现：智能随访页消费 API-09 顶层异常回院响应，展示后端返回的异常事件、回院任务、通知事件与 `traceId`，让随访触发到统一待办 / 通知的协同证据可见。
- 已覆盖闭环：页面不本地追加待办或通知，只展示 [FOLLOW-01](FOLLOW-01.md) / 本卡既有协同链路返回的真实 ID；看板统计文案收窄为“当前页”，不把分页局部统计写成全局完成率。
- 未冒领范围：本 PR 不新增全局随访统计聚合、不接外部随访渠道或真实外发连接器；回院任务是否进入待办仍以统一待办 API 真实查询为准。

### 收口验收（2026-06-05，本地目标验证）
- 已核查：PR1-18 已把统一待办 / 通知、推荐卡协同来源细分、随访异常、同步事件、创建 / 完成 / 转交通知、已读回执、统一审计、个人 + 组织闭包可见性、显式组织筛选、免打扰提示、外发补偿读回与五类声明通道补齐到真实关系库链路；PR19 把随访异常回院证据页内呈现，PR20 另把随访页统计改为后端作用域聚合。
- 本地证据：`rg` 核查 `engine/workflow`、`WorkflowTodos`、`Notifications`、`hooks.ts` 的来源类型、组织范围、审计、外发补偿、`sourceId` / `traceId` 呈现；后端目标 `mvn -q -Dtest=WorkflowCollaborationServiceTest,WorkflowTodoRepositoryTest,WorkflowNotificationRepositoryTest,WorkflowNotificationSettingsServiceTest,WorkflowNotificationSettingsControllerTest test` 退出码 0；前端目标 `npm test -- src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Notifications.test.tsx src/shared/api/hooks.test.ts` 3 文件 / 61 用例通过。
- 收口结论：本卡 B0 范围已满足；外部护理站、LIS / PACS、独立床旁知识系统、短信 / 邮件 / 移动推送 / Webhook / 院内消息真实连接器仍未接通，按诚实 `NOT_CONNECTED` 补偿登记与待处理问题边界保留，不作为本卡阻塞。

## 功能要求（原子可测条目）
- [x] FR-1 统一待办：CDSS 复核/随访/安全复核（[MED-C3](MED-C3.md)）任务统一进待办，带责任人/截止/来源。
- [x] FR-2 通知：待办/异常/同步事件转通知，去重 + 低打扰 + 已读回执。（新待办 / 待办完成 / 转交 / 随访异常 / 同步事件站内通知已覆盖；通知中心已显示免打扰生效与 CRITICAL / HIGH 安全绕过，并可读回已登记的短信 / 邮件 / 移动推送 / Webhook / 院内消息补偿日志；真实发送连接器待后续）
- [x] FR-3 协同：护理任务、报告解读、床旁知识卡按上下文呈现。（推荐卡真实类型细分已覆盖；独立外部上游直连待后续）
- [x] FR-4 随访触发：随访（[FOLLOW-01](FOLLOW-01.md)）任务汇入待办、异常回院转通知。
- [x] FR-5 闭环：每条待办可流转到完成，状态机闭环可审计。（完成 / 转交 / 已读已写统一审计；个人项 + 组织闭包项已加后端访问保护；待办 / 通知显式组织范围筛选已接真实组织 API；来源 deepLink 已加站内安全降级；缺失 deepLink 已诚实提示；页面展示已落库来源对象与追踪链路，缺失 traceId 时显示诚实缺失状态）

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
- [x] AC-1（FR-1/2）：多源任务统一进待办；通知去重 + 已读回执；已登记外发补偿状态可读回且不伪造送达。
- [x] AC-2（FR-3/4）：协同卡按上下文；随访/异常汇入。
- [x] AC-3（FR-5）：待办闭环可审计（完成 / 转交 / 已读写入统一审计，快照不含患者 ID 或通知正文；默认可见性按个人项 + 组织闭包项收敛；来源跳转只开放安全站内 deepLink，缺失 deepLink 与异常 deepLink 均诚实提示；页面展示真实来源对象与追踪链路，缺失 traceId 不伪造追踪号）。
- 关联 A1–A9 剧本：A6 协同待办 · A8 安全复核。
- T-GATE：前后端真实性门禁全绿（无伪造待办 / 去重正确）。
- B0 验收：关模型待办/通知协同全可用。

## 完工证据
- 代码：PR1-18 已合入统一待办 / 通知聚合、TODO / NOTIFY 页面真实化、通知偏好、组织范围、审计、外发补偿与来源证据；本收口 PR 不改业务代码，只补卡与 backlog 状态。
- 测试：PR1-18 各自含本地 + 远端 CI 证据；收口分支复跑 `mvn -q -Dtest=WorkflowCollaborationServiceTest,WorkflowTodoRepositoryTest,WorkflowNotificationRepositoryTest,WorkflowNotificationSettingsServiceTest,WorkflowNotificationSettingsControllerTest test`（退出码 0）与 `npm test -- src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Notifications.test.tsx src/shared/api/hooks.test.ts`（3 文件 / 61 用例通过）。
- T-GATE：`git diff --check` 无输出；changed-mode 真实性 / 迁移规约 / 配置边界门禁均通过（文档收口扫描文件 0 个）；`scripts/check-comment-zh.sh` 0 fail / 0 warn。历史迁移 inventory 债务仍归 `DEFER-016`，不得冒领清零。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
