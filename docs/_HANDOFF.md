# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-pathway-outcome-replay`
- 基线：`origin/main` = `0243b009`（P10-3 `feat: 支持路径模板继承差异合并` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 PR #470、P9-2 PR #471、P9-3 PR #472、P9-4 PR #473、P9-5 PR #474、P10-1 PR #475、P10-2 PR #476、P10-3 PR #477 已合入主线。
- P10-4 已在当前分支实现：`pathway_outcome_binding` 进入 5 方言 V12，路径模板支持按模板 / 阶段 / 里程碑绑定 ACTIVE `EvaluationIndicator`，详情、发布影响、患者入径、患者详情和推进响应均返回结局闭环。
- 仿真统一为单快照、队列回放、时光机三种模式；队列/时光机只读执行，不写患者路径实例。
- 患者多路径并行时检测 `ORDER_SET` 同医嘱引用冲突，只返回“仅提示协调”的协调告警，不自动改医嘱。
- 前端 `PathwayTemplates` 已把结局指标绑定纳入 L2 配置与 L3 DSL，真实快照试运行支持队列回放；`PatientPathways` 详情抽屉展示结局指标闭环与多路径协调提示。
- `docs/backlog.md`、`docs/cards/D2/PATH-01.md` 与 OpenSpec P10-4 已同步；不新增施工文档。
- 线2 / 线3 / 线4当前已统一纳入 P9/P10 主线承接；后续继续从 P-HARDEN、P12/P13/P11 未完成项推进，不恢复并行线。

## 当前证据

- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#createsTemplateWithOutcomeBindingsAndStoresOutcomeAssetContent test` 先因缺 `outcomeBindings` 合同 / 表 / 服务保存失败。
- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#rejectsOutcomeBindingWhenIndicatorIsMissingOrInactive test` 先因未校验 EvaluationIndicator 失败。
- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#replaysSnapshotQueueWithoutWritingRuntimeState test` 先因缺队列回放模式失败。
- 后端 RED：`mvn -q -Dtest=PathwayEngineServiceTest#warnsParallelOrderSetConflictWithoutChangingOrders test` 先因缺多路径协调提示失败。
- 后端全量：`mvn -q test` 退出 0；Surefire XML 汇总 `281` files / `1889` tests / `0` failures / `0` errors / `3` skipped。本机无 Docker socket，Testcontainers 记录不可用日志但未导致命令失败。
- 前端聚焦：`npm run test -- PathwayTemplates PatientPathways hooks` 退出 0；`3` files / `105` tests 通过。
- 前端全量：`npm run verify` 退出 0；lint / stylelint / format / typecheck / Vitest 均通过，`78` files / `549` tests 通过；lint 仍有既有 no-nested-ternary warning，不阻断。
- 页面 E2E：后端 `http://localhost:18080/medkernel/api/v1/system/ping` 返回 OK；`E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 VITE_API_PROXY_TARGET=http://localhost:18080 npx playwright test e2e/pathway-graph-editor.spec.ts --project=chromium` 退出 0，`3/3` 通过。首次未配置 Vite 代理目标被配置边界拒绝，补真实后端代理后通过。
- OpenSpec/T-GATE：`openspec validate pathway-rule-authoring-overhaul --strict` 退出 0；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 38 tests 通过；changed-mode 真实性 / 配置边界 / 迁移规约分别扫描 19 / 16 / 5 个文件且 0 阻断；`bash scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 退出 0。

## 下一步

1. 复跑 OpenSpec/T-GATE 最终门禁。
2. 提交并推送 `codex/line2-pathway-outcome-replay`，创建 PR；远端 CI 通过后合入 `main`。
3. 从最新 `origin/main` 继续 P-HARDEN / P12 / P13 / P11 未完成项，仍按线1唯一主流程承接。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
