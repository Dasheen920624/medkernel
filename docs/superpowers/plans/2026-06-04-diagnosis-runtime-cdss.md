# 运行时鉴别诊断 CDSS（Plan B）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Plan A 的诊断知识 + 命中核心接到运行时，提供 `POST /engine/recommendations/diagnosis-assist`：从患者上下文取发现 → 对所有 ACTIVE 诊断知识命中产出竞争候选 → 规则引擎红线合流 → 按"高危先行 > 证据充分 > 来源可信"排序 → 组装鉴别诊断卡（复用推荐卡治理），无候选时诚实空态。

**Architecture:** `DiagnosisAssistService` 编排：`DiagnosisFindingExtractor`（从 `ContextSnapshot` 提标准化发现 + 未映射清单）→ 遍历 `findActiveDiagnosisVersions` 各版本的 `criteria` 跑 Plan A 的 `DiagnosisMatcher` → `DiagnosisRedlinePort`（调规则引擎红线；OPT-04 未就绪诚实空、不阻断）→ 排序 → 复用 `RecommendationEngineService.trigger` 落库治理。置信仍是分级非概率；空态明示"非排除诊断"。

**Tech Stack:** 同 Plan A（Spring Boot / Spring Data JDBC / JUnit / mvn）。

> **设计依据：** [设计稿](../specs/2026-06-03-diagnosis-knowledge-cdss-design.md) 第 4.3/4.4/4.8 节、原则 2/3/5。
> **依赖：** 必须先完成 [Plan A](2026-06-04-diagnosis-knowledge-asset.md)（提供 `DiagnosisMatcher`/`DiagnosisConfidenceEvaluator`/`DiagnosisCriterionRepository`/`DiagnosisConfidencePolicy(Repository)`/`mk_diagnosis_*` 与 `KnowledgeDomain.DIAGNOSIS`）。
> **同构约定：** 同 Plan A——独有逻辑给完整代码，机械同构给代表 + 指令。

---

## 文件结构

**新增**（`engine/knowledge/diagnosis/runtime/` 子包，运行时编排与 knowledge 维护分离）：
- `DiagnosisFindingExtractor.java` / `ExtractedFindings.java` / `FindingNormalizationPort.java` / `DefaultFindingNormalizationPort.java`
- `DiagnosisRedlinePort.java` / `RedlineHit.java` / `DefaultDiagnosisRedlinePort.java`
- `DiagnosisAssistService.java`
- DTO：`DiagnosisAssistRequest.java` / `DiagnosisAssistResponse.java` / `DiagnosisCandidate.java`
- `DiagnosisAssistController.java`

**修改：**
- `engine/recommendation/RecommendationCardType.java` — 加 `DIAGNOSIS`
- `engine/knowledge/KnowledgeAssetVersionRepository.java` — 加 `findActiveDiagnosisVersions`
- `shared/api/error/ErrorCode.java` — 加 `ENG_DX_002` / `ENG_DX_003` / `ENG_DX_005`

**复用（Plan A / 现有）：** `DiagnosisMatcher`、`DiagnosisCriterionRepository`、`DiagnosisConfidencePolicyRepository`、`KnowledgeIdentityRepository`、`ContextSnapshotService`、`RecommendationEngineService.trigger`、`RuleDslEvaluator`/`RuleEngineService`。

---

## Task 1: RecommendationCardType 加 DIAGNOSIS + 错误码

**Files:**
- Modify: `engine/recommendation/RecommendationCardType.java`、`shared/api/error/ErrorCode.java`
- Test: `RecommendationCardTypeTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.recommendation;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class RecommendationCardTypeTest {
    @Test void diagnosisTypeExists() {
        assertThat(RecommendationCardType.valueOf("DIAGNOSIS")).isNotNull();
    }
}
```

- [ ] **Step 2: 运行验证失败** — Run: `cd medkernel-backend && mvn -q -Dtest=RecommendationCardTypeTest test` — Expected: `No enum constant ... DIAGNOSIS`

