# AIK-STD-13 PR3 候选会签路由 + 院内覆盖角色边界 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为知识生产候选落「确定性会签路由决策」（按归属管道 + 领域 + 风险算出归口/领域会签角色 + 是否双签），并补 job 领域维度（含药学经 `domain=PHARMACY`）与候选风险级持久化，使候选可回溯路由。

**Architecture:** 纯确定性路由器 `CandidateReviewRouter`（B0，无上游/模型）；领域作生产 job 显式维度（药学＝知识的领域，**不另起资产类型**）；候选物化前**不建 `ReviewAssignment`**，只产路由决策记录交 P2-C 消费（不伪装已分派）。数据列原地改已合迁移 V130/V131（greenfield 无兼容，不新建 V132）。

**Tech Stack:** Spring Boot 3 + Spring Data JDBC（record 实体，`@Column` 映射）、Flyway 五方言（h2/postgres/oracle/dm/kingbase）、JUnit5 + Mockito + AssertJ。

**设计依据:** [docs/superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md](../specs/2026-06-15-aikstd13-production-orchestration-design.md) §9。卡 [AIK-STD-13](../../cards/wave2/AIK-STD-13.md)。

**关键事实（执行者必读）:**
- 后端根 = `medkernel-backend/`（全部 `mvn` 命令在此目录跑）。包 = `com.medkernel.engine.knowledge.production`。
- 角色枚举 `com.medkernel.engine.security.RoleCode`：`PLATFORM_KNOWLEDGE_GOVERNOR`/`KNOWLEDGE_GOVERNOR`/`CLINICAL_GOVERNOR`/`MEDICATION_SAFETY_USER`/`DIAGNOSTIC_SERVICE_USER`/`QUALITY_GOVERNOR` 均已存在。
- `com.medkernel.engine.knowledge.KnowledgeRiskLevel` = `{ LOW, MEDIUM, HIGH }`（高危=HIGH）。
- `com.medkernel.engine.versioning.VersionedAssetType` 17 类（**本卡不动它**）。
- 基线测试 `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`：`COMMON_CONSTRAINTS`（精确匹配 DDL 约束名）、`LIFECYCLE_FIELDS`（子串存在性）、`schemaConsistencyReportHasNoTableColumnDiffsAcrossDialects`（以 h2 为基准比对五方言列名——五方言一致加列即过，无硬编码列清单）。
- **`LATEST_MIGRATION_VERSION` 保持 131**（原地改 CREATE 内容，无新版本号）。

---

## Task 1: 路由器 `CandidateReviewRouter` + `KnowledgeDomain` + `ReviewRoutingDecision`

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeDomain.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/ReviewRoutingDecision.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/CandidateReviewRouter.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/CandidateReviewRouterTest.java`

- [ ] **Step 1: 写失败测试**

`CandidateReviewRouterTest.java`：

```java
package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.security.RoleCode;

/**
 * 候选会签路由器单元测试（AIK-STD-13 PR3，FR-6/FR-7，纯确定性 B0）。
 */
class CandidateReviewRouterTest {

    private final CandidateReviewRouter router = new CandidateReviewRouter();

