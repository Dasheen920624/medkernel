# API-05 Rule Engine API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收口 API-05 规则引擎客户面 REST 合同，补齐 `/api/v1/engine/rule/**`、统一 12 字段入参、影响分析、发布门禁、测试执行、解释端点，并清理旧 `/api/v1/engine/rules` 入口。

**Architecture:** 规则引擎继续以关系库规则定义、版本、测试用例和执行日志为唯一权威源；API 层改为统一客户面路径，写操作复用 API-04 的扁平 12 字段上下文校验模式。影响分析只返回当前数据库可真实证明的对象，路径/患者/同步目标反向索引缺失已登记 `DEFER-012`，接口中以 `PARTIAL` 和 unavailableScopes 诚实表达，禁止伪造。

**Tech Stack:** Spring Boot 3.3, Spring MVC, Spring Data JDBC, Bean Validation, JUnit 5, MockMvc, Mockito, AssertJ, Flyway 多方言迁移验证。

---

### Task 1: RED 契约测试与旧入口清理断言

**Files:**
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleEngineApiContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleEngineControllerSecurityTest.java`

- [ ] **Step 1: 写失败测试**

新增 `RuleEngineApiContractTest`，覆盖这些行为：

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RuleEngineApiContractTest {
    @Autowired MockMvc mvc;
    @MockBean RuleEngineService service;

    @Test
    @WithMockUser(authorities = "ROLE_SPECIALIST")
    void createRequiresUnifiedContextFields() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules")
                .contentType("application/json")
                .content("{\"ruleCode\":\"RULE.A\",\"name\":\"规则\",\"ruleType\":\"ORDER\",\"sourceRef\":\"规范\",\"dsl\":{\"trigger\":\"ORDER_SIGN\",\"then\":[],\"explain\":{}}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("request_id")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SPECIALIST")
    void oldPluralRootIsRemoved() throws Exception {
        mvc.perform(post("/api/v1/engine/rules").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_MEDICAL_AFFAIRS")
    void publishEndpointRequiresImpactDigestForHighRiskRule() throws Exception {
        // 直接测 service，接口层只负责把 RulePublishRequest 传入服务。
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void explainEndpointUsesNewCustomerRoute() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/executions/rex-1/explain"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
```

把 `RuleEngineControllerSecurityTest` 的路径改为新客户面：
`/api/v1/engine/rule/rules`、`/rules/{id}`、`/rules/{id}/test-cases`、`/rules/{id}/test`、`/rules/{id}/simulate`、`/rules/{id}/impact`、`/rules/{id}/publish`、`/rules/evaluate`、`/rules/executions/{executionId}/explain`。

- [ ] **Step 2: 跑红灯**

Run: `mvn -q -Dtest=RuleEngineApiContractTest,RuleEngineControllerSecurityTest test`

Expected: FAIL，原因应是新路径不存在、旧路径仍存在、统一入参尚未校验。

