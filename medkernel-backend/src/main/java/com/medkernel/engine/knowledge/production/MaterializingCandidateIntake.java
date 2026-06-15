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

    /** 据 PR3 路由建去重角色分派计划（归口 ∪ 领域异于归口时）。 */
    private ReviewAssignmentPlan assignmentPlan(ReviewRoutingDecision routing) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add(routing.ownerReviewerRole().code());
        roles.add(routing.domainReviewerRole().code());
        return new ReviewAssignmentPlan(List.copyOf(roles));
    }
}
