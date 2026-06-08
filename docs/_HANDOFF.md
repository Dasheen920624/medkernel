# 会话接力

## 唯一执行组织

- 当前分支：`codex/harden-domain-events`
- 基线：`origin/main` = `8fc1f4fe`（H-5 `feat: 增强引用包版本一致性护栏` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 至 H-6 已按线1统一路径承接；当前 OpenSpec `pathway-rule-authoring-overhaul` 的 P-HARDEN 已完成 H-1/H-2/H-3/H-4/H-5/H-6。
- H-6 新增统一 `EngineDomainEventPort`：规则真实命中发出 `RuleFired`，人工越权发出 `OverrideCaptured`，路径变异发出 `PathwayVarianceRecorded`，关键时钟 SLA 从 RUNNING 投影到 TIMEOUT 时发出 `ClockSlaBreached`。
- 协同域唯一 adapter `EngineWorkflowDomainEventAdapter` 复用现有待办、通知、质控驾驶舱表，按来源幂等写入，不新增前端假数据或平行兼容层。
- 5 方言 V98 迁移统一扩展 `RULE_EVENT` / `PATHWAY_EVENT` 来源与 `RULE_OVERRIDE` / `PATHWAY_VARIANCE` / `CLOCK_SLA_BREACH` 质控告警类型。
- OpenSpec H-6 已勾选；RULE-01 / PATH-01 只补最小进度、FR 与 AC，不新增施工文档。

## 当前证据

- 后端红灯：H-6 聚焦测试先失败于缺少 `com.medkernel.engine.event`、`EngineWorkflowDomainEventAdapter`、新来源枚举与质控告警类型。
- 后端聚焦：`mvn -q -Dtest=RuleEngineServiceTest#evaluatePublishedRulePersistsExecutionLogAndReturnsExplanation+captureOverrideRequiresRealBlockingActionAndPersistsReason,PathwayEngineServiceTest#clocksProjectTimeoutEscalationFromClockSlaPolicy+varianceCanPausePathwayAndPersistVariance,EngineWorkflowDomainEventAdapterTest test` 通过。
- 后端相关面：`mvn -q -Dtest=RuleEngineServiceTest,PathwayEngineServiceTest,EngineWorkflowDomainEventAdapterTest,WorkflowCollaborationServiceTest,WorkflowNotificationSettingsServiceTest test` 通过。
- 后端全量：`mvn -q test` 通过，Surefire 汇总 `1920` tests / 0 failures / 0 errors / 3 skipped。
- OpenSpec strict：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 38/38 通过。
- changed-mode：真实性 / 配置边界 / 迁移规约分别扫描 15 / 15 / 5 个文件且 0 阻断。
- 中文注释：`bash scripts/check-comment-zh.sh` 通过，0 fail / 0 warn。
- 空白检查：`git diff --check` 通过。

## 下一步

1. 提交、推送 `codex/harden-domain-events`，创建 PR。
2. 远端 CI 通过后合入 `main`。
3. 从最新 `origin/main` 继续 P12，不恢复并行线。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
