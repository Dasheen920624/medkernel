# AIK-STD-12 PR3 收尾实施计划 · 全专业资产模板(FR-1) + 候选退修(FR-3)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（inline）或 subagent-driven-development。步骤为 `- [ ]` 复选。
> 设计：[2026-06-15-aikstd12-pr3-templates-return-design.md](../specs/2026-06-15-aikstd12-pr3-templates-return-design.md)。分支 `claude/wave2-p2b-aikstd12-pr3-templates-return`。

**Goal:** 给 AIK-STD-12 补齐 FR-1（代码态全专业资产模板注册表 + 审核台消费）与 FR-3（候选退修 RETURN），使全部 FR 真实闭卡。

**Architecture:** 模板逻辑落 `engine.factory`（与信封/校验闸同域，纯确定性、不建表），端点挂既有 `KnowledgeProductionController` 零新治理面；退修复用现有 reject 分支模式 + V132 五方言放宽 CHECK。

**Tech Stack:** Spring Boot / Spring Data JDBC / JUnit5 + Mockito + MockMvc / Flyway 五方言 / React + Antd + vitest。

---

## 文件结构

- 新建 `engine/factory/TemplateSection.java`、`ProfessionalAssetTemplate.java`、`ProfessionalAssetTemplateRegistry.java` + 测试 `ProfessionalAssetTemplateRegistryTest.java`。
- 改 `engine/knowledge/production/KnowledgeProductionController.java`（+GET 端点、+依赖 registry）+ `KnowledgeProductionControllerSecurityTest.java`（+安全用例）。
- 改 `engine/knowledge/KnowledgeCandidateReviewDecision.java`（+RETURN）、`CandidateReviewStatus.java`（+RETURNED）、`KnowledgeVersionService.java`（reviewCandidate +RETURN 分支）+ `KnowledgeVersionServiceTest.java`（+退修用例）。
- 新建 `db/migration/{h2,postgres,oracle,dm,kingbase}/V132__knowledge_review_return.sql`（5 文件）+ 改 `MigrationBaselineContractTest.java`、`H2BaselineMigrationTest.java`、`FlywayMultiDialectSmokeTest.java`。
- 改前端 `shared/api/hooks.ts`（类型 +RETURN/RETURNED、`ProfessionalAssetTemplate`/`useAssetTemplates`）、`pages/quality/KnowledgeGovernance.tsx`（模板区 + 退修按钮）+ `KnowledgeGovernance.test.tsx`。
- 重生成 `product-function-catalog`；收尾改卡 `cards/wave2/AIK-STD-12.md` + `docs/_HANDOFF.md`。

---

## Task 1: 全专业资产模板注册表（factory）

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/factory/TemplateSection.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/factory/ProfessionalAssetTemplate.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/factory/ProfessionalAssetTemplateRegistry.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/factory/ProfessionalAssetTemplateRegistryTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.versioning.VersionedAssetType;

class ProfessionalAssetTemplateRegistryTest {

    private final ProfessionalAssetTemplateRegistry registry = new ProfessionalAssetTemplateRegistry();

    @Test
    void coversAllFr1ProfessionsWithNonEmptySections() {
        List<ProfessionalAssetTemplate> all = registry.listAll();
        // FR-1 列举十类 + 指南/药品/诊断 = 13 专业模板
        assertThat(all).hasSize(13);
        assertThat(all).allSatisfy(t -> {
            assertThat(t.professionCode()).isNotBlank();
            assertThat(t.displayName()).isNotBlank();
            assertThat(t.assetType()).isNotNull();
            assertThat(t.sections()).isNotEmpty();
            assertThat(t.sections()).allSatisfy(s -> {
                assertThat(s.key()).isNotBlank();
                assertThat(s.label()).isNotBlank();
            });
        });
    }

