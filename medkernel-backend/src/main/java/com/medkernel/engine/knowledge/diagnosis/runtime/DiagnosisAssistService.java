package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidence;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidencePolicy;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidencePolicyRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterion;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterionRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisMatchResult;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisMatcher;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运行时鉴别诊断编排：发现 → 命中各 ACTIVE 诊断版本 → 红线合流 → 排序 → 候选。
 *
 * <p>置信仍是分级非概率；弱支持默认折叠避免噪声；无候选返回空态明示"非排除诊断"。
 * 落库治理（复用推荐卡 trigger）在 Task 6 接线。
 */
@Service
public class DiagnosisAssistService {

    static final String ADVISORY_EMPTY =
        "系统无足够依据给出诊断提示，这不是排除诊断结论，请医师结合临床判断。";
    private static final String ADVISORY_CANDIDATES = "辅助建议，需医师确认（非自动诊断）。";

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
        // findById 自取当前租户；snapshot 不存在自抛 ENG-CONTEXT 错误，响应体含 resources。
        ContextSnapshotResponse snapshot = snapshots.findById(request.contextSnapshotId());
        ExtractedFindings findings = extractor.extract(tenant, snapshot.resources());
        DiagnosisConfidencePolicy policy = resolvePolicy(tenant);
        Set<String> redlineCodes = redlineCodes(tenant, findings.normalizedCodes());

        List<DiagnosisCandidate> candidates = new ArrayList<>();
        for (KnowledgeAssetVersion v : versions.findActiveDiagnosisVersions(tenant)) {
            List<DiagnosisCriterion> versionCriteria = criteria.findByTenantIdAndDiagnosisVersionId(tenant, v.id());
            DiagnosisMatchResult result = matcher.match(findings.normalizedCodes(), versionCriteria, policy);
            if (result.confidence() == DiagnosisConfidence.WEAK && !result.hitExclusion()) {
                continue; // 弱支持默认不并列呈现，避免低价值噪声（低打扰）
            }
            KnowledgeIdentity identity = identities.findByTenantIdAndId(tenant, v.identityId()).orElse(null);
            boolean redline = identity != null && redlineCodes.contains(identity.identityCode());
            candidates.add(new DiagnosisCandidate(
                v.identityId(),
                identity == null ? null : identity.subject(),
                identity == null ? null : identity.identityCode(),
                result.confidence(),
                result.supporting(), result.refuting(), result.missingRequired(),
                v.authorityLevel() == null ? null : v.authorityLevel().name(), redline, v.id()));
        }
        candidates.sort(rankComparator());
        return new DiagnosisAssistResponse(candidates, findings.unmappedFindings(),
            candidates.isEmpty() ? ADVISORY_EMPTY : ADVISORY_CANDIDATES, traceId());
    }

    private DiagnosisConfidencePolicy resolvePolicy(String tenant) {
        return policies.findByTenantIdAndScopeKey(tenant, "DEFAULT")
            .or(() -> policies.findByTenantIdAndScopeKey("t-1", "DEFAULT"))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_DX_005, "缺少默认置信策略 DEFAULT"));
    }

    private Set<String> redlineCodes(String tenant, Set<String> findings) {
        return redlinePort.check(tenant, findings).stream()
            .map(RedlineHit::identityCode)
            .collect(Collectors.toUnmodifiableSet());
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