- [ ] **Step 3: 实现** — `RecommendationCardType` 枚举加 `DIAGNOSIS`（更新 Javadoc 列举）；`ErrorCode` 仿 `ENG_DX_001` 加：`ENG_DX_002`（发现编码未标准化，部分可用提示）、`ENG_DX_003`（无诊断候选，诚实空态、非排除结论）、`ENG_DX_005`（置信策略缺失或非法）。

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=RecommendationCardTypeTest test` — Expected: PASS
- [ ] **Step 5: 提交** — `git commit -m "feat(diagnosis): RecommendationCardType.DIAGNOSIS + ENG_DX 运行时错误码"`

---

## Task 2: 发现提取器（标准化 + 部分可用）

**Files:**
- Create: `runtime/ExtractedFindings.java` / `FindingNormalizationPort.java` / `DefaultFindingNormalizationPort.java` / `DiagnosisFindingExtractor.java`
- Test: `DiagnosisFindingExtractorTest.java`

- [ ] **Step 1: 写失败测试（已映射进集合、未映射进 unmapped、不阻断）**

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
// 用 stub 端口：FEVER 能标准化，LOCALX 不能
class DiagnosisFindingExtractorTest {
    private final FindingNormalizationPort port = (tenant, type, code, system) ->
        "FEVER".equals(code) ? Optional.of("STD-FEVER") : Optional.empty();
    private final DiagnosisFindingExtractor extractor = new DiagnosisFindingExtractor(port);

    @Test void mapsKnownAndCollectsUnmapped() {
        // 构造仅含两个 condition code 的 ContextSnapshot（FEVER、LOCALX），见下方夹具
        var findings = extractor.extract("t-1", TestSnapshots.withConditionCodes("FEVER", "LOCALX"));
        assertThat(findings.normalizedCodes()).containsExactly("STD-FEVER");
        assertThat(findings.unmappedFindings()).containsExactly("LOCALX");
    }
}
```
（`TestSnapshots.withConditionCodes(...)` 为本测试夹具：构造一个 `ContextSnapshot`，其 `resources().conditions()` 含给定 code 的 `CanonicalCondition`；按 `CanonicalCondition` 构造器落地。）

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DiagnosisFindingExtractorTest test` — Expected: 编译失败

- [ ] **Step 3: 实现端口 + 结果 + 提取器（完整）**

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.List;
import java.util.Set;

/** 提取结果：标准化发现编码集 + 未能标准化的本地编码清单（部分可用，不阻断）。 */
public record ExtractedFindings(Set<String> normalizedCodes, List<String> unmappedFindings) {}
```

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.Optional;
import com.medkernel.engine.context.CanonicalResourceType;

/** 发现标准化端口：本地编码 → TERM-01 标准编码；未映射返回空（不猜不补）。 */
@FunctionalInterface
public interface FindingNormalizationPort {
    Optional<String> normalize(String tenantId, CanonicalResourceType type, String localCode, String codeSystem);
}
```

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.Optional;
import org.springframework.stereotype.Component;
import com.medkernel.engine.context.CanonicalResourceType;
// 默认实现：接 TERM-01。Plan B 落地时注入 TerminologyService/已确认映射查询；
// 未确认映射返回 Optional.empty()，绝不用字符近似兜底（守 TERM-01 确定性候选原则）。
@Component
public class DefaultFindingNormalizationPort implements FindingNormalizationPort {
    // TODO(接线): 注入 com.medkernel.engine.terminology 的已确认映射查询（standard_term ACTIVE + term_mapping CONFIRMED）。
    @Override
    public Optional<String> normalize(String tenantId, CanonicalResourceType type, String localCode, String codeSystem) {
        return Optional.empty(); // 占位：未接 TERM 前一律未映射；接线后改为真实查询。
    }
}
```

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.canonical.CanonicalCondition;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalMedication;
import com.medkernel.engine.context.canonical.CanonicalProcedure;

