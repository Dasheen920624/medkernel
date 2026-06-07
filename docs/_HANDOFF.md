# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-raci-worklist`
- 基线：`origin/main` = `e68afc44`（P10-1 `feat: 支持路径变异管理闭环` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 PR #470、P9-2 PR #471、P9-3 PR #472、P9-4 PR #473、P9-5 PR #474、P10-1 PR #475 已合入主线。
- P10-2 已在当前分支完成：`pathway_node` 增加 RACI（责任/签责/会诊/知会）角色；路径入径与节点推进通过 `PathwayWorklistPort` 投影到统一协同待办中心。
- 统一待办新增 `PATHWAY_NODE` 来源，路径节点待办按临床时钟唯一 sourceId 去重；推进到下一节点、完成或退出时自动闭环旧节点待办。
- 待办可见性收紧为“个人指派优先；无人指派时必须在组织闭包内，且角色待办需匹配 `user_role_assignment` 活跃角色”，避免角色待办全员可见。
- 路径模板 L2/L3 均保留 RACI；新增节点默认责任/签责为“专科医生”，C/I 用标签配置；工作台来源筛选与展示新增“路径节点”。
- `docs/backlog.md` 的 PATH-01 范围已同步纳入 RACI/统一待办工作清单；`openspec` P10-2 已勾选。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#enterPatientPathwayCreatesStartNodeWorklistFromRaciRoles+advanceClosesPreviousNodeTodoAndCreatesNextNodeWorklist,WorkflowTodoRepositoryTest#visibleAssigneeScopeHonorsRoleScopedPathwayNodeTodos test` 先因缺少 `PathwayWorklistPort`、RACI 字段、`PATHWAY_NODE` 枚举失败。
- 后端聚焦：`mvn -q -Dtest=PathwayEngineServiceTest,WorkflowCollaborationServiceTest,WorkflowTodoRepositoryTest test` 退出 0。
- 迁移基线：`mvn -q -Dtest=MigrationBaselineContractTest test` 退出 0。
- 后端编译：`mvn -q -DskipTests compile` 退出 0。
- 前端聚焦：`npm test -- WorkflowTodos.test.tsx PathwayTemplates.test.tsx` 退出 0，`27` tests 通过。
- 前端 typecheck：`npm run typecheck` 退出 0。
- 前端构建：`npm run build` 退出 0，`3397` 模块构建。
- 前端 lint：`npm run lint -- src/shared/api/hooks.ts src/pages/clinical/WorkflowTodos.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/tenant/PathwayTemplates.tsx src/pages/tenant/PathwayTemplates.test.tsx` 退出 0。
- OpenSpec/T-GATE：`openspec validate pathway-rule-authoring-overhaul --strict` 退出 0；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 38 tests 通过；`bash scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 退出 0。

## 下一步

1. 提交并推送 `codex/line2-pathway-raci-worklist`，创建 PR；远端 CI 通过后合入 `main`。
2. 从最新 `origin/main` 继续 P10-3 多级模板继承（STANDARD→…→SPECIALTY）与差异合并视图。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
