# 诊断知识资产建模与维护（Plan A）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 MedKernel 新增"诊断知识资产"（诊断标准/鉴别/诊疗指针/测试病例 + 可配置置信策略 + 确定性命中核心），并以"测试病例全绿"作为发布门禁，让诊断知识能建、能维护、能发布。

**Architecture:** 复用知识引擎 KNOW-01 的 `KnowledgeIdentity`/`KnowledgeAssetVersion`（新增 `domain=DIAGNOSIS`）承载诊断身份与版本；新增 5 张 `mk_diagnosis_*` 子表存结构化诊断知识；命中核心 `DiagnosisMatcher`（纯逻辑：发现集 + 诊断标准 → 候选证据 + 置信分级）被"测试病例发布门禁"复用，也为 Plan B 运行时复用。置信分级阈值放 `mk_diagnosis_confidence_policy`，可配置、可按科室覆盖、不硬编码。

**Tech Stack:** Java 21 / Spring Boot / Spring Data JDBC（record + `@Table`/`@Column`/`@Query`）/ Flyway 五方言迁移（h2/postgres/oracle/dm/kingbase）/ JUnit + `mvn test`。

> **设计依据：** [2026-06-03-diagnosis-knowledge-cdss-design.md](../specs/2026-06-03-diagnosis-knowledge-cdss-design.md) 第 4.1/4.2/4.6 节。本 Plan A = Spec 1 的"建模与维护"子系统；运行时 API/红线/卡组装是 Plan B。
>
> **同构约定（DRY，省 token）：** 五方言迁移与结构雷同的 record/仓储是 MedKernel 既定模式（有 `MigrationBaselineContractTest` / `FlywayMultiDialectSmokeTest` 守一致）。下文对独有逻辑（迁移 DDL、命中算法、置信策略、关键测试、错误码）给完整代码；对机械同构（五方言其余 4 份、雷同 record/仓储）给 1 份完整代表 + 明确同构指令，执行者照现有迁移/实体惯例补齐。

---

## 文件结构（先锁定边界）

**新增：**
- `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V67__diagnosis_knowledge_asset.sql` — 5 表五方言迁移
- `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/` 新包，下含：
  - 枚举：`DiagnosisDirection` / `DiagnosisWeight` / `DiagnosisConfidence` / `DiagnosisCarePointerType`
  - 实体：`DiagnosisCriterion` / `DiagnosisDifferential` / `DiagnosisCarePointer` / `DiagnosisTestCase` / `DiagnosisConfidencePolicy`
  - 仓储：上述 5 个对应 `*Repository`
  - 逻辑：`DiagnosisMatchResult`（命中结果 record）/ `DiagnosisConfidenceEvaluator`（置信分级）/ `DiagnosisMatcher`（命中核心）
  - 服务：`DiagnosisKnowledgeService`（CRUD + 发布门禁）
  - 控制器：`DiagnosisKnowledgeController`（维护 API）
  - DTO：`DiagnosisCriterionRequest` 等 Record DTO

**修改：**
- `engine/knowledge/KnowledgeDomain.java` — 加 `DIAGNOSIS`
- `shared/api/error/ErrorCode.java` — 加 `ENG_DX_001` / `ENG_DX_004` / `ENG_DX_006`
- `shared/architecture/DomainOwnershipCatalog.java:32-36` — `engine-knowledge` 的 `prefixes(...)` 加 `"mk_diagnosis_"`

**测试：** 每个逻辑单元一个 `*Test`，迁移冒烟复用现有 `FlywayMultiDialectSmokeTest`。

---

## Task 1: KnowledgeDomain 新增 DIAGNOSIS

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeDomain.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeDomainTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class KnowledgeDomainTest {
    @Test
    void diagnosisDomainExists() {
        assertThat(KnowledgeDomain.valueOf("DIAGNOSIS")).isNotNull();
    }
}
```

- [ ] **Step 2: 运行验证失败** — Run: `cd medkernel-backend && mvn -q -Dtest=KnowledgeDomainTest test` — Expected: 编译失败 / `IllegalArgumentException: No enum constant ... DIAGNOSIS`

- [ ] **Step 3: 实现** — 在 `KnowledgeDomain` 枚举体内现有值（`GUIDELINE`/`DRUG`…）后追加 `DIAGNOSIS`，并补中文 Javadoc 行（如 `/** 诊断知识：疾病诊断标准、鉴别与诊疗指针。 */`）。

> **⚠️ 动手验证发现（必读，否则诊断身份插不进库）**：`knowledge_identity.domain` 有 `ck_knowledge_identity_domain CHECK (domain IN (...))` 约束（`V3__knowledge_asset_baseline.sql` 第 73 行，五方言一致）。因此 **Task 2 的迁移必须同时 `ALTER`**：DROP 旧约束 + ADD 含 `DIAGNOSIS` 的新 CHECK（五方言），否则插入 `domain='DIAGNOSIS'` 会被约束拒。**迁移号**：Plan 原写 V67 已被 main 超越，实际用当时最大 +1（已核验 main 最大 V74 → 用 **V75**）。Java 枚举层（本 Task）与 DB CHECK 层（Task 2）缺一不可。

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=KnowledgeDomainTest test` — Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeDomain.java medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeDomainTest.java
git commit -m "feat(diagnosis): KnowledgeDomain 新增 DIAGNOSIS 域"
```

---

## Task 2: V67 五方言迁移（5 张 mk_diagnosis_* 表）

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/postgres/V67__diagnosis_knowledge_asset.sql`（+ h2/oracle/dm/kingbase 同构 4 份）

