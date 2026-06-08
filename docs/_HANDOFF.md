# 会话接力

## 唯一执行组织

- 当前分支：`codex/clinical-event-trigger-mapping`
- 基线：`origin/main` = `4ce9db26`（P13-1 适配器接入清单已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 完成 P13-2。
- 临床事件处理在派发规则 / 路径 / CDSS 前统一投影为 `ContextSnapshotResources`，并保留 `eventPayload` 原始证据。
- ORDER / ADMISSION / DIAGNOSIS / REPORT / FOLLOWUP / DISCHARGE 均已覆盖标准资源投影；ORDER 支持本地药品码归一到标准 ATC 后再求值。
- `ClinicalEventProcessor` 在无 `snapshotId` 时先创建 ACTIVE 标准上下文快照，再把同一个带 `snapshotId` 的上下文派发到三引擎；CDSS 不再拿空 snapshot 求值。
- 规则适配器将标准资源展开到顶层，规则 DSL 可直接读取 `medications[]` / `conditions[]` / `observations[]` 等字段，同时保留原始 payload。
- `ClinicalEventContext` 已收敛为唯一标准资源构造契约，不保留 payload-only 兼容构造。
- 只同步本文件与 OpenSpec 任务清单，不新增施工文档。

## 当前证据

- 红绿：`mvn -q -Dtest=ClinicalEventContextContractTest,ClinicalEventProcessorTest test`（先红：6 类事件无标准资源数组，ORDER 未产出药品映射锚点；实现后绿）。
- 规则适配器红绿：`mvn -q -Dtest=ClinicalEventEngineAdapterTest#ruleAdapterExposesCanonicalProjectionAtRootBeforeEvaluation test`（先红：顶层无 `medications[]`；实现后绿）。
- ORDER 端到端：`mvn -q -Dtest=ThirdPartyProjectionRulePathwayEndToEndTest#orderClinicalEventNormalizesLocalMedicationCodeBeforeRuleAndPathwayEvaluation test`。
- 纯净化复核：`mvn -q -Dtest=ClinicalEventContextContractTest,ClinicalEventProcessorTest,ClinicalEventEngineAdapterTest,ClinicalEventEngineDispatcherTest,ClinicalEventCallbackNotifierTest,ThirdPartyProjectionRulePathwayEndToEndTest test`。
- 后端聚焦：`mvn -q -Dtest=ClinicalEventContextContractTest,ClinicalEventProcessorTest,ClinicalEventEngineAdapterTest,ClinicalEventEngineDispatcherTest,ClinicalEventServiceTest,ClinicalEventOutboxWorkerTest,ThirdPartyProjectionRulePathwayEndToEndTest,RecommendationEngineServiceTest,RecommendationDeterministicMatcherTest test`。
- 后端全量：`mvn test`（1976 run, 0 failures, 0 errors, 3 skipped；3 个 Flyway 多方言 smoke 因本机无 Docker socket 跳过）。

## 下一步

1. 跑后端全量、T-GATE 与 OpenSpec 校验。
2. 提交并推送 `codex/clinical-event-trigger-mapping`，创建 PR，等待 CI 绿后 squash 合入 `main`。
3. 合入后继续 P13-3，不恢复并行线。
