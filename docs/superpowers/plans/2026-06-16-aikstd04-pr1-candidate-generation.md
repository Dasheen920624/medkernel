# AIK-STD-04 PR1 候选生成（编排核心 + 类型无关生成器 + 全 5 类）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从 AIK-STD-02 解析后的带锚点来源片段，确定性（B0）生成规则/路径/推荐/指标/随访五类知识候选信封，经既有 AIK-STD-13 job+intake 落审核链。

**Architecture:** 新增 `engine.knowledge.production.generation` 子包：`SourceCandidateGenerator`（纯转换，AIK-STD-12 模板桩 + 锚点绑定 + 真 SHA-256）+ `CandidateGenerationOrchestrationService`（逐资产类型建 MANUAL 生产 job → 喂既有 `submitCandidate`）。零新表、零迁移、零新权限码。设计见 [spec](../specs/2026-06-16-aikstd04-candidate-generation-design.md)。

**Tech Stack:** Spring Boot + Java records + Jackson ObjectMapper + JUnit5/AssertJ/Mockito + 真实 H2 集成；前端 productCatalog.test.ts 守端点漂移。

---

## File Map

- Create `engine/knowledge/production/generation/CandidateGenerationRequest.java`：请求 DTO（sourceVersionId + targetPipeline + domain + items）。
- Create `engine/knowledge/production/generation/GenerationItem.java`：单项（assetType + target）。
- Create `engine/knowledge/production/generation/GenerationSummary.java` + `GeneratedCandidate.java` + `SkippedType.java`：结果 DTO。
- Create `engine/knowledge/production/generation/SourceCandidateGenerator.java`：B0 模板桩生成器（@Component）。
- Create `engine/knowledge/production/generation/CandidateGenerationOrchestrationService.java`：编排（@Service）。
- Modify `engine/knowledge/production/KnowledgeProductionController.java`：加 `POST /generate` 端点 + 注入编排服务。
- Test `SourceCandidateGeneratorTest.java` / `CandidateGenerationOrchestrationServiceTest.java` / `KnowledgeProductionControllerSecurityTest.java`（增量）/ `CandidateGenerationIntegrationTest.java`。
- Modify `architecture/ServiceContractCatalog`（如登记新服务）+ 产品目录重生成 + `_HANDOFF.md` + 卡 `AIK-STD-04.md`。

**已读实关键契约**（不得偏离）：
- `KnowledgeAssetEnvelope(assetType, assetIdentity, subject, versionLabel, List<AssetSourceRef> sources, SourceAuthorityLevel trustLevel, GradeEvidenceQuality gradeQuality(可空), GradeRecommendationStrength gradeStrength(可空), KnowledgeRiskLevel riskLevel, String orgScope, String contentHash, String payload, AssetVersionStatus lifecycleStatus)`。
- 校验闸要求：sources≥1（各 sourceRef 非空 + authorityLevel 非空）、trustLevel/riskLevel 非空、payload 非空、lifecycleStatus∈{DRAFT,IN_REVIEW}、contentHash 64 位小写 hex 且 `== Sha256ContentHash.sha256(payload)`。
- `AssetSourceRef(String sourceRef, SourceAuthorityLevel authorityLevel)`；**sourceRef 必须 `"sourceCode:versionNo:anchorPath"`**（intake 经 `SourceReferenceResolver` 反解）。
- `submitCandidate(String jobCode, KnowledgeAssetEnvelope, MaterializationTarget)` 强约束 `candidate.assetType()==job.assetType()` 且 `candidate.orgScope()==job.tenantId()`。
- `createJob(ProductionJobRequest(String sourceScope非空, VersionedAssetType assetType, KnowledgeProducer producer, TargetPipeline targetPipeline, KnowledgeDomain domain, String modelStrategy可空))` → `ProductionJobResponse`（有 `jobCode()`）。
- `SourceFragment(id, tenantId, sourceVersionId, anchorPath, anchorLabel, textExcerpt, contentHash, createdAt)`；`SourceVersion(id, tenantId, sourceDocumentId, versionNo, …)`；`SourceDocument(id, tenantId, sourceCode, sourceType, authorityLevel, …, title, …)`。
- 仓储：`SourceVersionRepository.findByTenantIdAndId(tenant,id)`、`SourceDocumentRepository.findByTenantIdAndId(tenant,id)`、`SourceFragmentRepository.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(tenant, versionId)`。
- `Sha256ContentHash.sha256(String content, String emptyMsg)`（包 `com.medkernel.shared.hash`）。
- 模板：`ProfessionalAssetTemplateRegistry.findByAssetTypeAndDomain(VersionedAssetType, engine.knowledge.KnowledgeDomain)`；structural 模板 domain 传 `null`。`TemplateSection(key,label,required,hint)`。
- ⚠️ **真实性门禁**：后端 Javadoc 禁词 `占位/placeholder/模拟/仿真/演示`——注释用「留白/待编著/待填充」。