- [ ] **Step 1: 写 postgres 迁移（完整 DDL）**

```sql
-- MedKernel CDSS · 诊断知识资产建模（PostgreSQL）
-- 诊断身份/版本复用 knowledge_identity / knowledge_asset_version（domain=DIAGNOSIS）；本迁移建 5 张结构化子表。

CREATE TABLE IF NOT EXISTS mk_diagnosis_criterion (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    diagnosis_version_id BIGINT      NOT NULL,
    finding_term_code   VARCHAR(64)  NOT NULL,
    direction           VARCHAR(16)  NOT NULL,
    weight              VARCHAR(8)   NOT NULL,
    value_constraint    VARCHAR(512),
    temporal_constraint VARCHAR(256),
    citation_id         BIGINT,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(64)  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(128),
    CONSTRAINT ck_mk_diagnosis_criterion_dir CHECK (direction IN ('SUPPORTING','REFUTING','REQUIRED','EXCLUSION')),
    CONSTRAINT ck_mk_diagnosis_criterion_weight CHECK (weight IN ('MAJOR','MINOR'))
);
CREATE INDEX IF NOT EXISTS idx_mk_diagnosis_criterion_finding ON mk_diagnosis_criterion (tenant_id, finding_term_code);
CREATE INDEX IF NOT EXISTS idx_mk_diagnosis_criterion_version ON mk_diagnosis_criterion (tenant_id, diagnosis_version_id);

CREATE TABLE IF NOT EXISTS mk_diagnosis_differential (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(64)  NOT NULL,
    diagnosis_version_id    BIGINT       NOT NULL,
    differential_identity_id BIGINT      NOT NULL,
    key_point               VARCHAR(1024),
    suggested_workup        VARCHAR(512),
    bidirectional           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(64)  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    updated_by              VARCHAR(64)  NOT NULL,
    trace_id                VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_mk_diagnosis_differential_version ON mk_diagnosis_differential (tenant_id, diagnosis_version_id);

CREATE TABLE IF NOT EXISTS mk_diagnosis_care_pointer (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    diagnosis_version_id BIGINT      NOT NULL,
    pointer_type        VARCHAR(16)  NOT NULL,
    target_ref          VARCHAR(128) NOT NULL,
    is_soft             BOOLEAN      NOT NULL DEFAULT TRUE,
    description         VARCHAR(512),
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(64)  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(128),
    CONSTRAINT ck_mk_diagnosis_pointer_type CHECK (pointer_type IN ('TREATMENT','WORKUP','PATHWAY'))
);
CREATE INDEX IF NOT EXISTS idx_mk_diagnosis_pointer_version ON mk_diagnosis_care_pointer (tenant_id, diagnosis_version_id);

CREATE TABLE IF NOT EXISTS mk_diagnosis_test_case (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    diagnosis_version_id BIGINT      NOT NULL,
    case_code           VARCHAR(64)  NOT NULL,
    findings            TEXT         NOT NULL,
    expected_identity_id BIGINT      NOT NULL,
    expected_confidence VARCHAR(16)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(64)  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(128),
    CONSTRAINT ck_mk_diagnosis_testcase_conf CHECK (expected_confidence IN ('STRONG','MODERATE','WEAK','EXCLUDE')),
    CONSTRAINT uk_mk_diagnosis_testcase UNIQUE (tenant_id, diagnosis_version_id, case_code)
);
CREATE INDEX IF NOT EXISTS idx_mk_diagnosis_testcase_version ON mk_diagnosis_test_case (tenant_id, diagnosis_version_id);

CREATE TABLE IF NOT EXISTS mk_diagnosis_confidence_policy (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    scope_key           VARCHAR(128) NOT NULL,
    strong_min_major    INTEGER      NOT NULL DEFAULT 2,
    require_all_required BOOLEAN     NOT NULL DEFAULT TRUE,
    moderate_min_hits   INTEGER      NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(64)  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(128),
    CONSTRAINT uk_mk_diagnosis_confpolicy UNIQUE (tenant_id, scope_key)
);

COMMENT ON TABLE mk_diagnosis_criterion IS '诊断标准：支持/反对/必需/排除某诊断的发现项（引用标准术语编码）及权重';
COMMENT ON COLUMN mk_diagnosis_criterion.finding_term_code IS '发现项标准术语编码（TERM-01），不写死中文';
COMMENT ON COLUMN mk_diagnosis_criterion.direction IS '方向：SUPPORTING 支持 / REFUTING 反对 / REQUIRED 必需 / EXCLUSION 排除';
COMMENT ON COLUMN mk_diagnosis_criterion.temporal_constraint IS '时序/趋势约束（可选，求值留后续阶段接 RuleDslEvaluator，Spec 1 命中到编码级）';
COMMENT ON TABLE mk_diagnosis_differential IS '鉴别清单：与本诊断需鉴别的疾病、鉴别要点与建议补充检查';
COMMENT ON TABLE mk_diagnosis_care_pointer IS '诊疗指针：确诊后指向治疗/检查（规则·知识）或专病路径（恒软建议）';
COMMENT ON TABLE mk_diagnosis_test_case IS '诊断测试病例：发现集→期望候选/置信，作为发布门禁回归集';
COMMENT ON TABLE mk_diagnosis_confidence_policy IS '置信分级策略：权重→等级阈值，可按租户/科室 scope_key 覆盖，不硬编码';

-- 平台主租户默认置信策略种子（开箱可用；客户租户可新增 scope_key 覆盖，运行时未覆盖回退 t-1）
INSERT INTO mk_diagnosis_confidence_policy
    (tenant_id, scope_key, strong_min_major, require_all_required, moderate_min_hits, created_at, created_by, updated_at, updated_by)
VALUES ('t-1', 'DEFAULT', 2, TRUE, 1, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');
```

