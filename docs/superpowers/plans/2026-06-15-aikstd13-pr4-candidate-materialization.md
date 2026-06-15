# AIK-STD-13 PR4 候选真实物化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or subagent-driven-development) to implement task-by-task. Steps use checkbox (`- [ ]`).

**Goal:** 把 AIK-STD-13 生产候选从「暂存桩」真实物化进既有知识版本/审核链（建 `KnowledgeAssetVersion` + `CandidateClassification` + 据 PR3 路由建 `ReviewAssignment`），使候选自动进现审核台可审/发。

**Architecture:** 新增 `SourceReferenceResolver`（串源引用→受控源 FK，B0 确定性，解析不出诚实拒收）+ `MaterializationTarget`（生产方显式声明目标知识身份）+ `MaterializingCandidateIntake`（替换 `StagingCandidateIntake` 桩，编排解析+物化）；`KnowledgeVersionService.classifyCandidate` 聚焦扩展接 PR3 路由分派计划（既有调用方传 null 零回归）。仅覆盖 discovery-origin，B0、不碰 P6。

**Tech Stack:** Spring Boot 3 + Spring Data JDBC、JUnit5 + Mockito + AssertJ、H2 集成测试。**无新迁移**（复用 `knowledge_identity`/`knowledge_asset_version`/`mk_knowledge_candidate_classification`/`mk_knowledge_review_assignment`）。

**设计依据:** [docs/superpowers/specs/2026-06-15-aikstd13-pr4-candidate-materialization-design.md](../specs/2026-06-15-aikstd13-pr4-candidate-materialization-design.md)。

**关键事实（执行者必读）:**
- 后端根 = `medkernel-backend/`（全部 `mvn` 在此跑）。包 = `com.medkernel.engine.knowledge`（resolver/扩展）+ `com.medkernel.engine.knowledge.production`（target/intake/submit）。
- discovery 串源引用格式（`DiscoveryOrchestrationService`）：`sourceCode + ":" + versionNo + ":" + anchorPath`（anchorPath 可含 `:`，故 `split(":", 3)`）。
- `SourceDocumentRepository.findByTenantIdAndSourceCode(tenant, code)`、`SourceVersionRepository.findBySourceDocumentIdAndVersionNo(docId, versionNo)`。
- `KnowledgeIdentityRepository.findByTenantIdAndId`、`findByTenantIdAndIdentityCode`、`save`（ListCrudRepository）。`KnowledgeIdentityStatus`={ACTIVE,DEPRECATED,WITHDRAWN,ARCHIVED}（无 DRAFT；新建用 ACTIVE）。
- `KnowledgeIdentity` 构造：`(id, tenantId, identityCode, KnowledgeDomain domain, subject, specialtyId, description, KnowledgeIdentityStatus status, currentVersionId, createdAt, createdBy, updatedAt, updatedBy)`（`engine.knowledge.KnowledgeDomain`＝内容域 11 值）。
- `KnowledgeVersionService.classifyCandidate(Long identityId, KnowledgeVersionCreateRequest request)`：已建 version(PENDING_REPLACEMENT_REVIEW)+classification(4 态)+**ReviewAssignment**（当前 `assignedTo=提交人 actor`，行在 ~378-394）。
- `KnowledgeVersionCreateRequest`：`versionNo`(@NotBlank)、`sourceDocumentId`/`sourceVersionId`(@NotNull Long)、`content`(@NotBlank)、`riskLevel`(@NotNull)、`gradeQuality`(@NotNull `GradeEvidenceQuality`={HIGH,MODERATE,LOW,VERY_LOW})、`gradeStrength`(可空)、`anchors`(可空)、`reviewCycleMonths`(@NotNull 1..60)、context 字段(`tenant_id` 须等当前租户，余可 null)。
- `ReviewAssignment` 构造：`(id, tenantId, orgPath, candidateClassificationId, identityId, candidateVersionId, assignedTo, reviewStatus, decision, reason, decidedBy, decidedAt, createdAt, createdBy, updatedAt, updatedBy)`。
- PR3 已有：`CandidateReviewRouter.resolve(pipeline, domain, risk) → ReviewRoutingDecision(ownerReviewerRole, domainReviewerRole, requiresDualSign, domain)`（角色 `RoleCode`，`RoleCode.code()` 取串码）。
- `KnowledgeAssetEnvelope`：`assetType, assetIdentity, subject, versionLabel, sources(List<AssetSourceRef>), trustLevel, gradeQuality, gradeStrength, riskLevel, orgScope, contentHash, payload, lifecycleStatus`。`AssetSourceRef(sourceRef, authorityLevel)`。
- 当前 `submitCandidate(jobCode, KnowledgeAssetEnvelope)` 返回 `CandidateSubmissionResponse(candidateRef, routing)`（PR3）；控制器 `POST /jobs/{jobCode}/candidates` body=`@Valid KnowledgeAssetEnvelope`。

