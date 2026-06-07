# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-clock-sla`
- 基线：`origin/main` = `07414b96`（P9-3 `feat: 支持路径富节点类型 (#472)` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 PR #470、P9-2 PR #471、P9-3 PR #472 已合入主线。
- P9-4 已在当前分支完成：`clinical_clock` 增加基准事件、min/target/max 到期时间、升级级别和升级策略；路径发布、运行投影、包离线导入、五方言 V12 和前端路径模板/患者路径页统一使用同一套 `clockSla` 契约。
- 前端路径模板在时窗节点下显示 SLA 基准/最早/目标/最晚/上报分钟，非法 `min <= target <= max` 会提交前拦截；患者路径页展示目标/最晚到期与提醒/上报/质控记录状态。
- 包导入不再能绕过路径发布门禁：有时窗的离线节点必须配置 `clockSla` 且必须绑定时钟指标编码。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- 后端聚焦：`mvn -q -Dtest=PackageEngineServiceTest,PathwayEngineServiceTest,PathwayRepositoryTest,FollowupEngineServiceTest,MigrationBaselineContractTest#v12ShouldDeclarePathwayEngineApiTablesAndColumns test` 退出 0。
- 后端全量：`mvn -q test` 退出 0，Surefire 汇总 `281` reports / `1873` tests / `0` failures / `0` errors / `3` skipped。
- 前端聚焦：`npm test -- PathwayTemplates.test.tsx PatientPathways.test.tsx` 退出 0，`23` tests 通过。
- 前端全量：`npm run verify` 退出 0，`78` 文件 / `546` tests 通过；`npm run test:coverage` 退出 0，`78` 文件 / `546` tests 通过；`npm run build` 退出 0，`3397` 模块构建。
- 本地 T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 38 tests 通过；`bash scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 通过。
- 浏览器验收：临时后端 `18080` health `UP`，Vite dev `5173`；`E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 VITE_API_PROXY_TARGET=http://localhost:18080 npx playwright test e2e/pathway-graph-editor.spec.ts --project=chromium` 3 tests 通过，覆盖桌面连线/删除/拖拽、390px 窄屏无横向溢出、关键时钟 SLA 字段可见可填写。

## 下一步

1. 提交并推送 `codex/line2-pathway-clock-sla`，创建 PR；远端 CI 通过后合入 `main`。
2. 从最新 `origin/main` 继续 P9-5 守卫式分支；不要并行保留旧路径模型或兼容层。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