- [ ] **Step 2: 五方言同构补齐** — 复制为 h2/oracle/dm/kingbase 4 份，按现有迁移惯例做方言映射：Oracle/DM `TIMESTAMPTZ`→`TIMESTAMP`、`BIGSERIAL`→`NUMBER GENERATED BY DEFAULT AS IDENTITY`、`BOOLEAN`→`NUMBER(1)`、`TEXT`→`CLOB`、`VARCHAR`→`VARCHAR2`；H2（`MODE=PostgreSQL`）多数同 postgres。**含 DEFAULT 策略种子 INSERT**：Oracle/DM 的 `TRUE`→`1`，`CURRENT_TIMESTAMP` 五方言通用。参照同目录任一近期五方言迁移（如 `V58__knowledge_invalidation_affected_tasks.sql`）的对应写法。

- [ ] **Step 3: 运行迁移冒烟** — Run: `cd medkernel-backend && mvn -q -Dtest=FlywayMultiDialectSmokeTest,H2BaselineMigrationTest,MigrationBaselineContractTest test` — Expected: PASS（H2 迁移到 V67，二次 migrate 无新迁移）

- [ ] **Step 4: 提交**

```bash
git add medkernel-backend/src/main/resources/db/migration/*/V67__diagnosis_knowledge_asset.sql
git commit -m "feat(diagnosis): V67 五方言迁移，新增 5 张 mk_diagnosis_* 表"
```

---

## Task 3: 枚举与实体（record）

**Files:**
- Create: `engine/knowledge/diagnosis/DiagnosisDirection.java` / `DiagnosisWeight.java` / `DiagnosisConfidence.java` / `DiagnosisCarePointerType.java`
- Create: `DiagnosisCriterion.java` / `DiagnosisDifferential.java` / `DiagnosisCarePointer.java` / `DiagnosisTestCase.java` / `DiagnosisConfidencePolicy.java`
- Test: `DiagnosisCriterionMappingTest.java`

- [ ] **Step 1: 写枚举（4 个，完整）**

```java
package com.medkernel.engine.knowledge.diagnosis;

/** 诊断标准方向。 */
public enum DiagnosisDirection { SUPPORTING, REFUTING, REQUIRED, EXCLUSION }
```
其余 3 个同文件惯例：`DiagnosisWeight { MAJOR, MINOR }`；`DiagnosisConfidence { STRONG, MODERATE, WEAK, EXCLUDE }`；`DiagnosisCarePointerType { TREATMENT, WORKUP, PATHWAY }`，各带中文 Javadoc。

- [ ] **Step 2: 写 `DiagnosisCriterion` 实体（完整代表，仿 KnowledgeIdentity）**

```java
package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 诊断标准：支持/反对/必需/排除某诊断的发现项及权重。 */
@Table("mk_diagnosis_criterion")
public record DiagnosisCriterion(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("diagnosis_version_id") Long diagnosisVersionId,
    @Column("finding_term_code") String findingTermCode,
    @Column("direction") DiagnosisDirection direction,
    @Column("weight") DiagnosisWeight weight,
    @Column("value_constraint") String valueConstraint,
    @Column("temporal_constraint") String temporalConstraint,
    @Column("citation_id") Long citationId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
```

- [ ] **Step 3: 其余 4 个实体同构** — 按各自表列写 record（字段见 Task 2 DDL）：`DiagnosisDifferential`(differentialIdentityId/keyPoint/suggestedWorkup/bidirectional…)、`DiagnosisCarePointer`(pointerType:DiagnosisCarePointerType/targetRef/isSoft/description…)、`DiagnosisTestCase`(caseCode/findings/expectedIdentityId/expectedConfidence:DiagnosisConfidence…)、`DiagnosisConfidencePolicy`(scopeKey/strongMinMajor:int/requireAllRequired:boolean/moderateMinHits:int…)，审计字段同 `DiagnosisCriterion`。

- [ ] **Step 4: 写映射测试（验证 record 字段齐全，编译即覆盖核心）**

```java
package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DiagnosisCriterionMappingTest {
    @Test
    void buildsCriterion() {
        var c = new DiagnosisCriterion(null, "t-1", 10L, "TERM-FEVER",
            DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR, null, null, null,
            Instant.now(), "u", Instant.now(), "u", "tr");
        assertThat(c.direction()).isEqualTo(DiagnosisDirection.SUPPORTING);
        assertThat(c.findingTermCode()).isEqualTo("TERM-FEVER");
    }
}
```

