# 会话接力

## 唯一执行组织

- 当前分支：`codex/harden-event-evaluation-timeouts`
- 基线：`origin/main` = `39684380`（H-1 `feat: 增强创作能力开关灰度` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 至 H-1 已合入主线；当前继续 OpenSpec `pathway-rule-authoring-overhaul` 的 P-HARDEN。
- H-2 已在当前分支完成本地实现：临床事件触发的规则、路径和 CDSS 派发共用 `medkernel.events.sync-timeout-ms` 同步求值预算；配置中心不可用时回退到 `medkernel.events.sync-timeout` 安全默认。
- 任一下游引擎超时、预算耗尽或不可用时，派发结果返回 `UNAVAILABLE`；事件进入 `FAILED/ENG-SYS-002`，记录 `ENGINES_UNAVAILABLE` 状态迁移和失败审计，不发布 `ClinicalEventProcessedEvent`。
- 同步入口返回真实最终状态；当事件已进入人工核查结论时，outbox 队列项收口为已处理，避免同一 FAILED 事件被隐式重跑后覆盖结论。
- `UNKNOWN_AS_BLOCK` 缺失策略会强制动作 `requiresPhysicianConfirmation=true`，解释中明示人工核查原因，高危不静默放过。
- OpenSpec H-2 已勾选；RULE-01 / PATH-01 只补最小进度与 FR，不新增施工文档。

## 当前证据

- 后端红灯：`mvn -q -Dtest=ClinicalEventEngineDispatcherTest,ClinicalEventProcessorTest,ClinicalEventServiceTest,RuleDslEvaluatorTest,SystemConfigServiceTest test` 先因缺 dispatcher 时延预算、`UNAVAILABLE` 状态、processor 返回状态和 `UNKNOWN_AS_BLOCK` 人工核查失败。
- 后端聚焦：同命令已通过。
- 后端全量：`mvn -q test` 通过，Surefire 汇总 1901 tests / 0 failures / 0 errors / 3 skipped；Docker 不可用仅触发 Testcontainers 探测日志，最终退出码 0。
- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 通过，38 tests / 0 fail。
- T-GATE changed-mode：authenticity 9 files、config-boundary 9 files、migration 0 files，全部通过。
- 中文注释与空白：`bash scripts/check-comment-zh.sh` 通过，0 fail / 0 warn；`git diff --check` 通过。

## 下一步

1. 提交、推送 `codex/harden-event-evaluation-timeouts`，创建 PR。
2. 远端 CI 通过后合入 `main`。
3. 从最新 `origin/main` 继续 H-3，不恢复并行线。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