    @Test
    void platformSourceRoutesToPlatformKnowledgeGovernorAsOwner() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.GENERAL, KnowledgeRiskLevel.LOW);
        assertThat(d.ownerReviewerRole()).isEqualTo(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR);
    }

    @Test
    void tenantOverlayRoutesToOrgKnowledgeGovernorNeverPlatform() {
        // FR-7 院内覆盖角色边界：院内候选归口恒为机构知识治理员，永不平台归口。
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, KnowledgeRiskLevel.LOW);
        assertThat(d.ownerReviewerRole()).isEqualTo(RoleCode.KNOWLEDGE_GOVERNOR);
        assertThat(d.ownerReviewerRole()).isNotEqualTo(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR);
    }

    @Test
    void clinicalDomainCosignsClinicalGovernor() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.CLINICAL_GOVERNOR);
    }

    @Test
    void pharmacyDomainCosignsMedicationSafetyUser() {
        // 药学＝领域（非资产类型）：路由药事安全人员。
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.PHARMACY, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.MEDICATION_SAFETY_USER);
    }

    @Test
    void terminologyReportDomainCosignsDiagnosticServiceUser() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.TERMINOLOGY_REPORT, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.DIAGNOSTIC_SERVICE_USER);
    }

    @Test
    void evaluationInsuranceDomainCosignsQualityGovernor() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.EVALUATION_INSURANCE, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.QUALITY_GOVERNOR);
    }

    @Test
    void generalDomainCosignerEqualsOwnerRole() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.GENERAL, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(d.ownerReviewerRole());
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.KNOWLEDGE_GOVERNOR);
    }

    @Test
    void highRiskRequiresDualSign() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, KnowledgeRiskLevel.HIGH);
        assertThat(d.requiresDualSign()).isTrue();
    }

    @Test
    void nonHighRiskIsSingleSign() {
        assertThat(router.resolve(TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL,
            KnowledgeRiskLevel.LOW).requiresDualSign()).isFalse();
        assertThat(router.resolve(TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL,
            KnowledgeRiskLevel.MEDIUM).requiresDualSign()).isFalse();
    }

    @Test
    void decisionCarriesDomain() {
        assertThat(router.resolve(TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.PHARMACY,
            KnowledgeRiskLevel.HIGH).domain()).isEqualTo(KnowledgeDomain.PHARMACY);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=CandidateReviewRouterTest`
Expected: 编译失败（`KnowledgeDomain`/`ReviewRoutingDecision`/`CandidateReviewRouter` 不存在）。

- [ ] **Step 3: 建三类型**

`KnowledgeDomain.java`：

```java
package com.medkernel.engine.knowledge.production;

/**
 * 知识候选生产领域（AIK-STD-13 PR3，FR-6 会签领域归类）。
 *
 * <p>医学领域与结构资产类型（{@code VersionedAssetType}）正交：药学＝领域不是类型，
 * 药品说明书走 {@code KNOWLEDGE} 资产、DDI 走 {@code RULE} 资产，经本枚举区分领域并路由药事安全人员。
 */
public enum KnowledgeDomain {
    CLINICAL,
    PHARMACY,
    TERMINOLOGY_REPORT,
    EVALUATION_INSURANCE,
    GENERAL
}
```

`ReviewRoutingDecision.java`：

```java
package com.medkernel.engine.knowledge.production;

import com.medkernel.engine.security.RoleCode;

/**
 * 候选会签路由决策（AIK-STD-13 PR3，FR-6）。
 *
 * <p>纯确定性路由结论：归口审核角色（按管道归属）+ 领域会签角色（按领域）+ 是否双签（高危）。
 * PR3 只产此决策记录，<b>不执行分派</b>（候选物化前不建 {@code ReviewAssignment}，消费者＝P2-C 物化链 / AIK-STD-12 审核台）。
 *
 * @param ownerReviewerRole 归口审核角色（平台主源→平台知识治理员 / 院内覆盖→机构知识治理员）
 * @param domainReviewerRole 领域会签角色（按领域，GENERAL 时等于归口角色）
 * @param requiresDualSign 是否双签（高危 HIGH 须归口 + 领域两签）
 * @param domain 候选生产领域
 */
public record ReviewRoutingDecision(
    RoleCode ownerReviewerRole,
    RoleCode domainReviewerRole,
    boolean requiresDualSign,
    KnowledgeDomain domain
) {
}
```

`CandidateReviewRouter.java`：

```java
package com.medkernel.engine.knowledge.production;

import org.springframework.stereotype.Service;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.security.RoleCode;

/**
 * 候选会签路由器（AIK-STD-13 PR3，FR-6/FR-7）。
 *
 * <p>纯确定性函数（B0，无上游、无模型）：按归属管道 + 领域 + 风险算出归口/领域会签角色 + 是否双签。
 * FR-7 院内覆盖角色边界：{@code TENANT_OVERLAY} 候选归口恒为机构知识治理员，永不平台归口。
 */
@Service
public class CandidateReviewRouter {

    public ReviewRoutingDecision resolve(TargetPipeline pipeline, KnowledgeDomain domain,
                                         KnowledgeRiskLevel risk) {
        RoleCode owner = switch (pipeline) {
            case PLATFORM_SOURCE -> RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR;
            case TENANT_OVERLAY -> RoleCode.KNOWLEDGE_GOVERNOR;
        };
        RoleCode domainRole = switch (domain) {
            case CLINICAL -> RoleCode.CLINICAL_GOVERNOR;
            case PHARMACY -> RoleCode.MEDICATION_SAFETY_USER;
            case TERMINOLOGY_REPORT -> RoleCode.DIAGNOSTIC_SERVICE_USER;
            case EVALUATION_INSURANCE -> RoleCode.QUALITY_GOVERNOR;
            case GENERAL -> owner;
        };
        boolean dualSign = risk == KnowledgeRiskLevel.HIGH;
        return new ReviewRoutingDecision(owner, domainRole, dualSign, domain);
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=CandidateReviewRouterTest`
Expected: PASS（10 测试）。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeDomain.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/ReviewRoutingDecision.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/CandidateReviewRouter.java \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/CandidateReviewRouterTest.java
git commit -m "feat(aikstd13/PR3): 候选会签路由器（FR-6 领域/风险路由 + FR-7 院内归口边界，纯确定性 B0）"
```

---

## Task 2: job 领域维度端到端（迁移 V130 ×5 + 实体/请求/响应 + createJob/replayJob）

> **为何端到端一并改:** V130 加 `domain NOT NULL` 列后，若实体 `KnowledgeProductionJob` 无 `domain` 字段，真实 repo 集成测试 INSERT 会因 NOT NULL 失败。故迁移 + 实体 + 服务 + 既有测试夹具同任务改，保持每次提交可编译可绿。

**Files:**
- Modify（迁移，5 方言）: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V130__knowledge_production_job.sql`
- Modify: `.../production/KnowledgeProductionJob.java`、`ProductionJobRequest.java`、`ProductionJobResponse.java`、`KnowledgeProductionOrchestrationService.java`
- Modify（基线测试）: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify（既有测试夹具）: `.../production/KnowledgeProductionOrchestrationServiceTest.java`、`KnowledgeProductionJobRepositoryIntegrationTest.java`、`KnowledgeProductionControllerSecurityTest.java`

- [ ] **Step 1: 写失败测试（服务层 domain 持久化 + 基线约束）**

在 `KnowledgeProductionOrchestrationServiceTest.java` 加测试（先改 `request()` 夹具签名见 Step 3，否则不编译——本步只新增断言，编译失败由 Step 3 修）：

```java
    @Test
    void createJobPersistsDeclaredDomain() {
        asTenant(CUSTOMER);
        ProductionJobRequest req = new ProductionJobRequest("探索 run r-1", VersionedAssetType.RULE,
            KnowledgeProducer.MANUAL, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.PHARMACY, null);

        ProductionJobResponse response = service.createJob(req);

        assertThat(response.domain()).isEqualTo(KnowledgeDomain.PHARMACY);
    }
```

并在 `MigrationBaselineContractTest.java` 的 `COMMON_CONSTRAINTS`（`Set.of(` 约 line 533）加一项：

```java
        "ck_mk_knowledge_production_job_domain",
```

并改 `LIFECYCLE_FIELDS`（约 line 955）：

```java
        Map.entry("mk_knowledge_production_job", Set.of("status", "domain")),
```

- [ ] **Step 2: 跑确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=MigrationBaselineContractTest`
Expected: FAIL（DDL 无 `ck_mk_knowledge_production_job_domain` 约束、无 `domain` 列）。

- [ ] **Step 3: 改迁移 + 实体 + 请求 + 响应 + 服务 + 既有夹具**

**(3a) 五方言 V130 加列 + 约束。** 每个方言文件，在 `target_pipeline` 列行后插入 domain 列；在 `...status CHECK (...))` 后补逗号 + domain 约束。

h2 / kingbase / postgres（`VARCHAR(24)`）——`target_pipeline  VARCHAR(16)   NOT NULL,` 行后加：

```sql
    domain           VARCHAR(24)   NOT NULL,
```

oracle / dm（`VARCHAR2(24)`）——`target_pipeline  VARCHAR2(16)   NOT NULL,` 行后加：

```sql
    domain           VARCHAR2(24)  NOT NULL,
```

五方言统一：将
```sql
    CONSTRAINT ck_mk_knowledge_production_job_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);
```
改为
```sql
    CONSTRAINT ck_mk_knowledge_production_job_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_mk_knowledge_production_job_domain CHECK (domain IN ('CLINICAL', 'PHARMACY', 'TERMINOLOGY_REPORT', 'EVALUATION_INSURANCE', 'GENERAL'))
);
```
（注：列级 COMMENT 不加——既有迁移仅表级 `COMMENT ON TABLE`，保持一致；表注释已存在不动。）

**(3b) `KnowledgeProductionJob.java`** 在 `targetPipeline` 字段后加：

```java
    @Column("domain") KnowledgeDomain domain,
```

**(3c) `ProductionJobRequest.java`** 在 `targetPipeline` 后加（必填——无资产类型隐含药学，领域须显式申报）：

```java
    @NotNull KnowledgeDomain domain,
```
（字段顺序：`... @NotNull TargetPipeline targetPipeline, @NotNull KnowledgeDomain domain, String modelStrategy`）

**(3d) `ProductionJobResponse.java`** 加 `KnowledgeDomain domain` 字段（在 `targetPipeline` 后）并改 `from`：

```java
public record ProductionJobResponse(
    String jobCode, String tenantId, String sourceScope, VersionedAssetType assetType,
    KnowledgeProducer producer, TargetPipeline targetPipeline, KnowledgeDomain domain,
    String modelStrategy, ProductionJobStatus status, int candidateCount, Instant createdAt
) {
    public static ProductionJobResponse from(KnowledgeProductionJob job) {
        return new ProductionJobResponse(job.jobCode(), job.tenantId(), job.sourceScope(), job.assetType(),
            job.producer(), job.targetPipeline(), job.domain(), job.modelStrategy(), job.status(),
            job.candidateCount(), job.createdAt());
    }
}
```

**(3e) `KnowledgeProductionOrchestrationService.java`** —— `createJob` 与 `replayJob` 构造 `KnowledgeProductionJob` 时传入 domain。`createJob` 用 `request.domain()`；`replayJob` 用 `original.domain()`；`transition` 与 `submitCandidate` 重建 job 时用 `job.domain()`。即所有 `new KnowledgeProductionJob(...)` 调用按新字段顺序补 `domain` 实参（createJob/replayJob/submitCandidate 计数保存/transition 共 4 处）。createJob 示例：

```java
        KnowledgeProductionJob job = jobRepository.save(new KnowledgeProductionJob(
            null, tenantId, jobCode, request.sourceScope(), request.assetType(), request.producer(),
            request.targetPipeline(), request.domain(), request.modelStrategy(), ProductionJobStatus.PENDING,
            0, lineage, now, actor, now, actor, RequestContext.currentTraceId()));
```

**(3f) 修既有服务测试夹具** `KnowledgeProductionOrchestrationServiceTest.java`：
- `request(TargetPipeline)` 助手补 domain（用 `KnowledgeDomain.GENERAL`）：
  ```java
    private ProductionJobRequest request(TargetPipeline pipeline) {
        return new ProductionJobRequest("探索 run r-1", VersionedAssetType.KNOWLEDGE,
            KnowledgeProducer.MANUAL, pipeline, KnowledgeDomain.GENERAL, null);
    }
  ```
- `overlayJob(...)`、`jobWith(...)` 助手与 `jobRepository.save` mock answer（`new KnowledgeProductionJob(...)`）按新字段顺序补 `domain` 实参（`overlayJob`/`jobWith` 用 `KnowledgeDomain.GENERAL`；mock answer 透传 `j.domain()`）。

**(3g) 修 repo 集成测试** `KnowledgeProductionJobRepositoryIntegrationTest.java`：所有 `new KnowledgeProductionJob(...)` 补 `domain` 实参（用 `KnowledgeDomain.GENERAL`）。

**(3h) 修控制器安全测试** `KnowledgeProductionControllerSecurityTest.java`：若有构造 `ProductionJobRequest` 的 JSON/对象，补 `domain` 字段（JSON 加 `"domain":"GENERAL"`）。

- [ ] **Step 4: 跑确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=CandidateReviewRouterTest,MigrationBaselineContractTest,KnowledgeProductionOrchestrationServiceTest,KnowledgeProductionJobRepositoryIntegrationTest,KnowledgeProductionControllerSecurityTest`
Expected: PASS（含新 `createJobPersistsDeclaredDomain`）。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/resources/db/migration/*/V130__knowledge_production_job.sql \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionJob.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/ProductionJobRequest.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/ProductionJobResponse.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionOrchestrationService.java \
        medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/
git commit -m "feat(aikstd13/PR3): job 领域维度（domain 必填，V130 五方言原地加列+约束，会签路由依据）"
```

---

## Task 3: 候选风险级端到端（迁移 V131 ×5 + 实体 + submitCandidate 存风险级）

> 同 Task 2 理由：V131 加 `risk_level NOT NULL` 须与实体 `KnowledgeProductionCandidate` 字段、服务写入、既有测试夹具同任务改。

**Files:**
- Modify（迁移，5 方言）: `.../db/migration/{h2,postgres,oracle,dm,kingbase}/V131__knowledge_production_candidate.sql`
- Modify: `.../production/KnowledgeProductionCandidate.java`、`KnowledgeProductionOrchestrationService.java`
- Modify: `MigrationBaselineContractTest.java`
- Modify（既有测试）: `KnowledgeProductionOrchestrationServiceTest.java`、`KnowledgeProductionCandidateRepositoryIntegrationTest.java`

- [ ] **Step 1: 写失败断言（既有血缘测试加 risk_level 校验）+ 基线约束**

`KnowledgeProductionOrchestrationServiceTest.java` 的 `submitCandidatePersistsProductionLineageRow` 末尾加：

```java
        assertThat(lineage.getValue().riskLevel())
            .isEqualTo(com.medkernel.engine.knowledge.KnowledgeRiskLevel.MEDIUM);
```
（`envelope(...)` 助手已用 `KnowledgeRiskLevel.MEDIUM`。）

`MigrationBaselineContractTest.java`：`COMMON_CONSTRAINTS` 加：

```java
        "ck_mk_knowledge_production_candidate_risk",
```
`LIFECYCLE_FIELDS` 加新项：

```java
        Map.entry("mk_knowledge_production_candidate", Set.of("risk_level")),
```

- [ ] **Step 2: 跑确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=MigrationBaselineContractTest`
Expected: FAIL（无 `ck_mk_knowledge_production_candidate_risk` 约束 / 无 `risk_level` 列）。

- [ ] **Step 3: 改迁移 + 实体 + 服务 + 既有测试**

**(3a) 五方言 V131：** `candidate_ref` 列后加 `risk_level` 列；`created_by ... NULL` 行后补逗号 + 风险约束。

h2 / kingbase / postgres：将
```sql
    candidate_ref    VARCHAR(256)  NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)   NULL
);
```
改为
```sql
    candidate_ref    VARCHAR(256)  NOT NULL,
    risk_level       VARCHAR(16)   NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)   NULL,
    CONSTRAINT ck_mk_knowledge_production_candidate_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
);
```

oracle / dm：同理，`risk_level VARCHAR2(16) NOT NULL,`，`created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,`（保持各自原 created_at 写法），`created_by VARCHAR2(64) NULL,` 后加同名 CHECK 约束。

**(3b) `KnowledgeProductionCandidate.java`** 在 `candidateRef` 字段后加：

```java
    @Column("risk_level") com.medkernel.engine.knowledge.KnowledgeRiskLevel riskLevel,