---

## Task 1: 生成相关 DTO

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/generation/CandidateGenerationRequest.java`
- Create: `.../generation/GenerationItem.java`
- Create: `.../generation/GenerationSummary.java`
- Create: `.../generation/GeneratedCandidate.java`
- Create: `.../generation/SkippedType.java`

- [ ] **Step 1: 写 DTO（无测试，纯 record，随 Task 2/3 测试覆盖）**

`GenerationItem.java`:
```java
package com.medkernel.engine.knowledge.production.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.versioning.VersionedAssetType;

/** 单类候选生成项：产出资产类型 + 物化目标身份（AIK-STD-04）。 */
public record GenerationItem(
    @NotNull VersionedAssetType assetType,
    @NotNull @Valid MaterializationTarget target
) {
}
```

`CandidateGenerationRequest.java`:
```java
package com.medkernel.engine.knowledge.production.generation;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.TargetPipeline;

/**
 * 从受控来源生成知识候选请求（AIK-STD-04）。
 *
 * <p>从一个解析后的来源版本，按申报的资产类型清单逐类生成候选；目标管道与领域显式申报，供双形态隔离与会签路由。
 */
public record CandidateGenerationRequest(
    @NotNull Long sourceVersionId,
    @NotNull TargetPipeline targetPipeline,
    @NotNull KnowledgeDomain domain,
    @NotEmpty @Valid List<GenerationItem> items
) {
}
```

`GeneratedCandidate.java`:
```java
package com.medkernel.engine.knowledge.production.generation;

import com.medkernel.engine.knowledge.production.ReviewRoutingDecision;
import com.medkernel.engine.versioning.VersionedAssetType;

/** 已生成并提交的候选结果（AIK-STD-04）：资产类型 + 归属生产 job + 候选引用 + 会签路由。 */
public record GeneratedCandidate(
    VersionedAssetType assetType,
    String jobCode,
    String candidateRef,
    ReviewRoutingDecision routing
) {
}
```

`SkippedType.java`:
```java
package com.medkernel.engine.knowledge.production.generation;

import com.medkernel.engine.versioning.VersionedAssetType;

/** 未生成的资产类型与诚实原因（AIK-STD-04，铁律 #1 无源不生成）。 */
public record SkippedType(VersionedAssetType assetType, String reason) {
}
```

`GenerationSummary.java`:
```java
package com.medkernel.engine.knowledge.production.generation;

import java.util.List;

