# P5 第一阶段重新验收收官长任务实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to execute this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重新打开第一阶段验收后，把嵌入宿主、全真体验沙盘、主数据同步、术语冲突处置、遗留整改、D0-D6 覆盖矩阵、134 真实复演和 T-GATE 统一收敛到可交付状态。

**Architecture:** 以 `docs/_HANDOFF.md` 为接力事实，复用已拆分的沙盘 A/A2/B/C/D 子计划；外层按“缺口补齐 -> 本地验证 -> 134 真实复演 -> 证据与接力”推进。所有新增业务能力只调用既有引擎/治理/权限服务，不复制核心逻辑；无法现场完成的外部项写入 `docs/audit/deferred-issues.md` 并保持主线继续。

**Tech Stack:** Java 21 / Spring Boot / Maven / Flyway 五方言；React / TypeScript / Vitest / Playwright；Node 演练脚本；MedKernel T-GATE 脚本。

---

## 文件结构

| 文件或目录 | 职责 |
|---|---|
| `docs/_HANDOFF.md` | 只记录已验证事实、当前执行线和下一步 |
| `docs/audit/p5-first-phase-closeout.md` | 第一阶段正式收口结论与证据索引 |
| `docs/audit/p5-second-fresh-drill-checkpoint.md` | 重新验收过程、缺陷闭环和 134 批次记录 |
| `docs/audit/product-function-catalog.md` | 页面、菜单、API、数据、证据覆盖矩阵 |
| `docs/release/evidence/p5-second-fresh-drill-20260612/第一阶段最终收官/` | 本轮收官 JSON 证据 |
| `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/` | 全真体验沙盘后端目录与编排入口 |
| `frontend/src/features/sandbox/`、`frontend/src/pages/sandbox/` | 沙盘业务系统宿主、嵌入终端和路径检查器 |
| `medkernel-backend/src/main/java/com/medkernel/engine/integration/masterdata/` | 院内主数据同步接口与对账 |
| `scripts/drill/sandbox-fulltruth-run.mjs` | 沙盘真实链路复演脚本 |
| `scripts/drill/p5-first-phase-rectification-closeout.mjs` | 幕7 遗留整改闭环脚本 |

## Task 1: 固定当前事实与缺口

- [x] **Step 1:** 读取 `docs/_HANDOFF.md`，确认当前执行目标为第一阶段重新打开完整验收。
- [x] **Step 2:** 用 `git status --short --branch`、`git diff --stat`、`rg --files` 盘点当前分支改动。
- [x] **Step 3:** 将缺口归为：嵌入宿主、沙盘、术语冲突、院内主数据同步、遗留整改、覆盖矩阵、134 统一复演、T-GATE。
- [x] **Step 4:** 在 `_HANDOFF` 顶部更新本轮已执行的真实验证命令和未执行的外部复演项。

## Task 2: 沙盘和嵌入宿主收敛

- [x] **Step 1:** 保留 `sandbox.run` 与 `menu.sandbox` 进入同一权限治理。
- [x] **Step 2:** 增补 `GET /api/v1/engine/sandbox/scenarios`，让后端目录成为场景状态与验收目标权威。
- [x] **Step 3:** 前端 `useSandboxScenarios` 消费后端目录，并用本地表单细节补齐业务录入。
- [x] **Step 4:** 运行沙盘相关后端/前端定向测试，确认目录、运行、路由、菜单和路径检查器通过。
- [ ] **Step 5:** 运行 `scripts/drill/sandbox-fulltruth-run.mjs` 对目标环境复演；若未连接 134，记录为待复演而不冒领。

## Task 3: 主数据同步与院内身份链收敛

- [x] **Step 1:** 确认 `MasterDataSyncController`、`MasterDataSyncService`、人员/身份/用户适配器测试已纳入定向验证。
- [x] **Step 2:** 后端定向测试覆盖同步、失败记录、对账、安全授权和迁移合同。
- [x] **Step 3:** 五方言迁移 smoke 通过后，更新 `docs/audit/product-function-catalog.md` 的 API_ONLY 映射证据。

## Task 4: 遗留整改与术语冲突