---

## Task 1: `SourceReferenceResolver` + `ResolvedSource`

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/ResolvedSource.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/SourceReferenceResolver.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/SourceReferenceResolverTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

class SourceReferenceResolverTest {

    private SourceDocumentRepository documents;
    private SourceVersionRepository versions;
    private SourceReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        documents = mock(SourceDocumentRepository.class);
        versions = mock(SourceVersionRepository.class);
        resolver = new SourceReferenceResolver(documents, versions);
    }

    private SourceDocument doc() {
        return new SourceDocument(7L, "t1", "SRC-1", SourceType.GUIDELINE, SourceAuthorityLevel.A_REGULATION,
            "依据", "标题", "出版者", "license", "zh", Instant.now(), "u", Instant.now(), "u");
    }

    private SourceVersion ver() {
        return new SourceVersion(9L, "t1", 7L, "v1", Instant.now(), "hash", "uri", "zh", Instant.now(), "u");
    }

    @Test
    void resolvesSourceRefToForeignKeys() {
        when(documents.findByTenantIdAndSourceCode("t1", "SRC-1")).thenReturn(Optional.of(doc()));
        when(versions.findBySourceDocumentIdAndVersionNo(7L, "v1")).thenReturn(Optional.of(ver()));

        ResolvedSource resolved = resolver.resolve("t1", "SRC-1:v1:root/0");

        assertThat(resolved.sourceDocumentId()).isEqualTo(7L);
        assertThat(resolved.sourceVersionId()).isEqualTo(9L);
        assertThat(resolved.anchorPath()).isEqualTo("root/0");
    }

    @Test
    void rejectsWhenDocumentMissing() {
        when(documents.findByTenantIdAndSourceCode("t1", "SRC-X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve("t1", "SRC-X:v1:a")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsWhenVersionMissing() {
        when(documents.findByTenantIdAndSourceCode("t1", "SRC-1")).thenReturn(Optional.of(doc()));
        when(versions.findBySourceDocumentIdAndVersionNo(7L, "v9")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve("t1", "SRC-1:v9:a")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsMalformedRef() {
        assertThatThrownBy(() -> resolver.resolve("t1", "bad-ref")).isInstanceOf(ApiException.class);
    }
}
```

- [ ] **Step 2: 跑确认失败** — `cd medkernel-backend && mvn -q test -Dtest=SourceReferenceResolverTest`（编译失败：类不存在）。

- [ ] **Step 3: 建 `ResolvedSource`**

```java
package com.medkernel.engine.knowledge;

/** 解析后的受控源 FK + 锚点（AIK-STD-13 PR4 物化）。 */
public record ResolvedSource(Long sourceDocumentId, Long sourceVersionId, String anchorPath) {
}
```

- [ ] **Step 4: 建 `SourceReferenceResolver`**

```java
package com.medkernel.engine.knowledge;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 受控源引用解析器（AIK-STD-13 PR4，B0 纯确定性）。
 *
 * <p>把信封串源引用 {@code "sourceCode:versionNo:anchorPath"}（与 LLM-06 探索产出格式对齐）回查为受控源 FK；
 * 解析不出诚实拒收（铁律 #1 不伪造 FK、不半物化），强租户隔离。
 */
@Service
public class SourceReferenceResolver {

    private final SourceDocumentRepository documents;
    private final SourceVersionRepository versions;

    public SourceReferenceResolver(SourceDocumentRepository documents, SourceVersionRepository versions) {
        this.documents = documents;
        this.versions = versions;
    }

    public ResolvedSource resolve(String tenantId, String sourceRef) {
        String[] parts = sourceRef == null ? new String[0] : sourceRef.split(":", 3);
        if (parts.length < 3 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "来源引用格式非法，期望 sourceCode:versionNo:anchorPath：" + sourceRef);
        }
        String sourceCode = parts[0];
        String versionNo = parts[1];
        String anchorPath = parts[2];
        SourceDocument document = documents.findByTenantIdAndSourceCode(tenantId, sourceCode)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_KNOW_001, "受控来源不存在 code=" + sourceCode));
        SourceVersion version = versions.findBySourceDocumentIdAndVersionNo(document.id(), versionNo)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_KNOW_001,
                "受控来源版本不存在 code=" + sourceCode + " version=" + versionNo));
        return new ResolvedSource(document.id(), version.id(), anchorPath);
    }
}
```

（注：若 `ErrorCode.ENG_KNOW_001` 名称不符，执行时按 `KnowledgeVersionService` 既有用法核对实际枚举名替换。）

- [ ] **Step 5: 跑确认通过** — `mvn -q test -Dtest=SourceReferenceResolverTest`（PASS 4）。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/ResolvedSource.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/SourceReferenceResolver.java \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/SourceReferenceResolverTest.java
git commit -m "feat(aikstd13/PR4): 受控源引用解析器（串引用→源 FK，B0 解析不出诚实拒收）"
```