- [ ] **Step 5: 运行** — Run: `mvn -q -Dtest=DiagnosisCriterionMappingTest test` — Expected: PASS
- [ ] **Step 6: 提交** — `git commit -m "feat(diagnosis): 诊断知识枚举与 5 个实体 record"`

---

## Task 4: 仓储（5 个）

**Files:**
- Create: `DiagnosisCriterionRepository.java`（+ 其余 4 个同构）
- Test: `DiagnosisCriterionRepositoryTest.java`（Spring Data JDBC 切片测试，仿现有 `*RepositoryTest`）

- [ ] **Step 1: 写仓储测试（失败）** — 仿现有 `RecommendationRepositoryTest`：注入 `DiagnosisCriterionRepository`，保存两条不同 `diagnosisVersionId` 的 criterion，断言 `findByTenantIdAndDiagnosisVersionId("t-1", 10L)` 只返回该 version 的、且按 id 升序。

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DiagnosisCriterionRepositoryTest test` — Expected: 编译失败（仓储不存在）

- [ ] **Step 3: 写 `DiagnosisCriterionRepository`（完整代表，仿 KnowledgeIdentityRepository）**

```java
package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiagnosisCriterionRepository extends ListCrudRepository<DiagnosisCriterion, Long> {

    @Query("SELECT * FROM mk_diagnosis_criterion WHERE tenant_id = :tenantId "
         + "AND diagnosis_version_id = :versionId ORDER BY id ASC")
    List<DiagnosisCriterion> findByTenantIdAndDiagnosisVersionId(String tenantId, Long versionId);

    // Spring Data JDBC 不做派生 deleteBy，按项目惯例用 @Modifying + 显式 DELETE（参照 ClinicalEventOutboxRepository 的 @Modifying import）。
    @org.springframework.data.jdbc.repository.query.Modifying
    @Query("DELETE FROM mk_diagnosis_criterion WHERE tenant_id = :tenantId AND id = :id")
    void deleteByTenantIdAndId(String tenantId, Long id);
}
```

- [ ] **Step 4: 其余 4 仓储同构** — `DiagnosisDifferentialRepository`/`DiagnosisCarePointerRepository`/`DiagnosisTestCaseRepository` 各加 `findByTenantIdAndDiagnosisVersionId` + 同款 `@Modifying @Query` 删除；`DiagnosisConfidencePolicyRepository` 加 `Optional<DiagnosisConfidencePolicy> findByTenantIdAndScopeKey(String tenantId, String scopeKey)`。

- [ ] **Step 5: 运行验证通过** — Run: `mvn -q -Dtest=DiagnosisCriterionRepositoryTest test` — Expected: PASS
- [ ] **Step 6: 提交** — `git commit -m "feat(diagnosis): 5 个诊断知识仓储"`

---

## Task 5: 错误码与 owner 登记

**Files:**
- Modify: `shared/api/error/ErrorCode.java`
- Modify: `shared/architecture/DomainOwnershipCatalog.java:33`
- Test: `DomainOwnershipContractTest`（架构 owner 契约测试，复跑验证每张 `mk_diagnosis_*` 有唯一 owner）

- [ ] **Step 1: 加错误码** — 在 `ErrorCode` 枚举按真实签名 `(String code, int httpStatus, String message, ErrorClass class, boolean retryable)` 新增（4 个）：

```java
ENG_DX_001("ENG-DX-001", 409, "诊断知识版本无效或未发布", ErrorClass.DATA, false),
ENG_DX_004("ENG-DX-004", 400, "鉴别引用的诊断身份不存在", ErrorClass.DATA, false),
ENG_DX_005("ENG-DX-005", 409, "诊断置信策略缺失或非法", ErrorClass.DATA, false),
ENG_DX_006("ENG-DX-006", 409, "诊断测试病例未通过，不得发布", ErrorClass.DATA, false),
```

`ENG_DX_005` 在本 Plan A 定义（Task 8 `resolvePolicy` 先用）；**运行时 Plan B 不再新增 DX 错误码——空态/部分可用是正常响应字段，不抛异常**（已删原计划的 `ENG_DX_002/003`）。

- [ ] **Step 2: 登记 owner** — `DomainOwnershipCatalog.java` 第 33 行 `engine-knowledge` 的 `prefixes("knowledge_asset_", "knowledge_export_", "source_")` 改为 `prefixes("knowledge_asset_", "knowledge_export_", "source_", "mk_diagnosis_")`。

- [ ] **Step 3: 运行架构/owner 测试** — Run: `mvn -q -Dtest=DomainOwnershipContractTest,ErrorCodeTest test` — Expected: PASS（每张 `mk_diagnosis_*` 表经 `mk_diagnosis_` 前缀归 `engine-knowledge`、唯一 owner）

- [ ] **Step 4: 提交** — `git commit -m "feat(diagnosis): ENG_DX 错误码 + mk_diagnosis_ owner 登记"`

---

## Task 6: 置信策略 DiagnosisConfidenceEvaluator

**Files:**
- Create: `DiagnosisMatchStats.java`（命中统计 record）/ `DiagnosisConfidenceEvaluator.java`
- Test: `DiagnosisConfidenceEvaluatorTest.java`

- [ ] **Step 1: 写失败测试（覆盖 4 级 + 可配置）**

```java
package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DiagnosisConfidenceEvaluatorTest {
    private final DiagnosisConfidenceEvaluator evaluator = new DiagnosisConfidenceEvaluator();
    // 默认策略：strongMinMajor=2, requireAllRequired=true, moderateMinHits=1
    private final DiagnosisConfidencePolicy policy = new DiagnosisConfidencePolicy(
        1L, "t-1", "DEFAULT", 2, true, 1, null, "u", null, "u", null);

    @Test void exclusionWins() {
        var stats = new DiagnosisMatchStats(3, 0, 0, 0, true);
        assertThat(evaluator.evaluate(stats, policy)).isEqualTo(DiagnosisConfidence.EXCLUDE);
    }
    @Test void strongWhenAllRequiredAndEnoughMajor() {
        var stats = new DiagnosisMatchStats(2, 0, 1, 1, false); // major=2, required 1/1
        assertThat(evaluator.evaluate(stats, policy)).isEqualTo(DiagnosisConfidence.STRONG);
    }
    @Test void weakWhenRequiredMissing() {
        var stats = new DiagnosisMatchStats(2, 0, 2, 1, false); // required 1/2 缺失
        assertThat(evaluator.evaluate(stats, policy)).isEqualTo(DiagnosisConfidence.WEAK);
    }
    @Test void moderateWhenRequiredMetButFewMajor() {
        var stats = new DiagnosisMatchStats(1, 1, 1, 1, false);
        assertThat(evaluator.evaluate(stats, policy)).isEqualTo(DiagnosisConfidence.MODERATE);
    }
}
```

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DiagnosisConfidenceEvaluatorTest test` — Expected: 编译失败

