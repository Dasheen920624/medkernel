# B0 First Phase Perfect Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make first-phase B0 customer-facing functionality safe, coherent, and ready for later knowledge work by fixing the highest-risk maintenance, field, version, route, and validation issues in one large PR.

**Architecture:** Keep wave2 production paused. Move B0 manual diagnosis maintenance out of the review page, add safety blockers where runtime support is absent, bridge field catalog paths to runtime contexts, enrich data contracts, and add a B0 perfect verification layer. Use existing route, hook, service, and test patterns.

**Tech Stack:** React + TypeScript + Ant Design + Vitest for frontend; Spring Boot + Java records + JUnit/AssertJ/Mockito + Flyway for backend; Node guard scripts for repo verification.

---

## 2026-06-15 Execution Update

- 第一轮高风险止血已完成：诊断知识维护拆页、诊断不可求值约束发布阻断、findings 解析去重、路径入口条件仿真阻断、字段目录第三方契约元数据、规则/批量/数据接入契约包版本受控选择、第三方数据契约展示、B0 本地门禁。
- Task 3 的实际落地先收敛为路径仿真入口条件校验 + 前端字段目录不可用阻断；完整 `ContextFactBridge` 仍归入后续 Phase 3 深化，避免在本轮未完全验证前重写多引擎上下文事实模型。
- Task 5 的实际页面路径为 `frontend/src/pages/tenant/AdapterHub.tsx`；规则维护与批量维护的包版本风险一并纳入本轮整改。
- CI workflow 暂不改动；`scripts/b0-perfect-check.mjs` 作为当前大 PR 的本地强制证据门禁，待后续验收稳定后再纳入远端 CI。
- 沙盘 #2–#10 不由代码作者代签临床评审；当前只把 `scenario-rules` 与 `b0-perfect-check` 联动为自动门禁，证明未评审场景不能 seed、不能提前开放。
- 10 万级压测先补 B0 可复跑合同：平台主源知识身份列表改为数据库分页，新增知识身份与术语/映射 H2 10 万级仓储合同；术语候选生成已从全量本地词 × 全量标准词改为分页读取 + 确定性索引召回，并补 H2 10 万候选/冲突分页合同；候选生成接口已改为 V135 持久化异步任务，只返回任务、进度、摘要与 `candidatePageUri`，候选明细按 `generationJobCode` 分页查看；知识异步导出已修复 `filterJson` 未生效问题并补 H2 10 万提交、完成、轮询、下载合同；另补 opt-in PostgreSQL 15.18 + Oracle 21.3 Testcontainers 真实方言烟测，同步 #628 后按 135 版迁移构造 10 万知识身份、10 万标准术语、10 万院内术语、10 万正式映射并跑深页查询；同步 #628 后迁移号重排为 V132 退修、V133 文档解析任务、V134 诊断菜单权限、V135 术语候选任务。PG/Oracle 导出/筛选/候选冲突 P95 和资源占用仍按 deferred 保持 open。
- 术语候选异步任务已补服务/API/前端 hook/五方言迁移/B0 门禁证据：`TerminologyServiceTest`、`TerminologyApiContractTest`、`EngineEndToEndIntegrationTest`、`TerminologyRepositoryLargeScaleTest`、`MigrationBaselineContractTest`、`H2BaselineMigrationTest`、`FlywayMultiDialectSmokeTest`、`hooks.test.ts`、`TerminologyMapping.test.tsx` 和 `scripts/b0-perfect-check.mjs` 均通过。
- 项目 Playwright 截图链路已补为可复跑 E2E：`b0-screenshot-chain.spec.ts` 覆盖登录页、Header 用户菜单、规则桌面/390px、配置包发布弹窗和 runtime 错误记录；运行中发现并修复 `ConfigPackages` 发布弹窗打开前调用未挂载 `syncForm` 的 AntD 警告。
- 第二遍深查继续收敛包版本风险：`AuthoringAssets` 统一资产克隆草稿改为按资产类型从既有配置包选择包版本，新增页面回归和 B0 门禁，防止克隆到不存在或不可运行的配置包版本。
- 第二遍深查发现路径结局指标绑定仍可手写指标包版本且提示默认路径知识包版本；已改为读取 `EVALUATION` 配置包，选择 ACTIVE 评估指标时自动带出其评估包版本，并由 PathwayTemplates 回归和 B0 门禁锁定。
- 第二遍深查发现配置包分页超界空页会把真实 total 误报为 0；已改为保留 count 结果，`PackageEngineServiceTest` 81 tests 通过，避免版本选择器和配置包治理在翻页/筛选时误判“无包”。
- 国产化真实环境/data 本轮暂不处理，后续全面验收再回到国产 OS/JDK、达梦、金仓、真实国产数据和现场环境。