/** 从患者上下文快照提取标准化发现编码集合，未标准化项归入 unmapped（部分可用）。 */
@Component
public class DiagnosisFindingExtractor {

    private final FindingNormalizationPort port;

    public DiagnosisFindingExtractor(FindingNormalizationPort port) {
        this.port = port;
    }

    public ExtractedFindings extract(String tenantId, ContextSnapshot snapshot) {
        Set<String> normalized = new LinkedHashSet<>();
        List<String> unmapped = new ArrayList<>();
        var r = snapshot.resources();
        for (CanonicalCondition c : r.conditions()) {
            classify(tenantId, CanonicalResourceType.CONDITION, c.code(), c.codeSystem(), normalized, unmapped);
        }
        for (CanonicalObservation o : r.observations()) {
            classify(tenantId, CanonicalResourceType.OBSERVATION, o.code(), o.codeSystem(), normalized, unmapped);
        }
        for (CanonicalMedication m : r.medications()) {
            classify(tenantId, CanonicalResourceType.MEDICATION, m.code(), m.codeSystem(), normalized, unmapped);
        }
        for (CanonicalProcedure p : r.procedures()) {
            classify(tenantId, CanonicalResourceType.PROCEDURE, p.code(), p.codeSystem(), normalized, unmapped);
        }
        return new ExtractedFindings(Set.copyOf(normalized), List.copyOf(unmapped));
    }

    private void classify(String tenantId, CanonicalResourceType type, String code, String system,
                          Set<String> normalized, List<String> unmapped) {
        if (code == null || code.isBlank()) {
            return;
        }
        port.normalize(tenantId, type, code, system)
            .ifPresentOrElse(normalized::add, () -> unmapped.add(code));
    }
}
```
（注：`CanonicalCondition.code()`/`codeSystem()` 等访问器名以实际 record 为准；若不同，按真实访问器调整。`CanonicalResourceType` 枚举值以实际为准。）

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=DiagnosisFindingExtractorTest test` — Expected: PASS
- [ ] **Step 5: 提交** — `git commit -m "feat(diagnosis): 发现提取器 + TERM 标准化端口（部分可用降级）"`

---

## Task 3: ACTIVE 诊断版本查询

**Files:**
- Modify: `engine/knowledge/KnowledgeAssetVersionRepository.java`
- Test: `KnowledgeAssetVersionRepositoryTest.java`（追加用例）

- [ ] **Step 1: 写失败测试** — 造 1 个 `domain=DIAGNOSIS` 身份 + 1 个 ACTIVE 版本、1 个 `domain=GUIDELINE` 身份 + ACTIVE 版本；断言 `findActiveDiagnosisVersions("t-1")` 只返回诊断那条。

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=KnowledgeAssetVersionRepositoryTest test` — Expected: 编译失败

- [ ] **Step 3: 实现查询（join identity 过滤 domain）**

```java
    @Query("""
        SELECT v.* FROM knowledge_asset_version v
        JOIN knowledge_identity i ON v.identity_id = i.id AND v.tenant_id = i.tenant_id
        WHERE v.tenant_id = :tenantId AND v.status = 'ACTIVE' AND i.domain = 'DIAGNOSIS'
        ORDER BY v.updated_at DESC, v.id DESC
        """)
    List<KnowledgeAssetVersion> findActiveDiagnosisVersions(String tenantId);
```

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=KnowledgeAssetVersionRepositoryTest test` — Expected: PASS
- [ ] **Step 5: 提交** — `git commit -m "feat(diagnosis): 查询 ACTIVE 诊断知识版本"`

---

## Task 4: 红线端口（OPT-04 合流，诚实降级）

**Files:**
- Create: `runtime/RedlineHit.java` / `DiagnosisRedlinePort.java` / `DefaultDiagnosisRedlinePort.java`
- Test: `DefaultDiagnosisRedlinePortTest.java`