/** 候选生成汇总（AIK-STD-04）：已生成候选 + 诚实跳过项。 */
public record GenerationSummary(
    List<GeneratedCandidate> candidates,
    List<SkippedType> skipped
) {
    public GenerationSummary {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
}
```

- [ ] **Step 2: 编译通过**

Run: `cd medkernel-backend && mvn -q -o compile`
Expected: BUILD SUCCESS（仅新增 record）。

- [ ] **Step 3: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/generation
git commit -m "feat(aikstd04/PR1): 候选生成 DTO（请求/项/汇总/已生成/跳过）"
```

## Task 2: SourceCandidateGenerator（B0 模板桩生成器，类型无关）

**Files:**
- Create: `.../generation/SourceCandidateGenerator.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/generation/SourceCandidateGeneratorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.knowledge.production.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.junit.jupiter.api.Test;

class SourceCandidateGeneratorTest {

    private final SourceCandidateGenerator generator =
        new SourceCandidateGenerator(new ProfessionalAssetTemplateRegistry(), new ObjectMapper());

    private SourceDocument document() {
        return new SourceDocument(7L, "t-1", "GL-2024", SourceType.GUIDELINE, SourceAuthorityLevel.B,
            "卫健委指南", "高血压基层诊疗指南", "卫健委", "CC-BY", "zh", Instant.EPOCH, "sys", Instant.EPOCH, "sys");
    }

    private SourceVersion version() {
        return new SourceVersion(9L, "t-1", 7L, "v1", Instant.EPOCH,
            "a".repeat(64), "file://gl", "zh", Instant.EPOCH, "sys");
    }

    private List<SourceFragment> fragments() {
        return List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "总则", "血压≥140/90 诊断高血压。",
                "b".repeat(64), Instant.EPOCH),
            new SourceFragment(2L, "t-1", 9L, "section-2", "用药", "首选 CCB 或 ACEI。",
                "c".repeat(64), Instant.EPOCH));
    }

    @Test
    void generatesRuleDraftStubWithRealAnchorsAndHash() {
        KnowledgeAssetEnvelope envelope = generator.generate(
            "t-1", document(), version(), fragments(), VersionedAssetType.RULE, "identity:42");

        assertThat(envelope.assetType()).isEqualTo(VersionedAssetType.RULE);
        assertThat(envelope.assetIdentity()).isEqualTo("identity:42");
        assertThat(envelope.subject()).isEqualTo("高血压基层诊疗指南");
        assertThat(envelope.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(envelope.trustLevel()).isEqualTo(SourceAuthorityLevel.B);
        assertThat(envelope.orgScope()).isEqualTo("t-1");
        // sources≥1，第一条须 intake 可解析格式 sourceCode:versionNo:anchorPath
        assertThat(envelope.sources()).hasSize(2);
        assertThat(envelope.sources().get(0).sourceRef()).isEqualTo("GL-2024:v1:section-1");
        assertThat(envelope.sources().get(0).authorityLevel()).isEqualTo(SourceAuthorityLevel.B);
        // 逻辑字段留白不伪造；来源摘要真实
        assertThat(envelope.payload()).contains("待编著").contains("血压≥140/90");
        // contentHash 真实等于 sha256(payload)
        assertThat(envelope.contentHash()).matches("^[0-9a-f]{64}$");
        assertThat(com.medkernel.shared.hash.Sha256ContentHash.sha256(envelope.payload(), "x"))
            .isEqualTo(envelope.contentHash());
    }

    @Test
    void generatesEachOfFiveAssetTypes() {
        for (VersionedAssetType type : List.of(VersionedAssetType.RULE, VersionedAssetType.PATHWAY,
            VersionedAssetType.RECOMMENDATION, VersionedAssetType.EVALUATION, VersionedAssetType.FOLLOWUP)) {
            KnowledgeAssetEnvelope envelope = generator.generate(
                "t-1", document(), version(), fragments(), type, "identity:1");
            assertThat(envelope.assetType()).isEqualTo(type);
            assertThat(envelope.sources()).isNotEmpty();
            assertThat(envelope.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        }
    }

    @Test
    void rejectsWhenNoTemplate() {
        // KNOWLEDGE×null 无 structural 模板（仅领域型有 domain）→ 诚实抛错
        assertThatThrownBy(() -> generator.generate(
            "t-1", document(), version(), fragments(), VersionedAssetType.PACKAGE, "identity:1"))
            .isInstanceOf(com.medkernel.shared.api.error.ApiException.class);
    }
}
```

- [ ] **Step 2: Verify RED**

Run: `cd medkernel-backend && mvn -q -o -Dtest=SourceCandidateGeneratorTest test`
Expected: FAIL（`SourceCandidateGenerator` 不存在 / 编译失败）。

- [ ] **Step 3: 实现生成器**

```java
package com.medkernel.engine.knowledge.production.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.ProfessionalAssetTemplate;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.factory.TemplateSection;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 受控来源 → 知识候选信封的确定性（B0）生成器（AIK-STD-04，FR-1~5）。
 *
 * <p>类型无关：按 {@link VersionedAssetType} 取 AIK-STD-12 结构模板骨架，把来源锚点摘要绑入信封来源与
 * {@code sourceEvidence}，逻辑章节统一留白（{@code 待编著}）待人工/模型按真实来源填充——B0 不臆造医学逻辑
 * （铁律 #1）。产出恒候选态 {@link AssetVersionStatus#DRAFT}，{@code contentHash} 为 payload 真实 SHA-256。
 */
@Component
public class SourceCandidateGenerator {

    private final ProfessionalAssetTemplateRegistry templateRegistry;
    private final ObjectMapper json;

    public SourceCandidateGenerator(ProfessionalAssetTemplateRegistry templateRegistry, ObjectMapper json) {
        this.templateRegistry = templateRegistry;
        this.json = json;
    }

    /**
     * 生成一条某资产类型的候选信封。{@code fragments} 须非空（由编排层保证；无源不生成在编排层拦）。
     */
    public KnowledgeAssetEnvelope generate(String tenantId, SourceDocument document, SourceVersion version,
                                           List<SourceFragment> fragments, VersionedAssetType assetType,
                                           String assetIdentity) {
        ProfessionalAssetTemplate template = templateRegistry.findByAssetTypeAndDomain(assetType, null)
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                "无结构模板，不生成候选：assetType=" + assetType));

        String payload = buildPayload(template, fragments);
        String contentHash = Sha256ContentHash.sha256(payload, "候选内容不能为空");

        List<AssetSourceRef> sources = new ArrayList<>();
        for (SourceFragment fragment : fragments) {
            sources.add(new AssetSourceRef(
                document.sourceCode() + ":" + version.versionNo() + ":" + fragment.anchorPath(),
                document.authorityLevel()));
        }

        return new KnowledgeAssetEnvelope(
            assetType, assetIdentity, document.title(), "draft-from-" + version.versionNo(),
            sources, document.authorityLevel(), null, null, KnowledgeRiskLevel.MEDIUM, tenantId,
            contentHash, payload, AssetVersionStatus.DRAFT);
    }

    /** 组确定性 payload：模板章节留白 + 来源锚点摘要（保序，便于真实 hash 复算）。 */
    private String buildPayload(ProfessionalAssetTemplate template, List<SourceFragment> fragments) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("template", template.professionCode());
        Map<String, Object> sections = new LinkedHashMap<>();
        for (TemplateSection section : template.sections()) {
            sections.put(section.key(), "待编著（结构：" + section.label() + "）");
        }
        root.put("sections", sections);
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (SourceFragment fragment : fragments) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("anchorPath", fragment.anchorPath());
            ref.put("excerpt", fragment.textExcerpt());
            evidence.add(ref);
        }
        root.put("sourceEvidence", evidence);
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选内容序列化失败");
        }
    }
}
```

- [ ] **Step 4: Verify GREEN**

Run: `cd medkernel-backend && mvn -q -o -Dtest=SourceCandidateGeneratorTest test`
Expected: PASS（3 测试）。

- [ ] **Step 5: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/generation/SourceCandidateGenerator.java medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/generation/SourceCandidateGeneratorTest.java
git commit -m "feat(aikstd04/PR1): SourceCandidateGenerator B0 模板桩 + 锚点绑定 + 真 hash"
```

## Task 3: CandidateGenerationOrchestrationService（编排）

**Files:**
- Create: `.../generation/CandidateGenerationOrchestrationService.java`
- Test: `.../generation/CandidateGenerationOrchestrationServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.knowledge.production.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.production.CandidateSubmissionResponse;
import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.KnowledgeProductionOrchestrationService;
import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.knowledge.production.NewIdentitySpec;
import com.medkernel.engine.knowledge.production.ProductionJobRequest;
import com.medkernel.engine.knowledge.production.ProductionJobResponse;
import com.medkernel.engine.knowledge.production.ReviewRoutingDecision;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateGenerationOrchestrationServiceTest {

    private final SourceVersionRepository versions = mock(SourceVersionRepository.class);
    private final SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
    private final SourceFragmentRepository fragments = mock(SourceFragmentRepository.class);
    private final KnowledgeProductionOrchestrationService production =
        mock(KnowledgeProductionOrchestrationService.class);
    private final SourceCandidateGenerator generator =
        new SourceCandidateGenerator(new ProfessionalAssetTemplateRegistry(), new ObjectMapper());

    private final CandidateGenerationOrchestrationService service =
        new CandidateGenerationOrchestrationService(versions, documents, fragments, generator, production);

    @BeforeEach
    void setTenant() {
        RequestContext.bind(RequestContext.builder().orgScope(OrgScope.tenant("t-1")).userId("u-1").build());
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    private void seedSource() {
        when(versions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(
            new SourceVersion(9L, "t-1", 7L, "v1", Instant.EPOCH, "a".repeat(64), "file://gl", "zh",
                Instant.EPOCH, "sys")));
        when(documents.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(
            new SourceDocument(7L, "t-1", "GL-2024", SourceType.GUIDELINE, SourceAuthorityLevel.B,
                "卫健委指南", "高血压指南", "卫健委", "CC-BY", "zh", Instant.EPOCH, "sys", Instant.EPOCH, "sys")));
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "总则", "血压≥140/90。", "b".repeat(64),
                Instant.EPOCH)));
    }

    private GenerationItem item(VersionedAssetType type) {
        return new GenerationItem(type,
            new MaterializationTarget(null, new NewIdentitySpec(
                com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE, "高血压规则", "RULE-HTN-1")));
    }

    @Test
    void generatesCandidatePerTypeViaSubmitCandidate() {
        seedSource();
        when(production.createJob(any(ProductionJobRequest.class))).thenAnswer(invocation ->
            ProductionJobResponse.from(new com.medkernel.engine.knowledge.production.KnowledgeProductionJob(
                1L, "t-1", "job-x", "s", invocation.<ProductionJobRequest>getArgument(0).assetType(),
                com.medkernel.engine.knowledge.production.KnowledgeProducer.MANUAL,
                TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL, null,
                com.medkernel.engine.knowledge.production.ProductionJobStatus.PENDING, 0, "{}",
                Instant.EPOCH, "sys", Instant.EPOCH, "sys", "trace")));
        when(production.submitCandidate(eq("job-x"), any(), any())).thenReturn(
            new CandidateSubmissionResponse("kv:1:draft-from-v1",
                new ReviewRoutingDecision(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR,
                    RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR, false, KnowledgeDomain.CLINICAL)));

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(item(VersionedAssetType.RULE), item(VersionedAssetType.PATHWAY))));

        assertThat(summary.candidates()).hasSize(2);
        assertThat(summary.candidates().get(0).candidateRef()).isEqualTo("kv:1:draft-from-v1");
        assertThat(summary.skipped()).isEmpty();
        verify(production, org.mockito.Mockito.times(2)).createJob(any());
        verify(production, org.mockito.Mockito.times(2)).submitCandidate(eq("job-x"), any(), any());
    }

    @Test
    void skipsAllWhenSourceHasNoFragments() {
        when(versions.findByTenantIdAndId("t-1", 9L)).thenReturn(Optional.of(
            new SourceVersion(9L, "t-1", 7L, "v1", Instant.EPOCH, "a".repeat(64), "file://gl", "zh",
                Instant.EPOCH, "sys")));
        when(documents.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(
            new SourceDocument(7L, "t-1", "GL-2024", SourceType.GUIDELINE, SourceAuthorityLevel.B,
                "卫健委指南", "高血压指南", "卫健委", "CC-BY", "zh", Instant.EPOCH, "sys", Instant.EPOCH, "sys")));
        when(fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc("t-1", 9L)).thenReturn(List.of());

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            9L, TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.CLINICAL,
            List.of(item(VersionedAssetType.RULE))));

        assertThat(summary.candidates()).isEmpty();
        assertThat(summary.skipped()).hasSize(1);
        assertThat(summary.skipped().get(0).reason()).contains("无源");
        verify(production, never()).createJob(any());
    }
}
```

- [ ] **Step 2: Verify RED**

Run: `cd medkernel-backend && mvn -q -o -Dtest=CandidateGenerationOrchestrationServiceTest test`
Expected: FAIL（服务不存在）。

- [ ] **Step 3: 实现编排服务**

```java
package com.medkernel.engine.knowledge.production.generation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.production.CandidateSubmissionResponse;
import com.medkernel.engine.knowledge.production.KnowledgeProducer;
import com.medkernel.engine.knowledge.production.KnowledgeProductionOrchestrationService;
import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.knowledge.production.ProductionJobRequest;
import com.medkernel.engine.knowledge.production.ProductionJobResponse;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 从受控来源生成知识候选的编排服务（AIK-STD-04，FR-1~5）。
 *
 * <p>载入解析后来源版本的带锚点片段，按申报资产类型逐类各建一个确定性（{@link KnowledgeProducer#MANUAL}/B0）
 * 生产 job，调 {@link SourceCandidateGenerator} 产模板桩候选，喂既有 {@code submitCandidate}（经 AIK-STD-01
 * 校验闸 + §9 双形态隔离守卫 + PR3 会签路由 + intake 物化）。来源无片段则全类型诚实跳过、不建 job（铁律 #1）。
 */
@Service
public class CandidateGenerationOrchestrationService {

    private final SourceVersionRepository versions;
    private final SourceDocumentRepository documents;
    private final SourceFragmentRepository fragments;
    private final SourceCandidateGenerator generator;
    private final KnowledgeProductionOrchestrationService production;

    public CandidateGenerationOrchestrationService(SourceVersionRepository versions,
                                                   SourceDocumentRepository documents,
                                                   SourceFragmentRepository fragments,
                                                   SourceCandidateGenerator generator,
                                                   KnowledgeProductionOrchestrationService production) {
        this.versions = versions;
        this.documents = documents;
        this.fragments = fragments;
        this.generator = generator;
        this.production = production;
    }

    @Transactional
    public GenerationSummary generate(CandidateGenerationRequest request) {
        String tenantId = requireCurrentTenant();
        SourceVersion version = versions.findByTenantIdAndId(tenantId, request.sourceVersionId())
            .orElseThrow(() -> ApiException.notFound("来源版本 id=" + request.sourceVersionId()));
        SourceDocument document = documents.findByTenantIdAndId(tenantId, version.sourceDocumentId())
            .orElseThrow(() -> ApiException.notFound("来源文档 id=" + version.sourceDocumentId()));
        List<SourceFragment> sourceFragments =
            fragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(tenantId, version.id());

        List<GeneratedCandidate> generated = new ArrayList<>();
        List<SkippedType> skipped = new ArrayList<>();

        if (sourceFragments.isEmpty()) {
            for (GenerationItem item : request.items()) {
                skipped.add(new SkippedType(item.assetType(), "来源无锚点片段，无源不生成"));
            }
            return new GenerationSummary(generated, skipped);
        }

        for (GenerationItem item : request.items()) {
            item.target().validate();
            ProductionJobResponse job = production.createJob(new ProductionJobRequest(
                "source-version:" + version.id(), item.assetType(), KnowledgeProducer.MANUAL,
                request.targetPipeline(), request.domain(), null));
            KnowledgeAssetEnvelope envelope = generator.generate(
                tenantId, document, version, sourceFragments, item.assetType(),
                deriveIdentity(item.target()));
            CandidateSubmissionResponse response =
                production.submitCandidate(job.jobCode(), envelope, item.target());
            generated.add(new GeneratedCandidate(
                item.assetType(), job.jobCode(), response.candidateRef(), response.routing()));
        }
        return new GenerationSummary(generated, skipped);
    }

    private String deriveIdentity(MaterializationTarget target) {
        return target.targetIdentityId() != null
            ? "identity:" + target.targetIdentityId()
            : target.newIdentity().identityCode();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || scope.tenantId() == null) {
            throw new ApiException(com.medkernel.shared.api.error.ErrorCode.UNAUTHORIZED, "缺少租户上下文");
        }
        return scope.tenantId();
    }
}
```

> 注：`requireCurrentTenant` / `ProductionJobResponse.jobCode()` / `OrgScope.tenantId()` 须与既有签名一致；落地时若 `ProductionJobResponse` 无 `jobCode()` 访问器或 `RequestContext` 取租户写法不同，照既有 `KnowledgeProductionOrchestrationService.requireCurrentTenant` 同款实现对齐。

- [ ] **Step 4: Verify GREEN**

Run: `cd medkernel-backend && mvn -q -o -Dtest=CandidateGenerationOrchestrationServiceTest test`
Expected: PASS（2 测试）。

- [ ] **Step 5: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/generation/CandidateGenerationOrchestrationService.java medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/generation/CandidateGenerationOrchestrationServiceTest.java
git commit -m "feat(aikstd04/PR1): 候选生成编排（逐类建 MANUAL job → submitCandidate，无源诚实跳过）"
```

