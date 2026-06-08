# 会话接力

## 唯一执行组织

- 当前分支：`codex/authoring-readable-preview`
- 基线：`origin/main` = `b01e5c09`（H-6 `feat: 接入引擎领域事件协同分发` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 已完成 P9-1 至 H-6，并继续进入 P12 创作体验。
- P12-1 已实现统一后端 `POST /api/v1/engine/authoring/preview`：规则条件树与路径守卫共用同一预览服务，输出中文 `previewText`、分行、片段、warning 与 traceId。
- 规则页 L2 条件树、L3 DSL、详情 L2/L3 均接入同一自然语言预览组件；路径页 L2 边守卫、L3 DSL、详情 L2/L3 的已存边条件也接入同一组件。
- 前端预览统一走 `useAuthoringPreview` 与标准 API 上下文字段，不新增平行入口、不造假预览、不绕过包版本 / 租户 / 角色上下文。
- OpenSpec P12-1 已勾选；文档只同步本文件与任务清单，不新增施工说明。

## 当前证据

- 后端红灯：`mvn -q -Dtest=AuthoringPreviewServiceTest,AuthoringPreviewControllerTest test` 曾先失败于缺少 `AuthoringPreview*` 后端类；随后失败于派生表达式预览丢失 `expr` 明细。
- 前端红灯：`npm test -- --run src/shared/api/hooks.test.ts` 曾失败于缺少 `useAuthoringPreview`；`AuthoringReadablePreview.test.tsx` 曾失败于组件文件不存在；规则 / 路径页面测试曾失败于未显示“可读预览”。
- 后端聚焦：`mvn -q -Dtest=AuthoringPreviewServiceTest,AuthoringPreviewControllerTest test` 通过。
- 后端契约回归：`mvn -q -Dtest=ServiceContractGovernanceTest test` 通过；`AuthoringPreviewController` 已进入 `ServiceContractCatalog`。
- 后端全量：`mvn -q test` 通过；Surefire 汇总 287 suites / 1925 tests / 0 failures / 0 errors / 3 skipped。当前本机无 Docker，Testcontainers 探测仍输出不可用日志，但 Maven 退出码为 0。
- 前端聚焦：`npm test -- --run src/shared/api/hooks.test.ts src/shared/ui/condition/AuthoringReadablePreview.test.tsx` 通过，81 tests。
- 规则页：`npm test -- --run src/pages/tenant/RuleDefinitions.test.tsx` 通过，18 tests。
- 路径页：`npm test -- --run src/pages/tenant/PathwayTemplates.test.tsx` 通过，19 tests。
- 前端全量：`npm test` 通过，79 files / 552 tests。
- 前端静态与构建：`npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run build` 通过。
- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 通过，38 tests；changed-mode `authenticity-guard` / `config-boundary-guard` / `migration-convention-guard` 通过。
- 注释与空白：`bash scripts/check-comment-zh.sh`、`git diff --check` 通过。

## 下一步

1. 提交、推送 `codex/authoring-readable-preview`，创建 PR。
2. 远端 CI 通过后合入 `main`。
3. 从最新 `origin/main` 继续 P12-2，不恢复并行线。