- [ ] **Step 1: 写失败测试（OPT-04 未就绪时返回空、不抛错）**

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;
class DefaultDiagnosisRedlinePortTest {
    @Test void noRedlineRulesYieldsEmptyNotError() {
        var port = new DefaultDiagnosisRedlinePort(); // 未配置红线规则集
        assertThat(port.check("t-1", Set.of("STD-FEVER"))).isEmpty();
    }
}
```

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DefaultDiagnosisRedlinePortTest test` — Expected: 编译失败

- [ ] **Step 3: 实现（端口 + 结果 + 默认诚实降级）**

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;
/** 红线命中：要置顶且不可被疲劳抑制的高危项（致命病/危急值/严重 DDI）。 */
public record RedlineHit(String identityCode, String severity, String reason) {}
```

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;
import java.util.List;
import java.util.Set;
/** 红线合流端口：对发现集跑 OPT-04 红线。未就绪时实现返回空、不阻断（B0 诚实降级）。 */
@FunctionalInterface
public interface DiagnosisRedlinePort {
    List<RedlineHit> check(String tenantId, Set<String> normalizedFindings);
}
```

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
// 默认实现：OPT-04 红线规则集未落地前诚实返回空，不伪造红线、不阻断主链路。
// 接线点：OPT-04 就绪后，改为对标记为红线的 rule_definition 经 RuleDslEvaluator 求值，命中转 RedlineHit。
@Component
public class DefaultDiagnosisRedlinePort implements DiagnosisRedlinePort {
    @Override
    public List<RedlineHit> check(String tenantId, Set<String> normalizedFindings) {
        return List.of();
    }
}
```

- [ ] **Step 4: 运行验证通过** — Run: `mvn -q -Dtest=DefaultDiagnosisRedlinePortTest test` — Expected: PASS
- [ ] **Step 5: 提交** — `git commit -m "feat(diagnosis): 红线合流端口 + OPT-04 未就绪诚实降级"`

---

## Task 5: 候选编排 DiagnosisAssistService + DTO

**Files:**
- Create: `runtime/DiagnosisCandidate.java` / `DiagnosisAssistRequest.java` / `DiagnosisAssistResponse.java` / `DiagnosisAssistService.java`
- Test: `DiagnosisAssistServiceTest.java`

- [ ] **Step 1: 写失败测试（命中产候选 + 排序 + 空态非排除）**

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;
// @SpringBootTest，仿 RecommendationEngineServiceTest 夹具。三个用例：
//  1) 造 1 个 ACTIVE 诊断版本（criteria 命中 STRONG）+ 快照含对应发现 → assist 返回 1 个候选，confidence=STRONG，含支持证据；
//  2) 两个候选（STRONG vs MODERATE）→ STRONG 排在前（高危先行 > 证据充分 > 来源可信）；
//  3) 无任何命中 → candidates 空，advisoryNote 含“非排除诊断”，不抛错（空态安全）。
// 断言候选可回链 sourceVersionId；断言落库走推荐卡（cards 表存在 cardType=DIAGNOSIS 的卡）。
```

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DiagnosisAssistServiceTest test` — Expected: 编译失败

- [ ] **Step 3: 实现 DTO（完整）**

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;
import java.util.List;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidence;
/** 单个鉴别诊断候选（可解释、可追溯到诊断知识版本）。 */
public record DiagnosisCandidate(
    Long identityId, String diagnosisName, String icdCode,
    DiagnosisConfidence confidence,
    List<String> supporting, List<String> refuting, List<String> missingRequired,
    String authorityLevel, boolean redline, Long sourceVersionId) {}
```

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;
import jakarta.validation.constraints.NotBlank;
/** 鉴别诊断请求：以已建上下文快照为输入（12 字段统一上下文由 RequestContext 提供）。 */
public record DiagnosisAssistRequest(@NotBlank String contextSnapshotId) {}
```

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;
import java.util.List;
/** 鉴别诊断响应：候选并列 + 未标准化清单 + 辅助声明（空态非排除）+ trace。 */
public record DiagnosisAssistResponse(
    List<DiagnosisCandidate> candidates, List<String> unmappedFindings,
    String advisoryNote, String traceId) {}
```