---

## File Map

- `frontend/src/shared/config/routes.ts`: add B0 manual diagnosis maintenance route and remove review-page mixed goal.
- `frontend/src/app/router.tsx`: register the new page route if routing is explicit.
- `frontend/src/pages/quality/KnowledgeGovernance.tsx`: remove diagnosis maintenance tab and keep review/publish surface focused.
- `frontend/src/pages/quality/DiagnosisKnowledgeMaintenance.tsx`: new page wrapper around existing diagnosis maintenance panel.
- `frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx`: keep as reusable maintenance panel; add unsupported-constraint and evidence hints only if needed.
- `frontend/src/pages/quality/KnowledgeGovernance.test.tsx`: assert diagnosis maintenance no longer appears inside review page.
- `frontend/src/pages/quality/DiagnosisKnowledgeMaintenance.test.tsx`: assert new page loads maintenance panel and primary actions.
- `frontend/src/shared/config/routes.test.ts`: assert new route permissions and old route goal.
- `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeService.java`: block publish when unsupported value/time constraints are present; make findings parsing robust.
- `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeServiceTest.java`: red/green tests for unsupported constraints and duplicate findings.
- `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayEngineService.java`: route pathway criteria through canonical field context bridge.
- `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextFactBridge.java`: new shared bridge for canonical field paths used by rule/pathway/CDSS.
- `medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayEngineServiceTest.java`: tests using `observations[].valueNumeric` for include/exclude and exit criteria.
- `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextFieldDescriptor.java`: add runtime support metadata if not already present.
- `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextFieldCatalog.java`: mark supported engines and null/required policies for core B0 fields.
- `medkernel-backend/src/main/java/com/medkernel/engine/integration/service/IntegrationDataContractService.java`: include runtime support metadata in third-party data contract.
- `medkernel-backend/src/test/java/com/medkernel/engine/integration/service/IntegrationDataContractServiceTest.java`: assert contract exposes runtime metadata.
- `frontend/src/pages/integration/AdapterHub.tsx`: replace free packageVersion entry with controlled version selector if existing package hooks support it; otherwise add explicit status/hint.
- `frontend/src/shared/api/hooks.ts`: add/adjust hook types for enriched contract metadata and effective package list if needed.
- `docs/audit/2026-06-15-B0第一阶段全功能核查与完美化改造方案.md`: keep findings and task status aligned with implementation.
- `docs/_HANDOFF.md`: update after each major slice with real status only.
- `frontend/e2e/b0-screenshot-chain.spec.ts`: B0 Playwright screenshot evidence chain with runtime error records.

## Task 1: Route And Page Split For Diagnosis Maintenance

**Files:**
- Modify: `frontend/src/shared/config/routes.ts`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/pages/quality/KnowledgeGovernance.tsx`
- Create: `frontend/src/pages/quality/DiagnosisKnowledgeMaintenance.tsx`
- Test: `frontend/src/pages/quality/KnowledgeGovernance.test.tsx`
- Test: `frontend/src/pages/quality/DiagnosisKnowledgeMaintenance.test.tsx`
- Test: `frontend/src/shared/config/routes.test.ts`

- [ ] **Step 1: Write failing frontend tests**

Add tests that assert `/knowledge/governance` no longer renders the diagnosis maintenance tab and that `/knowledge/diagnosis` has title `诊断知识维护`.

- [ ] **Step 2: Verify RED**

Run:

```bash
cd frontend && npx vitest run src/pages/quality/KnowledgeGovernance.test.tsx src/shared/config/routes.test.ts
```

Expected: FAIL because the diagnosis tab still exists in `KnowledgeGovernance` and the new route does not exist.

- [ ] **Step 3: Implement route split**

Create `DiagnosisKnowledgeMaintenance.tsx` as a focused page wrapper around `DiagnosisKnowledgePanel`; update route config and app router; remove diagnosis tab from `KnowledgeGovernance`.

- [ ] **Step 4: Verify GREEN**

Run:

```bash
cd frontend && npx vitest run src/pages/quality/KnowledgeGovernance.test.tsx src/pages/quality/DiagnosisKnowledgeMaintenance.test.tsx src/shared/config/routes.test.ts
```

Expected: PASS.

## Task 2: Diagnosis Publish Safety Blockers

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeService.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeServiceTest.java`