---

## Task 2: `MaterializationTarget` + `NewIdentitySpec`

**Files:**
- Create: `.../production/NewIdentitySpec.java`、`.../production/MaterializationTarget.java`
- Test: `.../production/MaterializationTargetTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeDomain;

class MaterializationTargetTest {

    @Test
    void existingIdentityTargetIsValid() {
        MaterializationTarget t = new MaterializationTarget(5L, null);
        t.validate();
        assertThat(t.targetIdentityId()).isEqualTo(5L);
    }

    @Test
    void newIdentityTargetIsValid() {
        MaterializationTarget t = new MaterializationTarget(null,
            new NewIdentitySpec(KnowledgeDomain.GUIDELINE, "二甲双胍说明书", "KN-METFORMIN"));
        t.validate();
        assertThat(t.newIdentity().subject()).isEqualTo("二甲双胍说明书");
    }

    @Test
    void bothSetIsRejected() {
        MaterializationTarget t = new MaterializationTarget(5L,
            new NewIdentitySpec(KnowledgeDomain.GUIDELINE, "s", "KN-1"));
        assertThatThrownBy(t::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neitherSetIsRejected() {
        assertThatThrownBy(() -> new MaterializationTarget(null, null).validate())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 跑确认失败** — `mvn -q test -Dtest=MaterializationTargetTest`（编译失败）。

- [ ] **Step 3: 建两 record**

`NewIdentitySpec.java`：
```java
package com.medkernel.engine.knowledge.production;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.KnowledgeDomain;

/**
 * 新建知识身份壳声明（AIK-STD-13 PR4）：生产方显式声明内容域 + 主题 + 身份编码。
 */
public record NewIdentitySpec(
    @NotNull KnowledgeDomain domain,
    @NotBlank String subject,
    @NotBlank String identityCode
) {
}
```

`MaterializationTarget.java`：
```java
package com.medkernel.engine.knowledge.production;

import jakarta.validation.Valid;

/**
 * 物化目标知识身份（AIK-STD-13 PR4）：生产方显式声明——现有身份 id 异或新建身份壳，二选一。
 */
public record MaterializationTarget(
    Long targetIdentityId,
    @Valid NewIdentitySpec newIdentity
) {
    public void validate() {
        boolean hasExisting = targetIdentityId != null;
        boolean hasNew = newIdentity != null;
        if (hasExisting == hasNew) {
            throw new IllegalArgumentException("物化目标须二选一：targetIdentityId 或 newIdentity");
        }
    }
}
```

- [ ] **Step 4: 跑确认通过** — `mvn -q test -Dtest=MaterializationTargetTest`（PASS 4）。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/NewIdentitySpec.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/MaterializationTarget.java \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/MaterializationTargetTest.java
git commit -m "feat(aikstd13/PR4): 物化目标声明（现有身份 异或 新建身份壳，二选一校验）"
```

---

## Task 3: `classifyCandidate` 接 PR3 路由分派计划（`ReviewAssignmentPlan`）

**Files:**
- Create: `.../knowledge/ReviewAssignmentPlan.java`
- Modify: `.../knowledge/KnowledgeVersionService.java`（classifyCandidate 加重载 + ReviewAssignment 创建分支）