- [ ] **Step 3: 实现 stats record + evaluator（完整、确定性、无百分比）**

```java
package com.medkernel.engine.knowledge.diagnosis;

/** 单个候选诊断的命中统计。 */
public record DiagnosisMatchStats(
    int majorHits, int minorHits, int requiredTotal, int requiredHit, boolean hitExclusion) {}
```

```java
package com.medkernel.engine.knowledge.diagnosis;

import org.springframework.stereotype.Component;

/** 确定性置信分级：仅输出等级，绝不输出百分比概率（守 NMPA 边界）。阈值取自可配置策略。 */
@Component
public class DiagnosisConfidenceEvaluator {

    public DiagnosisConfidence evaluate(DiagnosisMatchStats s, DiagnosisConfidencePolicy p) {
        if (s.hitExclusion()) {
            return DiagnosisConfidence.EXCLUDE;
        }
        boolean requiredSatisfied = !p.requireAllRequired() || s.requiredHit() >= s.requiredTotal();
        if (!requiredSatisfied) {
            return DiagnosisConfidence.WEAK;
        }
        if (s.majorHits() >= p.strongMinMajor()) {
            return DiagnosisConfidence.STRONG;
        }
        if (s.majorHits() + s.minorHits() >= p.moderateMinHits()) {
            return DiagnosisConfidence.MODERATE;
        }
        return DiagnosisConfidence.WEAK;
    }
}
```

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=DiagnosisConfidenceEvaluatorTest test` — Expected: PASS（4 测试全绿）
- [ ] **Step 5: 提交** — `git commit -m "feat(diagnosis): 可配置确定性置信分级 evaluator"`

---

## Task 7: 命中核心 DiagnosisMatcher

**Files:**
- Create: `DiagnosisMatchResult.java`（结果 record）/ `DiagnosisMatcher.java`
- Test: `DiagnosisMatcherTest.java`

- [ ] **Step 1: 写失败测试（命中/缺失必需/排除/证据分类，可复现）**

```java
package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiagnosisMatcherTest {
    private final DiagnosisMatcher matcher = new DiagnosisMatcher(new DiagnosisConfidenceEvaluator());
    private final DiagnosisConfidencePolicy policy = new DiagnosisConfidencePolicy(
        1L, "t-1", "DEFAULT", 2, true, 1, null, "u", null, "u", null);

    private DiagnosisCriterion crit(String code, DiagnosisDirection dir, DiagnosisWeight w) {
        return new DiagnosisCriterion(null, "t-1", 10L, code, dir, w, null, null, null,
            Instant.now(), "u", Instant.now(), "u", "tr");
    }

    @Test void strongCandidateWithEvidenceAndMissing() {
        var criteria = List.of(
            crit("FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit("COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR),
            crit("CRP_HIGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR),
            crit("RASH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MINOR));
        var result = matcher.match(Set.of("FEVER", "COUGH", "CRP_HIGH"), criteria, policy);
        assertThat(result.confidence()).isEqualTo(DiagnosisConfidence.STRONG);
        assertThat(result.supporting()).contains("FEVER", "COUGH", "CRP_HIGH");
        assertThat(result.missingRequired()).isEmpty();
        assertThat(result.hitExclusion()).isFalse();
    }

    @Test void exclusionMarksExclude() {
        var criteria = List.of(crit("NEG_MARKER", DiagnosisDirection.EXCLUSION, DiagnosisWeight.MAJOR));
        var result = matcher.match(Set.of("NEG_MARKER"), criteria, policy);
        assertThat(result.confidence()).isEqualTo(DiagnosisConfidence.EXCLUDE);
        assertThat(result.hitExclusion()).isTrue();
    }

    @Test void missingRequiredLowersToWeak() {
        var criteria = List.of(
            crit("FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            crit("COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR));
        var result = matcher.match(Set.of("COUGH"), criteria, policy);
        assertThat(result.confidence()).isEqualTo(DiagnosisConfidence.WEAK);
        assertThat(result.missingRequired()).contains("FEVER");
    }
}
```

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DiagnosisMatcherTest test` — Expected: 编译失败

- [ ] **Step 3: 实现结果 record + matcher（完整，纯逻辑、可复现）**

```java
package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;

/** 单候选命中结果：置信 + 支持/反对证据 + 缺失必需项 + 是否命中排除。 */
public record DiagnosisMatchResult(
    DiagnosisConfidence confidence,
    List<String> supporting,
    List<String> refuting,
    List<String> missingRequired,
    boolean hitExclusion) {}
```

```java
package com.medkernel.engine.knowledge.diagnosis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 诊断命中核心：发现集 + 一组诊断标准 → 候选证据 + 置信分级。
 *
 * <p>确定性、可复现（同输入同标准同策略结果一致）；按 finding_term_code 命中。
 * value_constraint / temporal_constraint 的求值是<b>后续阶段挂点</b>（接 RuleDslEvaluator 的 between/unit_compare/temporal）；
 * Spec 1（Plan A+B）命中到编码级，这两个约束字段已落库但暂不求值。
 */
@Component
public class DiagnosisMatcher {

    private final DiagnosisConfidenceEvaluator evaluator;

    public DiagnosisMatcher(DiagnosisConfidenceEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public DiagnosisMatchResult match(Set<String> findings, List<DiagnosisCriterion> criteria,
                                      DiagnosisConfidencePolicy policy) {
        List<String> supporting = new ArrayList<>();
        List<String> refuting = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        int majorHits = 0;
        int minorHits = 0;
        int requiredTotal = 0;
        int requiredHit = 0;
        boolean hitExclusion = false;

        for (DiagnosisCriterion c : criteria) {
            boolean present = findings.contains(c.findingTermCode());
            switch (c.direction()) {
                case REQUIRED -> {
                    requiredTotal++;
                    if (present) {
                        requiredHit++;
                        supporting.add(c.findingTermCode());
                        if (c.weight() == DiagnosisWeight.MAJOR) majorHits++; else minorHits++;
                    } else {
                        missingRequired.add(c.findingTermCode());
                    }
                }
                case SUPPORTING -> {
                    if (present) {
                        supporting.add(c.findingTermCode());
                        if (c.weight() == DiagnosisWeight.MAJOR) majorHits++; else minorHits++;
                    }
                }
                case REFUTING -> {
                    if (present) refuting.add(c.findingTermCode());
                }
                case EXCLUSION -> {
                    if (present) { hitExclusion = true; refuting.add(c.findingTermCode()); }
                }
            }
        }
        var stats = new DiagnosisMatchStats(majorHits, minorHits, requiredTotal, requiredHit, hitExclusion);
        return new DiagnosisMatchResult(evaluator.evaluate(stats, policy),
            List.copyOf(supporting), List.copyOf(refuting), List.copyOf(missingRequired), hitExclusion);
    }
}
```

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=DiagnosisMatcherTest test` — Expected: PASS（3 测试全绿）
- [ ] **Step 5: 提交** — `git commit -m "feat(diagnosis): 确定性命中核心 DiagnosisMatcher"`

---

## Task 8: DiagnosisKnowledgeService（CRUD + 发布门禁）

**Files:**
- Create: `DiagnosisKnowledgeService.java`、DTO `DiagnosisCriterionRequest.java` 等（Record DTO + 校验注解）
- Test: `DiagnosisKnowledgeServiceTest.java`

- [ ] **Step 1: 写失败测试（关键路径：建标准 + 发布门禁红绿）**

```java
package com.medkernel.engine.knowledge.diagnosis;
// 仿现有 RecommendationEngineServiceTest 的 @SpringBootTest + 真实仓储 + RequestContext 设置惯例。
// 用例：
//  1) addCriterion 后 findByVersion 能取回；
//  2) 一条 test_case（findings 命中全部 REQUIRED+2 MAJOR，expected=STRONG）→ publishGate 通过；
//  3) 把 expected 改成 WEAK（与实际 STRONG 不符）→ publishGate 抛 ApiException(ENG_DX_006)。
```
（按 `RecommendationEngineServiceTest` 的 `@SpringBootTest`/上下文夹具写法落地三个 `@Test`，断言第 3 个 `assertThatThrownBy(...).isInstanceOf(ApiException.class)` 且 errorCode 为 `ENG_DX_006`。）

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DiagnosisKnowledgeServiceTest test` — Expected: 编译失败

- [ ] **Step 3: 实现 service（CRUD + 门禁，完整关键逻辑）**

```java
package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 诊断知识维护服务：标准/鉴别/指针/测试病例 CRUD + 以测试病例全绿为发布门禁。 */
@Service
public class DiagnosisKnowledgeService {

    private final DiagnosisCriterionRepository criteria;
    private final DiagnosisTestCaseRepository testCases;
    private final DiagnosisConfidencePolicyRepository policies;
    private final DiagnosisMatcher matcher;
    private final AuditEventPublisher audit;
    private final com.medkernel.engine.knowledge.KnowledgeVersionService knowledgeVersions;

    public DiagnosisKnowledgeService(DiagnosisCriterionRepository criteria,
            DiagnosisTestCaseRepository testCases, DiagnosisConfidencePolicyRepository policies,
            DiagnosisMatcher matcher, AuditEventPublisher audit,
            com.medkernel.engine.knowledge.KnowledgeVersionService knowledgeVersions) {
        this.criteria = criteria;
        this.testCases = testCases;
        this.policies = policies;
        this.matcher = matcher;
        this.audit = audit;
        this.knowledgeVersions = knowledgeVersions;
    }

    @Transactional
    public DiagnosisCriterion addCriterion(Long versionId, DiagnosisCriterionRequest req) {
        String tenant = tenant();
        String actor = actor();
        Instant now = Instant.now();
        DiagnosisCriterion saved = criteria.save(new DiagnosisCriterion(null, tenant, versionId,
            req.findingTermCode(), req.direction(), req.weight(), req.valueConstraint(),
            req.temporalConstraint(), req.citationId(), now, actor, now, actor, traceId()));
        audit.publish(AuditAction.CREATE, "mk_diagnosis_criterion", String.valueOf(saved.id()),
            "新增诊断标准 " + req.findingTermCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DiagnosisCriterion> listCriteria(Long versionId) {
        return criteria.findByTenantIdAndDiagnosisVersionId(tenant(), versionId);
    }

    /** 发布门禁：该版本所有测试病例经命中核心复算，分级与期望一致才放行，否则 ENG_DX_006。 */
    @Transactional(readOnly = true)
    public void publishGate(Long versionId) {
        String tenant = tenant();
        List<DiagnosisCriterion> versionCriteria = criteria.findByTenantIdAndDiagnosisVersionId(tenant, versionId);
        DiagnosisConfidencePolicy policy = resolvePolicy(tenant);
        List<DiagnosisTestCase> cases = testCases.findByTenantIdAndDiagnosisVersionId(tenant, versionId);
        for (DiagnosisTestCase tc : cases) {
            Set<String> findings = parseFindings(tc.findings());
            DiagnosisMatchResult result = matcher.match(findings, versionCriteria, policy);
            if (result.confidence() != tc.expectedConfidence()) {
                throw new ApiException(ErrorCode.ENG_DX_006,
                    "测试病例 " + tc.caseCode() + " 期望 " + tc.expectedConfidence()
                        + " 实得 " + result.confidence());
            }
        }
    }

    /** 发布诊断知识版本：先过测试病例门禁（publishGate）全绿，才调通用版本激活。门禁失败抛 ENG_DX_006。 */
    @Transactional
    public com.medkernel.engine.knowledge.KnowledgeAssetVersion publishDiagnosis(
            Long identityId, Long versionId, String reason) {
        publishGate(versionId);
        return knowledgeVersions.activate(identityId, versionId, reason);
    }

    private DiagnosisConfidencePolicy resolvePolicy(String tenant) {
        // 当前租户 DEFAULT 优先，未覆盖回退平台主源 t-1（V67 已种子）；都缺才诚实失败。
        return policies.findByTenantIdAndScopeKey(tenant, "DEFAULT")
            .or(() -> policies.findByTenantIdAndScopeKey("t-1", "DEFAULT"))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_DX_005, "缺少默认置信策略 DEFAULT"));
    }

    private Set<String> parseFindings(String raw) {
        // findings 存为逗号分隔标准编码；空安全。
        if (raw == null || raw.isBlank()) return Set.of();
        return Set.of(raw.split("\\s*,\\s*"));
    }

    private String tenant() {
        String t = RequestContext.currentOrgScope().tenantId();
        if (t == null || t.isBlank()) throw ApiException.tenantMissing();
        return t;
    }
    private String actor() { return RequestContext.currentUserId().orElse("system"); }
    private String traceId() { return RequestContext.currentTraceId(); }
}
```
DTO `DiagnosisCriterionRequest`（Record + Bean Validation，仿现有 `*Request`）：`findingTermCode`(@NotBlank)、`direction`(@NotNull)、`weight`(@NotNull)、`valueConstraint`、`temporalConstraint`、`citationId`。

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=DiagnosisKnowledgeServiceTest test` — Expected: PASS（含 ENG_DX_006 门禁红线）
- [ ] **Step 5: 提交** — `git commit -m "feat(diagnosis): 诊断知识 CRUD + 测试病例发布门禁"`

---

## Task 9: 维护 API 控制器 + 契约测试

**Files:**
- Create: `DiagnosisKnowledgeController.java`
- Test: `DiagnosisKnowledgeApiContractTest.java`（仿现有 `*ApiContractTest` + `@WebMvcTest`/`MockMvc` 或 security 切片）

- [ ] **Step 1: 写失败契约测试** — 仿 `RecommendationEngineControllerSecurityTest`：`POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/criteria` 需 `@PreAuthorize("@perm.has('knowledge.write')")`，无权限 403；带权限 201 返回 `ApiResult` 且 body 含 `findingTermCode`。`GET .../criteria` 需 `knowledge.read`。**另加发布门禁端到端**：建一个 `expected_confidence` 与命中不符的测试病例，`POST .../identities/{id}/versions/{vid}/publish` 应 409 `ENG_DX_006`、版本不被激活；修正期望后再发布应成功激活（验证门禁真生效）。

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DiagnosisKnowledgeApiContractTest test` — Expected: 404/编译失败（控制器不存在）

- [ ] **Step 3: 实现控制器（完整，仿 RecommendationEngineController + DataScope）**

```java
package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/** 诊断知识维护 API（归 knowledge 客户面，复用知识读写权限）。 */
@RestController
@RequestMapping("/api/v1/engine/knowledge/diagnosis")
@DataScope(requireTenant = true)
public class DiagnosisKnowledgeController {

    private final DiagnosisKnowledgeService service;

    public DiagnosisKnowledgeController(DiagnosisKnowledgeService service) {
        this.service = service;
    }

    @PostMapping("/versions/{versionId}/criteria")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DiagnosisCriterion> addCriterion(@PathVariable Long versionId,
            @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid DiagnosisCriterionRequest req) {
        return ApiResult.ok(service.addCriterion(versionId, req));
    }

    @GetMapping("/versions/{versionId}/criteria")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DiagnosisCriterion>> listCriteria(@PathVariable Long versionId) {
        return ApiResult.ok(service.listCriteria(versionId));
    }

    // 发布诊断知识版本：必过测试病例门禁（publishGate）才激活——门禁真正生效的接线点。
    @PostMapping("/identities/{identityId}/versions/{versionId}/publish")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<com.medkernel.engine.knowledge.KnowledgeAssetVersion> publish(
            @PathVariable Long identityId, @PathVariable Long versionId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String reason) {
        return ApiResult.ok(service.publishDiagnosis(identityId, versionId, reason));
    }
}
```
（差异/鉴别/指针/测试病例端点按同样式补齐。**发布门禁已接线**：诊断版本发布走上面 `publish` 端点 → `service.publishDiagnosis` → 先 `publishGate` 全绿再 `KnowledgeVersionService.activate`，门禁真正生效、非死方法。）

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=DiagnosisKnowledgeApiContractTest test` — Expected: PASS
- [ ] **Step 5: 后端全量回归 + T-GATE**

Run: `cd medkernel-backend && mvn -q test` — Expected: 全绿，0 failures/errors；H2/PostgreSQL/Oracle 迁移到 V67 且二次 no-op。
Run（changed-mode 真实性门禁）: `node ../scripts/authenticity-guard.mjs --changed`（按项目实际命令）— Expected: 0 阻断。

- [ ] **Step 6: 提交** — `git commit -m "feat(diagnosis): 诊断知识维护 API + 契约测试"`

---

## Self-Review（写完即查）

**Spec 覆盖（对设计 4.1/4.2/4.6）：**
- 诊断身份/版本复用 KNOW-01 + `domain=DIAGNOSIS` → Task 1；5 子表 → Task 2；实体/仓储 → Task 3/4。
- 时序 `temporal_constraint` 字段 → Task 2/3（求值挂点留 Plan B，已在 `DiagnosisMatcher` Javadoc 标注）。
- 置信分级非概率、可配置不硬编码 → Task 6（`mk_diagnosis_confidence_policy` + evaluator）。
- 命中证据（支持/反对/缺失必需/排除） → Task 7。
- 测试病例发布门禁 → Task 8/9（`publishGate` + `ENG_DX_006`，经 `publishDiagnosis` 接到 `KnowledgeVersionService.activate`、由 `publish` 端点真正触发，非死方法）。
- 防爆炸（发现引用编码 + 反向索引） → Task 2（`idx_..._finding`）+ Task 3（`findingTermCode`）。
- owner/错误码 → Task 5。维护 API → Task 9。
- **不在 Plan A（属 Plan B，设计已分期）：** 患者上下文接入、红线 OPT-04 合流、卡组装、`diagnosis-assist` 运行 API、标准化部分可用、空态安全、value/temporal DSL 求值。

**类型一致性：** `DiagnosisConfidence{STRONG,MODERATE,WEAK,EXCLUDE}`、`DiagnosisMatchStats(majorHits,minorHits,requiredTotal,requiredHit,hitExclusion)`、`DiagnosisMatchResult(confidence,supporting,refuting,missingRequired,hitExclusion)`、`DiagnosisConfidencePolicy(...,strongMinMajor,requireAllRequired,moderateMinHits,...)`、`matcher.match(Set,List,policy)`、`evaluator.evaluate(stats,policy)`、`service.publishGate(versionId)` — 全计划一致。

**占位扫描：** 无 TBD/TODO；机械同构处（五方言、雷同 record/仓储/端点）均给完整代表 + 明确同构指令，非空泛占位。

---

## 执行交接

Plan A 完成后即有"诊断知识可建、可维护、发布前测试病例全绿门禁"的可独立测试软件。**Plan B（运行时鉴别诊断：患者上下文 + 红线合流 + 卡组装 + `diagnosis-assist` API）依赖本 Plan A，另出计划。**
