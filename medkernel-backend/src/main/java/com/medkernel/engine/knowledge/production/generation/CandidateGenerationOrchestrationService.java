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
import com.medkernel.engine.knowledge.production.gate.CandidateSafetyGateService;
import com.medkernel.engine.knowledge.production.gate.GateContext;
import com.medkernel.engine.knowledge.production.gate.GateItemResult;
import com.medkernel.engine.knowledge.production.gate.GateOutcome;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowContext;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowDecision;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowEvaluationService;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageContext;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageDecision;
import com.medkernel.engine.knowledge.production.triage.KnowledgeGenerationTriageService;
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
    private final CandidateSafetyGateService gateService;
    private final KnowledgeGenerationTriageService triageService;
    private final KnowledgeShadowEvaluationService shadowService;

    public CandidateGenerationOrchestrationService(SourceVersionRepository versions,
                                                   SourceDocumentRepository documents,
                                                   SourceFragmentRepository fragments,
                                                   SourceCandidateGenerator generator,
                                                   KnowledgeProductionOrchestrationService production,
                                                   CandidateSafetyGateService gateService,
                                                   KnowledgeGenerationTriageService triageService,
                                                   KnowledgeShadowEvaluationService shadowService) {
        this.versions = versions;
        this.documents = documents;
        this.fragments = fragments;
        this.generator = generator;
        this.production = production;
        this.gateService = gateService;
        this.triageService = triageService;
        this.shadowService = shadowService;
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
        List<BlockedCandidate> blocked = new ArrayList<>();

        if (sourceFragments.isEmpty()) {
            for (GenerationItem item : request.items()) {
                skipped.add(new SkippedType(item.assetType(), "来源无锚点片段，无源不生成"));
            }
            return new GenerationSummary(generated, skipped, blocked);
        }

        for (GenerationItem item : request.items()) {
            item.target().validate();
            ProductionJobResponse job = production.createJob(new ProductionJobRequest(
                "source-version:" + version.id(), item.assetType(), KnowledgeProducer.MANUAL,
                request.targetPipeline(), request.domain(), null));
            KnowledgeAssetEnvelope envelope = generator.generate(
                tenantId, document, version, sourceFragments, item.assetType(),
                deriveIdentity(item.target()));
            // AIK-STD-05：候选须过安全门禁才提审；不过即拦截、诚实报因、不静默放行（铁律 #1）。
            GateOutcome outcome = gateService.evaluate(
                envelope,
                new GateContext(tenantId, job.jobCode(), item.target().targetIdentityId()));
            if (!outcome.passed()) {
                blocked.add(new BlockedCandidate(item.assetType(), job.jobCode(), outcome.failedItems()));
                continue;
            }
            GenerationTriageDecision triage = triageService.evaluate(envelope, new GenerationTriageContext(
                tenantId, job.jobCode(), item.target().targetIdentityId(), item.assetType()));
            if (!triage.shouldSubmit()) {
                skipped.add(new SkippedType(item.assetType(), "生成期分流跳过：" + triage.basis()));
                continue;
            }
            KnowledgeShadowDecision shadow = shadowService.evaluate(envelope, new KnowledgeShadowContext(
                tenantId, job.jobCode(), item.target().targetIdentityId(), item.assetType()));
            if (!shadow.readyForReview()) {
                blocked.add(new BlockedCandidate(item.assetType(), job.jobCode(),
                    List.of(GateItemResult.fail(KnowledgeShadowEvaluationService.SHADOW_GATE_CODE, shadow.basis()))));
                continue;
            }
            CandidateSubmissionResponse response =
                production.submitCandidate(job.jobCode(), envelope, item.target());
            generated.add(new GeneratedCandidate(
                item.assetType(), job.jobCode(), response.candidateRef(), response.routing()));
        }
        return new GenerationSummary(generated, skipped, blocked);
    }

    private String deriveIdentity(MaterializationTarget target) {
        return target.targetIdentityId() != null
            ? "identity:" + target.targetIdentityId()
            : target.newIdentity().identityCode();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