```
（或顶部 import 后写简名 `KnowledgeRiskLevel riskLevel`。字段顺序：`... candidateRef, riskLevel, createdAt, createdBy`。）

**(3c) `KnowledgeProductionOrchestrationService.submitCandidate`** 落血缘行时传 `candidate.riskLevel()`：

```java
        candidateRepository.save(new KnowledgeProductionCandidate(null, tenantId, jobCode,
            candidate.assetIdentity(), candidate.contentHash(), candidateRef, candidate.riskLevel(),
            now, actor));
```

**(3d) 修既有服务测试** `KnowledgeProductionOrchestrationServiceTest.java`：`listCandidatesReturnsJobLineage` 中 `new KnowledgeProductionCandidate(...)` 补 risk 实参：

```java
        KnowledgeProductionCandidate row = new KnowledgeProductionCandidate(5L, CUSTOMER, "job-1",
            "discovery:SRC:v1:a", "hash", "staged:x",
            com.medkernel.engine.knowledge.KnowledgeRiskLevel.MEDIUM,
            java.time.Instant.now(), "user-001");
```

**(3e) 修 repo 集成测试** `KnowledgeProductionCandidateRepositoryIntegrationTest.java`：所有 `new KnowledgeProductionCandidate(...)` 补 risk 实参（用 `KnowledgeRiskLevel.MEDIUM`）。

- [ ] **Step 4: 跑确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=MigrationBaselineContractTest,KnowledgeProductionOrchestrationServiceTest,KnowledgeProductionCandidateRepositoryIntegrationTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/resources/db/migration/*/V131__knowledge_production_candidate.sql \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionCandidate.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionOrchestrationService.java \
        medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/
git commit -m "feat(aikstd13/PR3): 候选风险级持久化（V131 五方言原地加列+约束，路由可回溯非派生输入）"
```

