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

## 保留限制

- 推荐卡已自动生成待办，但反馈闭环后待办状态与通知触达仍分散；后续体验重构应把推荐卡、患者路径、待办、疲劳治理聚合到统一运行视图。
- 血钾规则在幕6补建为标准 LOINC `2823-3` 规则，以匹配标准上下文；幕4旧 `K001` 规则保留历史治理证据，但不作为本幕运行主证据。
- DDI 重复规则按正式治理接口退役，没有删除历史测试、会签或发布审计。