    @Test
    void medicalDomainTemplatesAreKnowledgeTypedAndMatchable() {
        Optional<ProfessionalAssetTemplate> nursing =
            registry.findByAssetTypeAndDomain(VersionedAssetType.KNOWLEDGE, KnowledgeDomain.NURSING);
        assertThat(nursing).isPresent();
        assertThat(nursing.get().sections()).anySatisfy(s -> assertThat(s.label()).contains("护理"));
    }

    @Test
    void structuralTemplatesHaveNullDomain() {
        Optional<ProfessionalAssetTemplate> rule =
            registry.findByAssetTypeAndDomain(VersionedAssetType.RULE, null);
        assertThat(rule).isPresent();
        assertThat(rule.get().knowledgeDomain()).isNull();
    }

    @Test
    void listIsImmutable() {
        List<ProfessionalAssetTemplate> all = registry.listAll();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> all.add(null));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=ProfessionalAssetTemplateRegistryTest`
Expected: 编译失败（TemplateSection/ProfessionalAssetTemplate/ProfessionalAssetTemplateRegistry 不存在）。

- [ ] **Step 3: 写 TemplateSection record**

```java
package com.medkernel.engine.factory;

/**
 * 专业资产模板的结构章节（AIK-STD-12 FR-1）。
 *
 * <p>仅承载结构骨架元数据——章节名取自既有专业文书结构标准（如药品说明书法定项、护理程序），
 * 供生产/审核对照核查完整性。本类不含任何医学内容，正文须按真实来源填充（守铁律 #1）。
 *
 * @param key 稳定章节码
 * @param label 章节中文名
 * @param required 该专业资产是否必备此章节
 * @param hint 结构填写提示
 */
public record TemplateSection(String key, String label, boolean required, String hint) {
}
```

- [ ] **Step 4: 写 ProfessionalAssetTemplate record**

```java
package com.medkernel.engine.factory;

import java.util.List;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 全专业领域标准资产模板（AIK-STD-12 FR-1）。
 *
 * <p>按结构维 {@link VersionedAssetType} × 医学领域维 {@link KnowledgeDomain} 定位一个专业的标准资产结构。
 * 模板＝结构骨架（章节清单），不新建资产类型、不预填医学内容（守铁律 #1）。医学领域型模板
 * （assetType=KNOWLEDGE × domain）供知识审核台按领域匹配；结构型模板 domain 为空，供编著/生产工作台。
 *
 * @param professionCode 专业稳定码
 * @param displayName 专业中文名
 * @param assetType 资产结构类型
 * @param knowledgeDomain 医学领域（结构型模板为空）
 * @param sections 标准结构章节（有序，非空）
 */
public record ProfessionalAssetTemplate(
    String professionCode,
    String displayName,
    VersionedAssetType assetType,
    KnowledgeDomain knowledgeDomain,
    List<TemplateSection> sections
) {

    public ProfessionalAssetTemplate {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
```

- [ ] **Step 5: 写 ProfessionalAssetTemplateRegistry @Service**

```java
package com.medkernel.engine.factory;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 全专业领域标准资产模板注册表（AIK-STD-12 FR-1）。
 *
 * <p>确定性代码态目录，不建表、不做租户自定义（无消费者需要）。覆盖术语/规则/路径/推荐/指标/随访/
 * 护理/报告/中医/医保 + 指南/药品/诊断 共 13 专业。章节为既有专业文书结构标准，供生产/审核对照完整性。
 */
@Service
public class ProfessionalAssetTemplateRegistry {

    private static final List<ProfessionalAssetTemplate> TEMPLATES = List.of(
        // —— 医学领域型（assetType=KNOWLEDGE × domain）：知识审核台按 identity.domain 匹配 ——
        knowledge("GUIDELINE", "指南共识", KnowledgeDomain.GUIDELINE,
            req("recommendation", "推荐意见"), req("evidence", "证据等级"),
            opt("population", "适用人群"), opt("implementation", "实施要点"), req("references", "参考文献")),
        knowledge("DRUG", "药品说明书", KnowledgeDomain.DRUG,
            req("indication", "适应症"), req("dosage", "用法用量"), req("contraindication", "禁忌"),
            req("adverse", "不良反应"), opt("interaction", "药物相互作用"),
            opt("precaution", "注意事项"), opt("special_population", "特殊人群用药")),
        knowledge("NURSING", "护理", KnowledgeDomain.NURSING,
            req("assessment", "护理评估"), req("diagnosis", "护理诊断"), req("goal", "护理目标"),
            req("intervention", "护理措施"), req("evaluation", "护理评价")),
        knowledge("REPORT", "报告解读", KnowledgeDomain.REPORT,
            req("item", "检查项目"), req("reference_range", "参考区间"), req("interpretation", "异常判读"),
            opt("clinical_meaning", "临床意义"), opt("recheck", "复查建议")),
        knowledge("TCM", "中医药", KnowledgeDomain.TCM,
            req("syndrome", "病名证候"), req("differentiation", "辨证分型"), req("therapy", "治法"),
            req("prescription", "方药"), opt("technique", "适宜技术"), opt("regimen", "调护")),
        knowledge("POLICY", "医保政策", KnowledgeDomain.POLICY,
            req("basis", "政策依据"), req("scope", "适用范围"), opt("admission", "准入条件"),
            req("payment", "支付标准"), opt("execution", "执行要点")),
        knowledge("DIAGNOSIS", "诊断", KnowledgeDomain.DIAGNOSIS,
            req("criteria", "诊断标准"), req("differential", "鉴别诊断"),
            opt("staging", "分型分期"), opt("indication", "诊疗指针")),
        // —— 结构型（domain 空）：目录完整性，供编著/生产工作台 ——
        structural("TERMINOLOGY", "术语", VersionedAssetType.TERMINOLOGY,
            req("term", "标准术语"), req("code", "编码体系"), opt("synonym", "同义词"),
            opt("mapping", "映射关系"), req("source", "术语来源")),
        structural("RULE", "规则", VersionedAssetType.RULE,
            req("trigger", "触发条件"), req("logic", "判定逻辑"), req("action", "动作建议"),
            req("risk", "风险级别"), opt("redline", "红线标识"), req("source", "来源依据")),
        structural("PATHWAY", "路径", VersionedAssetType.PATHWAY,
            req("admission", "准入标准"), req("branch", "分型分支"), req("stage", "阶段节点"),
            req("exit", "退出条件"), opt("variance", "变异处理")),
        structural("RECOMMENDATION", "推荐", VersionedAssetType.RECOMMENDATION,
            req("scenario", "推荐场景"), req("trigger", "触发条件"), req("content", "推荐内容"),
            opt("evidence", "证据强度"), req("source", "来源")),
        structural("EVALUATION", "指标", VersionedAssetType.EVALUATION,
            req("definition", "指标定义"), req("formula", "计算口径"), req("data_source", "数据来源"),
            req("threshold", "阈值标准"), opt("cycle", "评价周期")),
        structural("FOLLOWUP", "随访", VersionedAssetType.FOLLOWUP,
            req("population", "随访人群"), req("cycle", "随访周期"), req("item", "随访项目"),
            opt("alert", "异常预警"), opt("return_indication", "回院指针"))
    );

    public List<ProfessionalAssetTemplate> listAll() {
        return TEMPLATES;
    }

    public Optional<ProfessionalAssetTemplate> findByAssetTypeAndDomain(VersionedAssetType assetType,
            KnowledgeDomain domain) {
        return TEMPLATES.stream()
            .filter(t -> t.assetType() == assetType && t.knowledgeDomain() == domain)
            .findFirst();
    }

    private static ProfessionalAssetTemplate knowledge(String code, String name, KnowledgeDomain domain,
            TemplateSection... sections) {
        return new ProfessionalAssetTemplate(code, name, VersionedAssetType.KNOWLEDGE, domain, List.of(sections));
    }

    private static ProfessionalAssetTemplate structural(String code, String name, VersionedAssetType type,
            TemplateSection... sections) {
        return new ProfessionalAssetTemplate(code, name, type, null, List.of(sections));
    }

    private static TemplateSection req(String key, String label) {
        return new TemplateSection(key, label, true, label + "（必备结构）");
    }

    private static TemplateSection opt(String key, String label) {
        return new TemplateSection(key, label, false, label + "（建议结构）");
    }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=ProfessionalAssetTemplateRegistryTest`
Expected: PASS（4 用例）。

- [ ] **Step 7: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/factory/TemplateSection.java \
        medkernel-backend/src/main/java/com/medkernel/engine/factory/ProfessionalAssetTemplate.java \
        medkernel-backend/src/main/java/com/medkernel/engine/factory/ProfessionalAssetTemplateRegistry.java \
        medkernel-backend/src/test/java/com/medkernel/engine/factory/ProfessionalAssetTemplateRegistryTest.java
git commit -m "feat(aikstd12/PR3): 全专业资产模板注册表（FR-1，13 专业代码态目录）"
```

---

## Task 2: 模板端点挂 KnowledgeProductionController

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionController.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionControllerSecurityTest.java`

- [ ] **Step 1: 写失败安全测试**（加到 `KnowledgeProductionControllerSecurityTest`）

```java
    @Test
    @WithMockUser(authorities = {"knowledge.read"})
    void assetTemplatesReadableWithKnowledgeRead() throws Exception {
        RequestContext.set(RequestContext.builder().tenantId("t-1").userId("u-1").build());
        mockMvc.perform(get("/api/v1/engine/knowledge-production/asset-templates")
                .with(jwt().authorities(new SimpleGrantedAuthority("knowledge.read"))))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"recommendation.read"})
    void assetTemplatesForbiddenWithoutKnowledgeRead() throws Exception {
        RequestContext.set(RequestContext.builder().tenantId("t-1").userId("u-1").build());
        mockMvc.perform(get("/api/v1/engine/knowledge-production/asset-templates")
                .with(jwt().authorities(new SimpleGrantedAuthority("recommendation.read"))))
            .andExpect(status().isForbidden());
    }
```

> 注：若该测试类已有 `RequestContext.set(...)` 的既有写法/builder 形态，按文件现有用例的上下文构造方式对齐（沿用同类已有 GET 用例的 setup 模式）。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeProductionControllerSecurityTest`
Expected: FAIL（端点 404/未定义）。

- [ ] **Step 3: 控制器加依赖 + 端点**

在 `KnowledgeProductionController` 构造器注入 `ProfessionalAssetTemplateRegistry templateRegistry`（import `com.medkernel.engine.factory.ProfessionalAssetTemplate` / `ProfessionalAssetTemplateRegistry`），并加方法：

```java
    /** 全专业标准资产模板目录（AIK-STD-12 FR-1）：审核台/工作台按资产类型+领域对照核查完整性。 */
    @GetMapping("/asset-templates")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<ProfessionalAssetTemplate>> assetTemplates() {
        return ApiResult.ok(templateRegistry.listAll());
    }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeProductionControllerSecurityTest`
Expected: PASS。

- [ ] **Step 5: 重生成产品功能目录**

Run: `node frontend/scripts/export-product-capabilities.mjs`（或仓库既有重生成命令；确认 `frontend/src/shared/config/product-function-catalog.*` 的 KnowledgeProductionController 端点 +1）
Run: `cd frontend && npx vitest run src/shared/config/productCatalog.test.ts`
Expected: PASS 5/5。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/.../KnowledgeProductionController.java \
        medkernel-backend/.../KnowledgeProductionControllerSecurityTest.java \
        frontend/src/shared/config/
git commit -m "feat(aikstd12/PR3): 模板目录只读端点挂知识生产控制器（knowledge.read）"
```

---

## Task 3: 退修后端 — 枚举 + 服务逻辑

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCandidateReviewDecision.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/CandidateReviewStatus.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java:412-489`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeVersionServiceTest.java`

- [ ] **Step 1: 写失败测试**（加到 `KnowledgeVersionServiceTest`，对齐文件既有 reviewCandidate 用例的桩/上下文构造）

```java
    @Test
    void returnDecisionSendsCandidateBackToDraftWithMandatoryReason() {
        // 沿用既有 reviewCandidate 测试的 setup：PENDING_REPLACEMENT_REVIEW 候选 + classification + 上下文
        var request = reviewRequestWith(KnowledgeCandidateReviewDecision.RETURN, "请补充禁忌章节后重提");
        KnowledgeCandidateResponse resp = service.reviewCandidate(CANDIDATE_ID, request);

        assertThat(resp.reasonCode()).isEqualTo("RETURNED");
        // 候选版本回 DRAFT
        assertThat(savedVersionStatus()).isEqualTo(KnowledgeVersionStatus.DRAFT);
        // classification → RETURNED
        assertThat(savedClassificationStatus()).isEqualTo(CandidateReviewStatus.RETURNED);
        // assignment 决策 RETURN
        assertThat(savedAssignmentDecision()).isEqualTo(KnowledgeCandidateReviewDecision.RETURN);
    }

    @Test
    void returnDecisionRejectsBlankReason() {
        var request = reviewRequestWith(KnowledgeCandidateReviewDecision.RETURN, "  ");
        assertThatThrownBy(() -> service.reviewCandidate(CANDIDATE_ID, request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("退修");
    }
```

> 注：`reviewRequestWith(...)`、`savedVersionStatus()` 等按该测试类既有辅助/桩对齐；若无则参照既有 approve/reject 用例的 mock 验证方式（`verify(versionRepository).save(captor)`）就地构造。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeVersionServiceTest`
Expected: 编译失败（`RETURN`/`RETURNED` 未定义）。

- [ ] **Step 3: 加枚举值**

`KnowledgeCandidateReviewDecision.java`：
```java
public enum KnowledgeCandidateReviewDecision {
    APPROVE,
    REJECT,
    /** 退修：可修订，退回生产者并附修订意见，期待修订重提（区别于 REJECT 永久拒绝） */
    RETURN
}
```

`CandidateReviewStatus.java`：在枚举末尾加
```java
    /** 已退修，退回生产者修订重提，退出审核台队列 */
    RETURNED
```

- [ ] **Step 4: reviewCandidate 加 RETURN 分支**

在 `KnowledgeVersionService.reviewCandidate`（line 422 的 `if (APPROVE)` 之后、reject 默认分支之前）插入 RETURN 处理：

```java
        if (request.decision() == KnowledgeCandidateReviewDecision.RETURN) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "退修须填写修订意见");
            }
            KnowledgeAssetVersion candidate = versionRepository
                .findByTenantIdAndId(tenantId, classification.candidateVersionId())
                .orElseThrow(() -> ApiException.notFound("知识版本 id=" + classification.candidateVersionId()));
            KnowledgeAssetVersion draft = new KnowledgeAssetVersion(
                candidate.id(), candidate.tenantId(), candidate.identityId(),
                candidate.versionNo(), candidate.versionLabel(),
                candidate.sourceDocumentId(), candidate.sourceVersionId(),
                candidate.contentHash(), candidate.anchors(),
                KnowledgeVersionStatus.DRAFT, candidate.riskLevel(),
                candidate.authorityLevel(), candidate.gradeQuality(), candidate.gradeStrength(),
                candidate.conflictArbitration(),
                candidate.effectiveOrganizationScope(), candidate.effectiveApplicableScope(),
                candidate.scopeKeyForStatus(KnowledgeVersionStatus.DRAFT),
                candidate.effectiveFrom(), candidate.effectiveTo(),
                candidate.reviewedBy(), candidate.reviewedAt(),
                candidate.activatedAt(), candidate.supersededAt(),
                candidate.withdrawnAt(), candidate.withdrawnReason(),
                candidate.createdAt(), candidate.createdBy(),
                now, actor,
                candidate.reviewCycleMonths(), candidate.nextReviewAt());
            KnowledgeAssetVersion savedDraft = versionRepository.save(draft);
            CandidateClassification returned = candidateClassificationRepository.save(classificationWithStatus(
                classification,
                CandidateReviewStatus.RETURNED,
                appendReason(classification.basis(), request.reason()),
                now,
                actor));
            reviewAssignmentRepository.save(reviewAssignment(
                returned,
                CandidateReviewStatus.RETURNED,
                KnowledgeCandidateReviewDecision.RETURN,
                request.reason(),
                actor,
                now));
            return new KnowledgeCandidateResponse(
                classification.identityId(),
                List.of(savedDraft),
                List.of(returned),
                true,
                "RETURNED",
                "候选已退修，退回生产者修订重提");
        }
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=KnowledgeVersionServiceTest`
Expected: PASS（含 2 新增）。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCandidateReviewDecision.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/CandidateReviewStatus.java \
        medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java \
        medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeVersionServiceTest.java
git commit -m "feat(aikstd12/PR3): 候选退修 RETURN（FR-3，回 DRAFT + RETURNED 留痕 + 必填修订意见）"
```

---

## Task 4: 退修迁移 V132 五方言 + 契约基线

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V132__knowledge_review_return.sql`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java:25`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/FlywayMultiDialectSmokeTest.java:33`

- [ ] **Step 1: 写失败契约断言**（`MigrationBaselineContractTest`）

`EXPECTED_MIGRATIONS` 末尾（V131 之后）追加 `"V132__knowledge_review_return.sql"`；新增方法：
```java
    @Test
    void v132ShouldAddReturnDecisionForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V132__knowledge_review_return.sql");
            assertThat(ddl).as("%s 候选退修态迁移", dialect)
                .contains("ck_knowledge_candidate_review_status")
                .contains("ck_review_assignment_review_status")
                .contains("ck_review_assignment_decision")
                .contains("RETURNED")
                .contains("'RETURN'")
                .contains("COMMENT ON COLUMN mk_knowledge_review_assignment.decision");
        }
    }
```

- [ ] **Step 2: bump LATEST_MIGRATION_VERSION**

`H2BaselineMigrationTest.java:25` 与 `FlywayMultiDialectSmokeTest.java:33`：`131` → `132`。

- [ ] **Step 3: 跑测试确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest`
Expected: FAIL（V132 文件缺失）。

- [ ] **Step 4: 写 V132（5 方言同内容，仿 V88 写法）**

每个方言文件内容一致（h2/postgres/oracle/dm/kingbase）：
```sql
-- MedKernel · AIK-STD-12 FR-3 候选退修态（<方言名>）
-- 放宽知识候选审核 review_status 与 review_assignment.decision 两组 CHECK，加入退修态 RETURNED / RETURN；值名兼容、存量数据不受影响。
-- ROLLBACK：确认无 RETURNED 候选/退修记录后，删除三 CHECK 并恢复各自原值集合。

ALTER TABLE mk_knowledge_candidate_classification DROP CONSTRAINT ck_knowledge_candidate_review_status;
ALTER TABLE mk_knowledge_candidate_classification ADD CONSTRAINT ck_knowledge_candidate_review_status
    CHECK (review_status IN ('PENDING_REPLACEMENT_REVIEW','DUPLICATE_SKIPPED','APPROVED','REJECTED','RETURNED'));

ALTER TABLE mk_knowledge_review_assignment DROP CONSTRAINT ck_review_assignment_review_status;
ALTER TABLE mk_knowledge_review_assignment ADD CONSTRAINT ck_review_assignment_review_status
    CHECK (review_status IN ('PENDING_REPLACEMENT_REVIEW','DUPLICATE_SKIPPED','APPROVED','REJECTED','RETURNED'));

ALTER TABLE mk_knowledge_review_assignment DROP CONSTRAINT ck_review_assignment_decision;
ALTER TABLE mk_knowledge_review_assignment ADD CONSTRAINT ck_review_assignment_decision
    CHECK (decision IS NULL OR decision IN ('APPROVE','REJECT','RETURN'));

COMMENT ON COLUMN mk_knowledge_candidate_classification.review_status IS '审核状态：PENDING_REPLACEMENT_REVIEW 待替换审核 / DUPLICATE_SKIPPED 重复跳过 / APPROVED 通过 / REJECTED 拒绝 / RETURNED 退修';
COMMENT ON COLUMN mk_knowledge_review_assignment.review_status IS '审核状态：PENDING_REPLACEMENT_REVIEW 待替换审核 / APPROVED 通过 / REJECTED 拒绝 / RETURNED 退修';
COMMENT ON COLUMN mk_knowledge_review_assignment.decision IS '审核结论：APPROVE 通过 / REJECT 拒绝 / RETURN 退修';
```
（仅首行方言名替换；其余完全一致——与 V88 跨方言一致策略相同。）

- [ ] **Step 5: 跑测试确认通过（含五方言 smoke）**

Run: `cd medkernel-backend && mvn -q test -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest`
Expected: PASS（FlywayMultiDialectSmokeTest 真实容器 h2/postgres/oracle/dm/kingbase 应用 V132 干净）。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/src/main/resources/db/migration/*/V132__knowledge_review_return.sql \
        medkernel-backend/src/test/java/com/medkernel/migration/
git commit -m "feat(aikstd12/PR3): V132 五方言放宽审核 CHECK 加退修态 RETURNED/RETURN"
```

---

## Task 5: 前端 — 模板消费 + 退修动作

**Files:**
- Modify: `frontend/src/shared/api/hooks.ts`
- Modify: `frontend/src/pages/quality/KnowledgeGovernance.tsx`
- Test: `frontend/src/pages/quality/KnowledgeGovernance.test.tsx`

- [ ] **Step 1: 写失败测试**（加到 `KnowledgeGovernance.test.tsx`，对齐既有 mock server / render 模式）

```tsx
  it("详情抽屉按候选领域展示专业标准模板结构清单", async () => {
    // mock GET /asset-templates 返回含 NURSING 模板；选中 NURSING 领域候选打开抽屉
    // 断言渲染「专业标准模板」区 + 章节 label（如「护理评估」）
  });

  it("退修按钮提交 RETURN 且必填修订意见", async () => {
    // 点「退修」→ 空意见不提交（校验）→ 填写后以 decision=RETURN 调 review
  });
```
> 按该测试文件既有用例的 mock/断言风格补全实现（沿用 PR2 新增的 provenance 用例骨架）。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npx vitest run src/pages/quality/KnowledgeGovernance.test.tsx`
Expected: FAIL。

- [ ] **Step 3: hooks.ts — 类型 + useAssetTemplates**

- `KnowledgeCandidateReviewDecision`（line 969）改为 `"APPROVE" | "REJECT" | "RETURN"`。
- `CandidateReviewStatus`（line 962-967）联合加 `"RETURNED"`。
- 新增类型 + hook：
```ts
export interface TemplateSection {
  key: string;
  label: string;
  required: boolean;
  hint: string;
}

export interface ProfessionalAssetTemplate {
  professionCode: string;
  displayName: string;
  assetType: string;
  knowledgeDomain: KnowledgeDomain | null;
  sections: TemplateSection[];
}

/** AIK-STD-12 FR-1：全专业标准资产模板目录（按 assetType+domain 对照核查完整性）。 */
export function useAssetTemplates() {
  return useQuery({
    queryKey: ["knowledge-production", "asset-templates"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ProfessionalAssetTemplate[] }>(
        `/engine/knowledge-production/asset-templates`,
      );
      return data.data;
    },
  });
}
```
> API 路径前缀对齐 `useCandidateProvenance`（line 1263-1271）的写法（同 `/engine/knowledge-production/...` 基址）。

- [ ] **Step 4: KnowledgeGovernance.tsx — 模板区 + 退修按钮**

- 顶部引入 `useAssetTemplates`、`ProfessionalAssetTemplate`。
- 组件内 `const templatesQuery = useAssetTemplates();`，匹配：
```tsx
  const domainTemplate = (templatesQuery.data ?? []).find(
    (t) => t.assetType === "KNOWLEDGE" && t.knowledgeDomain === selectedIdentity?.domain,
  );
```
- 详情 Drawer（line 1150 区）加「专业标准模板」区块：有 `domainTemplate` 渲染 `sections`（label + 必填 Tag + hint，BASE-10 token），无则 `<Empty>该领域暂无标准模板</Empty>` 文案。
- 审核动作区加「退修」按钮：点开必填「修订意见」输入（复用现 reason Modal/Input），提交以 `decision: "RETURN"` 调 `useReviewKnowledgeCandidate`（line 1292），空白意见禁用提交。主按钮（发布/批准）保持 ≤1。
- 候选状态映射加 `RETURNED → "已退修"` 标签。

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/pages/quality/KnowledgeGovernance.test.tsx`
Expected: PASS（含 2 新增）。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/shared/api/hooks.ts frontend/src/pages/quality/KnowledgeGovernance.tsx \
        frontend/src/pages/quality/KnowledgeGovernance.test.tsx
git commit -m "feat(aikstd12/PR3): 审核台接专业模板对照 + 候选退修动作（FR-1/FR-3 前端）"
```

---

## Task 6: 全量验证 + 收尾

- [ ] **Step 1: 后端全量 + 门禁**

Run: `cd medkernel-backend && mvn -q test`
Expected: 全绿（基线 2525 + 新增 ≈ registry 4 + 控制器 2 + 服务 2 + 迁移 1 ≈ +9）。
Run: `node medkernel-backend/scripts/authenticity-guard.mjs --mode=changed --base=origin/main`（+ config/migration/comment-zh 三门禁 changed）
Expected: 全过（模板 Javadoc 无禁词）。

- [ ] **Step 2: 前端全套**

Run: `cd frontend && npm run verify`（vitest + tsc + eslint + prettier format:check + productCatalog）
Expected: 全绿。

- [ ] **Step 3: git diff --check**

Run: `git diff --check`
Expected: 干净。

- [ ] **Step 4: 收尾文档**

- `cards/wave2/AIK-STD-12.md`：勾 FR-1 / FR-3 + AC-1/AC-2，补「实现进度（PR3）」。
- `docs/_HANDOFF.md`：顶部新增 PR3 收尾段（状态/下一步/全 FR 闭卡）。
- 提交：`git commit -m "docs(aikstd12/PR3): 卡片 FR 勾全 + handoff 收尾"`

- [ ] **Step 5: 推送 + 开 PR（合并 main 逐 PR 授权）**

```bash
git push -u origin claude/wave2-p2b-aikstd12-pr3-templates-return
gh pr create --title "feat(aikstd12/PR3): 全专业资产模板(FR-1) + 候选退修(FR-3) 收尾闭卡" --body "..."
```
（合并需用户逐 PR 授权。）

---

## 自审

- **Spec 覆盖**：FR-1（Task 1/2/5）✅ · FR-3 退修（Task 3/4/5）✅ · V132 五方言（Task 4）✅ · 前端消费（Task 5）✅ · 门禁/产品目录（Task 2/6）✅ · 卡片闭卡（Task 6）✅。
- **占位扫描**：无 TBD/TODO；测试辅助处明确标注「对齐既有用例风格」而非空泛占位（执行时按现有桩补全）。
- **类型一致**：`ProfessionalAssetTemplate`/`TemplateSection`/`RETURN`/`RETURNED`/`useAssetTemplates`/`findByAssetTypeAndDomain` 前后一致。
- **风险点**：① V132 跨方言 DROP CONSTRAINT 无 IF EXISTS（与 V88 同，真实容器已验可行）；② 新控制器端点须重生成产品目录（Task 2 Step 5）；③ 退修版本回 DRAFT 退出队列（队列按 PENDING_REPLACEMENT_REVIEW 过滤，自然移出）。
