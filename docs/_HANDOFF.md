# 会话接力

## 当前执行

- **新主线「全流程演练 · 使用指南 · 体验重构」正在幕8「配置包与发布治理」收口**：总体计划见 [docs/superpowers/plans/2026-06-10-full-flow-drill-usability-program.md](superpowers/plans/2026-06-10-full-flow-drill-usability-program.md)。已合入：计划 #526、DOC-SYNC #528 `fbaa012e`、幕0 #529 `d7cc0e9c`、幕1 #530 `97a3b217`、幕2 #531 `882182f6`、幕3 #532 `2e108ea4`、幕4 #533 `844059ed`、幕5 #534 `9bf7e031`、幕6 #535 `275f7363`、幕7 #536 `b2657f0d`。当前分支 `codex/demo-drill-act8-package-release` 承载幕8配置包业务 ID 归一、统一版本范围解析修复、134 真实演练证据、试点准备手册「配置包与发布」章、术语表和计划更新。
- **用户授权仍有效**：演练、指南、体验重构全程由 AI 自主裁决，无需逐项咨询；质量准绳为最高质量和最佳体验。未上线产品按纯粹实现推进，不为旧低质实现加兼容层。所有外向/生产配置动作必须备份、留痕、可回滚或如实登记不能回滚原因。
- **幕8主链事实**：最终批次 `act8-8sinb347c5` 在 134 真实完成。配置包 `DRILL.ACT8.CONFIG.ACT8-8SINB347C5@2026.06.11-act8-8sinb347c5`（ID `e845b6cc-fbe7-4577-836f-6fed3bdae47d`）打包幕2术语包、幕3知识、幕4规则、幕5路径和撤回沙箱知识。发布适配器 `drill-local-runtime-package-sink-20260611` 健康。
- **幕8发布事实**：灰度计划 `348c1ac9-d715-4e1f-b4e2-26b591697d56` 验证呼吸科边界，心内科未进入灰度；全量计划 `cb2db40d-cd7a-47b8-bd32-8204a22d5df3` 后包状态 `ACTIVE`，同步日志 2 条。离线导出 manifest 与包体摘要一致，包含 4 个条目和 4 个资产快照；模拟导入返回 `409`，作为重复版本/对账保护证据。安全撤回 `withdrawalId=1` 后再发布被 `400` 阻断。
- **幕8后端修复事实**：本分支修复 4 个真实运行缺口：① `InheritanceResolver` 对组织树 slash path 与 `tenant:/department:` 语义范围做别名解析；② `EffectiveKnowledgePackageResolver` 将规则 `ruleId`、路径 `templateId` 和外部术语包条目映射到统一版本资产 ID，同时输出仍保留业务 ID；③ `PathwayEngineService` 发布路径模板时使用租户语义范围；④ `TerminologyKnowledgePackageService` 与查询侧统一小写语义组织范围。
- **134 发布与数据修复事实**：本幕 4 次发布后端到 134，最新发布 source=`codex-demo-drill-act8-terminology-package-bridge`，backup=`/zoesoft/medkernel/backups/deploy-20260611-085825`，jar SHA-256 `6dc6e2a845cc20355ee2ecc5f218919548aceb168cb52953f5033a3a435e8a8b`，readiness `UP`，nginx `200`。134 历史路径资产已从 UUID 组织范围修正为 `tenant:drill-hospital-20260611`；历史术语包资产已从大写 `TENANT:` 修正为小写 `tenant:`，均有 evidence JSON 留痕。
- **幕8体验结论**：配置包中心已经能承载“草稿校验 → 灰度 → 全量 → 离线导出 → 安全撤回”的主叙事，但页面仍需要把业务 ID 与统一版本资产 ID 的差异藏到专家/调试视图，普通用户只应看“条目可发布/不可发布”的业务信号。离线导入当前以模拟对账方式证明重复版本保护，整套演练场景包仍由 OPT-DEMO-01 承接。

## 当前状态

- 当前工作树：`/Users/zhikunzheng/.config/superpowers/worktrees/codex/codex-demo-drill-act8-package-release`。
- 当前分支：`codex/demo-drill-act8-package-release`，基于幕7合并点 `b2657f0d`。
- 当前未收尾改动范围：
  - 后端范围与配置包解析修复：`InheritanceResolver.java`、`EffectiveKnowledgePackageResolver.java`、`PathwayEngineService.java`、`TerminologyKnowledgePackageService.java`、`TermMappingSnapshotRepository.java` 及对应测试。
  - 文档与证据：幕8证据目录、`docs/handbook/user-guides/tenant-readiness.md`、`docs/glossary.md`、计划文件、本 `_HANDOFF`。
- 已执行的关键验证：
  - TDD 红绿：租户语义范围继承解析、规则/路径业务 ID 到统一版本资产 ID、外部术语包桥接、路径发布范围、术语包组织范围均先出现可复现失败，再修复为通过。
  - 聚焦后端回归：`mvn -q -Dtest=EffectiveKnowledgePackageResolverTest,PackageEngineServiceTest,InheritanceResolverTest,PathwayEngineServiceTest,TerminologyKnowledgePackageServiceTest,EffectiveTermMappingRepositoryIntegrationTest,ReleaseGovernanceControllerTest,SafetyWithdrawalServiceTest test` 通过。
  - 134 发布：`deploy/onprem/mk-publish.sh --backend --source codex-demo-drill-act8-terminology-package-bridge` 通过，远程 readiness `UP`。
  - 134 演练：`NODE_TLS_REJECT_UNAUTHORIZED=0 node /tmp/act8-package-release.mjs` 最终批次 `act8-8sinb347c5` 通过。
  - 收口静态检查：幕8证据 JSON `jq empty`、失败证据残留检查、`git diff --check` 均通过。
  - 全量门禁：`node scripts/authenticity-guard.mjs --mode=all`、`node scripts/config-boundary-guard.mjs --mode=all`、`scripts/check-comment-zh.sh`、`node scripts/migration-convention-guard.mjs --mode=all` 均通过。
  - 前端聚焦回归：`npm test -- src/pages/tenant/ConfigPackages.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/shared/api/hooks.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts` 通过，4 个测试文件、131 个测试。
  - 暂存后 changed-mode 门禁：`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`、`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`、`scripts/check-comment-zh.sh`、`git diff --cached --check` 均通过。
- 尚未执行的收口验证：CI。

## 下一步

1. 运行幕8收口验证：证据 JSON `jq empty`、失败证据残留检查、`git diff --check`、真实性/配置边界/中文注释门禁、必要后端聚焦回归。
2. 暂存全部幕8改动后运行 changed-mode 门禁与 staged diff 检查。
3. 提交、推送、创建 PR，等待 CI 全绿后合入 `main`，清理分支 / worktree。
4. 合入后从最新 `origin/main` 继续幕9「第三方对接能力案例集」，不停在幕8收口。
