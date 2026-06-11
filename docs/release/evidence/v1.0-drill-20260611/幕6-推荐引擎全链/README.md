# 幕6 · 推荐引擎全链证据

> 环境：`https://193.112.107.134`
>
> 租户：`drill-hospital-20260611`
>
> 运行批次：`act6-8oh7bn024a`
>
> 凭据：只在服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json` 读取，未提交到仓库。

## 结论

幕6已在 134 上真实完成“临床事件 → 标准上下文 → 规则/CDSS → 推荐卡 → 医生反馈 → 药师复核/疲劳信号”的运行链：

- 新患者 `mpi-01KTSWK7P3XQQ8VC0JZFZ63AMY` 已创建并进入 CAP 患者路径 `pp-9b4c8389-c46e-4a12-bf5f-6d66be5768fc`。
- 血钾危急值事件 `evt-act6-8oh7bn024a-k-critical` 已处理为 `PROCESSED`，生成 CRITICAL 推荐卡 `rc-12c39901-6293-4704-acb9-4ee9b477f633`，医生反馈后状态为 `ACCEPTED`。
- 华法林 + 阿司匹林 DDI 事件 `evt-act6-8oh7bn024a-ddi-warfarin-aspirin` 已处理为 `PROCESSED`，生成 HIGH 推荐卡 `rc-85e2897f-c31c-4eba-b351-71f2fc4cf605`，医生记录覆盖理由后状态为 `REJECTED`，药师账号可读取复核详情。
- 推荐来源同时包含 `CONTEXT`、`PATHWAY`、`RULE`，推荐触发编码已包含源临床事件 ID，例如 `CLINICAL_EVENT_REPORT_evt-act6-8oh7bn024a-k-critical`。
- 统计结果为 `total=2`、`pending=0`、`accepted=1`、`rejected=1`，没有未闭环推荐卡残留。
- 已通过正式治理状态机退役重复 DDI 规则 `rule-6ee21ff5-75d5-450e-88c9-76340a3e9c78`，保留幕4主规则 `rule-6c2285f8-777b-4402-ad64-9ef0eca71fcb`。

## 运行资产

| 项 | 值 |
|---|---|
| 术语运行包 | `TERM.DRILL.ACT2@2026.06.11-act2-024101` |
| 路径包 | `PATH.DRILL.CAP@2026.06.11-act5-1781126077791` |
| CAP 模板 | `TPL.DRILL.CAP.1781126077791` |
| CAP 模板 ID | `pt-e8b9a1f1-f423-44aa-ba6c-835c7246c186` |
| Act6 LOINC 血钾规则 | `rule-04ffef1b-79f2-4d19-b41b-7eccfcef1751` |
| DDI 主规则 | `rule-6c2285f8-777b-4402-ad64-9ef0eca71fcb` |
| 后端部署 | `codex-demo-drill-act6-recommendation-runtime-trigger-code` |
| 后端 jar SHA-256 | `3c811730a2e79dae1084b7fb666833215bff25d214fbdf9d43462762e3bad852` |

## 证据文件

| 文件 | 内容 |
|---|---|
| `00-readiness-actors-runtime.json` | 后端健康、演练账号、权限、运行包和最新后端部署预检查 |
| `01-mpi-context-pathway-entry.json` | MPI 患者创建、标准上下文快照、CAP 患者入径和医生路径读取 |
| `02-critical-potassium-event-recommendation-closure.json` | LOINC `2823-3` 血钾危急值事件、CRITICAL 推荐卡、医生 ACCEPT 闭环、待办/通知观察 |
| `03-ddi-order-override-pharmacist-review.json` | 华法林 `B01AA03` + 阿司匹林 `B01AC06` 医嘱事件、HIGH 推荐卡、医生覆盖 REJECT、药师读取详情 |
| `04-runtime-chain-overview.json` | 两条运行链汇总、推荐统计、疲劳信号和体验发现 |
| `05-backend-deploy-runtime-fixes.json` | 幕6后端修复发布记录、jar 指纹和本地聚焦测试命令 |
| `06-pathway-full-rollout-precondition.json` | CAP 模板从灰度切换到全量发布的前置证据 |
| `07-act6-loinc-potassium-rule-governance.json` | 标准 LOINC 血钾规则创建、测试、会签、影子、灰度、全量治理证据 |
| `08-retire-duplicate-ddi-rule.json` | 重复 DDI 规则 `FULL -> MONITOR -> RETIRED` 退役证据，主规则保持 `FULL` |
| `trace-ids.txt` | 本幕 API 演练 traceId 汇总 |

## 幕8.5 前台复演

幕8.5 第三批在浏览器里补做幕6客户视角走查：外部 LIS/HIS 仍由 API 扮演触发源，医生、心内科医生和临床药师的接收、查看、反馈、复核动作均在前台页面完成，截图统一落在 [ui-replay/](ui-replay/)。

| 角色 | 页面路由 | 前台操作 | 截图 |
|---|---|---|---|
| 呼吸科医生 | `/mpi` | 查看演练患者 360、标准快照和在径路径 | [01-mpi-patient-360.png](ui-replay/01-mpi-patient-360.png) |
| 呼吸科医生 | `/pathway/patients` | 定位患者路径当前节点与关键时钟 | [02-pathway-runtime-position.png](ui-replay/02-pathway-runtime-position.png) |
| 呼吸科医生 | `/workflow/todos` | 接收血钾危急值临床提醒 | [03-critical-todo-received.png](ui-replay/03-critical-todo-received.png) |
| 呼吸科医生 | `/notifications` | 查看并标记血钾危急值通知已读 | [04-critical-notification-received.png](ui-replay/04-critical-notification-received.png)、[05-critical-notification-read.png](ui-replay/05-critical-notification-read.png) |
| 呼吸科医生 | `/cdss/fatigue` | 查看血钾推荐卡、打开可信归因、前台采纳 | [06-critical-card-feedback-before.png](ui-replay/06-critical-card-feedback-before.png)、[07-critical-card-diagnose.png](ui-replay/07-critical-card-diagnose.png)、[08-critical-card-accepted.png](ui-replay/08-critical-card-accepted.png) |
| 呼吸科医生 | `/workflow/todos` | 确认危急值待办闭环或无待处理 | [09-critical-todo-completed.png](ui-replay/09-critical-todo-completed.png) |
| 心内科医生 | `/workflow/todos`、`/notifications` | 接收 DDI 待办和通知 | [10-ddi-todo-received.png](ui-replay/10-ddi-todo-received.png)、[11-ddi-notification-center.png](ui-replay/11-ddi-notification-center.png) |
| 心内科医生 | `/cdss/fatigue` | 查看 DDI 推荐卡并前台填写覆盖理由 | [12-ddi-card-feedback-before.png](ui-replay/12-ddi-card-feedback-before.png)、[13-ddi-card-rejected.png](ui-replay/13-ddi-card-rejected.png) |
| 心内科医生 | `/workflow/todos` | 确认 DDI 待办闭环或无待处理 | [14-ddi-todo-completed.png](ui-replay/14-ddi-todo-completed.png) |
| 临床药师 | `/cdss/fatigue` | 复核 DDI 覆盖后的推荐卡 | [15-pharmacist-ddi-review.png](ui-replay/15-pharmacist-ddi-review.png) |

四问结论：推荐运行链前台可操作，推荐卡本身能解释来源；但入口仍分散在 MPI、患者路径、待办、通知和“智能建议治理”之间，现场回答“推荐引擎在哪里”仍不够直接。新增 `OPT-WORKFLOW-01`：新危急值推荐卡未稳定聚合到医生待办第一页，待办中心需要患者 / trace / 来源对象检索，并修复推荐卡与待办状态同步。

## 保留限制

- 推荐卡已自动生成待办，但反馈闭环后待办状态与通知触达仍分散；后续体验重构应把推荐卡、患者路径、待办、疲劳治理聚合到统一运行视图。
- 血钾规则在幕6补建为标准 LOINC `2823-3` 规则，以匹配标准上下文；幕4旧 `K001` 规则保留历史治理证据，但不作为本幕运行主证据。
- DDI 重复规则按正式治理接口退役，没有删除历史测试、会签或发布审计。
