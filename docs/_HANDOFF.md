# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-template-inheritance`
- 基线：`origin/main` = `8fe228e2`（P10-2 `feat: 支持路径RACI工作清单` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 PR #470、P9-2 PR #471、P9-3 PR #472、P9-4 PR #473、P9-5 PR #474、P10-1 PR #475、P10-2 PR #476 已合入主线。
- P10-3 已在当前分支完成实现并通过本地最终门禁，待提交、PR、CI 与合并：`pathway_template.parent_template_id`、`pathway_node.disabled_flag` 已进入 5 方言 V12；后端新增继承解析与 `GET /api/v1/engine/pathway/pathway-templates/{templateId}/inheritance-diff`。
- 模板继承支持 STANDARD→HOSPITAL→DEPARTMENT→SPECIALTY 层级校验、同病种父级校验、环检测；下级覆盖/新增/禁用父级节点会生成差异项，并输出合并后的有效节点/边。
- 前端 `PathwayTemplates` 创建模板可选择父级模板，节点可标记禁用继承；详情抽屉新增“继承差异”页签，画布、发布拓扑、指标绑定校验、仿真、患者入径和推进主链路只消费有效节点/边。
- `docs/backlog.md`、`docs/cards/D2/PATH-01.md` 与 OpenSpec P10-3 已同步。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#templateInheritanceDiffMergesOverrideAddAndDisabledNodes test` 先因缺少继承 DTO / API / 字段失败。
- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#inheritedTemplateUsesMergedGraphForPublishAndPatientEntry test` 先因发布影响仍读取子模板本地节点而失败（缺少继承终止节点）。
- 前端 RED：`npm test -- PathwayTemplates.test.tsx -t 继承差异` 先因缺少“继承差异”页签失败。
- 后端聚焦 + 迁移基线：`mvn -q -Dtest=PathwayEngineServiceTest,MigrationBaselineContractTest test` 退出 0。
- 后端编译：`mvn -q -DskipTests compile` 退出 0。
- 后端全量：`mvn -q test` 退出 0；Surefire XML 汇总 `281` files / `1885` tests / `0` failures / `0` errors / `3` skipped。本机无 Docker socket，Testcontainers 记录不可用日志但未导致命令失败。
- 前端聚焦：`npm test -- PathwayTemplates.test.tsx -t 继承差异` 退出 0；`npm test -- PathwayTemplates.test.tsx` 退出 0，`18` tests 通过。
- 前端 typecheck：`npm run typecheck` 退出 0。
- 前端构建：`npm run build` 退出 0，`3397` 模块构建。
- 前端 lint/stylelint/规则测试：`npm run lint` 退出 0；`npm run stylelint` 退出 0；`npm run test:lint-rules` 4 tests 通过。
- 前端全量：`npm run verify` 退出 0；`78` files / `548` tests 通过。
- OpenSpec/T-GATE：`openspec validate pathway-rule-authoring-overhaul --strict` 退出 0；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 38 tests 通过；changed-mode 真实性 / 配置边界 / 迁移规约分别扫描 13 / 11 / 5 个文件且 0 阻断；`bash scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 退出 0。

## 下一步

1. 复跑最终门禁并提交推送 `codex/line2-pathway-template-inheritance`，创建 PR；远端 CI 通过后合入 `main`。
2. 从最新 `origin/main` 继续 P10-4 结局指标绑定 + 患者实例状态机 + 队列回放/时光机 + 多路径冲突协调。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