---

## Task 4: submitCandidate 返回路由决策 `CandidateSubmissionResponse`

**Files:**
- Create: `.../production/CandidateSubmissionResponse.java`
- Modify: `KnowledgeProductionOrchestrationService.java`（注入 router + submit 返回类型）、`KnowledgeProductionController.java`、`KnowledgeProductionOrchestrationServiceTest.java`、`KnowledgeProductionControllerSecurityTest.java`

- [ ] **Step 1: 改既有 submit 测试断言 + 加路由断言**

`KnowledgeProductionOrchestrationServiceTest.java`：
- `setUp()` 构造 service 处补 router 实参（见 Step 3）。
- `submitCandidateValidatesIsolatesCountsAndAudits`：将 `String ref = service.submitCandidate(...)` 与 `assertThat(ref).isEqualTo("staged:...")` 改为：
  ```java
        CandidateSubmissionResponse resp = service.submitCandidate("job-1",
            envelope(CUSTOMER, VersionedAssetType.KNOWLEDGE));
        assertThat(resp.candidateRef()).isEqualTo("staged:discovery:SRC:v1:a");
        assertThat(resp.routing().ownerReviewerRole())
            .isEqualTo(com.medkernel.engine.security.RoleCode.KNOWLEDGE_GOVERNOR); // overlay→机构归口
  ```
