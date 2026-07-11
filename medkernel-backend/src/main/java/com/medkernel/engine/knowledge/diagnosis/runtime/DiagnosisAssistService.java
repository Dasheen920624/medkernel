package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidence;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidencePolicy;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidencePolicyRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCarePointerRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterion;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterionRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDifferential;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDifferentialRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisMatchResult;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisMatcher;
import com.medkernel.engine.recommendation.KnowledgeSourceLocator;
import com.medkernel.engine.recommendation.RecommendationCardRequest;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationSourceRequest;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.BusinessMetrics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运行时鉴别诊断编排：发现 → 当前机构生效版本锁定诊断版本 → 红线合流 → 排序 → 候选 → 复用推荐卡统一落库治理。
 *
 * <p>置信仍是分级非概率；弱支持默认折叠避噪声；无候选返回空态明示"不是排除诊断"、不落库；
 * 候选恒 requiresPhysicianConfirmation=true、非自动诊断。落库走 CDS Hook patient-view + scenarioCode S16。
 */
@Service
public class DiagnosisAssistService {

    static final String ADVISORY_EMPTY =
        "系统无足够依据给出诊断提示，这不是排除诊断结论，请医师结合临床判断。";
    private static final String ADVISORY_CANDIDATES = "辅助建议，需医师确认（非自动诊断）。";
    // 诊断辅助发生在 patient-view 触发点；业务场景保留在 scenarioCode（S16 辅助诊疗）。
    private static final String TRIGGER_HOOK = "patient-view";
    private static final String SCENARIO_CODE = "S16";

    private final ContextSnapshotService snapshots;
    private final RuntimeReleaseDiagnosisSelector runtimeDiagnoses;
    private final DiagnosisCriterionRepository criteria;
    private final DiagnosisCarePointerRepository carePointers;
    private final DiagnosisDifferentialRepository differentials;
    private final KnowledgeIdentityRepository identities;
    private final DiagnosisConfidencePolicyRepository policies;
    private final DiagnosisMatcher matcher;
    private final DiagnosisFindingExtractor extractor;
    private final DiagnosisRedlinePort redlinePort;
    private final RecommendationEngineService recommendationEngine;
    private final ObjectMapper objectMapper;
    private final BusinessMetrics businessMetrics;

    public DiagnosisAssistService(ContextSnapshotService snapshots,
            RuntimeReleaseDiagnosisSelector runtimeDiagnoses,
            DiagnosisCriterionRepository criteria,
            DiagnosisCarePointerRepository carePointers,
            DiagnosisDifferentialRepository differentials,
            KnowledgeIdentityRepository identities,
            DiagnosisConfidencePolicyRepository policies,
            DiagnosisMatcher matcher,
            DiagnosisFindingExtractor extractor, DiagnosisRedlinePort redlinePort,
            RecommendationEngineService recommendationEngine, ObjectMapper objectMapper,
            BusinessMetrics businessMetrics) {
        this.snapshots = snapshots;
        this.runtimeDiagnoses = runtimeDiagnoses;
        this.criteria = criteria;
        this.carePointers = carePointers;
        this.differentials = differentials;
        this.identities = identities;
        this.policies = policies;
        this.matcher = matcher;
        this.extractor = extractor;
        this.redlinePort = redlinePort;
        this.recommendationEngine = recommendationEngine;
        this.objectMapper = objectMapper;
        this.businessMetrics = businessMetrics;
    }