- [ ] **Step 4: 实现编排服务（完整核心）**

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.diagnosis.*;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 运行时鉴别诊断编排：发现→命中→红线→排序→候选（落库治理见 step 5b）。 */
@Service
public class DiagnosisAssistService {

    private static final String ADVISORY_EMPTY =
        "系统无足够依据给出诊断提示，这不是排除诊断结论，请医师结合临床判断。";

    private final ContextSnapshotService snapshots;
    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeIdentityRepository identities;
    private final DiagnosisCriterionRepository criteria;
    private final DiagnosisConfidencePolicyRepository policies;
    private final DiagnosisMatcher matcher;
    private final DiagnosisFindingExtractor extractor;
    private final DiagnosisRedlinePort redlinePort;

    public DiagnosisAssistService(ContextSnapshotService snapshots, KnowledgeAssetVersionRepository versions,
            KnowledgeIdentityRepository identities, DiagnosisCriterionRepository criteria,
            DiagnosisConfidencePolicyRepository policies, DiagnosisMatcher matcher,
            DiagnosisFindingExtractor extractor, DiagnosisRedlinePort redlinePort) {
        this.snapshots = snapshots;
        this.versions = versions;
        this.identities = identities;
        this.criteria = criteria;
        this.policies = policies;
        this.matcher = matcher;
        this.extractor = extractor;
        this.redlinePort = redlinePort;
    }