- 其余 `submitCandidate(...)` 返回值未取用处（拒绝类测试）无需改。`submitCandidatePersistsProductionLineageRow` 不取返回值，无需改。

- [ ] **Step 2: 跑确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeProductionOrchestrationServiceTest`
Expected: 编译失败（`CandidateSubmissionResponse` 不存在 / service 构造参数不匹配）。

- [ ] **Step 3: 建 DTO + 注入 router + 改返回 + 控制器**

`CandidateSubmissionResponse.java`：

```java
package com.medkernel.engine.knowledge.production;

/**
 * 提交候选响应（AIK-STD-13 PR3，FR-6）：候选引用 + 会签路由决策。
 *
 * @param candidateRef intake 返回的候选引用标识
 * @param routing 会签路由决策（归口/领域角色 + 是否双签）
 */
public record CandidateSubmissionResponse(String candidateRef, ReviewRoutingDecision routing) {
}
```

`KnowledgeProductionOrchestrationService.java`：
- 加字段 `private final CandidateReviewRouter reviewRouter;`，构造器补该参数并赋值。
- `submitCandidate` 返回类型 `String` → `CandidateSubmissionResponse`，末尾改为：
  ```java
        ReviewRoutingDecision routing = reviewRouter.resolve(
            job.targetPipeline(), job.domain(), candidate.riskLevel());
        return new CandidateSubmissionResponse(candidateRef, routing);
  ```

`KnowledgeProductionController.java`：`submitCandidate` 返回类型 `ApiResult<String>` → `ApiResult<CandidateSubmissionResponse>`（方法体 `service.submitCandidate(...)` 不变）。

`KnowledgeProductionOrchestrationServiceTest.setUp()`：service 构造补 router：
```java
        service = new KnowledgeProductionOrchestrationService(
            jobRepository, candidateRepository, candidateIntake, new KnowledgeAssetSchemaValidator(),
            auditRecorder, new CandidateReviewRouter());
