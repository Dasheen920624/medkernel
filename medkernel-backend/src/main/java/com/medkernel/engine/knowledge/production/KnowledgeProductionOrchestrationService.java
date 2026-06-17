package com.medkernel.engine.knowledge.production;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.KnowledgeAssetSchemaValidator;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/**
 * 知识生产编排服务（AIK-STD-13）。
 *
 * <p>统一编排层：四生产器产物统一进候选池走同一流水线，落双形态物理隔离（§9 平台主源不可污染 / 客户覆盖不反写），
 * 是「AI 只产候选不产事实」红线归口。PR1 落 job 生命周期 + 隔离守卫 + 候选血缘/审计；候选物化经
 * {@link KnowledgeCandidateIntake} 端口走既有版本/审核链。
 */
@Service
public class KnowledgeProductionOrchestrationService {

    private final KnowledgeProductionJobRepository jobRepository;
    private final KnowledgeProductionCandidateRepository candidateRepository;
    private final KnowledgeCandidateIntake candidateIntake;
    private final KnowledgeAssetSchemaValidator schemaValidator;
    private final AuditRecorder auditRecorder;
    private final CandidateReviewRouter reviewRouter;

    public KnowledgeProductionOrchestrationService(
            KnowledgeProductionJobRepository jobRepository,
            KnowledgeProductionCandidateRepository candidateRepository,
            KnowledgeCandidateIntake candidateIntake,
            KnowledgeAssetSchemaValidator schemaValidator,
            AuditRecorder auditRecorder,
            CandidateReviewRouter reviewRouter) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.candidateIntake = candidateIntake;
        this.schemaValidator = schemaValidator;
        this.auditRecorder = auditRecorder;
        this.reviewRouter = reviewRouter;
    }

    /**
     * 建生产 job（FR-1）。FR-4 双形态隔离：平台主源管道仅平台租户、院内覆盖管道仅客户租户，越界拒。
     */
    @Transactional
    public ProductionJobResponse createJob(ProductionJobRequest request) {
        String tenantId = requireCurrentTenant();
        guardPipelineOwnership(request.targetPipeline(), tenantId);
        String actor = currentActor();
        Instant now = Instant.now();
        String jobCode = UUID.randomUUID().toString();
        String lineage = "{\"producer\":\"" + request.producer()
            + "\",\"pipeline\":\"" + request.targetPipeline()
            + "\",\"modelStrategy\":\"" + (request.modelStrategy() == null ? "" : request.modelStrategy())
            + "\",\"createdAt\":\"" + now + "\"}";
        KnowledgeProductionJob job = jobRepository.save(new KnowledgeProductionJob(
            null, tenantId, jobCode, request.sourceScope(), request.assetType(), request.producer(),
            request.targetPipeline(), request.domain(), request.modelStrategy(), ProductionJobStatus.PENDING,
            0, lineage, now, actor, now, actor, RequestContext.currentTraceId()));
        auditRecorder.record(AuditAction.CREATE, "mk_knowledge_production_job", jobCode,
            "建知识生产 job producer=" + request.producer() + " pipeline=" + request.targetPipeline()
                + " assetType=" + request.assetType());
        return ProductionJobResponse.from(job);
    }

    /**
     * 提交候选（FR-3）：经 AIK-STD-01 校验闸 + §9 隔离守卫，记血缘审计 + 计数，候选物化经 intake 端口。
     */
    @Transactional
    public CandidateSubmissionResponse submitCandidate(String jobCode, KnowledgeAssetEnvelope candidate,
                                                       MaterializationTarget target) {
        String tenantId = requireCurrentTenant();
        KnowledgeProductionJob job = jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("知识生产 job=" + jobCode));

        schemaValidator.validate(candidate);

        // FR-4 §9 红线：院内覆盖管道候选禁反写平台主源 t-1。
        if (job.targetPipeline() == TargetPipeline.TENANT_OVERLAY
            && PlatformTenant.isPlatformTenant(candidate.orgScope())) {
            throw new ApiException(ErrorCode.KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION,
                "院内覆盖候选禁反写平台主源 t-1");
        }
        if (job.targetPipeline() == TargetPipeline.PLATFORM_SOURCE
            && !PlatformTenant.isPlatformTenant(candidate.orgScope())) {
            throw new ApiException(ErrorCode.KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION,
                "客户候选禁产平台主源，平台主源管道仅允许 t-1 候选");
        }
        if (candidate.assetType() != job.assetType()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                "候选资产类型与 job 不一致：job=" + job.assetType() + " 候选=" + candidate.assetType());
        }
        if (!job.tenantId().equals(candidate.orgScope())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                "候选组织作用域与 job 租户不一致，禁跨租户入池");
        }

        ReviewRoutingDecision routing = reviewRouter.resolve(
            job.targetPipeline(), job.domain(), candidate.riskLevel());
        String candidateRef = candidateIntake.intake(job, candidate, target, routing);

        Instant now = Instant.now();
        String actor = currentActor();
        // FR-5 候选生产血缘：每条提交候选落一行回溯（job/身份/指纹/候选引用/时点）。
        candidateRepository.save(new KnowledgeProductionCandidate(null, tenantId, jobCode,
            candidate.assetIdentity(), candidate.contentHash(), candidateRef, candidate.riskLevel(),
            now, actor));
        jobRepository.save(new KnowledgeProductionJob(
            job.id(), job.tenantId(), job.jobCode(), job.sourceScope(), job.assetType(), job.producer(),
            job.targetPipeline(), job.domain(), job.modelStrategy(), ProductionJobStatus.RUNNING,
            job.candidateCount() + 1, job.lineage(), job.createdAt(), job.createdBy(), now, actor,
            job.traceId()));
        auditRecorder.record(AuditAction.EXECUTE, "mk_knowledge_production_job", jobCode,
            "提交候选 producer=" + job.producer() + " pipeline=" + job.targetPipeline()
                + " 身份=" + candidate.assetIdentity() + " 指纹=" + candidate.contentHash()
                + " 候选引用=" + candidateRef);
        return new CandidateSubmissionResponse(candidateRef, routing);
    }

    @Transactional(readOnly = true)
    public ProductionJobResponse getJob(String jobCode) {
        String tenantId = requireCurrentTenant();
        return jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .map(ProductionJobResponse::from)
            .orElseThrow(() -> ApiException.notFound("知识生产 job=" + jobCode));
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeProductionJob> listJobs(int page, int size) {
        String tenantId = requireCurrentTenant();
        PageRequest pageRequest = new PageRequest(page, size, null);
        long total = jobRepository.countByTenantId(tenantId);
        if (total == 0) {
            return PageResponse.empty(pageRequest);
        }
        return PageResponse.of(
            jobRepository.pageByTenantId(tenantId, pageRequest.offset(), pageRequest.safeSize()),
            pageRequest,
            total
        );
    }

    /** 列某 job 的候选生产血缘 + 会签路由决策（FR-5/6 可回溯，路由只读 resolve）。 */
    @Transactional(readOnly = true)
    public PageResponse<ProductionCandidateView> listCandidates(String jobCode, int page, int size) {
        String tenantId = requireCurrentTenant();
        KnowledgeProductionJob job = requireJob(tenantId, jobCode);
        PageRequest pageRequest = new PageRequest(page, size, null);
        long total = candidateRepository.countByTenantIdAndJobCode(tenantId, jobCode);
        if (total == 0) {
            return PageResponse.empty(pageRequest);
        }
        List<ProductionCandidateView> items = candidateRepository
            .pageByTenantIdAndJobCode(tenantId, jobCode, pageRequest.offset(), pageRequest.safeSize())
            .stream()
            .map(row -> ProductionCandidateView.from(row,
                reviewRouter.resolve(job.targetPipeline(), job.domain(), row.riskLevel())))
            .toList();
        return PageResponse.of(items, pageRequest, total);
    }

    /** 完成 job（FR-1）：PENDING/RUNNING → COMPLETED。 */
    @Transactional
    public ProductionJobResponse completeJob(String jobCode) {
        return transition(jobCode, ProductionJobStatus.COMPLETED, "完成知识生产 job");
    }

    /** 中止 job（FR-1）：PENDING/RUNNING → CANCELLED；终态拒。 */
    @Transactional
    public ProductionJobResponse cancelJob(String jobCode) {
        return transition(jobCode, ProductionJobStatus.CANCELLED, "中止知识生产 job");
    }

    /** 重放 job（FR-5）：复制 job 定义建新 PENDING job，记 replayedFrom 血缘；隔离守卫复用建 job 路径。 */
    @Transactional
    public ProductionJobResponse replayJob(String jobCode) {
        String tenantId = requireCurrentTenant();
        KnowledgeProductionJob original = requireJob(tenantId, jobCode);
        guardPipelineOwnership(original.targetPipeline(), tenantId);
        String actor = currentActor();
        Instant now = Instant.now();
        String newCode = UUID.randomUUID().toString();
        String lineage = "{\"producer\":\"" + original.producer()
            + "\",\"pipeline\":\"" + original.targetPipeline()
            + "\",\"replayedFrom\":\"" + original.jobCode()
            + "\",\"createdAt\":\"" + now + "\"}";
        KnowledgeProductionJob replay = jobRepository.save(new KnowledgeProductionJob(
            null, tenantId, newCode, original.sourceScope(), original.assetType(), original.producer(),
            original.targetPipeline(), original.domain(), original.modelStrategy(), ProductionJobStatus.PENDING,
            0, lineage, now, actor, now, actor, RequestContext.currentTraceId()));
        auditRecorder.record(AuditAction.CREATE, "mk_knowledge_production_job", newCode,
            "重放知识生产 job replayedFrom=" + original.jobCode());
        return ProductionJobResponse.from(replay);
    }

    /** job 状态跃迁：仅非终态（PENDING/RUNNING）可流转，终态拒（结构化 409）。 */
    private ProductionJobResponse transition(String jobCode, ProductionJobStatus target, String summary) {
        String tenantId = requireCurrentTenant();
        KnowledgeProductionJob job = requireJob(tenantId, jobCode);
        if (isTerminal(job.status())) {
            throw new ApiException(ErrorCode.CONFLICT,
                "job 当前状态 " + job.status() + " 为终态，不允许该生命周期操作");
        }
        Instant now = Instant.now();
        String actor = currentActor();
        KnowledgeProductionJob saved = jobRepository.save(new KnowledgeProductionJob(
            job.id(), job.tenantId(), job.jobCode(), job.sourceScope(), job.assetType(), job.producer(),
            job.targetPipeline(), job.domain(), job.modelStrategy(), target, job.candidateCount(),
            job.lineage(), job.createdAt(), job.createdBy(), now, actor, job.traceId()));
        auditRecorder.record(AuditAction.UPDATE, "mk_knowledge_production_job", jobCode, summary);
        return ProductionJobResponse.from(saved);
    }

    private boolean isTerminal(ProductionJobStatus status) {
        return status == ProductionJobStatus.COMPLETED
            || status == ProductionJobStatus.FAILED
            || status == ProductionJobStatus.CANCELLED;
    }

    /** FR-4：平台主源管道仅平台租户 t-1 可产；院内覆盖管道仅客户租户可产（平台不产覆盖）。 */
    private void guardPipelineOwnership(TargetPipeline pipeline, String tenantId) {
        boolean platform = PlatformTenant.isPlatformTenant(tenantId);
        if (pipeline == TargetPipeline.PLATFORM_SOURCE && !platform) {
            throw new ApiException(ErrorCode.KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION,
                "客户租户禁产平台主源，平台主源管道仅平台租户 t-1");
        }
        if (pipeline == TargetPipeline.TENANT_OVERLAY && platform) {
            throw new ApiException(ErrorCode.KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION,
                "平台租户不产院内覆盖，院内覆盖管道仅客户租户");
        }
    }

    private KnowledgeProductionJob requireJob(String tenantId, String jobCode) {
        return jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("知识生产 job=" + jobCode));
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId().orElse(null);
    }
}