- [ ] **Step 1: Write failing backend tests**

Add one test proving publish rejects any criterion with nonblank `valueConstraint` and one test proving duplicate comma findings do not throw an unstructured exception.

- [ ] **Step 2: Verify RED**

Run:

```bash
cd medkernel-backend && mvn -q -Dtest=DiagnosisKnowledgeServiceTest test
```

Expected: FAIL because publish currently ignores value/time constraints and duplicate `Set.of(...)` can throw.

- [ ] **Step 3: Implement minimal safe behavior**

Add a `rejectUnsupportedRuntimeConstraints` check in publish gate. Replace `Set.of(raw.split("\\s*,\\s*"))` with ordered trim/filter/deduplicate logic and structured validation for empty effective findings.

- [ ] **Step 4: Verify GREEN**

Run:

```bash
cd medkernel-backend && mvn -q -Dtest=DiagnosisKnowledgeServiceTest,DiagnosisMatcherTest test
```

Expected: PASS.

## Task 3: Pathway Canonical Field Bridge

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextFactBridge.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayEngineService.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayEngineServiceTest.java`

- [ ] **Step 1: Write failing pathway tests**

Add tests for entry include, entry exclude, exit include, and exit exclude using canonical catalog fields such as `observations[].valueNumeric` with expression aggregation.

- [ ] **Step 2: Verify RED**

Run:

```bash
cd medkernel-backend && mvn -q -Dtest=PathwayEngineServiceTest test
```

Expected: FAIL because pathway context currently exposes mostly `observation.<code>.*`, not canonical arrays.

- [ ] **Step 3: Implement bridge**

Create `ContextFactBridge` to build both existing dotted facts and canonical arrays from `ContextSnapshotResources`. Route pathway criteria context through the bridge.

- [ ] **Step 4: Verify GREEN**

Run:

```bash
cd medkernel-backend && mvn -q -Dtest=PathwayEngineServiceTest test
```

Expected: PASS.

## Task 4: Field Contract Runtime Metadata

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextFieldDescriptor.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextFieldCatalog.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/integration/service/IntegrationDataContractService.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/context/ContextFieldCatalogTest.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/integration/service/IntegrationDataContractServiceTest.java`

- [ ] **Step 1: Write failing contract tests**

Assert core fields include runtime support status, supported engines, required policy, null policy, and that data contract JSON exposes those attributes.

- [ ] **Step 2: Verify RED**

Run:

```bash
cd medkernel-backend && mvn -q -Dtest=ContextFieldCatalogTest,IntegrationDataContractServiceTest test
```

Expected: FAIL because field descriptors and contract responses lack runtime metadata.

- [ ] **Step 3: Implement metadata**

Extend descriptor records compatibly. Populate core fields with conservative metadata. Include the metadata in generated data contracts.

- [ ] **Step 4: Verify GREEN**

Run:

```bash
cd medkernel-backend && mvn -q -Dtest=ContextFieldCatalogTest,ContextFieldCatalogServiceMergeTest,IntegrationDataContractServiceTest test
```

Expected: PASS.

## Task 5: Package Version Selection And Contract UX

**Files:**
- Modify: `frontend/src/pages/integration/AdapterHub.tsx`
- Modify: `frontend/src/shared/api/hooks.ts`
- Test: `frontend/src/pages/integration/AdapterHub.test.tsx`
- Test: `frontend/src/shared/api/hooks.test.ts`

- [ ] **Step 1: Write failing frontend tests**

Assert the data contract panel does not present packageVersion as an unexplained free-text-only field and displays contract runtime metadata.

- [ ] **Step 2: Verify RED**

Run:

```bash
cd frontend && npx vitest run src/pages/integration/AdapterHub.test.tsx src/shared/api/hooks.test.ts
```

Expected: FAIL because the current contract fetch is driven by manual packageVersion input and does not render runtime metadata.