```

`KnowledgeProductionControllerSecurityTest.java`：若断言了 submit 响应体为字符串，改为对象（`candidateRef`/`routing` 字段）；MockMvc 仅校验状态码/权限者无需改。

- [ ] **Step 4: 跑确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeProductionOrchestrationServiceTest,KnowledgeProductionControllerSecurityTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/ \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/
git commit -m "feat(aikstd13/PR3): submitCandidate 提交即返回会签路由决策（FR-6）"
```

---

## Task 5: listCandidates 返回带路由的候选视图 `ProductionCandidateView`

**Files:**
- Create: `.../production/ProductionCandidateView.java`
- Modify: `KnowledgeProductionOrchestrationService.java`（listCandidates 返回类型）、`KnowledgeProductionController.java`、`KnowledgeProductionOrchestrationServiceTest.java`

- [ ] **Step 1: 改 listCandidates 测试为带路由视图**

`KnowledgeProductionOrchestrationServiceTest.listCandidatesReturnsJobLineage` 改为：

```java
    @Test
    void listCandidatesReturnsJobLineageWithRouting() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE)));
        KnowledgeProductionCandidate row = new KnowledgeProductionCandidate(5L, CUSTOMER, "job-1",
            "discovery:SRC:v1:a", "hash", "staged:x",
            com.medkernel.engine.knowledge.KnowledgeRiskLevel.HIGH, java.time.Instant.now(), "user-001");
        when(candidateRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1")).thenReturn(List.of(row));

        List<ProductionCandidateView> views = service.listCandidates("job-1");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).candidateRef()).isEqualTo("staged:x");
        assertThat(views.get(0).routing().requiresDualSign()).isTrue(); // HIGH→双签
        assertThat(views.get(0).routing().ownerReviewerRole())
            .isEqualTo(com.medkernel.engine.security.RoleCode.KNOWLEDGE_GOVERNOR);
    }
```

