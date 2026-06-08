# 会话接力

## 唯一执行组织

- 当前分支：`codex/integration-health-contract`
- 基线：`origin/main` = `2d459d5a`（P12-7 批量创作已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 完成 P13-1。
- AdapterHub 后端状态新增 `requiredSources`，稳定列出 HIS / EMR / LIS 必接系统；缺失适配器显示 `MISSING` + `NOT_CONNECTED`，已绑定但未真实连通仍显示 `BOUND` + 真实健康状态。
- 必接系统优先按接入申请 `sourceSystem` 绑定真实适配器，未登记时才按适配器标识/名称兜底识别；不创建虚假适配器记录。
- 前端「适配器中心」首屏新增「必接系统清单」和「数据接入契约」，用户显式输入 packageVersion 后读取 `/engine/integration/data-contract`。
- 只同步本文件与 OpenSpec 任务清单，不新增施工文档。

## 当前证据

- 后端红绿：`mvn -q -Dtest=IntegrationServiceTest#adapterHubStatusIncludesRequiredHisEmrLisChecklistWithoutFakingMissingConnections test`。
- 后端边界红绿：`mvn -q -Dtest=IntegrationServiceTest#requiredSourceFallbackDoesNotMatchUnrelatedSubstringInsideAdapterIdentifier test`。
- 后端聚焦：`mvn -q -Dtest=IntegrationServiceTest,IntegrationDataContractServiceTest,IntegrationDataContractControllerTest,IntegrationControllerSecurityTest test`。
- 前端聚焦：`npm test -- --run src/pages/tenant/AdapterHub.test.tsx src/shared/api/hooks.test.ts`（97 tests）。
- 前端全量：`npm run verify`（81 files / 578 tests / lint / stylelint / lint-rules / format / typecheck 全绿）。
- 前端构建：`npm run build`（Vite 3403 modules transformed）。
- 浏览器烟测：本地 dev 后端 `:18080` + Vite `:5173`，`/adapter/hub` 空库首屏显示 HIS / EMR / LIS `MISSING` + `NOT_CONNECTED` 与数据接入契约入口；390px 视口无横向溢出。
- 后端全量：`mvn test`（1967 tests / 0 failures / 0 errors / 3 skipped；3 skipped 为本机无 Docker 的 Testcontainers 多方言 smoke）。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`（38 pass）；`authenticity-guard --mode=all`（1440 files）；`config-boundary-guard --mode=all`（1355 files）；`migration-convention-guard --mode=changed --base=origin/main`（0 files）；`check-comment-zh.sh`（0 fail / 0 warn）。
- 规范检查：`openspec validate pathway-rule-authoring-overhaul --strict`；`git diff --check`。

## 下一步

1. 提交并推送 `codex/integration-health-contract`，创建 PR，等待 CI 绿后 squash 合入 `main`。
2. 合入后继续 P13-2，不恢复并行线。
