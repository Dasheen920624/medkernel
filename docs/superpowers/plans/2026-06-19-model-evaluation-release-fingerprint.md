# 医学评测制品指纹与签署状态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 阻止旧部署制品的医学评测被签署或用于 Provider 放行，并修复已签署态误导、知识治理白屏与无权限包查询。

**Architecture:** 为评测运行冻结 `medkernel.runtime.release-fingerprint`，由服务层统一校验当前制品一致性；历史行保持原状但失去当前放行资格。前端显式区分已签署、历史签署与待复核状态，并按权限启用评测包引用查询。

**Tech Stack:** Java 21、Spring Boot、Spring Data JDBC、Flyway 五方言、React、TypeScript、TanStack Query、Vitest。

---

### Task 1: 建立后端失败测试

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/eval/ModelEvalServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/eval/MedicalRegressionRepositoryTest.java`

- [x] 新增当前指纹、异指纹和空历史指纹用例，断言异指纹不可签署、不可通过 `isClearedForGoLive`，详情返回 `releaseCurrent=false`。
- [x] 运行 `cd medkernel-backend && mvn -q -Dtest=ModelEvalServiceTest,MedicalRegressionRepositoryTest test`，确认测试因字段和门禁缺失失败。

### Task 2: 实现运行指纹与五方言迁移

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/RuntimeProperties.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRun.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRunDetailResponse.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRunSummaryResponse.java`
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V156__model_eval_release_fingerprint.sql`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`

- [x] 增加 `releaseFingerprint` 配置和评测行字段，生产中心拒绝占位指纹。
- [x] 生成评测时写指纹，签署与上线门禁比较当前指纹，详情返回 `releaseCurrent`。
- [x] 新增五方言迁移、中文注释并更新最新迁移契约。
- [x] 运行 Task 1 定向测试和迁移测试，确认全部通过。

### Task 3: 建立并修复前端回归

**Files:**
- Modify: `frontend/src/shared/api/hooks.ts`
- Modify: `frontend/src/pages/quality/MedicalRegressionReview.tsx`
- Modify: `frontend/src/pages/quality/MedicalRegressionReview.test.tsx`
- Modify: `frontend/src/pages/quality/QcEvalSets.tsx`
- Modify: `frontend/src/pages/quality/QcEvalSets.test.tsx`
- Modify: `frontend/src/shared/api/hooks.test.ts`

- [x] 先新增失败测试：`PASSED` 显示签署事实；历史签署显示警告；无 `package.read` 时 `usePackages` 不启用。
- [x] 运行对应 Vitest，确认当前实现失败。
- [x] 为详情类型增加 `releaseCurrent`，按状态渲染签署结果；为 `usePackages` 增加 `enabled`，评价指标页按权限调用。
- [x] 重跑对应 Vitest，确认通过且不再产生包列表请求。

### Task 4: 修复知识治理白屏与同类越权预取

**Files:**
- Modify: `frontend/src/shared/api/hooks.ts`
- Modify: `frontend/src/pages/quality/KnowledgeGovernance.tsx`
- Modify: `frontend/src/pages/tenant/RuleDefinitions.tsx`
- Create: `frontend/src/app/AppErrorBoundary.tsx`
- Create: `frontend/src/shared/lib/frontendDiagnostics.ts`

- [x] 将生产候选接口按真实 `PageResponse` 契约读取 `.items`，并补齐服务端分页参数。
- [x] 仅在生产入口启用生产就绪/任务查询，仅在选中候选且具备 `package.read` 时查询审核配置包。
- [x] 规则定义页按 `package.read` 禁用配置包预取，消除同类无权限请求。
- [x] 增加应用级错误边界和结构化前端诊断事件，渲染异常不再直接白屏。

### Task 5: 全量验证与接力

**Files:**
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`

- [x] 运行后端全量、前端 `npm run verify`、B0、中文注释、产品目录、迁移规约和 `git diff --check`。
- [x] 更新接力：记录 2026-06-19 现场根因、旧签署仅保留历史审计、新制品须重跑评测。
- [x] 本地提交，不 push、不创建 PR。

### Task 6: 134 前向部署与现场核验

**Files:**
- Modify: 134 `/zoesoft/medkernel/conf/medkernel.env`
- Create: `runtime/release-freeze/<commit>/`

- [x] 将 `MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT` 设置为最终部署提交 SHA，构建并冻结制品。
- [x] 不清库前向部署至 134，核验 Flyway V156、JAR/hash、readiness、服务 active/enabled 和 `NRestarts=0`。
- [x] 只读核验旧运行 1、2不再满足当前放行门禁，页面显示历史签署警告。
- [ ] 由真人专家接手后重新生成当前制品评测并逐例签署；不得自动代签或在无人接管时主动外调模型。
