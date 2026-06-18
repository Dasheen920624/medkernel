# 医学回归独立专家复核 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建成可由独立质量专家核查逐用例真实证据并安全签署医学回归运行的产品闭环。

**Architecture:** 评测器产生结构化逐例裁决，服务在运行与证据同一事务中持久化；查询层输出租户隔离的摘要和详情 DTO，签字层重新核验完整性与当前基准。前端新增质量域专页，普通视图使用医院语言，技术字段通过全局专家模式渐进展示。

**Tech Stack:** Java 21、Spring Boot、Spring Data JDBC、Flyway 五方言、React、TypeScript、TanStack Query、Ant Design、Vitest、JUnit 5。

---

## 文件结构

- 新建 `ModelEvalCaseEvidence`、存储库和运行响应 DTO，分别承载不可变逐例证据、租户查询和安全接口契约。
- 修改 `MedicalRegressionEvaluator`、`ModelEvalService`、`ModelEvalController`，分别负责裁决、事务与 HTTP 边界。
- 新建五方言 V151，统一新增证据表、复核意见和菜单权限。
- 新建 `MedicalRegressionReview.tsx` 与测试，复用 Ant Design 和全局设计令牌；只在路由、菜单和共享 hooks 接线，不改模型能力页与评价指标页。

### Task 1: 逐例裁决与不可变证据

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/MedicalRegressionEvaluator.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalCaseEvidence.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalCaseEvidenceRepository.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/llm/eval/MedicalRegressionEvaluatorTest.java`

- [x] **Step 1: 先写失败测试**：断言裁决逐例返回模型输出、精确来源引用、期望命中、引用核验、红线与失败原因。
- [x] **Step 2: 运行 `mvn -q -Dtest=MedicalRegressionEvaluatorTest test`**，确认因 `caseEvidence` 契约不存在而失败。
- [x] **Step 3: 最小实现**：给 `EvalVerdict` 增加 `List<EvalCaseEvidence>`；每例只执行一次 provider，构造不可变裁决；新增与 `mk_llm_eval_case_evidence` 一一映射的 record 和按租户/运行查询的 repository。
- [x] **Step 4: 重跑定向测试**，预期全绿。

### Task 2: 五方言证据模式与迁移门禁

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,kingbase,oracle,dm}/V151__model_eval_review_evidence.sql`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`

- [x] **Step 1: 先写失败迁移契约**：要求五方言包含 `mk_llm_eval_case_evidence`、中文 COMMENT、运行 `review_comment`、运行/用例索引和 `menu.model-evaluation-review`。
- [x] **Step 2: 运行迁移定向测试**，确认 V151 缺失而失败。
- [x] **Step 3: 实现五方言 V151**：证据表保存运行/用例快照、输出、引用和裁决；运行表增加复核意见；权限表新增质量域菜单权限。
- [x] **Step 4: 运行迁移、中文 COMMENT 与五方言一致性测试**，预期全绿。

### Task 3: 安全分页、详情和强化签字

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRunSummaryResponse.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRunDetailResponse.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalCaseEvidenceResponse.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalSignOffRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRun.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRunRepository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalController.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/llm/eval/ModelEvalServiceTest.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/llm/eval/ModelEvalControllerSecurityTest.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/llm/eval/MedicalRegressionRepositoryTest.java`

- [x] **Step 1: 先写服务失败测试**：分页限定当前租户；详情拒绝跨租户；评测原子保存逐例证据；无证据、证据不全、旧基准、未确认或空意见均不能签字。
- [x] **Step 2: 运行三个定向测试类**，确认新接口和门禁缺失而失败。
- [x] **Step 3: 最小实现**：分页使用 1 起始 `PageRequest`；详情返回安全 DTO；签字请求要求 `evidenceAcknowledged=true` 和 10–1000 字意见；签字与上线门禁均重新核验逐例证据、基准指纹和独立复核信息，条件更新同时保存意见。
- [x] **Step 4: 重跑三个定向测试类**，预期全绿。

### Task 4: 质量域菜单与服务目录

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/PermissionCode.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/DefaultPermissionPolicy.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/MenuPermissionCatalog.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/security/DefaultPermissionPolicyTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/security/MenuPermissionCatalogTest.java`
- Modify: `docs/audit/product-function-catalog.md`

- [x] **Step 1: 先写失败测试**：质量治理员拥有新菜单，其他临床角色没有；菜单归属质量管理；服务目录列出运行分页和详情。
- [x] **Step 2: 运行权限与目录定向测试**，确认失败。
- [x] **Step 3: 实现新菜单权限和目录同步**，不授予模型配置权限。
- [x] **Step 4: 重跑定向测试**，预期全绿。

### Task 5: 前端 hooks 与独立复核页

**Files:**
- Modify: `frontend/src/shared/api/hooks.ts`
- Modify: `frontend/src/shared/config/routes.ts`
- Modify: `frontend/src/shared/config/routes.test.ts`
- Modify: `frontend/src/app/router.tsx`
- Create: `frontend/src/pages/quality/MedicalRegressionReview.tsx`
- Create: `frontend/src/pages/quality/MedicalRegressionReview.test.tsx`

- [x] **Step 1: 先写失败组件测试**：默认查询待复核、服务端翻页、加载/空/错误状态、逐例证据详情、专家模式技术字段、确认与意见校验、签字成功刷新。
- [x] **Step 2: 运行 `npm test -- MedicalRegressionReview.test.tsx routes.test.ts menu.test.ts`**，确认页面与 hooks 缺失而失败。
- [x] **Step 3: 最小实现**：新增三个 query/mutation hooks；质量域专页使用单一主操作、详情抽屉和签字 Modal；路由只允许质量治理员且要求新菜单、`llm.eval.manage`。
- [x] **Step 4: 重跑前端定向测试**，预期全绿。

### Task 6: 文档、全量验证和本地提交

**Files:**
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`
- Modify: `docs/release/evidence/p9-production-golive-20260618/README.md`

- [x] **Step 1: 同步产品边界、旧运行不可签和部署后重跑要求**。
- [x] **Step 2: 运行前端 `npm run verify`、后端全量测试、T-GATE、配置边界、五方言和中文 COMMENT 门禁**。
- [x] **Step 3: 运行 `git diff --check`、敏感信息扫描和人工自审；发现问题直接修复并重跑相关验证**。
- [x] **Step 4: 仅本地提交中文 commit；不推送远程**。
- [x] **Step 5: 用最新本地提交重新发布 134，重新运行本地/外网评测生成逐例证据；不冒充独立专家签字**。提交 `09306b0531309bee48978dab09c02f649d3482e6` 已发布，Flyway V151；新运行 `3`、`4` 均 1/1、逐例证据完整、基准当前、可人工复核，provider 仍停用且未代签。