- [ ] **Step 2: 跑确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeProductionOrchestrationServiceTest`
Expected: 编译失败（`ProductionCandidateView` 不存在 / `listCandidates` 返回 `List<KnowledgeProductionCandidate>`）。

- [ ] **Step 3: 建视图 DTO + 改服务 + 控制器**

`ProductionCandidateView.java`：

```java
package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;

/**
 * 候选生产血缘视图（AIK-STD-13 PR3，FR-5/6 可回溯）：血缘行 + 会签路由决策（只读计算）。
 */
public record ProductionCandidateView(
    String jobCode,
    String assetIdentity,
    String contentHash,
    String candidateRef,
    KnowledgeRiskLevel riskLevel,
    Instant createdAt,
    String createdBy,
    ReviewRoutingDecision routing
) {
    public static ProductionCandidateView from(KnowledgeProductionCandidate row, ReviewRoutingDecision routing) {
        return new ProductionCandidateView(row.jobCode(), row.assetIdentity(), row.contentHash(),
            row.candidateRef(), row.riskLevel(), row.createdAt(), row.createdBy(), routing);
    }
}
```

`KnowledgeProductionOrchestrationService.listCandidates`：返回类型 `List<KnowledgeProductionCandidate>` → `List<ProductionCandidateView>`：

```java
    @Transactional(readOnly = true)
    public List<ProductionCandidateView> listCandidates(String jobCode) {
        String tenantId = requireCurrentTenant();
        KnowledgeProductionJob job = requireJob(tenantId, jobCode);
        return candidateRepository.findByTenantIdAndJobCode(tenantId, jobCode).stream()
            .map(row -> ProductionCandidateView.from(row,
                reviewRouter.resolve(job.targetPipeline(), job.domain(), row.riskLevel())))
            .toList();
    }
```
（注：原实现 `requireJob` 仅校验存在；现需 job 对象取 pipeline/domain，已就近持有。）

`KnowledgeProductionController.listCandidates`：返回类型 `ApiResult<List<KnowledgeProductionCandidate>>` → `ApiResult<List<ProductionCandidateView>>`（方法体不变）。

- [ ] **Step 4: 跑确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeProductionOrchestrationServiceTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/ \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/
git commit -m "feat(aikstd13/PR3): 列候选附会签路由（FR-5/6 可回溯，只读 resolve）"
```

---

## Task 6: 全量验证 + 卡片 + 接力收尾

**Files:**
- Modify: `docs/cards/wave2/AIK-STD-13.md`、`docs/_HANDOFF.md`

- [ ] **Step 1: 全量后端测试**

