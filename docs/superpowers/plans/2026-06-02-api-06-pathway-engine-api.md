# D2 API-06 Pathway Engine API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收口路径引擎客户面 API 到 `/api/v1/engine/pathway/**`，补齐 12 字段统一入参、患者路径推进/变异/时钟合同，并同步清理前端共享 API 旧入口。

**Architecture:** 复用现有 `PathwayEngineService` 的真实专病包、模板、患者路径、推进、变异和时钟能力；控制器只负责新客户面路由、权限、统一上下文校验和响应包装。前端只调整共享 hooks 与路径页面中直接影响 API 可用性的旧枚举/路由口径，避免扩大到 PATH-01 页面深度重构。

**Tech Stack:** Spring Boot + Spring MVC + Spring Security + Spring Data JDBC；Vitest + React Query API hooks；Maven / npm / T-GATE。

---

### Task 1: API-06 基线与失败合同测试

**Files:**
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayEngineApiContractTest.java`
- Modify: `frontend/src/pages/tenant/RulePathwayCleanliness.test.ts`

- [ ] **Step 1: 建立后端绿色基线**

Run:
```bash
mvn -q -Dtest=PathwayEngineServiceTest,PathwayEngineControllerSecurityTest,PathwayProgressorTest,PathwayRepositoryTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```
Expected: PASS；若失败先定根因，不写生产代码。

- [ ] **Step 2: 写后端失败合同测试**

新增 `PathwayEngineApiContractTest`，覆盖：
```java
mvc.perform(post("/api/v1/engine/pathway/specialty-packages").with(writeJwt()).contentType(MediaType.APPLICATION_JSON).content("{...缺 apiContext...}"))
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.code").value("ENG-API-002"));