## Task 4: 控制器端点 + 安全测试

**Files:**
- Modify: `engine/knowledge/production/KnowledgeProductionController.java`
- Test: `engine/knowledge/production/KnowledgeProductionControllerSecurityTest.java`（增量）

- [ ] **Step 1: 写失败安全测试**

按既有 `KnowledgeProductionControllerSecurityTest` 同款（MockMvc + 权限装配）加两例：
```java
    @Test
    void generateRequiresKnowledgeWrite() throws Exception {
        // 无 knowledge.write → 403
        mockMvc.perform(post("/api/v1/engine/knowledge-production/generate")
                .with(user("viewer").authorities(() -> "knowledge.read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceVersionId\":9,\"targetPipeline\":\"PLATFORM_SOURCE\",\"domain\":\"CLINICAL\",\"items\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void generateAllowsKnowledgeWrite() throws Exception {
        when(orchestration.generate(any())).thenReturn(
            new com.medkernel.engine.knowledge.production.generation.GenerationSummary(
                java.util.List.of(), java.util.List.of()));
        mockMvc.perform(post("/api/v1/engine/knowledge-production/generate")
                .with(user("author").authorities(() -> "knowledge.write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceVersionId\":9,\"targetPipeline\":\"PLATFORM_SOURCE\",\"domain\":\"CLINICAL\",\"items\":[{\"assetType\":\"RULE\",\"target\":{\"targetIdentityId\":1}}]}"))
            .andExpect(status().isOk());
    }
```
> 若既有安全测试用 `@MockBean`/构造注入，须为控制器新依赖 `CandidateGenerationOrchestrationService orchestration` 加 mock，并按既有装配补齐其它已注入 bean。

