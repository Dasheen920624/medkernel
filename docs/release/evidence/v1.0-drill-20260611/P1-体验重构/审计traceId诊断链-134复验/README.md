# 审计 traceId 诊断链 134 复验

## 结论

- 目标：`UI-ACT10-AUDIT-01`。
- 环境：134，source `codex-demo-drill-audit-trace-jump-74353b56`，main `74353b56edc3a8f6c4550e6a301359642c6f5334`。
- 发布备份：`/zoesoft/medkernel/backups/deploy-20260611-183604`。
- 后端 jar SHA-256：`51fdd05aabaad6b51f4016eeec9a4bcb0f4ff7934f5cb641c4117f651c90679e`，readiness：`UP`。
- 复验账号：drill-audit-20260611（`drill-audit-20260611`）。
- 复验 Trace ID：`act6-8oh7bn024a-k-event`。
- 结果：`pass=true`；审计列表 traceId 直搜命中 7 条真实事件，诊断链返回 8 条状态流转，Payload 摘要数 0（当前数据为空时页面显示「无 Payload 摘要」空态）。

## 截图证据

| 视口         | 证据                                            | 文件                                                                                 |
| ------------ | ----------------------------------------------- | ------------------------------------------------------------------------------------ |
| desktop-1440 | desktop-1440 审计页按 Trace ID 直搜命中真实事件 | [01-desktop-audit-trace-search.png](./01-desktop-audit-trace-search.png)             |
| desktop-1440 | desktop-1440 审计详情显示 Trace ID 与诊断链入口 | [02-desktop-audit-detail-trace-entry.png](./02-desktop-audit-detail-trace-entry.png) |
| desktop-1440 | desktop-1440 诊断链展示状态流转                 | [03-desktop-trace-diagnosis-state.png](./03-desktop-trace-diagnosis-state.png)       |
| desktop-1440 | desktop-1440 诊断链展示 Payload 摘要区域        | [04-desktop-trace-diagnosis-payload.png](./04-desktop-trace-diagnosis-payload.png)   |
| mobile-390   | mobile-390 审计页按 Trace ID 直搜命中真实事件   | [05-mobile-audit-trace-search.png](./05-mobile-audit-trace-search.png)               |
| mobile-390   | mobile-390 审计详情显示 Trace ID 与诊断链入口   | [06-mobile-audit-detail-trace-entry.png](./06-mobile-audit-detail-trace-entry.png)   |
| mobile-390   | mobile-390 诊断链展示状态流转                   | [07-mobile-trace-diagnosis-state.png](./07-mobile-trace-diagnosis-state.png)         |
| mobile-390   | mobile-390 诊断链展示 Payload 摘要区域          | [08-mobile-trace-diagnosis-payload.png](./08-mobile-trace-diagnosis-payload.png)     |

## 机器可读证据

- [00-audit-trace-diagnosis-134-proof.json](./00-audit-trace-diagnosis-134-proof.json)