> **零回归原则:** 既有 `classifyCandidate(identityId, request)` 保留，委托新重载 `classifyCandidate(identityId, request, null)`；plan==null 时 ReviewAssignment 创建逻辑与现状逐字节一致（assignedTo=actor 单行）。新行为（路由分派）由 Task 4 集成测试覆盖。

- [ ] **Step 1: 建 `ReviewAssignmentPlan`**

```java
package com.medkernel.engine.knowledge;

import java.util.List;

/**
 * 候选审核分派计划（AIK-STD-13 PR4）：物化时据 PR3 会签路由决策列出应分派的审核角色码。
 *
 * <p>{@code reviewerRoleCodes} 为去重后的角色码（归口 ∪ 领域异于归口时）；空/ null 表示沿用默认（提交人单行）。
 */
public record ReviewAssignmentPlan(List<String> reviewerRoleCodes) {
    public ReviewAssignmentPlan {
        reviewerRoleCodes = reviewerRoleCodes == null ? List.of() : List.copyOf(reviewerRoleCodes);
    }

    public boolean isEmpty() {
        return reviewerRoleCodes.isEmpty();
    }
}
```

- [ ] **Step 2: 改 `classifyCandidate`**

(2a) 既有签名改为委托：
```java
    public KnowledgeCandidateResponse classifyCandidate(Long identityId, KnowledgeVersionCreateRequest request) {
        return classifyCandidate(identityId, request, null);
    }

    public KnowledgeCandidateResponse classifyCandidate(Long identityId, KnowledgeVersionCreateRequest request,
                                                        ReviewAssignmentPlan assignmentPlan) {
```
（原方法体并入新重载；DUPLICATE 早返回分支不变。）

(2b) 把现有单行 ReviewAssignment 创建（~378-394）替换为分支：
```java
        if (assignmentPlan != null && !assignmentPlan.isEmpty()) {
            for (String reviewerRole : assignmentPlan.reviewerRoleCodes()) {
                reviewAssignmentRepository.save(new ReviewAssignment(
                    null, tenantId, orgPath, classification.id(), identityId, candidate.id(),
                    reviewerRole, CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
                    null, null, null, null, now, actor, now, actor));
            }
        } else {
            reviewAssignmentRepository.save(new ReviewAssignment(
                null, tenantId, orgPath, classification.id(), identityId, candidate.id(),
                actor, CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
                null, null, null, null, now, actor, now, actor));
        }
```

- [ ] **Step 3: 跑既有知识版本测试确认零回归**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeVersionServiceTest,KnowledgeVersionControllerSecurityTest`（若类名不同，执行时 `ls src/test/java/com/medkernel/engine/knowledge/*Version*Test*` 核对）。
Expected: PASS（既有 null 路径行为不变）。

- [ ] **Step 4: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/ReviewAssignmentPlan.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java
git commit -m "feat(aikstd13/PR4): classifyCandidate 接路由分派计划（plan→多角色分派 / null 零回归）"
```

---

## Task 4: `MaterializingCandidateIntake`（替换桩）+ 端口签名升级

**Files:**
- Modify: `.../production/KnowledgeCandidateIntake.java`（端口签名加 target + routing）
- Delete: `.../production/StagingCandidateIntake.java`
- Create: `.../production/MaterializingCandidateIntake.java`
- Test: `.../production/MaterializingCandidateIntakeTest.java`

- [ ] **Step 1: 升级端口签名**

```java
package com.medkernel.engine.knowledge.production;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 候选入既有版本/审核链的接收端口（AIK-STD-13，FR-3）。
 *
 * <p>PR4 起：真实物化——解析源 FK + 目标身份 → 经 {@code KnowledgeVersionService} 落版本/审核链，
 * 据 PR3 路由决策建会签分派；返回真实物化版本引用（供血缘回溯）。
 */
public interface KnowledgeCandidateIntake {