    @Transactional
    public DiagnosisAssistResponse assist(DiagnosisAssistRequest request) {
        String tenant = tenant();
        // findById 自取当前租户；snapshot 不存在自抛 ENG-CONTEXT 错误，响应体含 resources。
        ContextSnapshotResponse snapshot = snapshots.findById(request.contextSnapshotId());
        ExtractedFindings findings = extractor.extract(
            tenant, snapshot.runtimeReleaseId(), snapshot.resources());
        List<RuntimeDiagnosisReference> runtimeDiagnosisVersions =
            runtimeDiagnoses.select(tenant, snapshot.runtimeReleaseId());
        DiagnosisConfidencePolicy policy = resolvePolicy(tenant);
        // 红线合流：OPT-04 红线对结构化上下文求值，命中的致命病 / 危急值诊断身份码置顶且不可疲劳抑制。
        Set<String> redlinePinned = redlinePort.pinnedDiagnosisCodes(tenant, snapshot);

        List<DiagnosisCandidate> candidates = new ArrayList<>();
        for (RuntimeDiagnosisReference diagnosis : runtimeDiagnosisVersions) {
            List<DiagnosisCriterion> versionCriteria =
                criteria.findByTenantIdAndDiagnosisVersionId(diagnosis.sourceTenantId(), diagnosis.knowledgeVersionId());
            DiagnosisMatchResult result = matcher.match(findings.normalizedCodes(), versionCriteria, policy);
            if (result.confidence() == DiagnosisConfidence.WEAK && !result.hitExclusion()) {
                continue; // 弱支持默认不并列呈现，避免低价值噪声（低打扰）
            }
            boolean redline = redlinePinned.contains(diagnosis.identityCode());
            List<DiagnosisDifferentialSuggestion> differentialSuggestions =
                differentialSuggestions(diagnosis.sourceTenantId(), diagnosis.knowledgeVersionId());
            List<DiagnosisCareSuggestion> careSuggestions = carePointers
                .findByTenantIdAndDiagnosisVersionId(diagnosis.sourceTenantId(), diagnosis.knowledgeVersionId()).stream()
                .map(pointer -> new DiagnosisCareSuggestion(
                    pointer.pointerType(), pointer.targetType(), pointer.targetRef(),
                    pointer.description(), true))
                .toList();
            candidates.add(new DiagnosisCandidate(
                diagnosis.identityId(),
                diagnosis.diagnosisName(),
                diagnosis.identityCode(),
                result.confidence(),
                result.supporting(), result.refuting(), result.missingRequired(),
                differentialSuggestions,
                careSuggestions,
                diagnosis.authorityLevel(), redline, diagnosis.sourceTenantId(), diagnosis.knowledgeVersionId()));
        }
        candidates.sort(rankComparator());
        // 可观测（设计 §4.8）：调用数 + 候选分级分布；采纳率由推荐卡反馈层覆盖（诊断卡即推荐卡）。
        businessMetrics.incDiagnosisAssist();
        candidates.forEach(c -> businessMetrics.incDiagnosisCandidate(c.confidence().name()));
        persist(request.contextSnapshotId(), findings.normalizedCodes(), candidates);
        return new DiagnosisAssistResponse(candidates, findings.unmappedFindings(),
            candidates.isEmpty() ? ADVISORY_EMPTY : ADVISORY_CANDIDATES, traceId());
    }

    /** 落库治理：候选转推荐卡，经 RecommendationEngineService.trigger 统一持久化 / 审计 / 疲劳；空态不落库。 */
    private void persist(String snapshotId, Set<String> findingCodes, List<DiagnosisCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        List<RecommendationCardRequest> cards = candidates.stream().map(this::toCard).toList();
        recommendationEngine.trigger(new RecommendationTriggerRequest(
            "DX-" + snapshotId, TRIGGER_HOOK, null, snapshotId, null, null, null,
            SCENARIO_CODE, inputDigest(findingCodes), null, cards, Boolean.FALSE));
    }

