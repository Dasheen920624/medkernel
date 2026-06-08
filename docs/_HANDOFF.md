# 会话接力

## 唯一执行组织

- 当前分支：`codex/ckd-package-end-to-end`
- 基线：`origin/main` = `25c984da`（P13-3 Outbox 分发已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 已完成 P13-4 本地实现与验证，待提交、推送、PR、CI 与合入 `main`。
- CKD 专病包已以统一知识包承载路径、规则、值集、字段目录、受控公式、条件片段与结局指标，不新增并行包模型。
- `FORMULA` 已纳入统一 `VersionedAssetType` 与五方言迁移约束；声明型资产统一走版本资产、包条目、离线导入导出与本地覆盖校验。
- CKD 端到端覆盖入径、eGFR 分期、开医嘱阻断、变异节点与结局指标绑定。
- 只同步本文件与 OpenSpec 任务清单，不新增施工文档。

## 当前证据

- TDD 红绿：`EffectiveKnowledgePackageResolverTest#resolvesCkdSpecialtyPackageWithFormulaValueSetsFieldCatalogAndLocalOverride`、`PackageEngineServiceTest#addPackageItemAcceptsCkdDeclarativeAssetsBackedByUnifiedVersions`、`PackageEngineServiceTest#exportOfflinePackageIncludesCkdDeclarativeAssetSnapshots`。
- 后端聚焦：`mvn -q -Dtest=RuleDslEvaluatorTest,RuleApplicabilityEvaluatorTest,RuleEngineServiceTest,PathwayProgressorTest,PathwayProgressorFeatureGateTest,PathwayEngineServiceTest,PathwayVersionedAssetAdapterTest,PackageEngineServiceTest,EffectiveKnowledgePackageResolverTest,IntegrationPackageSyncAdapterTest,CkdSpecialtyPackageEndToEndTest test`。
- 迁移：`mvn -q -Dtest=MigrationBaselineContractTest,FlywayMultiDialectSmokeTest#h2FlywayBaselineMigrates,H2BaselineMigrationTest test`。
- 后端全量：`mvn -q test`。
- 前端全量：`npm run verify`（`frontend/`）。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`、`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`。
- 静态门禁：`git diff --check`、`scripts/check-comment-zh.sh`。

## 下一步

1. 跑文档同步后的最终 OpenSpec 与 T-GATE 复核。
2. 提交并推送 `codex/ckd-package-end-to-end`，创建 PR，等待 CI 绿后 squash 合入 `main`。
3. 基于最新 `main` 继续 P13-5 实时 CDS，不恢复并行线。