    String intake(KnowledgeProductionJob job, KnowledgeAssetEnvelope candidate,
                  MaterializationTarget target, com.medkernel.engine.knowledge.production.ReviewRoutingDecision routing);
}
```
（`ReviewRoutingDecision` 在 `production` 包内，import 简名即可；上面写全名仅示意。）

- [ ] **Step 2: 删桩 + 写失败测试**

删 `StagingCandidateIntake.java`。写 `MaterializingCandidateIntakeTest.java`（mock `KnowledgeVersionService`/`KnowledgeIdentityRepository`/`SourceReferenceResolver`，断言：现有身份路径调 classifyCandidate 带计划、新建身份 find-or-create、GENERAL 单角色计划、源解析失败传播拒收）：

```java
package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeApiContext;
import com.medkernel.engine.knowledge.KnowledgeCandidateResponse;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.ResolvedSource;
import com.medkernel.engine.knowledge.ReviewAssignmentPlan;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceReferenceResolver;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

class MaterializingCandidateIntakeTest {

    private static final String TENANT = "tenant-1";

    private KnowledgeVersionService versionService;
    private KnowledgeIdentityRepository identities;
    private SourceReferenceResolver sourceResolver;
    private MaterializingCandidateIntake intake;

    @BeforeEach
    void setUp() {
        versionService = mock(KnowledgeVersionService.class);
        identities = mock(KnowledgeIdentityRepository.class);
        sourceResolver = mock(SourceReferenceResolver.class);
        intake = new MaterializingCandidateIntake(versionService, identities, sourceResolver);
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant(TENANT), "user-001"));
        when(sourceResolver.resolve(eq(TENANT), any())).thenReturn(new ResolvedSource(7L, 9L, "root/0"));
        when(versionService.classifyCandidate(any(), any(), any()))
            .thenReturn(mock(KnowledgeCandidateResponse.class));
    }

    private KnowledgeAssetEnvelope envelope() {
        String payload = "受控候选正文";
        return new KnowledgeAssetEnvelope(VersionedAssetType.KNOWLEDGE, "discovery:SRC-1:v1:root/0", "二甲双胍",
            "run-1", List.of(new AssetSourceRef("SRC-1:v1:root/0", SourceAuthorityLevel.A_REGULATION)),
            SourceAuthorityLevel.A_REGULATION, null, null, KnowledgeRiskLevel.HIGH, TENANT,
            Sha256ContentHash.sha256(payload, "x"), payload, AssetVersionStatus.DRAFT);
    }

    private KnowledgeProductionJob overlayJob() {
        return new KnowledgeProductionJob(1L, TENANT, "job-1", "run-1", VersionedAssetType.KNOWLEDGE,
            KnowledgeProducer.MANUAL, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.PHARMACY, null,
            ProductionJobStatus.RUNNING, 0, null, java.time.Instant.now(), "u", java.time.Instant.now(), "u", "t");
    }

    @Test
    void materializesExistingIdentityWithRoutedAssignmentPlan() {
        when(identities.findByTenantIdAndId(TENANT, 5L)).thenReturn(Optional.of(mock(KnowledgeIdentity.class)));
        ReviewRoutingDecision routing = new ReviewRoutingDecision(
            RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.MEDICATION_SAFETY_USER, true, KnowledgeDomain.PHARMACY);

        intake.intake(overlayJob(), envelope(), new MaterializationTarget(5L, null), routing);

        ArgumentCaptor<ReviewAssignmentPlan> plan = ArgumentCaptor.forClass(ReviewAssignmentPlan.class);
        verify(versionService).classifyCandidate(eq(5L), any(KnowledgeVersionCreateRequest.class), plan.capture());
        assertThat(plan.getValue().reviewerRoleCodes())
            .containsExactlyInAnyOrder(RoleCode.KNOWLEDGE_GOVERNOR.code(), RoleCode.MEDICATION_SAFETY_USER.code());
    }

    @Test
    void findsOrCreatesNewIdentityShell() {
        when(identities.findByTenantIdAndIdentityCode(TENANT, "KN-MET")).thenReturn(Optional.empty());
        KnowledgeIdentity created = new KnowledgeIdentity(42L, TENANT, "KN-MET", KnowledgeDomain.GUIDELINE,
            "二甲双胍", null, null, KnowledgeIdentityStatus.ACTIVE, null, java.time.Instant.now(), "u",
            java.time.Instant.now(), "u");
        when(identities.save(any())).thenReturn(created);
        ReviewRoutingDecision routing = new ReviewRoutingDecision(
            RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.KNOWLEDGE_GOVERNOR, false, KnowledgeDomain.GENERAL);

        intake.intake(overlayJob(), envelope(),
            new MaterializationTarget(null, new NewIdentitySpec(KnowledgeDomain.GUIDELINE, "二甲双胍", "KN-MET")),
            routing);

        verify(identities).save(any(KnowledgeIdentity.class));
        ArgumentCaptor<ReviewAssignmentPlan> plan = ArgumentCaptor.forClass(ReviewAssignmentPlan.class);
        verify(versionService).classifyCandidate(eq(42L), any(), plan.capture());
        // GENERAL：归口==领域，去重后单角色
        assertThat(plan.getValue().reviewerRoleCodes()).containsExactly(RoleCode.KNOWLEDGE_GOVERNOR.code());
    }
}
```

- [ ] **Step 3: 跑确认失败** — `mvn -q test -Dtest=MaterializingCandidateIntakeTest`（编译失败：类不存在 / classifyCandidate 三参重载来自 Task 3）。

- [ ] **Step 4: 建 `MaterializingCandidateIntake`**

```java
package com.medkernel.engine.knowledge.production;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeVersionCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.ResolvedSource;
import com.medkernel.engine.knowledge.ReviewAssignmentPlan;
import com.medkernel.engine.knowledge.SourceReferenceResolver;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.RequestContext;

