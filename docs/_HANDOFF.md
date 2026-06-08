# 会话接力

## 唯一执行组织

- 当前分支：`codex/clinical-event-outbox-dispatch`
- 基线：`origin/main` = `3c64b05c`（P13-2 临床事件触发映射已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 已完成 P13-3 本地实现与验证。
- 临床事件 outbox 处理完成后，同一事务内同步投影到协同中心：临床事件通知、CDSS 推荐派生待办与待办通知即时生成。
- 推荐派生待办新增按 `recommendation_trigger.source_event_id` 查询，只分发当前临床事件产出，不再靠列表页懒同步扫全租户。
- 分发链路复用既有去重键：`clinical-event:<eventId>`、推荐卡 cardId、领域事件 sourceId；重放不会产生重复待办、通知或质控告警。
- 协同投影不可达时抛 `DOWNSTREAM_UNAVAILABLE`，由临床事件 outbox 回写失败、退避重试并最终死信；不把下游失败伪装为已处理。
- 只同步本文件与 OpenSpec 任务清单，不新增施工文档。

## 当前证据

- 红绿：`mvn -q -Dtest=ClinicalEventOutputProjectionListenerTest,WorkflowCollaborationServiceTest#projectProcessedClinicalEventImmediatelyFansOutSyncNotificationAndReminderTodo,ClinicalEventOutboxWorkerTest#pollRetriesOutputDistributionUnavailableWithHonestCode,EngineWorkflowDomainEventAdapterTest#pathwayVarianceReplayDoesNotDuplicateTodoNotificationOrQualityAlert test`（先红：缺监听器与协同投影入口；实现后绿）。
- 事件范围 SQL：`mvn -q -Dtest=ClinicalEventOutputProjectionListenerTest,WorkflowCollaborationServiceTest#projectProcessedClinicalEventImmediatelyFansOutSyncNotificationAndReminderTodo,ClinicalEventOutboxWorkerTest#pollRetriesOutputDistributionUnavailableWithHonestCode,EngineWorkflowDomainEventAdapterTest#pathwayVarianceReplayDoesNotDuplicateTodoNotificationOrQualityAlert,RecommendationRepositoryTest#openRecommendationWorkflowRowsCanBeScopedToClinicalEventSource test`。
- 后端聚焦：`mvn -q -Dtest=ClinicalEventOutputProjectionListenerTest,ClinicalEventProcessorTest,ClinicalEventOutboxWorkerTest,ClinicalEventCallbackNotifierTest,WorkflowCollaborationServiceTest,EngineWorkflowDomainEventAdapterTest,RecommendationRepositoryTest,RecommendationEngineServiceTest,ClinicalEventEngineAdapterTest,ThirdPartyProjectionRulePathwayEndToEndTest test`。
- 后端全量：`mvn test`（1982 run, 0 failures, 0 errors, 3 skipped；3 个 Flyway 多方言 smoke 因本机无 Docker socket 跳过）。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`（38 pass）。
- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict`。
- 静态门禁：`git diff --check`、`bash scripts/check-comment-zh.sh`、`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`。

## 下一步

1. 跑文档同步后的最终 T-GATE 与 OpenSpec 复核。
2. 提交并推送 `codex/clinical-event-outbox-dispatch`，创建 PR，等待 CI 绿后 squash 合入 `main`。
3. 合入后继续 P13-4，不恢复并行线。
