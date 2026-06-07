# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-variance-management`
- 基线：`origin/main` = `5fc64ae2`（P9-5 `feat: 支持路径守卫式分支` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 PR #470、P9-2 PR #471、P9-3 PR #472、P9-4 PR #473、P9-5 PR #474 已合入主线。
- P10-1 已在当前分支完成：路径变异分类收敛为 `CLINICAL` / `SYSTEM` / `PATIENT` / `FAMILY`，原因码、原因说明、责任角色、结构化处置决策统一落入 `pathway_variance`。
- 变异处置决策统一为 `HOLD`（暂停观察）、`REENTER`（再入径）、`TERMINATE`（终止路径）；再入径必须选择可达目标节点，终止会保留变异事实并退出患者路径实例。
- 患者路径页面的“登记变异”改为结构化表单，详情抽屉只展示后端返回的变异事实，不在前端补写本地记录。
- `docs/backlog.md` 的 PATH-01 范围已同步纳入变异分类/原因码/责任角色/再入径或终止；`openspec` P10-1 已勾选。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#varianceCanPausePathwayAndPersistVariance+varianceCanReenterRequestedNodeAndPersistVarianceDecision+varianceCanTerminatePathwayAndPersistVarianceDecision test` 先因缺少新分类、`VarianceResolutionDecision`、原因码/责任角色字段失败。
- 前端 RED：`npm test -- PatientPathways.test.tsx -t "records a variance reason"` 先因页面缺少“变异分类”等结构化控件失败。
- 后端聚焦：`mvn -q -Dtest=PathwayProgressorTest,PathwayEngineServiceTest,PathwayRepositoryTest test` 退出 0，H2 从空库成功应用 97 个迁移到 v97。
- 前端聚焦：`npm test -- PatientPathways.test.tsx` 退出 0，`7` tests 通过。
- 前端构建：`npm run build` 退出 0，`tsc -b && vite build` 完成，`3397` 模块构建。
- 前端 lint：`npm run lint -- src/pages/clinical/PatientPathways.tsx src/pages/clinical/PatientPathways.test.tsx src/shared/api/hooks.ts` 退出 0。
- 后端编译：`mvn -q -DskipTests compile` 退出 0。
- OpenSpec/T-GATE：`openspec validate pathway-rule-authoring-overhaul --strict` 退出 0；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 38 tests 通过；`bash scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 退出 0。

## 下一步

1. 提交并推送 `codex/line2-pathway-variance-management`，创建 PR；远端 CI 通过后合入 `main`。
2. 从最新 `origin/main` 继续 P10-2 角色 RACI + 工作清单（对接待办中心）。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