/**
 * 候选真实物化实现（AIK-STD-13 PR4，替换 PR1 暂存桩）。
 *
 * <p>解析受控源 FK（{@link SourceReferenceResolver}）+ 目标知识身份（现有 / find-or-create 身份壳）→ 构造标准版本请求
 * → 经 {@link KnowledgeVersionService#classifyCandidate} 落版本/审核链 + 据 PR3 路由决策建会签分派；返回真实物化版本引用。
 * 仅覆盖可解析受控源（discovery-origin），解析不出经 resolver 诚实拒收（铁律 #1）。
 */
@Component
public class MaterializingCandidateIntake implements KnowledgeCandidateIntake {

    private static final int DEFAULT_REVIEW_CYCLE_MONTHS = 12;

    private final KnowledgeVersionService versionService;
    private final KnowledgeIdentityRepository identityRepository;
    private final SourceReferenceResolver sourceResolver;

    public MaterializingCandidateIntake(KnowledgeVersionService versionService,
                                        KnowledgeIdentityRepository identityRepository,
                                        SourceReferenceResolver sourceResolver) {
        this.versionService = versionService;
        this.identityRepository = identityRepository;
        this.sourceResolver = sourceResolver;
    }

    @Override
    public String intake(KnowledgeProductionJob job, KnowledgeAssetEnvelope candidate,
                         MaterializationTarget target, ReviewRoutingDecision routing) {
        target.validate();
        String tenantId = job.tenantId();
        String actor = RequestContext.currentUserId().orElse(null);
        Long identityId = resolveIdentity(tenantId, target, actor);
        ResolvedSource source = sourceResolver.resolve(tenantId, candidate.sources().get(0).sourceRef());
        String versionNo = candidate.versionLabel() == null || candidate.versionLabel().isBlank()
            ? "kv-" + candidate.contentHash().substring(0, 12) : candidate.versionLabel();

        KnowledgeVersionCreateRequest request = new KnowledgeVersionCreateRequest(
            null, RequestContext.currentTraceId(), tenantId, null, null, null, null, null, null, actor,
            List.of(), null, versionNo, candidate.versionLabel(), source.sourceDocumentId(),
            source.sourceVersionId(), candidate.payload(), source.anchorPath(), candidate.riskLevel(),
            candidate.gradeQuality() == null ? GradeEvidenceQuality.VERY_LOW : candidate.gradeQuality(),
            candidate.gradeStrength(), DEFAULT_REVIEW_CYCLE_MONTHS);

        versionService.classifyCandidate(identityId, request, assignmentPlan(routing));
        return "kv:" + identityId + ":" + versionNo;
    }

    private Long resolveIdentity(String tenantId, MaterializationTarget target, String actor) {
        if (target.targetIdentityId() != null) {
            return identityRepository.findByTenantIdAndId(tenantId, target.targetIdentityId())
                .map(KnowledgeIdentity::id)
                .orElseThrow(() -> ApiException.notFound("知识身份 id=" + target.targetIdentityId()));
        }
        NewIdentitySpec spec = target.newIdentity();
        return identityRepository.findByTenantIdAndIdentityCode(tenantId, spec.identityCode())
            .map(KnowledgeIdentity::id)
            .orElseGet(() -> {
                Instant now = Instant.now();
                KnowledgeIdentity saved = identityRepository.save(new KnowledgeIdentity(
                    null, tenantId, spec.identityCode(), spec.domain(), spec.subject(), null, null,
                    KnowledgeIdentityStatus.ACTIVE, null, now, actor, now, actor));
                return saved.id();
            });
    }

