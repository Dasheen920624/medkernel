# P5 幕10 · 审计导出审批

> 执行时间：2026-06-13T15:45:57.286Z
> 环境：`https://193.112.107.134`
> 脚本：`scripts/drill/p5-act10-audit-export-approval.mjs`
> runTag：`p5-act10-audit-20260613-234800`

## 结果

- failures：0
- 审计导出幂等键：`p5-act10-60613-234800`
- 申请人：`compliance-auditor`；审批/导出登记人：`organization-admin`。
- 自审批负向探针预期：`403 / ENG-API-004`。
- 导出闭环预期：审批 `APPROVED` → 大列表任务 `SUCCESS` → 审批 `EXPORTED`，导出摘要为 `sm3:64hex`。
- 证据预期：审批证据与导出证据均由后端验签通过，证据包导出返回真实 NDJSON 文件。
- 运行态预期：`/system/operations` 暴露 `NOT_CONNECTED` 与 `MODEL_DISABLED`，不伪装外部系统或模型可用。

## 收敛说明

- canonical 批次为 `p5-act10-audit-20260613-234800`，`00-act10-summary.json failures=[]`。
- 此前真实跑过 `probe-act10-1781365216275` 探针与误写日期标签的 `p5-act10-audit-20260614-000500` 收敛批次，均未清库；证据包 `itemCount=3` 因此包含本批导出证据及先前真实导出证据。
- 134 当前程序仍为幕8部署代码；幕10只新增脚本与证据文档，未部署新程序。

## 证据文件

- `00-act10-summary.json`：主汇总，敏感字段已脱敏。
- `10-server-facts.json`：审计、审批、导出、证据验签、证据包与运行态服务端事实。
- `trace-ids.json`：脚本请求 traceId 与响应 traceId。
- `audit-events-export.csv`：后端大列表真实生成并下载的审计 CSV。
- `approval-evidence-file.json` / `export-evidence-file.json`：后端证据快照真实文件。
- `compliance-export-evidence-package.ndjson`：后端证据包真实文件。
- `01-*.png`、`03-*.png` 至 `08-*.png`：真实前台截图，带 URL 栏。
