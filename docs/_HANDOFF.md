# 会话接力

## 唯一执行组织

- 当前分支：`codex/harden-reference-package-consistency`
- 基线：`origin/main` = `cecf4dd2`（H-4 `feat: 增强路径规则环与边界护栏` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 至 H-5 已按线1统一路径承接；当前 OpenSpec `pathway-rule-authoring-overhaul` 的 P-HARDEN 已完成 H-1/H-2/H-3/H-4/H-5。
- H-5 收口引用与包版本一致性：规则发布前拒绝 DSL 中显式跨包版本引用，规则运行期拒绝与上下文快照包版本不一致的规则，临床事件规则入口同步传递 `packageVersion`。
- 路径发布门禁统一校验模板入/出径条件、节点配置、边条件和结局指标绑定的包版本；路径入径、推进、单快照仿真和队列回放均按模板所属专病包版本校验上下文快照。
- 规则 / 路径影响摘要均纳入引用资产摘要；引用值集、字段、子路径、医嘱集、结局指标等资产变化会改变发布前 `impactDigest`。
- OpenSpec H-5 已勾选；RULE-01 / PATH-01 只补最小进度、FR 与 AC，不新增施工文档。

## 当前证据

- 后端红灯：H-5 聚焦测试先失败于规则发布未拒绝跨包值集引用、规则运行期未拒绝与上下文快照包版本不一致、路径仿真未按模板包版本校验快照。
- 后端聚焦：`mvn -q -Dtest=RuleEngineServiceTest#draftTransitionRejectsCrossPackageValueSetReference+evaluateRejectsSnapshotWithDifferentPackageVersionForExplicitRule,PathwayEngineServiceTest#simulateRejectsSnapshotWithDifferentPackageVersionFromTemplatePackage test` 通过；`mvn -q -Dtest=RuleEngineServiceTest,PathwayEngineServiceTest test` 通过；`mvn -q -Dtest=ClinicalEventEngineAdapterTest#ruleAdapterCallsRuleEngineWithSameEventContext test` 通过。
- 后端全量：`mvn -q test` 通过，Surefire 汇总 1916 tests / 0 failures / 0 errors / 3 skipped；Docker 不可用仅触发 Testcontainers 探测日志，最终退出码 0。
- OpenSpec / T-GATE / changed-mode / 中文注释 / 空白检查：`openspec validate pathway-rule-authoring-overhaul --strict` 通过；T-GATE 三 guard 测试 38/38 通过；changed-mode 真实性 / 配置边界 / 迁移规约分别扫描 4 / 4 / 0 个文件且 0 阻断；中文注释 0 fail / 0 warn；`git diff --check` 通过。

## 下一步

1. 运行 OpenSpec strict、T-GATE、changed-mode、中文注释与空白检查。
2. 提交、推送 `codex/harden-reference-package-consistency`，创建 PR。
3. 远端 CI 通过后合入 `main`。
4. 从最新 `origin/main` 继续 H-6，不恢复并行线。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