- [ ] **Step 3: Implement UX hardening**

Reuse existing package/effective package hooks if available. If no safe list hook exists, keep input but add explicit status/help text, validation, and display returned packageVersion/schema/runtime metadata so the user cannot mistake it for arbitrary free text.

- [ ] **Step 4: Verify GREEN**

Run:

```bash
cd frontend && npx vitest run src/pages/integration/AdapterHub.test.tsx src/shared/api/hooks.test.ts
```

Expected: PASS.

## Task 6: B0 Perfect Verification Gate

**Files:**
- Create: `scripts/b0-perfect-check.mjs`
- Create: `scripts/b0-perfect-check.test.mjs`
- Read/verify: `scripts/sandbox/scenario-rules.json`
- Read/verify: `scripts/sandbox/scenario-rules.mjs`
- Test: `scripts/sandbox/scenario-rules.test.mjs`
- Modify: `.github/workflows/ci.yml` only if the check is lightweight and safe for CI
- Modify: `docs/audit/2026-06-15-B0第一阶段全功能核查与完美化改造方案.md`

- [ ] **Step 1: Write failing Node test**

Test that the checker fails when `KnowledgeGovernance` imports `DiagnosisKnowledgePanel`, when `_HANDOFF` contains `已实现待合`, or when the B0 audit report is missing required sections.

- [ ] **Step 2: Verify RED**

Run:

```bash
node --test scripts/b0-perfect-check.test.mjs
```

Expected: FAIL because the checker does not exist.

- [ ] **Step 3: Implement checker**

Implement a small repository text/AST guard using Node built-ins. Scope it to fast checks only: handoff status, diagnosis route split, audit report sections, sandbox clinical-review gate, and国产化真实环境 exclusion wording.

- [ ] **Step 4: Verify GREEN**

Run:

```bash
node --test scripts/b0-perfect-check.test.mjs
node --test scripts/sandbox/scenario-rules.test.mjs
node scripts/b0-perfect-check.mjs
```

Expected: PASS.

## Task 7: Documentation And Handoff Closure

**Files:**
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/audit/2026-06-15-B0第一阶段全功能核查与完美化改造方案.md`
- Modify: `docs/audit/deferred-issues.md` only for items genuinely closed or explicitly deferred out of current scope

- [ ] **Step 1: Update docs with actual status**

Mark completed `AUDIT-B0-*` items based on implemented evidence. Keep国产化真实环境 out of current scope and do not mark related deferred items done.

- [ ] **Step 2: Verify docs**

Run:

```bash
git diff --check
node scripts/b0-perfect-check.mjs
```

Expected: PASS.

## Task 8: Final Verification Before PR

**Files:**
- No direct source edits unless failures reveal real issues.

- [ ] **Step 1: Backend targeted tests**

Run:

```bash
cd medkernel-backend && mvn -q -Dtest=DiagnosisKnowledgeServiceTest,DiagnosisMatcherTest,PathwayEngineServiceTest,ContextFieldCatalogTest,ContextFieldCatalogServiceMergeTest,IntegrationDataContractServiceTest test
```

Expected: PASS.

- [ ] **Step 2: Frontend targeted tests**

Run:

```bash
cd frontend && npx vitest run src/pages/quality/KnowledgeGovernance.test.tsx src/pages/quality/DiagnosisKnowledgeMaintenance.test.tsx src/shared/config/routes.test.ts src/pages/integration/AdapterHub.test.tsx src/shared/api/hooks.test.ts
```

Expected: PASS.

- [ ] **Step 3: Guard checks**

Run:

```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check
```

Expected: PASS.

- [ ] **Step 4: Broader regression**

Run when targeted checks are green:

```bash
cd medkernel-backend && mvn -q test
cd frontend && npm run verify
node --test cli/test/*.test.mjs
node --test mcp-server/test/*.test.mjs
```

Expected: PASS. If runtime is too long, record exact skipped command and reason in `_HANDOFF`.

## Execution Notes

- Keep all commits on branch `codex/b0-first-phase-perfect-remediation`.
- Do not push or merge to remote `main`.
- Do not mark国产化真实环境/data done in this PR.
- Do not enter P6 or produce formal knowledge assets.
- Prefer small local commits per task, but final delivery is one PR.
