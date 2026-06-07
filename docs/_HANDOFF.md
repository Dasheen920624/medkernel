# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-guarded-branches`
- 基线：`origin/main` = `a8020427`（P9-4 `feat: 支持路径临床时钟SLA (#473)` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 PR #470、P9-2 PR #471、P9-3 PR #472、P9-4 PR #473 已合入主线。
- P9-5 已在当前分支完成：决策节点条件边复用统一条件树，由 `PathwayProgressor` 按优先级选择命中守卫；显式请求决策分支不能绕过守卫，未命中会诚实拒绝。
- 路径发布、离线包导入、前端路径模板提交前校验统一要求 DECISION 节点同时具备条件守卫分支与 `DEFAULT` 默认兜底边；条件分支缺守卫会被中文错误拦截。
- L3 DSL 回填 L2 时会在未显式提供起始节点时默认使用第一个节点，避免专家模式回填后被起点必填阻断。
- 远端后端全量测试暴露 JWT TTL 边界抖动：`JwtIssuer` 初始签发已改为同一个 `now` 同源生成 `iat`/`exp`，避免跨秒后 `exp - iat` 从 120 抖成 119。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- 后端 RED：`mvn -q -Dtest=PathwayProgressorTest#decisionNodeChoosesLowestPriorityMatchedGuardAndRecordsSelectionEvidence+decisionNodeRejectsRequestedGuardedTargetWhenGuardDoesNotMatch test` 先因缺少选择证据/显式目标绕过守卫失败。
- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#publishFailsWhenDecisionNodeHasNoDefaultFallbackBranch test` 先因未拦截缺默认兜底失败；`mvn -q -Dtest=PackageEngineServiceTest#importOfflinePackageRejectsDecisionNodeWithoutDefaultFallbackBranch test` 先因离线包未拦截失败。
- 前端 RED：`npm test -- PathwayTemplates.test.tsx -t "默认兜底边"` 先因缺少提交前默认兜底校验失败。
- GREEN 聚焦：`mvn -q -Dtest=PathwayProgressorTest#decisionNodeChoosesLowestPriorityMatchedGuardAndRecordsSelectionEvidence+decisionNodeRejectsRequestedGuardedTargetWhenGuardDoesNotMatch,PathwayEngineServiceTest#publishFailsWhenDecisionNodeHasNoDefaultFallbackBranch,PackageEngineServiceTest#importOfflinePackageRejectsDecisionNodeWithoutDefaultFallbackBranch test` 退出 0。
- 后端聚焦：`mvn -q -Dtest=PathwayProgressorTest,PathwayEngineServiceTest,PackageEngineServiceTest test` 退出 0。
- 认证回归：`mvn -q -Dtest=SystemConfigControllerTest#jwtTtlIsBackedByConfigCenterWithoutRestart,AuthControllerTest test` 退出 0。
- 前端聚焦：`npm test -- PathwayTemplates.test.tsx` 退出 0，`17` tests 通过。
- 前端构建：`npm run build` 退出 0，`tsc -b && vite build` 完成，`3397` 模块构建。
- 前端 lint：`npm run lint -- src/pages/tenant/PathwayTemplates.tsx src/pages/tenant/PathwayTemplates.test.tsx` 退出 0。
- OpenSpec/T-GATE：`openspec validate pathway-rule-authoring-overhaul --strict` 退出 0；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 38 tests 通过；`bash scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 退出 0。

## 下一步

1. 跑 OpenSpec/T-GATE/差异检查，提交并推送 `codex/line2-pathway-guarded-branches`，创建 PR；远端 CI 通过后合入 `main`。
2. 从最新 `origin/main` 继续 P10-1 变异管理；不要并行保留旧路径模型或兼容层。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