- [x] **Step 1:** 保留 `docs/release/evidence/p5-second-fresh-drill-20260612/第一阶段最终收官/01-rectification-closeout.json` 作为整改收官证据。
- [ ] **Step 2:** 运行 `node --check scripts/drill/p5-first-phase-rectification-closeout.mjs` 已通过；目标环境脚本尚未执行，幕7 遗留 open 整改任务不得写成 134 已闭环。
- [ ] **Step 3:** 对术语一对多冲突前台处置执行测试/页面验证；若仍缺现场数据，登记为第一阶段阻断或待处理问题，不写成完成。

## Task 5: 覆盖矩阵与文档同步

- [ ] **Step 1:** 复查 `docs/audit/product-function-catalog.md` 中 D0-D6 页面、菜单、API、数据、证据行，确保 `/sandbox`、嵌入 API、主数据同步、运行保障均有真实锚点。
- [x] **Step 2:** 更新 `docs/audit/p5-first-phase-closeout.md` 和 `docs/audit/p5-second-fresh-drill-checkpoint.md`，只写已经验证的命令与证据。
- [x] **Step 3:** 同步 `docs/_HANDOFF.md` 当前执行线、已跑验证、下一步和阻断项。

## Task 6: 本地验证门禁

- [x] **Step 1:** `node --check scripts/drill/sandbox-fulltruth-run.mjs && node --check scripts/drill/p5-first-phase-rectification-closeout.mjs && node --check scripts/sandbox/seed-scenarios.mjs && node --test scripts/sandbox/scenario-rules.test.mjs`。
- [x] **Step 2:** `cd medkernel-backend && mvn -Dtest=SandboxScenarioCatalogTest,SandboxOrchestrationServiceTest,SandboxScenarioApiContractTest,SandboxScenarioControllerSecurityTest,MasterDataSyncServiceTest,MasterDataSyncRepositoryTest,MasterDataSyncControllerSecurityTest,MasterDataSyncMigrationContractTest,ComplianceMasterDataPersonnelAdapterTest,IdentityBindingExternalSyncTest,ComplianceUserExternalRoleSyncTest,PermissionCodeTest test`。
- [x] **Step 3:** `cd frontend && npm test -- src/features/sandbox/sandboxScenarios.test.ts src/features/sandbox/SandboxDataEntry.test.tsx src/features/sandbox/SandboxEmbedFrame.test.tsx src/features/sandbox/SandboxPathInspector.test.tsx src/pages/sandbox/SandboxHost.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/shared/config/menu.test.ts`。
- [x] **Step 4:** `cd medkernel-backend && mvn test` 全量后端。
- [x] **Step 5:** `cd frontend && npm test && npm run build && npm run lint` 全量前端。
- [x] **Step 6:** 五方言迁移 smoke：`cd medkernel-backend && mvn test` 已覆盖 `FlywayMultiDialectSmokeTest`、`H2BaselineMigrationTest`、`MigrationBaselineContractTest`。
- [x] **Step 7:** T-GATE：`authenticity-guard`、`config-boundary-guard`、`migration-convention-guard`、`scripts/check-comment-zh.sh`、`git diff --check`；未 stage，`git diff --cached --check` 不适用。

## Task 7: 134 统一真实复演

- [ ] **Step 1:** 发布前备份、隔离恢复校验和部署留痕，保持 `destructive_action_performed=false`。
- [ ] **Step 2:** 部署当前 commit 到 134，记录 manifest、jar SHA、服务状态、Flyway 版本、表计数、xattr。
- [ ] **Step 3:** 执行沙盘、嵌入宿主、主数据同步、整改闭环、备份恢复、降级和全量第一阶段复演脚本。
- [ ] **Step 4:** 归档 JSON、截图、traceId、服务端事实和敏感扫描结果。

## Task 8: 收口交付

- [ ] **Step 1:** 所有验证通过后提交中文 commit，说明范围、验证、医疗安全、部署和迁移影响。
- [ ] **Step 2:** 推送分支并创建 PR；CI 全绿后按项目规则合并。
- [ ] **Step 3:** 合并后从新 `origin/main` 继续下一阶段；正式知识生产在文献资料库根地址真实配置与独立验收前继续阻断。
