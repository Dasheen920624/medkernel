# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-rich-node-types`，PR `#472`
- 基线：`origin/main` = `b77cbc26`
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 PR #470、P9-2 PR #471 已合入主线，提交 `b77cbc26` 成为当前基线。
- P9-3 已在当前分支完成并进入 PR `#472`：后端路径节点枚举统一扩展 `DECISION` / `PARALLEL` / `WAIT_TIMER` / `SUBPATHWAY` / `MANUAL_GATE` / `ORDER_SET`，边类型扩展 `JOIN`；Progressor、发布门禁、包导入、五方言 V12 约束和中文 COMMENT 同步。
- 前端路径模板 L2 只暴露后端权威节点 / 边类型，富节点配置改为结构化字段；旧前端自造类型不再保留。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- 后端聚焦：`mvn -q -Dtest=PathwayProgressorTest,PathwayEngineServiceTest,PackageEngineServiceTest,MigrationBaselineContractTest#v12ShouldDeclarePathwayEngineApiTablesAndColumns test` 退出 0。
- 后端全量：`mvn -q test` 退出 0，Surefire 汇总 `281` reports / `1869` tests / `0` failures / `0` errors / `3` skipped；本机 Docker 不可用，Testcontainers 五方言 smoke 只记录为跳过/不可用。
- 前端聚焦：`npm test -- PathwayTemplates.test.tsx` 退出 0，15 tests 通过。
- 前端全量：`npm run verify` 退出 0，`78` 文件 / `545` tests 通过；`npm run test:coverage` 退出 0，`78` 文件 / `545` tests 通过；`npm run build` 退出 0，`3397` 模块构建。
- 本地 T-GATE：`git diff --check`、真实性 / 配置边界 / 迁移规约 changed、三类门禁测试、中文注释门禁均通过。
- 浏览器验收：临时后端 `18080` health `UP`，Vite dev `5173`；Playwright 从 `/login` 真实登录 `platform-admin` 后进入 `/pathway/templates`，可选择 `ORDER_SET`、`WAIT_TIMER`、`JOIN` 并填写结构化引用，无 HTTP/控制台错误、无横向溢出；截图 `/tmp/medkernel-p9-3-rich-node-browser-smoke.png`。`npx playwright test e2e/pathway-graph-editor.spec.ts --project=chromium` 2 tests 通过。

## 下一步

1. 推送 PR `#472` 的覆盖率超时收尾修复，等待远端 CI 后合入 `main`。
2. 从最新 `origin/main` 继续 P9-4 临床时钟 SLA；不要并行保留旧路径模型或兼容层。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