    @Transactional(readOnly = true)
    public DiagnosisAssistResponse assist(DiagnosisAssistRequest request) {
        String tenant = tenant();
        ContextSnapshot snapshot = snapshots.loadById(tenant, request.contextSnapshotId()); // 按实际方法名
        ExtractedFindings findings = extractor.extract(tenant, snapshot);
        DiagnosisConfidencePolicy policy = policies.findByTenantIdAndScopeKey(tenant, "DEFAULT")
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_DX_005, "缺少默认置信策略 DEFAULT"));
        Set<String> redlineCodes = redlineCodes(tenant, findings.normalizedCodes());

        List<DiagnosisCandidate> candidates = new ArrayList<>();
        for (KnowledgeAssetVersion v : versions.findActiveDiagnosisVersions(tenant)) {
            List<DiagnosisCriterion> versionCriteria = criteria.findByTenantIdAndDiagnosisVersionId(tenant, v.id());
            DiagnosisMatchResult result = matcher.match(findings.normalizedCodes(), versionCriteria, policy);
            if (result.confidence() == DiagnosisConfidence.WEAK && !result.hitExclusion()) {
                continue; // 弱支持默认不并列呈现，避免低价值噪声（REMIND-01 低打扰）
            }
            KnowledgeIdentity identity = identities.findByTenantIdAndId(tenant, v.identityId()).orElse(null);
            boolean redline = identity != null && redlineCodes.contains(identity.identityCode());
            candidates.add(new DiagnosisCandidate(
                v.identityId(), identity == null ? null : identity.subject(),
                identity == null ? null : identity.identityCode(), result.confidence(),
                result.supporting(), result.refuting(), result.missingRequired(),
                v.authorityLevel() == null ? null : v.authorityLevel().name(), redline, v.id()));
        }
        candidates.sort(rankComparator());
        // step 5b（落库治理）：把 candidates 转 RecommendationCardRequest（source=诊断版本、
        // requiresPhysicianConfirmation=true、cardType=DIAGNOSIS、红线→riskLevel 高），
        // 调 recommendationEngineService.trigger 统一落库/审计/疲劳。详见 Task 6 接线。
        return new DiagnosisAssistResponse(candidates, findings.unmappedFindings(),
            candidates.isEmpty() ? ADVISORY_EMPTY : "辅助建议，需医师确认。", traceId());
    }

    private Set<String> redlineCodes(String tenant, Set<String> findings) {
        return redlinePort.check(tenant, findings).stream().map(RedlineHit::identityCode)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 高危先行 > 证据充分（置信）> 来源可信（A<B<C..）。 */
    private Comparator<DiagnosisCandidate> rankComparator() {
        return Comparator.comparing((DiagnosisCandidate c) -> c.redline() ? 0 : 1)
            .thenComparing(c -> confidenceRank(c.confidence()))
            .thenComparing(c -> c.authorityLevel() == null ? "Z" : c.authorityLevel());
    }

    private int confidenceRank(DiagnosisConfidence c) {
        return switch (c) {
            case STRONG -> 0; case MODERATE -> 1; case EXCLUDE -> 2; case WEAK -> 3;
        };
    }

    private String tenant() {
        String t = RequestContext.currentOrgScope().tenantId();
        if (t == null || t.isBlank()) throw ApiException.tenantMissing();
        return t;
    }
    private String traceId() { return RequestContext.currentTraceId(); }
}
```
（`snapshots.loadById` / `v.authorityLevel()` 等以实际方法/字段名为准；`KnowledgeAssetVersion` 的 `authorityLevel` 来自 KNOW-01 PR2。step 5b 的 trigger 接线在 Task 6 与契约一起落地并测试。）

- [ ] **Step 5: 运行验证通过** — Run: `mvn -q -Dtest=DiagnosisAssistServiceTest test` — Expected: PASS（命中/排序/空态非排除三用例）
- [ ] **Step 6: 提交** — `git commit -m "feat(diagnosis): 鉴别诊断候选编排 + 排序 + 空态安全"`

---

## Task 6: diagnosis-assist API + 落库治理 + 契约/安全测试

**Files:**
- Create: `runtime/DiagnosisAssistController.java`
- Modify: `DiagnosisAssistService`（接 `RecommendationEngineService.trigger` 落库，step 5b）
- Test: `DiagnosisAssistApiContractTest.java`、`DiagnosisAssistControllerSecurityTest.java`

- [ ] **Step 1: 写失败契约/安全测试** — `POST /api/v1/engine/recommendations/diagnosis-assist` 需 `@PreAuthorize("@perm.has('recommendation.write')")`；无权限 403；带权限 + 有效 `contextSnapshotId` 返回 `ApiResult<DiagnosisAssistResponse>`，候选项含 `confidence`、`advisoryNote` 非空；空态用例 candidates 空且 advisoryNote 含“非排除诊断”。落库断言：调用后该患者存在 `cardType=DIAGNOSIS` 推荐卡。

- [ ] **Step 2: 运行验证失败** — Run: `mvn -q -Dtest=DiagnosisAssistApiContractTest,DiagnosisAssistControllerSecurityTest test` — Expected: 404/编译失败

- [ ] **Step 3: 实现落库治理（step 5b）** — 在 `DiagnosisAssistService.assist` 末尾，把每个候选转 `RecommendationCardRequest`（`cardType=DIAGNOSIS`、`requiresPhysicianConfirmation=true`、`riskLevel` 红线→HIGH 否则按置信、`sources` 含一条 `sourceType=KNOWLEDGE / sourceRefId=sourceVersionId`、`explanationJson` 放支持/反对/缺失/鉴别）；组 `RecommendationTriggerRequest(triggerType=DIAGNOSIS_ASSIST, contextSnapshotId, candidateCards)` 调 `recommendationEngineService.trigger(...)`。注意：方法改为 `@Transactional`（写）；候选为空则不触发、仅返回空态。

- [ ] **Step 4: 实现控制器（完整）**

```java
package com.medkernel.engine.knowledge.diagnosis.runtime;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;

/** 运行时鉴别诊断 API（归推荐引擎客户面，复用推荐写权限与卡治理）。 */
@RestController
@RequestMapping("/api/v1/engine/recommendations")
@DataScope(requireTenant = true)
public class DiagnosisAssistController {

