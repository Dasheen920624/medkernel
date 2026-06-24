package com.medkernel.engine.knowledge.production.generation;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.authoring.GeneratedAssetCandidateRequest;
import com.medkernel.engine.authoring.GeneratedAssetCandidateService;
import com.medkernel.engine.authoring.GeneratedAssetDraftResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
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
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 从受控来源生成统一资产草稿的编排服务（AIK-STD-04，FR-1~5）。
 *
 * <p>载入解析后来源版本的带锚点片段，为知识、规则、路径建立确定性
 * （{@link KnowledgeProducer#MANUAL}/B0）生产 job，调 {@link SourceCandidateGenerator}
 * 产模板桩候选，经 AIK-STD-01 校验闸、分流与影子验证后物化：
 * 知识继续进入知识候选审核链；规则/路径直接进入统一资产版本草稿，不再伪装成知识版本。
 * 来源无片段则诚实跳过、不建 job。
 */
@Service
public class CandidateGenerationOrchestrationService {

    private final SourceVersionRepository versions;
    private final SourceDocumentRepository documents;
    private final SourceFragmentRepository fragments;
    private final KnowledgeIdentityRepository identities;
    private final SourceCandidateGenerator generator;
    private final KnowledgeProductionOrchestrationService production;
    private final CandidateSafetyGateService gateService;
    private final KnowledgeGenerationTriageService triageService;
    private final KnowledgeShadowEvaluationService shadowService;
    private final GeneratedAssetCandidateService generatedAssets;
    private final ObjectMapper json = new ObjectMapper();

    public CandidateGenerationOrchestrationService(SourceVersionRepository versions,
                                                   SourceDocumentRepository documents,
                                                   SourceFragmentRepository fragments,
                                                   KnowledgeIdentityRepository identities,
                                                   SourceCandidateGenerator generator,
                                                   KnowledgeProductionOrchestrationService production,
                                                   CandidateSafetyGateService gateService,
                                                   KnowledgeGenerationTriageService triageService,
                                                   KnowledgeShadowEvaluationService shadowService,
                                                   GeneratedAssetCandidateService generatedAssets) {
        this.versions = versions;
        this.documents = documents;
        this.fragments = fragments;
        this.identities = identities;
        this.generator = generator;
        this.production = production;
        this.gateService = gateService;
        this.triageService = triageService;
        this.shadowService = shadowService;
        this.generatedAssets = generatedAssets;
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
            if (!supportsSourceGeneration(item.assetType())) {
                skipped.add(new SkippedType(item.assetType(),
                    "受控来源模板生成仅支持知识、规则和路径草稿，其他资产请使用对应维护入口"));
                continue;
            }
            KnowledgeIdentity targetIdentity = resolveTargetIdentity(tenantId, item.target());
            ProductionJobResponse job = production.createJob(new ProductionJobRequest(
                "source-version:" + version.id(), item.assetType(), KnowledgeProducer.MANUAL,
                request.targetPipeline(), request.domain(), null));
            KnowledgeAssetEnvelope envelope = generator.generate(
                tenantId, document, version, sourceFragments, item.assetType(),
                resolveKnowledgeDomain(item, targetIdentity),
                deriveIdentity(item.target(), targetIdentity));
            // AIK-STD-05：候选须过安全门禁才提审；不过即拦截、诚实报因、不静默放行（铁律 #1）。
            GateOutcome outcome = gateService.evaluate(
                envelope,
                new GateContext(tenantId, job.jobCode(), item.target().targetIdentityId()));
            if (!outcome.passed()) {
                blocked.add(new BlockedCandidate(item.assetType(), job.jobCode(), outcome.failedItems()));
                production.cancelJob(job.jobCode());
                continue;
            }
            GenerationTriageDecision triage = triageService.evaluate(envelope, new GenerationTriageContext(
                tenantId, job.jobCode(), item.target().targetIdentityId(), item.assetType()));
            if (!triage.shouldSubmit()) {
                skipped.add(new SkippedType(item.assetType(), "生成期分流跳过：" + triage.basis()));
                production.completeJob(job.jobCode());
                continue;
            }
            KnowledgeShadowDecision shadow = shadowService.evaluate(envelope, new KnowledgeShadowContext(
                tenantId, job.jobCode(), item.target().targetIdentityId(), item.assetType()));
            if (!shadow.readyForReview()) {
                blocked.add(new BlockedCandidate(item.assetType(), job.jobCode(),
                    List.of(GateItemResult.fail(KnowledgeShadowEvaluationService.SHADOW_GATE_CODE, shadow.basis()))));
                production.cancelJob(job.jobCode());
                continue;
            }
            if (item.assetType() != VersionedAssetType.KNOWLEDGE) {
                GeneratedAssetDraftResponse draft = materializeGeneratedDraft(
                    tenantId, version, item, job, envelope, targetIdentity, blocked);
                if (draft == null) {
                    production.cancelJob(job.jobCode());
                    continue;
                }
                generated.add(new GeneratedCandidate(
                    item.assetType(), job.jobCode(), "asset-version:" + draft.versionId(), null));
                production.completeJob(job.jobCode());
                continue;
            }
            CandidateSubmissionResponse response =
                production.submitCandidate(job.jobCode(), envelope, item.target());
            generated.add(new GeneratedCandidate(
                item.assetType(), job.jobCode(), response.candidateRef(), response.routing()));
            production.completeJob(job.jobCode());
        }
        return new GenerationSummary(generated, skipped, blocked);
    }

    private GeneratedAssetDraftResponse materializeGeneratedDraft(
            String tenantId,
            SourceVersion version,
            GenerationItem item,
            ProductionJobResponse job,
            KnowledgeAssetEnvelope envelope,
            KnowledgeIdentity targetIdentity,
            List<BlockedCandidate> blocked) {
        try {
            return generatedAssets.materializeDraft(new GeneratedAssetCandidateRequest(
                tenantId,
                item.assetType(),
                deriveIdentity(item.target(), targetIdentity),
                tenantId,
                "ALL",
                "source-version:" + version.id(),
                RequestContext.currentUserId().orElse("system"),
                RequestContext.currentTraceId(),
                json.readTree(envelope.payload()),
                List.of()
            ));
        } catch (JsonProcessingException | ApiException invalidDraft) {
            blocked.add(new BlockedCandidate(item.assetType(), job.jobCode(), List.of(
                GateItemResult.fail("GENERATED_ASSET_SCHEMA", invalidDraft.getMessage()))));
            return null;
        }
    }

    private boolean supportsSourceGeneration(VersionedAssetType assetType) {
        return assetType == VersionedAssetType.KNOWLEDGE
            || assetType == VersionedAssetType.RULE
            || assetType == VersionedAssetType.PATHWAY;
    }

    private String deriveIdentity(MaterializationTarget target, KnowledgeIdentity targetIdentity) {
        return targetIdentity != null ? targetIdentity.identityCode() : target.newIdentity().identityCode();
    }

    private KnowledgeDomain resolveKnowledgeDomain(
            GenerationItem item,
            KnowledgeIdentity targetIdentity) {
        if (item.assetType() != com.medkernel.engine.versioning.VersionedAssetType.KNOWLEDGE) {
            return null;
        }
        if (targetIdentity != null) {
            return targetIdentity.domain();
        }
        MaterializationTarget target = item.target();
        if (target.newIdentity() == null || target.newIdentity().domain() == null) {
            throw new ApiException(
                com.medkernel.shared.api.error.ErrorCode.BAD_REQUEST,
                "新知识身份必须声明知识领域");
        }
        return target.newIdentity().domain();
    }

    private KnowledgeIdentity resolveTargetIdentity(String tenantId, MaterializationTarget target) {
        if (target.targetIdentityId() == null) {
            return null;
        }
        return identities.findByTenantIdAndId(tenantId, target.targetIdentityId())
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + target.targetIdentityId()));
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