- [ ] **Step 2: Verify RED**

Run: `cd medkernel-backend && mvn -q -o -Dtest=KnowledgeProductionControllerSecurityTest test`
Expected: FAIL（端点不存在 / 控制器缺依赖）。

- [ ] **Step 3: 加端点**

控制器构造器加 `CandidateGenerationOrchestrationService orchestration` 字段+注入，并加：
```java
    /** 从受控来源生成知识候选（AIK-STD-04）：逐类建 job → 模板桩候选 → 既有审核链。 */
    @PostMapping("/generate")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<com.medkernel.engine.knowledge.production.generation.GenerationSummary> generate(
            @Valid @RequestBody
            com.medkernel.engine.knowledge.production.generation.CandidateGenerationRequest request) {
        return ApiResult.ok(orchestration.generate(request));
    }
```
（import 按既有风格提到文件顶部；此处全限定仅为计划可读。）

- [ ] **Step 4: Verify GREEN**

Run: `cd medkernel-backend && mvn -q -o -Dtest=KnowledgeProductionControllerSecurityTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionController.java medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionControllerSecurityTest.java
git commit -m "feat(aikstd04/PR1): POST /knowledge-production/generate 端点（knowledge.write）"
```

## Task 5: 真实 H2 端到端集成测试

**Files:**
- Test: `.../generation/CandidateGenerationIntegrationTest.java`