    private final DiagnosisAssistService service;

    public DiagnosisAssistController(DiagnosisAssistService service) {
        this.service = service;
    }

    @PostMapping("/diagnosis-assist")
    @PreAuthorize("@perm.has('recommendation.write')")
    public ApiResult<DiagnosisAssistResponse> diagnosisAssist(@RequestBody @Valid DiagnosisAssistRequest request) {
        return ApiResult.ok(service.assist(request));
    }
}
```

- [ ] **Step 5: 运行验证通过 + 全量回归 + T-GATE**

Run: `mvn -q -Dtest=DiagnosisAssistApiContractTest,DiagnosisAssistControllerSecurityTest test` — Expected: PASS
Run: `cd medkernel-backend && mvn -q test` — Expected: 全绿，H2/PostgreSQL/Oracle 迁移到 V67 二次 no-op。
Run（changed T-GATE）: `node ../scripts/authenticity-guard.mjs --changed` — Expected: 0 阻断（无 Math.random/吞错返成功/假 hash/占位 Javadoc）。

- [ ] **Step 6: 提交** — `git commit -m "feat(diagnosis): diagnosis-assist API + 推荐卡统一落库治理 + 契约安全测试"`

---

## Self-Review（对设计 4.3/4.4/4.8）

**Spec 覆盖：**
- 取患者发现 + 标准化部分可用 → Task 2（`DiagnosisFindingExtractor` + `ENG_DX_002` unmapped 清单）。
- 候选并列、不排他 → Task 5（遍历各 ACTIVE 版本各自成候选，WEAK 折叠非排除）。
- 证据装配（支持/反对/缺失） → 复用 Plan A `DiagnosisMatchResult`，落入 `DiagnosisCandidate`。
- 置信非概率 → 复用 Plan A `DiagnosisConfidence`（无百分比字段）。
- 红线合流、强优先、OPT-04 未就绪诚实降级 → Task 4 + 排序 `redline` 优先。
- 排序（高危>证据>来源） → Task 5 `rankComparator`。
- 统一治理（复用 trigger） → Task 6 step 3。
- 空态非排除 → Task 5 `ADVISORY_EMPTY` + Task 1 `ENG_DX_003`。
- 不自动诊断 → `requiresPhysicianConfirmation=true`、只读 assist + 卡需医师反馈。

**类型一致性：** `ExtractedFindings(normalizedCodes:Set,unmappedFindings:List)`、`RedlineHit(identityCode,severity,reason)`、`DiagnosisCandidate(...confidence:DiagnosisConfidence...redline:boolean,sourceVersionId)`、`DiagnosisAssistResponse(candidates,unmappedFindings,advisoryNote,traceId)`、`matcher.match(Set,List,policy)`（与 Plan A 一致）、`policies.findByTenantIdAndScopeKey`（Plan A Task 4 定义）— 一致。

**占位扫描：** `DefaultFindingNormalizationPort`/`DefaultDiagnosisRedlinePort` 的 `TODO(接线)` 是**有意的诚实降级挂点**（OPT-04/TERM 深接未就绪时不伪造），非计划占位；其行为有测试覆盖（返回空/不阻断）。其余步骤均给完整代码或明确接线指令。

**待执行者确认的真实签名（实现时核对）：** `ContextSnapshotService` 按 id 取快照的方法名、`Canonical*` 的 `code()/codeSystem()` 访问器、`CanonicalResourceType` 枚举值、`KnowledgeAssetVersion.authorityLevel()`、`RecommendationTriggerRequest`/`RecommendationCardRequest` 构造参数顺序。

---

## 执行交接

Plan A + Plan B 合起来交付 Spec 1 全量：诊断知识可建可维护可发布（A）+ 运行时产出可解释、可降级、守监管边界的鉴别诊断卡（B）。两计划同分支评审后，按 Plan A→Plan B 顺序逐任务 TDD 执行。