    private ReviewAssignmentPlan assignmentPlan(ReviewRoutingDecision routing) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add(routing.ownerReviewerRole().code());
        roles.add(routing.domainReviewerRole().code());
        return new ReviewAssignmentPlan(List.copyOf(roles));
    }
}
```

（注：`KnowledgeVersionCreateRequest` 构造参数顺序须与实体逐一对齐——执行时按 Task 关键事实里的字段清单核对实参位置；`RoleCode.code()` 取串码方法名若不同按实际替换。）

- [ ] **Step 5: 跑确认通过** — `mvn -q test -Dtest=MaterializingCandidateIntakeTest`（PASS）。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeCandidateIntake.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/MaterializingCandidateIntake.java \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/MaterializingCandidateIntakeTest.java
git rm medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/StagingCandidateIntake.java
git commit -m "feat(aikstd13/PR4): 候选真实物化 intake 替换暂存桩（源 FK+身份解析→版本链+路由分派）"
```

---

## Task 5: `submitCandidate` + 控制器接入 target

**Files:**
- Create: `.../production/CandidateSubmissionRequest.java`
- Modify: `.../production/KnowledgeProductionOrchestrationService.java`、`KnowledgeProductionController.java`
- Modify（测试）: `KnowledgeProductionOrchestrationServiceTest.java`、`KnowledgeProductionControllerSecurityTest.java`

- [ ] **Step 1: 改服务测试（submit 传 target + intake 4 参）**

`KnowledgeProductionOrchestrationServiceTest`：`candidateIntake` mock 改 `intake(any(),any(),any(),any())`；`submitCandidate*` 调用补 target 实参（用 `new MaterializationTarget(5L, null)`）。示例（`submitCandidateValidatesIsolatesCountsAndAudits`）：
```java
        when(candidateIntake.intake(any(), any(), any(), any())).thenReturn("kv:5:run-1");
        CandidateSubmissionResponse resp = service.submitCandidate("job-1",
            envelope(CUSTOMER, VersionedAssetType.KNOWLEDGE), new MaterializationTarget(5L, null));
        assertThat(resp.candidateRef()).isEqualTo("kv:5:run-1");
```
拒绝类测试（隔离/类型/越租户/无源）在 intake 前抛出，补 target 实参即可（不影响断言）。

- [ ] **Step 2: 跑确认失败** — `mvn -q test -Dtest=KnowledgeProductionOrchestrationServiceTest`（编译失败：submitCandidate 三参 / intake 四参）。

- [ ] **Step 3: 改服务 + 控制器 + DTO**

`CandidateSubmissionRequest.java`：
```java
package com.medkernel.engine.knowledge.production;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 提交候选请求（AIK-STD-13 PR4）：候选信封 + 物化目标身份声明。
 */
public record CandidateSubmissionRequest(
    @NotNull @Valid KnowledgeAssetEnvelope candidate,
    @NotNull @Valid MaterializationTarget target
) {
}
```

`KnowledgeProductionOrchestrationService.submitCandidate`：签名加 `MaterializationTarget target`；末尾 intake 调用改：
```java
        ReviewRoutingDecision routing = reviewRouter.resolve(
            job.targetPipeline(), job.domain(), candidate.riskLevel());
        String candidateRef = candidateIntake.intake(job, candidate, target, routing);
```
（删除原先 `String candidateRef = candidateIntake.intake(job, candidate);` 那行；血缘 save 与计数不变，仍用 candidateRef + candidate.riskLevel()。返回 `new CandidateSubmissionResponse(candidateRef, routing)`。）

`KnowledgeProductionController.submitCandidate`：
```java
    public ApiResult<CandidateSubmissionResponse> submitCandidate(@PathVariable String jobCode,
                                             @Valid @RequestBody CandidateSubmissionRequest request) {
        return ApiResult.ok(service.submitCandidate(jobCode, request.candidate(), request.target()));
    }
```

