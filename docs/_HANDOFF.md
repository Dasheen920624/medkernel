# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-entry-exit-tree`
- 基线：`origin/main` = `24662465`
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- PR #469 已合入主线，提交 `24662465`，P8-3 历史回测与漂移监测已成为当前基线。
- P9-1 已实现待提交/PR：路径模板新增 `entryMode`，入径/出径纳入和排除条件复用真实递归条件树；自动建议入径严格校验纳入/排除，人工确认入径允许纳入未命中但仍阻断排除命中；出径按真实快照校验条件树。
- 后端 V12 五方言同步新增 `pathway_template.entry_mode` 约束与迁移契约；路径模板创建、复制、详情、包导入导出均保留入径模式。
- 前端路径模板页在 L1 配置入径模式，在 L2 用同一条件树编辑入径/出径条件；患者入径下拉展示并提示入径模式。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- 后端聚焦：`mvn -q -Dtest=PathwayEngineServiceTest,PathwayRepositoryTest,PackageEngineServiceTest,RecommendationDeterministicMatcherTest,RelationalRuleImpactIndexRepositoryTest,RelationalRuleImpactIndexTest,ClinicalSafetyGuardTest,SafetyWithdrawalServiceTest,MigrationBaselineContractTest#v12ShouldDeclarePathwayEngineApiTablesAndColumns test` 通过。
- 后端安全回归：`mvn -q -Dtest=PathwayEngineControllerSecurityTest#specialistCanWritePathwayButDataScopeRejectsMissingTenant test` 通过。
- 后端全量：`mvn -q test` 退出 0，Surefire 汇总 `281` reports / `1859` tests / `0` failures / `0` errors / `3` skipped；本机 Docker 不可用，Testcontainers 五方言 smoke 只记录为跳过/不可用。
- 前端聚焦：`npm test -- PathwayTemplates.test.tsx PatientPathways.test.tsx` 通过，`19` tests 通过。
- 前端全量：`npm run verify` 退出 0，`78` 文件 / `542` tests 通过。
- 前端构建/浏览器：`npm run build` 成功，`3397` 模块构建；临时后端 `18080` health `UP`，preview `5176` 登录页 Playwright 冒烟通过，控制台 error 为 `0`，截图 `/tmp/medkernel-p9-1-login-with-backend.png`。
- 本地 T-GATE：`git diff --check`、真实性 changed（8 文件）、配置边界 changed（5 文件）、迁移规约 changed（5 文件）、中文注释门禁均通过。

## 下一步

1. 提交 P9-1 PR，等待远端 CI 全绿并合入 `main`。
2. 从最新 `origin/main` 继续 P9-2 阶段 / 里程碑 / 天序结构与里程碑达成判定。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
