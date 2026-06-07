# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-milestone-day-sequence`
- 基线：`origin/main` = `c9be3210`
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 PR #470 已合入主线，提交 `c9be3210`，路径入出径条件树与入径模式成为当前基线。
- P9-2 已在当前分支完成：路径模板新增阶段 / 里程碑 / 天序一等模型；节点可绑定里程碑；患者路径详情返回里程碑达成状态；包导入导出纳入里程碑与节点归属。
- 后端 V12 五方言同步新增 `pathway_milestone` 与 `pathway_node.milestone_code`，迁移契约已覆盖表、索引、约束和 H2 结构检查。
- 前端路径模板 L2 可配置阶段天序里程碑并绑定节点，详情页展示阶段天序视图；患者路径运行态时间线优先展示后端 `milestoneStatuses`，并保留关键时钟和变异审计证据。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- 后端聚焦：`mvn -q -Dtest=PathwayEngineServiceTest,PackageEngineServiceTest,MigrationBaselineContractTest#v12ShouldDeclarePathwayEngineApiTablesAndColumns test` 退出 0。
- 后端全量：`mvn -q test` 退出 0，Surefire 汇总 `281` reports / `1861` tests / `0` failures / `0` errors / `3` skipped；本机 Docker 不可用，Testcontainers 五方言 smoke 只记录为跳过/不可用。
- 前端聚焦：`npm test -- PathwayTemplates.test.tsx -t "阶段里程碑|天序"` 退出 0，2 tests 通过；`npm test -- PatientPathways.test.tsx` 退出 0，7 tests 通过。
- 前端全量：`npm run verify` 退出 0，`78` 文件 / `544` tests 通过。
- 前端构建/浏览器：`npm run build` 成功，`3397` 模块构建；临时后端 `18080` health `UP`，preview `5176` 下 Playwright 冒烟通过：`specialist /pathway/templates` 桌面 1440px、`doctor /pathway/patients` 移动 390px，无 HTTP/控制台错误、无横向溢出；截图 `/tmp/medkernel-p9-2-pathway-patients-mobile.png`。
- 本地 T-GATE：`git diff --check`、OpenSpec strict、真实性 changed（15 文件）、配置边界 changed（12 文件）、迁移规约 changed（5 文件）、中文注释门禁均通过。

## 下一步

1. 提交并推送 P9-2，等待远端 CI 后合入 `main`。
2. 从最新 `origin/main` 继续 P9-3 富节点类型；不要并行保留旧路径模型或兼容层。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