mvc.perform(post("/api/v1/engine/pathways/packages").with(writeJwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isNotFound());

mvc.perform(get("/api/v1/engine/pathway/patient-pathways/pp-1/variances").with(readJwt()))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.success").value(true));
```

- [ ] **Step 3: 写前端失败合同测试**

在 `RulePathwayCleanliness.test.ts` 增加共享 API 入口断言：
```ts
const hooksSource = readSource("src/shared/api/hooks.ts");
expect(hooksSource).not.toContain("/engine/rules");
expect(hooksSource).not.toContain("/engine/pathways");
expect(hooksSource).toContain("/engine/rule/rules");
expect(hooksSource).toContain("/engine/pathway/pathway-templates");
```

- [ ] **Step 4: 运行 RED**

Run:
```bash
mvn -q -Dtest=PathwayEngineApiContractTest,PathwayEngineControllerSecurityTest test
npm test -- --run src/pages/tenant/RulePathwayCleanliness.test.ts
```
Expected: 新路由/旧入口/前端 hooks 断言失败，失败原因必须对应本卡缺口。

### Task 2: 后端客户面路由与 12 字段上下文

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayApiContext.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayOperationRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayEngineController.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/*Request.java`

- [ ] **Step 1: 新增路径 API 标准上下文**

实现 `PathwayApiContext`，字段与 API-05 `RuleApiContext` 一致：`request_id/trace_id/tenant_id/group_id/hospital_id/campus_id/site_id/department_id/specialty_id/user_id/role_codes/package_version`。校验必填 `request_id/trace_id/tenant_id/user_id/role_codes/package_version`，租户不一致抛 `ORG_SCOPE_DENIED`。

- [ ] **Step 2: DTO 接入上下文且保留旧测试构造器**

让 `SpecialtyPackageCreateRequest`、`PathwayTemplateCreateRequest`、`PatientPathwayEnterRequest`、`PathwayAdvanceRequest`、`PathwaySimulateRequest` 实现 `PathwayContextRequest`。新增 `apiContext` 主构造字段，同时保留现有服务测试使用的业务字段构造器；`PathwayAdvanceRequest` 增加 `withPatientPathwayId(String)` 以支持路径参数权威。

- [ ] **Step 3: 控制器收口到 singular root**

将根路径改为 `/api/v1/engine/pathway`，映射为：
```text
GET/POST /specialty-packages
GET/POST /pathway-templates
GET /pathway-templates/{templateId}
POST /pathway-templates/{templateId}/simulate
POST /pathway-templates/{templateId}/publish
POST /patient-pathways/enter
GET /patient-pathways/{patientPathwayId}
POST /patient-pathways/{patientPathwayId}/advance
GET /patient-pathways/{patientPathwayId}/variances
GET /patient-pathways/{patientPathwayId}/clocks
```
清理旧 `/api/v1/engine/pathways/**` 与旧 customer-facing `/diagnose` 入口。

- [ ] **Step 4: 服务补变异查询**

新增 `PathwayEngineService.variances(patientPathwayId)`，先校验患者路径属于当前租户，再返回 `variances.findByPatientPathwayIdAndTenantIdOrderByCreatedAtAsc(...)`。

- [ ] **Step 5: 更新安全/服务/契约测试**

把 `PathwayEngineControllerSecurityTest` 迁到新路由；给 `PathwayEngineServiceTest` 增加 `variancesOnlyReturnsTenantScopedRuntimeFacts`。

### Task 3: 前端共享调用口径与路径页面可用性

**Files:**
- Modify: `frontend/src/shared/api/hooks.ts`
- Modify: `frontend/src/pages/tenant/PathwayTemplates.tsx`
- Modify: `frontend/src/pages/clinical/PatientPathways.tsx`

- [ ] **Step 1: 清理旧 API hooks**

规则 hooks 改为 `/engine/rule/rules/**`，执行解释改为 `/engine/rule/rules/executions/{id}/explain`；路径 hooks 改为 `/engine/pathway/**` 新命名。删除旧路径诊断 hook，新增 `usePatientPathwayVariances`。

- [ ] **Step 2: 对齐路径枚举**

前端路径节点/边/患者状态类型对齐后端：`ASSESSMENT/FOLLOWUP/...`、`DEFAULT/CONDITION/...`、`NODE_EXECUTING/VARIANCE/...`。模板默认 JSON 使用合法 enum，患者入径默认起点改为模板起点空缺由服务端处理或 `ASSESS`。

- [ ] **Step 3: 清理触碰路径页面技术化文案**

删除 `PathwayTemplates.tsx` 与 `PatientPathways.tsx` 中旧“物理完成/物理退径/物理诊断”等文案，改为临床用户可理解的“完成当前节点/退径/解释追溯”。

### Task 4: 文档、服务目录和接力

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/architecture/OpenApiContractConfigurationTest.java`
- Modify: `docs/cards/D2/API-06.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md`
- Modify: `docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md`
- Modify: `docs/superpowers/specs/2026-05-27-engine-pathway-api-design.md`

- [ ] **Step 1: 服务契约路径更新**

`ServiceContractCatalog` 与 OpenAPI 测试改为 `/api/v1/engine/pathway`。

- [ ] **Step 2: 权威文档更新**

把 API-06 卡、backlog、落地计划、业务场景和旧路径 API design 的客户面路径更新到 `/api/v1/engine/pathway/**`；只保留历史计划中的旧路径作为历史记录，不把旧路径写成当前可用。

- [ ] **Step 3: handoff 接力**

归档 API-05：PR #250，merge `522f47b`，CI 8/8，本地/远端分支清理；新增当前 API-06 工作线，写清当前分支、下一步和待验证命令。

### Task 5: 验证、PR、合并清理

- [ ] **Step 1: 聚焦后端**

Run:
```bash
mvn -q -Dtest=PathwayEngineApiContractTest,PathwayEngineControllerSecurityTest,PathwayEngineServiceTest,PathwayProgressorTest,PathwayRepositoryTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

- [ ] **Step 2: 聚焦前端**

Run:
```bash
npm test -- --run src/pages/tenant/RulePathwayCleanliness.test.ts src/pages/pages.smoke.test.tsx
npm run build
```

- [ ] **Step 3: 全量与 T-GATE**

Run:
```bash
mvn -q test
npm run verify
node --test scripts/authenticity-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/config-boundary-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=all
scripts/check-comment-zh.sh --mode=full
git diff --check
```

- [ ] **Step 4: PR 生命周期**

提交、推送、创建 PR，等待远端 CI 8/8 通过，squash 合并，确认 `origin/main` 包含合并提交，删除远端分支并清理本地 worktree，再从最新 main 领取下一张 D2 卡。