- [ ] **Step 4: 改控制器安全测试 body**

`KnowledgeProductionControllerSecurityTest`：`CANDIDATE_BODY` 包成 `{"candidate":{...原信封...},"target":{"targetIdentityId":5}}`；submit mock 改 `service.submitCandidate(anyString(), any(), any())`。

- [ ] **Step 5: 跑确认通过** — `mvn -q test -Dtest=KnowledgeProductionOrchestrationServiceTest,KnowledgeProductionControllerSecurityTest`（PASS）。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/ \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/
git commit -m "feat(aikstd13/PR4): submitCandidate 接物化目标（控制器 body 含 target，候选真实物化入审核链）"
```

---

## Task 6: 全量验证 + 卡片 + 接力 + PR

**Files:** Modify `docs/cards/wave2/AIK-STD-13.md`、`docs/_HANDOFF.md`

- [ ] **Step 1: 全量后端** — `cd medkernel-backend && mvn test`（BUILD SUCCESS；基线 2507 + 新增；含 `MaterializingCandidateIntake`/`SourceReferenceResolver` 真实落库可加 H2 集成测试核 version+classification+assignment 真实生成——若加，置 `.../production/CandidateMaterializationIntegrationTest.java`，断言 `reviewAssignmentRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc` 返回路由角色行）。
- [ ] **Step 2: 五方言 Flyway smoke** — `mvn -q test -Dtest=FlywayMultiDialectSmokeTest`（本卡无新迁移，验既有基线不破）。
- [ ] **Step 3: 四门禁 changed + git diff --check**（仓库根）：
```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
bash scripts/check-comment-zh.sh
git diff --check
```
- [ ] **Step 4: 前端目录** — `cd frontend && npx vitest run src/shared/config/productCatalog.test.ts`（控制器端点数不变，应无漂移）。
- [ ] **Step 5: 卡片 + 接力**：`AIK-STD-13.md` 实现进度加 PR4 节（FR-3 物化 + FR-6 路由→分派落地，仅 discovery）；FR-3 勾「✅（PR4，discovery）」。`_HANDOFF.md` 顶部加 PR4 段（分支、已实现待合、验证数据、下一步＝MANUAL/无 FK 源物化 + 双签强制 + AIK-STD-12 审核台 AI 标识/模板）。
- [ ] **Step 6: 提交 + 推送 + 开 PR（合并待逐 PR 授权，勿自动合）**

```bash
git add docs/cards/wave2/AIK-STD-13.md docs/_HANDOFF.md
git commit -m "docs(aikstd13/PR4): 卡片验收勾 + 接力更新（候选真实物化 discovery 闭环）"
git push -u origin claude/wave2-p2b-aikstd13-pr4-candidate-materialization
gh pr create --base main --title "feat(wave2/P2-B): AIK-STD-13 PR4 候选真实物化（discovery→版本/审核链 + PR3 路由分派）" --body "..."
```

---

## 自检（spec 覆盖）
- 源 FK 解析（机械 B0 + 诚实拒收）→ Task 1。✅
- 生产方显式声明目标身份（现有 异或 新建壳）→ Task 2 + Task 4 resolveIdentity。✅
- classifyCandidate 接 PR3 路由分派（归口∪领域；null 零回归）→ Task 3 + Task 4 assignmentPlan。✅
- 替换暂存桩、真实物化入版本/审核链 → Task 4。✅
- submitCandidate 加 target、控制器 body 升级、candidateRef 落真实引用 → Task 5。✅
- 无新表、不碰 P6、仅 discovery、双签不强制（建两行分派）→ 全程。✅
- GRADE 缺失保守取 VERY_LOW（不夸大）、身份壳 ACTIVE 权威在版本层把关 → Task 4。✅

**类型一致性核对:** `SourceReferenceResolver.resolve(tenant, sourceRef)→ResolvedSource(sourceDocumentId,sourceVersionId,anchorPath)`；`MaterializationTarget(targetIdentityId, newIdentity).validate()`；`ReviewAssignmentPlan(reviewerRoleCodes)`；`classifyCandidate(identityId, request, plan)`；`intake(job, candidate, target, routing)→String`；`submitCandidate(jobCode, candidate, target)→CandidateSubmissionResponse(candidateRef, routing)`；`CandidateSubmissionRequest(candidate, target)` 跨任务一致。