    private RecommendationCardRequest toCard(DiagnosisCandidate c) {
        String name = c.diagnosisName() == null ? String.valueOf(c.identityId()) : c.diagnosisName();
        String suggestedAction = c.careSuggestions().isEmpty()
            ? "辅助建议，请医师确认（非自动诊断）"
            : "医师确认诊断后查看 " + c.careSuggestions().size() + " 项诊疗建议";
        return new RecommendationCardRequest(
            "dx-" + c.identityId(),
            RecommendationCardType.DIAGNOSIS,
            name,
            "支持 " + c.supporting().size() + " 项 / 缺失必需 " + c.missingRequired().size() + " 项",
            suggestedAction,
            c.redline() ? RecommendationRiskLevel.HIGH : RecommendationRiskLevel.LOW,
            RecommendationInterruptLevel.INFO,
            true,
            false,
            "置信 " + c.confidence() + "；来源诊断知识版本 " + c.sourceVersionId(),
            explanationJson(c),
            "dx:" + c.identityId(),
            null,
            null,
            List.of(new RecommendationSourceRequest(
                RecommendationSourceType.KNOWLEDGE, c.icdCode(), null,
                name, KnowledgeSourceLocator.citationLocator(c.sourceTenantId(), c.sourceVersionId()),
                null, "诊断知识命中")));
    }

    private String explanationJson(DiagnosisCandidate c) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "supporting", c.supporting(),
                "refuting", c.refuting(),
                "missingRequired", c.missingRequired(),
                "differentials", c.differentials(),
                "careSuggestions", c.careSuggestions(),
                "confidence", c.confidence().name()));
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "诊断解释序列化失败", e);
        }
    }

    private String inputDigest(Set<String> findingCodes) {
        // 发现集稳定摘要（排序后拼接），同输入同摘要，供推荐触发审计 / 幂等。
        return "dx:" + String.join(",", new TreeSet<>(findingCodes));
    }

    private List<DiagnosisDifferentialSuggestion> differentialSuggestions(
            String sourceTenantId,
            Long diagnosisVersionId) {
        return differentials.findByTenantIdAndDiagnosisVersionId(sourceTenantId, diagnosisVersionId).stream()
            .map(differential -> toDifferentialSuggestion(sourceTenantId, differential))
            .toList();
    }

    private DiagnosisDifferentialSuggestion toDifferentialSuggestion(
            String sourceTenantId,
            DiagnosisDifferential differential) {
        KnowledgeIdentity target = identities
            .findByTenantIdAndId(sourceTenantId, differential.differentialIdentityId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_DX_001,
                "鉴别诊断目标身份不存在：" + differential.differentialIdentityId()));
        if (!target.isActive()) {
            throw new ApiException(
                ErrorCode.ENG_DX_001,
                "鉴别诊断目标身份未激活：" + target.identityCode());
        }
        return new DiagnosisDifferentialSuggestion(
            differential.differentialIdentityId(),
            target.identityCode(),
            target.subject(),
            differential.keyPoint(),
            differential.suggestedWorkup()
        );
    }

    private DiagnosisConfidencePolicy resolvePolicy(String tenant) {
        return policies.findByTenantIdAndScopeKey(tenant, "DEFAULT")
            .or(() -> policies.findByTenantIdAndScopeKey(PlatformTenant.ID, "DEFAULT"))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_DX_005, "缺少默认置信策略 DEFAULT"));
    }

    /** 高危先行 > 证据充分（置信）> 来源可信（A&lt;B&lt;C…）。 */
    private Comparator<DiagnosisCandidate> rankComparator() {
        return Comparator.comparingInt((DiagnosisCandidate c) -> c.redline() ? 0 : 1)
            .thenComparingInt(c -> confidenceRank(c.confidence()))
            .thenComparing(c -> c.authorityLevel() == null ? "Z" : c.authorityLevel());
    }

    private int confidenceRank(DiagnosisConfidence c) {
        return switch (c) {
            case STRONG -> 0;
            case MODERATE -> 1;
            case EXCLUDE -> 2;
            case WEAK -> 3;
        };
    }

    private String tenant() {
        String t = RequestContext.currentOrgScope().tenantId();
        if (t == null || t.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return t;
    }

    private String traceId() {
        return RequestContext.currentTraceId();
    }
}