- [ ] **Step 1: 写集成测试**

仿 `engine/knowledge/production/CandidateMaterializationIntegrationTest`（@SpringBootTest + 真实 H2 + RequestContext 绑定 t-1）：
1. 经仓储 seed：`source_document`（sourceCode=GL-2024, authority=B, title）→ `source_version`（versionNo=v1, sourceDocumentId）→ 两条 `source_fragment`（anchor section-1/section-2, 真实 excerpt + content_hash）。
2. 调 `service.generate(new CandidateGenerationRequest(versionId, PLATFORM_SOURCE, CLINICAL, [RULE, PATHWAY]))`（platform 管道须 t-1 租户上下文）。
3. 断言：`summary.candidates()` 2 条、各 `candidateRef` 形如 `kv:<id>:draft-from-v1`、`skipped` 空；经 `KnowledgeProductionOrchestrationService.listCandidates(jobCode)` 查到血缘行（FR-5）；候选 payload 含 `待编著` 与真实摘要、不含任何凭空医学逻辑。

> 平台主源管道要求当前租户为 `t-1`（`PlatformTenant.ID`）。集成测试用 t-1 上下文 + PLATFORM_SOURCE，绕过隔离守卫越界。

- [ ] **Step 2: Verify GREEN**

Run: `cd medkernel-backend && mvn -q -o -Dtest=CandidateGenerationIntegrationTest test`
Expected: PASS。