### Task 2: 统一 12 字段入参与新客户面路由

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineController.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleCreateRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEvaluateRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleSimulateRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleTestCaseRequest.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleApiContext.java`

- [ ] **Step 1: 增加规则 API 上下文**

创建 `RuleApiContext` 和 `RuleContextRequest`，字段与 API-04 一致：`request_id`、`trace_id`、`tenant_id`、`group_id`、`hospital_id`、`campus_id`、`site_id`、`department_id`、`specialty_id`、`user_id`、`role_codes`、`package_version`。`validateTenant` 必须在缺 `request_id/trace_id/tenant_id/user_id/role_codes/package_version` 时抛 `ErrorCode.VALIDATION_FAILED`，租户不一致时抛 `ErrorCode.ORG_SCOPE_DENIED`。

- [ ] **Step 2: 请求 DTO 实现上下文接口**

给 `RuleCreateRequest`、`RuleEvaluateRequest`、`RuleSimulateRequest`、`RuleTestCaseRequest` 增加同样 12 字段与 `context()` 方法；保留旧构造器给现有服务测试使用，避免测试噪声掩盖行为改动。

- [ ] **Step 3: 控制器切换路径并校验上下文**

`RuleEngineController` 根路径改为 `/api/v1/engine/rule`，所有客户面端点挂在 `/rules/**` 下。写操作、simulate、test、publish、evaluate 在进入服务前调用 `validateContext(request)`；旧 `/api/v1/engine/rules` 不再保留兼容映射。

- [ ] **Step 4: 跑绿灯**

Run: `mvn -q -Dtest=RuleEngineApiContractTest,RuleEngineControllerSecurityTest,RuleEngineServiceTest test`

Expected: PASS。

### Task 3: CRUD 更新与测试执行端点

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleUpdateRequest.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleTestRunResponse.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleEngineServiceTest.java`

- [ ] **Step 1: 写失败测试**

在 `RuleEngineServiceTest` 增加：
`updateDraftRulePersistsDefinitionAndVersion`、`updatePublishedRuleIsRejected`、`runTestsReturnsAllCaseResultsWithoutPublishing`。

- [ ] **Step 2: 跑红灯**

Run: `mvn -q -Dtest=RuleEngineServiceTest#updateDraftRulePersistsDefinitionAndVersion,RuleEngineServiceTest#runTestsReturnsAllCaseResultsWithoutPublishing test`

Expected: FAIL，原因是 `updateRule` / `runTests` 方法不存在。

- [ ] **Step 3: 最小实现**

`updateRule(ruleId, RuleUpdateRequest)` 只允许 `DRAFT`，校验 DSL 后更新当前定义与当前版本，不新增表、不伪造版本能力；`runTests(ruleId)` 运行当前版本所有测试用例并回写结果，但不推进规则状态。

- [ ] **Step 4: 跑绿灯**

Run: `mvn -q -Dtest=RuleEngineServiceTest test`

Expected: PASS。

### Task 4: 影响分析与高危发布门禁

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleImpactResponse.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleImpactObject.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RulePublishRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RulePublishResponse.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleEngineServiceTest.java`

- [ ] **Step 1: 写失败测试**

新增服务测试：
`impactReturnsRuleAndDigestWithUnavailableScopes`、`highRiskPublishWithoutImpactDigestIsDenied`、`highRiskPublishWithMatchingImpactDigestCanContinueGate`。

- [ ] **Step 2: 跑红灯**

Run: `mvn -q -Dtest=RuleEngineServiceTest#impactReturnsRuleAndDigestWithUnavailableScopes,RuleEngineServiceTest#highRiskPublishWithoutImpactDigestIsDenied test`

Expected: FAIL，原因是 `impact` / `RulePublishRequest` 不存在。

- [ ] **Step 3: 最小实现**

`impact(ruleId)` 读取真实规则和版本，返回 `analysisStatus=PARTIAL`、`affectedRules` 包含当前规则，`affectedPathways/inPathPatients/syncTargets` 为空，并在 `unavailableScopes` 明确说明尚无真实反向索引；`impactDigest` 使用 SHA-256 基于 `tenantId|ruleId|versionId|riskLevel|analysisStatus|unavailableScopes` 生成。

`publish(ruleId, RulePublishRequest)` 对 `HIGH/CRITICAL` 规则要求 `impactDigest` 与当前 `impact(ruleId).impactDigest()` 一致，再执行测试覆盖与全绿门禁；低中风险也允许传 digest 留痕。

- [ ] **Step 4: 跑绿灯**

Run: `mvn -q -Dtest=RuleEngineServiceTest test`

Expected: PASS。

### Task 5: 执行解释端点与错误码别名

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleExplanationResponse.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/api/error/ErrorCode.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleEngineServiceTest.java`

- [ ] **Step 1: 写失败测试**

新增 `explainReturnsHitChainFromExecutionLog`，断言返回 `executionId/ruleId/versionId/triggerPoint/inputDigest/actionsJson/explanationJson/status/traceId`。

- [ ] **Step 2: 跑红灯**

Run: `mvn -q -Dtest=RuleEngineServiceTest#explainReturnsHitChainFromExecutionLog test`

Expected: FAIL，原因是 `explain` 不存在。

- [ ] **Step 3: 最小实现**

`explain(executionId)` 只读 `rule_execution_log`，返回执行日志里的真实命中链，不从请求重新推导，也不补假解释。错误码增加 `RULE_DSL_INVALID`、`RULE_PUBLISH_GATE_DENIED` 新枚举名，并逐步替换 API-05 新逻辑的使用；旧 `ENG_RULE_*` 常量不删除，以免破坏既有客户端 code 字符串。

- [ ] **Step 4: 跑绿灯**

Run: `mvn -q -Dtest=RuleEngineServiceTest,RuleEngineControllerSecurityTest,RuleEngineApiContractTest test`

Expected: PASS。

### Task 6: 契约目录、文档接力与验证

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/architecture/OpenApiContractConfigurationTest.java`
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/audit/deferred-issues.md`

- [ ] **Step 1: 更新服务契约目录**

把 `RuleEngineController` 的契约路径从 `/api/v1/engine/rules` 改为 `/api/v1/engine/rule/**`，测试同步更新。

- [ ] **Step 2: 更新接力**

把 API-04 从在途移动到已归档，写明 PR #249、merge `b7fd2979`、CI 8/8；新增 API-05 在途线，写清当前分支、计划文件、`DEFER-012`。

- [ ] **Step 3: 验证**

Run:

```bash
mvn -q -Dtest=RuleEngineApiContractTest,RuleEngineServiceTest,RuleEngineControllerSecurityTest,RuleDslEvaluatorTest,RuleRepositoryTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
mvn -q -Dtest=FlywayMultiDialectSmokeTest test
node --test scripts/authenticity-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/config-boundary-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=all
scripts/check-comment-zh.sh --mode=full
git diff --check
```

Expected: Java 聚焦与多方言迁移通过；T-GATE 规则测试通过；真实性全仓扫描通过；中文注释扫描若仍只有历史 `DEFER-006` GAP，不作为当前阻塞但必须如实记录；diff 检查通过。

### Self-Review

- Spec coverage: FR-1 由 CRUD + PUT + 新路径覆盖；FR-2 由 test/run + simulate 覆盖；FR-3 由 impact 覆盖并登记真实跨域缺口；FR-4 由高危 impactDigest + 全用例门禁覆盖；FR-5 由 evaluate + explain 覆盖；FR-6 由 12 字段上下文 + ApiResult/ProblemDetail 覆盖。
- Placeholder scan: 无 TBD/TODO/implement later；`DEFER-012` 有明确关闭证据。
- Type consistency: Controller 使用 `RuleContextRequest`，服务层只接具体 request；旧 `ENG_RULE_*` 保留，新别名只作为 API-05 新逻辑可读常量。