Run: `cd medkernel-backend && mvn test`
Expected: BUILD SUCCESS，测试数 = 基线 2496 + 新增（路由 10 + 服务新增/改 + repo）。若 `FlywayMultiDialectSmokeTest` 因 Oracle/DM/Kingbase 容器缺失跳过，须在有 Docker 环境补跑（见 Step 2）。

- [ ] **Step 2: 五方言 Flyway 冒烟（须 Docker）**

Run: `cd medkernel-backend && mvn test -Dtest=FlywayMultiDialectSmokeTest`
Expected: PASS——原地改的 V130/V131 在 h2/postgres/oracle/dm/kingbase 全部干净建表（domain/risk_level 列 + 两 CHECK 约束）。

- [ ] **Step 3: 四门禁（changed 模式）+ git diff --check**

Run（在仓库根）:
```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
git diff --check
```
Expected: 全过（无真实性禁词如「占位/模拟/仿真/演示」；迁移规约：表注释/命名/约束齐；无行尾空白）。comment-zh 注释门禁随 CI 跑。

- [ ] **Step 4: 前端产品目录回归**

Run: `cd frontend && npx vitest run src/shared/config/productCatalog.test.ts`
Expected: PASS（本卡无控制器端点增减，仅响应体扩展，目录应无漂移；若提示重生成则跑生成脚本后再测）。

- [ ] **Step 5: 卡片勾验收 + 接力更新**

`docs/cards/wave2/AIK-STD-13.md`：「实现进度」加 PR3 节（FR-6 路由 + FR-7 边界已落，药学经 domain），FR-6/FR-7 勾「✅（PR3）」；FR-2 外部生产器、候选物化仍 pending（PR4+）。backlog 仍 pending（多 PR 大卡）。

`docs/_HANDOFF.md`：最上方加 PR3 段（分支 `claude/wave2-p2b-aikstd13-pr3-review-routing`、已实现待合、验证全绿数据、下一步＝PR4 FR-2 外部生产器 P6 闸 / 候选真实物化 AIK-STD-04/10）。

- [ ] **Step 6: 提交 + 推送 + 开 PR（合并 main 须逐 PR 点名授权，勿自动合）**

```bash
git add docs/cards/wave2/AIK-STD-13.md docs/_HANDOFF.md
git commit -m "docs(aikstd13/PR3): 卡片验收勾 + 接力更新（FR-6 路由 + FR-7 边界落地）"
git push -u origin claude/wave2-p2b-aikstd13-pr3-review-routing
gh pr create --base main --title "feat(wave2/P2-B): AIK-STD-13 PR3 候选会签路由（FR-6）+ 院内覆盖角色边界（FR-7）" --body "..."
```

---

## 自检（spec 覆盖）

- FR-6 候选按归属+风险+领域路由会签 → Task 1（路由器）+ Task 4（提交返回）+ Task 5（列候选附路由）。✅
- FR-7 院内覆盖角色边界（院内候选只路由机构侧角色）→ Task 1 `tenantOverlayRoutesToOrgKnowledgeGovernorNeverPlatform`。✅
- 药学＝领域非类型（domain=PHARMACY→药事安全人员，不动 VersionedAssetType）→ Task 1 `pharmacyDomainCosignsMedicationSafetyUser`。✅
- 可回溯（risk_level 持久 + 路由只读 resolve，不存派生列）→ Task 3 + Task 5。✅
- 原地改 V130/V131 五方言、不新建 V132、LATEST_MIGRATION_VERSION 保持 131 → Task 2/3。✅
- 不建 ReviewAssignment（物化前不伪装已分派）→ 全程无 `ReviewAssignment` 写入。✅
- B0：路由器纯确定性无上游/模型 → Task 1。✅

**类型一致性核对:** `CandidateReviewRouter.resolve(TargetPipeline, KnowledgeDomain, KnowledgeRiskLevel)` → `ReviewRoutingDecision(ownerReviewerRole, domainReviewerRole, requiresDualSign, domain)`；服务字段名 `reviewRouter`；`KnowledgeProductionJob.domain()`、`KnowledgeProductionCandidate.riskLevel()`、`ProductionJobResponse.domain()`、`CandidateSubmissionResponse.routing()/candidateRef()`、`ProductionCandidateView.routing()/candidateRef()` 跨任务一致。