- [ ] **Step 3: Commit**

```bash
git add medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/generation/CandidateGenerationIntegrationTest.java
git commit -m "test(aikstd04/PR1): 来源→生成→候选落审核链 真实 H2 端到端 + 血缘"
```

## Task 6: 治理登记 + 产品目录 + 文档

**Files:**
- Modify: `architecture/ServiceContractCatalog`（若新服务需登记契约/审计点——核既有 `knowledge-production` 契约是否已覆盖，无新表/新端点审计点则可不改；新增 generate 端点须确认契约声明）。
- Regenerate: `docs/audit/product-function-catalog.md`（控制器端点 +1）。
- Modify: `docs/_HANDOFF.md`（新增 AIK-STD-04 PR1 段 + 翻转常驻「B0 暂停」为 P2-C 恢复）+ 卡 `docs/cards/wave2/AIK-STD-04.md`（勾 FR/AC + 实现进度 PR1）。

- [ ] **Step 1: 重生成产品目录**

Run:
```bash
node scripts/audit/export-product-capabilities.mjs
cd frontend && npx vitest run src/shared/config/productCatalog.test.ts
```
Expected: 目录新增 generate 端点、`productCatalog.test.ts` 5/5 PASS（KnowledgeProductionController MERGE，无漂移）。

- [ ] **Step 2: 契约/域归属核查**

