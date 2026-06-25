package com.medkernel.engine.knowledge.production;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.knowledge.CandidateClassification;
import com.medkernel.engine.knowledge.CandidateClassificationRepository;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 候选共存读模型服务（AIK-STD-09/11）。
 *
 * <p>解析生产候选引用 {@code kv:{identityId}:{versionNo}}，只读拼接待审候选、当前 {@code ACTIVE}、
 * 新旧识别结果与生产血缘。服务不发布、不改状态、不调用模型，用于审核台明确「候选不执行、现行仍执行」。
 */
@Service
public class CandidateCoexistenceService {

    private final KnowledgeAssetVersionRepository versionRepository;
    private final CandidateClassificationRepository classificationRepository;
    private final KnowledgeProductionCandidateRepository candidateRepository;
    private final KnowledgeProductionJobRepository jobRepository;

    public CandidateCoexistenceService(KnowledgeAssetVersionRepository versionRepository,
                                       CandidateClassificationRepository classificationRepository,
                                       KnowledgeProductionCandidateRepository candidateRepository,
                                       KnowledgeProductionJobRepository jobRepository) {
        this.versionRepository = versionRepository;
        this.classificationRepository = classificationRepository;
        this.candidateRepository = candidateRepository;
        this.jobRepository = jobRepository;
    }

    /** 返回候选与现行权威版本的共存对照；非待审候选返回不可审核读态，不伪装成共存审核。 */
    @Transactional(readOnly = true)
    public CandidateCoexistenceView resolve(String candidateRef) {
        String tenantId = requireCurrentTenant();
        ParsedCandidateRef ref = parse(candidateRef);
        String normalizedRef = candidateRef.trim();
        KnowledgeAssetVersion candidate = versionRepository
            .findByTenantIdAndIdentityIdAndVersionNo(tenantId, ref.identityId(), ref.versionNo())
            .orElseThrow(() -> ApiException.notFound("知识候选引用 " + candidateRef));
        KnowledgeAssetVersion active = versionRepository.findActiveByEffectiveScope(
            tenantId,
            candidate.identityId(),
            candidate.effectiveOrganizationScope(),
            candidate.effectiveApplicableScope()).orElse(null);
        CandidateCoexistenceView.ProductionLineage lineage = lineage(tenantId, normalizedRef);
        if (candidate.status() != KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW) {
            return CandidateCoexistenceView.notReplacementReview(normalizedRef, candidate, active, lineage);
        }
        CandidateClassification classification = classificationRepository
            .findByTenantIdAndCandidateVersionId(tenantId, candidate.id())
            .orElse(null);
        return CandidateCoexistenceView.of(normalizedRef, candidate, active, classification, lineage);
    }

    private CandidateCoexistenceView.ProductionLineage lineage(String tenantId, String candidateRef) {
        List<KnowledgeProductionCandidate> rows =
            candidateRepository.findByTenantIdAndCandidateRefIn(tenantId, List.of(candidateRef.trim()));
        if (rows.isEmpty()) {
            return null;
        }
        KnowledgeProductionCandidate row = rows.get(0);
        KnowledgeProductionJob job = jobRepository.findByTenantIdAndJobCode(tenantId, row.jobCode()).orElse(null);
        return CandidateCoexistenceView.ProductionLineage.from(row, job);
    }

    private ParsedCandidateRef parse(String candidateRef) {
        if (candidateRef == null || candidateRef.isBlank() || !candidateRef.trim().startsWith("kv:")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选引用格式必须为 kv:{identityId}:{versionNo}");
        }
        String ref = candidateRef.trim();
        int versionSeparator = ref.indexOf(':', 3);
        if (versionSeparator < 0 || versionSeparator == ref.length() - 1) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选引用格式必须为 kv:{identityId}:{versionNo}");
        }
        String identityPart = ref.substring(3, versionSeparator);
        String versionNo = ref.substring(versionSeparator + 1).trim();
        if (versionNo.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选引用版本号不能为空");
        }
        try {
            return new ParsedCandidateRef(Long.valueOf(identityPart), versionNo);
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选引用身份 id 必须是数字", ex);
        }
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private record ParsedCandidateRef(Long identityId, String versionNo) {
    }
}
