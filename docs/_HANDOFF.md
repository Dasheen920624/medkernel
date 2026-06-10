# 会话接力

## 当前执行

- **新主线「全流程演练 · 使用指南 · 体验重构」正在幕5「CAP 临床路径」收口**：总体计划见 [docs/superpowers/plans/2026-06-10-full-flow-drill-usability-program.md](superpowers/plans/2026-06-10-full-flow-drill-usability-program.md)。已合入：计划 #526、DOC-SYNC #528 `fbaa012e`、幕0 #529 `d7cc0e9c`、幕1 #530 `97a3b217`、幕2 #531 `882182f6`、幕3 #532 `2e108ea4`、幕4 #533 `844059ed`。当前分支 `codex/demo-drill-act5-cap-pathway` 承载幕5 CAP 路径建模、图形阅读修复、134 证据、临床运行手册首章和术语表更新。
- **用户授权仍有效**：演练、指南、体验重构全程由 AI 自主裁决，无需逐项咨询；质量准绳为最高质量和最佳体验。未上线产品按纯粹实现推进，不为旧低质实现加兼容层。所有外向/生产配置动作必须备份、留痕、可回滚或如实登记不能回滚原因。
- **幕5 CAP 路径事实**：已创建并发布路径知识包 `PATH.DRILL.CAP@2026.06.11-act5-1781126077791`；模板 `TPL.DRILL.CAP.1781126077791` 发布可用，模板 ID `pt-e8b9a1f1-f423-44aa-ba6c-835c7246c186`，病种 `ZD0456`。L2 画布包含 6 个节点、6 条流转边和 3 个里程碑，覆盖入院评估（CURB-65）→ 经验性抗感染 → 48-72h 疗效评估 → 降阶梯 / 升级分支 → 出院评估。
- **幕5试运行与评审事实**：降阶梯轨迹 `CAP_ASSESS -> CAP_EMPIRIC_ABX -> CAP_EFFECT_EVAL -> CAP_DEESCALATE -> CAP_DISCHARGE_ASSESS` 和升级轨迹 `CAP_ASSESS -> CAP_EMPIRIC_ABX -> CAP_EFFECT_EVAL -> CAP_UPGRADE -> CAP_DISCHARGE_ASSESS` 均返回 `COMPLETED`。呼吸科医生账号具备 `pathway.read` 和 `patient-pathways` 菜单，可读取已发布模板详情并按图口述；专科专家具备 `pathway-templates` 菜单和 `pathway.write`，配置图阅读证据已保留。
- **幕5前端修复事实**：134 页面最初暴露出详情态只有节点没有边线。根因是 `PathwayGraphEditor` 在只读态不渲染 React Flow `Handle`，导致已有边无法挂接。本分支按 TDD 新增“详情态为已有流转边保留只读连接点”测试，并把 source / target Handle 改为始终渲染、只在编辑态允许连接。前端已发布到 134，source=`codex-demo-drill-act5-cap-pathway-graph-readonly-edges`，backup=`/zoesoft/medkernel/backups/deploy-20260611-052756`，readiness `UP`。
- **幕5账号恢复事实**：专科专家账号 `drill-role-specialist-20260611` 曾因 UI 登录口令探测触发失败计数阈值，已恢复 `platform_credential.status=ACTIVE`、`sys_login_attempt.failed_count=0`，并以 traceId `act5-ui-login-specialist-restored` 验证正确凭据登录 200；未改密码、角色或 MFA。
- **幕5证据目录**：`docs/release/evidence/v1.0-drill-20260611/幕5-CAP临床路径/`。已包含：账号/权限预检查、路径包与模板发布、试运行和医生只读评审、页面截图、UI 统计、前端发布记录、专科专家账号恢复记录、README 和 traceId 清单。凭据只在服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json` 读取，未提交到仓库。

## 当前状态

- 当前工作树：`/Users/zhikunzheng/.config/superpowers/worktrees/codex/codex-demo-drill-act5-cap-pathway`。
- 当前分支：`codex/demo-drill-act5-cap-pathway`，基于 `origin/main` 的幕4合并点 `844059ed`。
- 当前未收尾改动范围：
  - 前端只读路径图边线修复与测试：`frontend/src/pages/tenant/PathwayGraphEditor.tsx`、`frontend/src/pages/tenant/PathwayGraphEditor.test.tsx`。
  - 文档与证据：幕5证据目录、`docs/handbook/user-guides/clinical-runtime.md`、`docs/handbook/user-guides/README.md`、`docs/glossary.md`、本 `_HANDOFF`。
- 已执行的关键验证：
  - TDD 红绿：新增 `PathwayGraphEditor.test.tsx` 用例修复前失败（只读态 `.react-flow__handle` 为 0），修复后 `./node_modules/.bin/vitest run src/pages/tenant/PathwayGraphEditor.test.tsx` 通过 4 项。
  - 证据格式：`find docs/release/evidence/v1.0-drill-20260611/幕5-CAP临床路径 -name '*.json' -print0 | xargs -0 -n 1 jq empty` 通过。
  - 前端类型、构建和静态检查：`npm run typecheck` 通过；`npm run build` 通过；`npm run lint` 通过；`npm run stylelint` 通过。
  - T-GATE：`node scripts/authenticity-guard.mjs --mode=all` 扫描 1547 个文件通过；`node scripts/config-boundary-guard.mjs --mode=all` 扫描 1458 个文件通过；`node scripts/migration-convention-guard.mjs --mode=all` 扫描 570 个迁移文件通过；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 通过。
  - 暂存后门禁：`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main` 扫描 1 个文件通过；`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main` 扫描 0 个文件通过；`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main` 扫描 0 个文件通过；`git diff --cached --check` 通过。
  - 134 发布：`deploy/onprem/mk-publish.sh --frontend --skip-build --source codex-demo-drill-act5-cap-pathway-graph-readonly-edges` 通过，远程 readiness `UP`。
  - UI 截图：`03-ui-pathway-templates.png`、`04-ui-pathway-graph-review.png` 已在 134 远端真实登录后截取；`05-ui-screenshot-check.json` 统计 6 个节点、6 条边线、12 个只读连接点、0 个删除按钮。

## 下一步

1. 提交、推送、创建 PR，等待 CI 全绿后合入 `main`，清理分支 / worktree。
2. 合入后从最新 `origin/main` 继续幕6「运行态推荐 / 患者路径」，不停在幕5收口。