Run: `cd medkernel-backend && mvn -q -o -Dtest=ServiceContractGovernanceTest,DomainOwnershipArchTest test`（按既有契约测试名）
Expected: PASS（新服务归 engine-knowledge 既有域，无新表/权限码）。若 FAIL 按提示补登记。

- [ ] **Step 3: 更新文档 + Commit**

```bash
git add docs/audit/product-function-catalog.md docs/_HANDOFF.md docs/cards/wave2/AIK-STD-04.md frontend/src/shared/config/productCatalog.test.ts
git commit -m "docs(aikstd04/PR1): 产品目录重生成 + 卡 FR/AC + HANDOFF 恢复 P2-C"
```

## Task 7: 最终验证（PR 前）

- [ ] **Step 1: 定向测试全绿**

Run:
```bash
cd medkernel-backend && mvn -q -o -Dtest=SourceCandidateGeneratorTest,CandidateGenerationOrchestrationServiceTest,KnowledgeProductionControllerSecurityTest,CandidateGenerationIntegrationTest test
```
Expected: PASS。

- [ ] **Step 2: 门禁 changed**

Run:
```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check
```
Expected: PASS（无迁移 → migration guard 0；真实性门禁注意禁词）。

- [ ] **Step 3: 全量回归**

Run:
```bash
cd medkernel-backend && MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test
cd frontend && npm run verify
```
Expected: PASS（后端基线 + 本卡新增；前端含 productCatalog 5/5）。若耗时过长，记录精确跳过命令与原因入 `_HANDOFF`。

- [ ] **Step 4: 推送 + 开 PR（合并 main 逐 PR 授权，不自动合）**

```bash
git push -u origin claude/wave2-p2c-aikstd04-candidate-generation
```
开 PR，等用户授权 squash 合并。

## Execution Notes

- 分支 `claude/wave2-p2c-aikstd04-candidate-generation`，TDD 红绿，小步频提。
- 恒守：B0 + P6 阻断（不接真实模型、不进 P6）+ 铁律 #1（无源不生成、逻辑留白不伪造）+ 域归属 SYS-02 + 合并 main 逐 PR 授权。
- 落地若发现既有签名（`ProductionJobResponse.jobCode()`、`RequestContext` 取租户、安全测试装配、契约测试名）与计划假设不符，以**既有代码为准**对齐，不照计划字面硬套。
