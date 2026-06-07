# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-rule-backtesting-drift`
- 基线：`origin/main` = `9a2051ed`
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- PR #468 已合入主线，提交 `9a2051ed`，P8-2 影子/静默运行已成为当前基线。
- P8-3 已实现待提交/PR：规则历史回测以当前版本测试用例作为真实脱敏金标准样本，计算 TP/FP/TN/FN、灵敏度、特异度、准确率、触发率和误报/漏报样本；上线后漂移监测基于生产执行日志窗口与最近回测基线比较，超过阈值标记 `WARNING`。
- 后端新增 `rule_backtest_run` / `rule_drift_snapshot` 五方言 V11 表、索引、约束、审计与 REST 入口；前端规则详情“治理与发布”页展示回测、漂移、窗口和阈值配置，空证据明确显示为 `null` 空态，不再触发 React Query `undefined` 控制台错误。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- 后端聚焦：`mvn -q -Dtest=RuleEngineServiceTest#backtestRuleCalculatesSensitivitySpecificityAndPersistsRun+monitorDriftComparesCurrentWindowWithLatestBacktest,RuleEngineControllerSecurityTest#specialistCanReachBacktestAndDriftButDataScopeRejectsMissingTenant+guestCannotReadRules test` 通过；`MigrationBaselineContractTest,H2BaselineMigrationTest` 通过；`RuleRepositoryTest` 通过。
- 后端全量：`mvn -q test` 退出 0，Surefire 汇总 `281` reports / `1856` tests / `0` failures / `0` errors / `3` skipped；本机 Docker 不可用，Testcontainers 五方言 smoke 只记录为跳过/不可用，不伪造真实五方言运行通过。
- 前端：`npm run verify` 退出 0，`78` 文件 / `541` 项通过；`npm run build` 成功，`3397` 模块构建。
- 浏览器：后端 `18080` 健康 `UP`，前端 `5174` 真实登录页 → 首次改密 → `/dashboard` → `/rule/definitions` → 创建本地 H2 规则资产 → 详情“治理与发布”→“历史回测与漂移监测”可见，控制台 error 为 `0`；截图 `/tmp/medkernel-rule-p8-3-browser-detail.png`。Vite 路由切换时出现一次 `Dashboard.tsx ERR_ABORTED` 模块请求中止，非 API/控制台错误。
- 本地 T-GATE：`git diff --check`、真实性 changed（14 文件）、配置边界 changed（12 文件）、迁移规约 changed（5 文件）、中文注释门禁均通过。

## 下一步

1. 提交 P8-3 PR，等待远端 CI 全绿并合入 `main`。
2. 从最新 `origin/main` 继续 P9-1 入径/出径真实条件树，顺序承接线2/3/4未完成项。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
