package com.medkernel.engine.knowledge.production.model;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.authoring.GeneratedAssetCandidateRequest;
import com.medkernel.engine.authoring.GeneratedAssetCandidateService;
import com.medkernel.engine.authoring.GeneratedAssetDraftResponse;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.production.CandidateSubmissionResponse;
import com.medkernel.engine.knowledge.production.KnowledgeProducer;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJob;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJobRepository;
import com.medkernel.engine.knowledge.production.KnowledgeProductionOrchestrationService;
import com.medkernel.engine.knowledge.production.KnowledgeProductionReadinessItem;
import com.medkernel.engine.knowledge.production.KnowledgeProductionReadinessResponse;
import com.medkernel.engine.knowledge.production.KnowledgeProductionReadinessService;
import com.medkernel.engine.knowledge.production.gate.CandidateSafetyGateService;
import com.medkernel.engine.knowledge.production.gate.GateContext;
import com.medkernel.engine.knowledge.production.gate.GateItemResult;
import com.medkernel.engine.knowledge.production.gate.GateOutcome;
import com.medkernel.engine.knowledge.production.generation.BlockedCandidate;
import com.medkernel.engine.knowledge.production.generation.GeneratedCandidate;
import com.medkernel.engine.knowledge.production.generation.GenerationSummary;
import com.medkernel.engine.knowledge.production.generation.SkippedType;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowContext;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowDecision;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowEvaluationService;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageContext;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageDecision;
import com.medkernel.engine.knowledge.production.triage.KnowledgeGenerationTriageService;
import com.medkernel.engine.llm.ModelGatewayService;
import com.medkernel.engine.llm.ModelTaskRequest;
import com.medkernel.engine.llm.ModelTaskResponse;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型知识生产器（AIK-STD-13 FR2）。
 *
 * <p>真实模型调用只经 {@link ModelGatewayService}；模型输出只形成候选信封，之后必须走同一门禁、分流、影子评测与提交链。
 */
@Service
public class ModelKnowledgeProducer {

    public static final String MODEL_OUTPUT_SCHEMA_GATE = "MODEL_OUTPUT_SCHEMA";

    private final KnowledgeProductionJobRepository jobRepository;
    private final KnowledgeProductionReadinessService readinessService;
    private final ModelGatewayService modelGateway;
    private final KnowledgeProductionOrchestrationService production;
    private final CandidateSafetyGateService gateService;
    private final KnowledgeGenerationTriageService triageService;
    private final KnowledgeShadowEvaluationService shadowService;
    private final GeneratedAssetCandidateService generatedAssets;
    private final ObjectMapper objectMapper;

    public ModelKnowledgeProducer(KnowledgeProductionJobRepository jobRepository,
                                  KnowledgeProductionReadinessService readinessService,
                                  ModelGatewayService modelGateway,
                                  KnowledgeProductionOrchestrationService production,
                                  CandidateSafetyGateService gateService,
                                  KnowledgeGenerationTriageService triageService,
                                  KnowledgeShadowEvaluationService shadowService,
                                  GeneratedAssetCandidateService generatedAssets,
                                  ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.readinessService = readinessService;
        this.modelGateway = modelGateway;
        this.production = production;
        this.gateService = gateService;
        this.triageService = triageService;
        this.shadowService = shadowService;
        this.generatedAssets = generatedAssets;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ModelKnowledgeProductionResult generate(String jobCode, ModelKnowledgeProductionRequest request) {
        String tenantId = requireCurrentTenant();
        request.target().validate();
        KnowledgeProductionJob job = jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("知识生产 job=" + jobCode));
        guardModelProducer(job);
        guardGeneratedAssetType(job);
        guardCapability(request.capabilityCode());
        guardLocalModelPipeline(job);

        KnowledgeProductionReadinessResponse readiness = readinessService.evaluate(
            job.producer(), request.capabilityCode(), request.providerCode());
        if (!readiness.modelInvocationAllowed()) {
            return result(jobCode, null, null, null, null, null,
                new GenerationSummary(List.of(), List.of(), List.of(new BlockedCandidate(
                    job.assetType(), jobCode, readinessFailures(readiness.items())))));
        }

        ModelTaskResponse task = modelGateway.submitTask(new ModelTaskRequest(
            request.capabilityCode(), request.prompt(), request.timeoutSeconds(),
            requiredRouteStrategy(job.producer()), request.providerCode()));
        if (isB0Fallback(task)) {
            return result(jobCode, task, new GenerationSummary(
                List.of(),
                List.of(new SkippedType(job.assetType(), fallbackReason(task))),
                List.of()));
        }

        JsonNode modelOutput = parseModelOutput(task);
        if (modelOutput == null) {
            return result(jobCode, task, new GenerationSummary(
                List.of(),
                List.of(),
                List.of(new BlockedCandidate(job.assetType(), jobCode, List.of(GateItemResult.fail(
                    MODEL_OUTPUT_SCHEMA_GATE, "模型输出不是合法 JSON 对象，禁止进入候选链"))))));
        }

        KnowledgeAssetEnvelope envelope = toEnvelope(tenantId, job, request, task, modelOutput);
        GateOutcome gate = gateService.evaluate(
            envelope, new GateContext(tenantId, jobCode, request.target().targetIdentityId()));
        if (!gate.passed()) {
            return result(jobCode, task, new GenerationSummary(
                List.of(), List.of(), List.of(new BlockedCandidate(job.assetType(), jobCode, gate.failedItems()))));
        }
        GenerationTriageDecision triage = triageService.evaluate(envelope, new GenerationTriageContext(
            tenantId, jobCode, request.target().targetIdentityId(), job.assetType()));
        if (!triage.shouldSubmit()) {
            return result(jobCode, task, new GenerationSummary(
                List.of(),
                List.of(new SkippedType(job.assetType(), "生成期分流跳过：" + triage.basis())),
                List.of()));
        }
        KnowledgeShadowDecision shadow = shadowService.evaluate(envelope, new KnowledgeShadowContext(
            tenantId, jobCode, request.target().targetIdentityId(), job.assetType()));
        if (!shadow.readyForReview()) {
            return result(jobCode, task, new GenerationSummary(
                List.of(),
                List.of(),
                List.of(new BlockedCandidate(job.assetType(), jobCode, List.of(GateItemResult.fail(
                    KnowledgeShadowEvaluationService.SHADOW_GATE_CODE, shadow.basis()))))));
        }
        if (job.assetType() != VersionedAssetType.KNOWLEDGE) {
            try {
                GeneratedAssetDraftResponse draft = generatedAssets.materializeDraft(new GeneratedAssetCandidateRequest(
                    tenantId,
                    job.assetType(),
                    request.assetIdentity(),
                    tenantId,
                    "ALL",
                    job.sourceScope(),
                    RequestContext.currentUserId().orElse("system"),
                    RequestContext.currentTraceId(),
                    generatedAssetContent(request, task, modelOutput),
                    List.of()
                ));
                return result(jobCode, task, new GenerationSummary(
                    List.of(new GeneratedCandidate(
                        job.assetType(), jobCode, "asset-version:" + draft.versionId(), null)),
                    List.of(),
                    List.of()));
            } catch (ApiException invalidGeneratedAsset) {
                return result(jobCode, task, new GenerationSummary(
                    List.of(),
                    List.of(),
                    List.of(new BlockedCandidate(job.assetType(), jobCode, List.of(GateItemResult.fail(
                        MODEL_OUTPUT_SCHEMA_GATE, invalidGeneratedAsset.getMessage()))))));
            }
        }
        CandidateSubmissionResponse submitted = production.submitCandidate(jobCode, envelope, request.target());
        return result(jobCode, task, new GenerationSummary(
            List.of(new GeneratedCandidate(job.assetType(), jobCode, submitted.candidateRef(), submitted.routing())),
            List.of(),
            List.of()));
    }

    private void guardModelProducer(KnowledgeProductionJob job) {
        if (job.producer() != KnowledgeProducer.API_MODEL && job.producer() != KnowledgeProducer.LOCAL_MODEL) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "job 生产器不是模型生产器，禁止调用模型生成");
        }
    }

    private void guardGeneratedAssetType(KnowledgeProductionJob job) {
        if (job.assetType() != VersionedAssetType.KNOWLEDGE
                && job.assetType() != VersionedAssetType.RULE
                && job.assetType() != VersionedAssetType.PATHWAY) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "模型生成只允许产生知识、规则或路径草稿，其他资产请使用对应维护入口"
            );
        }
    }

    private void guardCapability(String capabilityCode) {
        if (capabilityCode == null
            || !KnowledgeProductionReadinessService.DEFAULT_CAPABILITY_CODE.equalsIgnoreCase(capabilityCode.trim())) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "正式模型知识生产能力码必须为 " + KnowledgeProductionReadinessService.DEFAULT_CAPABILITY_CODE
            );
        }
    }

    private void guardLocalModelPipeline(KnowledgeProductionJob job) {
        if (job.producer() == KnowledgeProducer.LOCAL_MODEL
            && job.targetPipeline() != TargetPipeline.TENANT_OVERLAY) {
            throw new ApiException(ErrorCode.KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION,
                "本地模型生产器只允许生成院内覆盖候选，禁止进入平台主源管道");
        }
    }

    private String requiredRouteStrategy(KnowledgeProducer producer) {
        return producer == KnowledgeProducer.LOCAL_MODEL ? "LOCAL_MODEL" : "EXTERNAL_MODEL";
    }

    private List<GateItemResult> readinessFailures(List<KnowledgeProductionReadinessItem> items) {
        return items.stream()
            .filter(item -> item.required() && !item.ready())
            .map(item -> GateItemResult.fail(item.code(), item.message()))
            .toList();
    }

    private boolean isB0Fallback(ModelTaskResponse task) {
        return task == null
            || "B0".equalsIgnoreCase(task.modelMode())
            || !"SUCCEEDED".equalsIgnoreCase(task.status());
    }

    private String fallbackReason(ModelTaskResponse task) {
        if (task == null) {
            return "模型网关未返回结果，未生成候选";
        }
        String reason = task.fallbackReason() == null || task.fallbackReason().isBlank()
            ? "模型网关未返回可用模型输出"
            : task.fallbackReason();
        if ("B0".equalsIgnoreCase(task.modelMode())) {
            return "模型网关降级 B0，未生成模型候选：" + reason;
        }
        return "模型网关未成功(status=" + task.status() + ", mode=" + task.modelMode()
            + ")，未生成模型候选：" + reason;
    }

    private JsonNode parseModelOutput(ModelTaskResponse task) {
        String output = task.outputContent();
        if (output == null || output.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(output);
            return parsed != null && parsed.isObject() ? parsed : null;
        } catch (JsonProcessingException invalidJson) {
            return null;
        }
    }

    private KnowledgeAssetEnvelope toEnvelope(String tenantId, KnowledgeProductionJob job,
                                              ModelKnowledgeProductionRequest request,
                                              ModelTaskResponse task,
                                              JsonNode modelOutput) {
        String payload = payload(request, task, modelOutput);
        return new KnowledgeAssetEnvelope(
            job.assetType(),
            request.assetIdentity(),
            request.subject(),
            "ai-draft-" + task.taskId(),
            request.sources(),
            request.trustLevel(),
            null,
            null,
            request.riskLevel(),
            tenantId,
            Sha256ContentHash.sha256(payload, "资产内容不能为空"),
            payload,
            AssetVersionStatus.DRAFT);
    }

    private String payload(ModelKnowledgeProductionRequest request, ModelTaskResponse task, JsonNode modelOutput) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("aiGenerated", true);
        root.put("modelTaskId", task.taskId());
        root.put("modelMode", task.modelMode());
        root.put("modelVersion", task.modelVersion());
        root.put("promptVersion", task.promptVersion());
        root.put("toolVersion", task.toolVersion());
        root.put("capabilityCode", request.capabilityCode());
        root.put("promptInputHash", Sha256ContentHash.sha256(request.prompt(), "生产提示不能为空"));
        root.put("fallbackUsed", task.fallbackUsed());
        if (task.confidence() != null) {
            root.put("confidence", task.confidence());
        }
        if (task.fallbackReason() != null && !task.fallbackReason().isBlank()) {
            root.put("fallbackReason", task.fallbackReason());
        }
        root.set("sourceCitations", parseCitations(task.sourceCitations()));
        root.set("modelOutput", modelOutput);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException impossible) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "模型候选 payload 序列化失败");
        }
    }

    private JsonNode generatedAssetContent(ModelKnowledgeProductionRequest request,
                                           ModelTaskResponse task,
                                           JsonNode modelOutput) {
        ObjectNode root = modelOutput == null || !modelOutput.isObject()
            ? objectMapper.createObjectNode()
            : modelOutput.deepCopy();
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("aiGenerated", true);
        evidence.put("modelTaskId", task.taskId());
        evidence.put("modelMode", task.modelMode());
        evidence.put("modelVersion", task.modelVersion());
        evidence.put("promptVersion", task.promptVersion());
        evidence.put("toolVersion", task.toolVersion());
        evidence.put("capabilityCode", request.capabilityCode());
        evidence.put("promptInputHash", Sha256ContentHash.sha256(request.prompt(), "生产提示不能为空"));
        evidence.put("fallbackUsed", task.fallbackUsed());
        if (task.confidence() != null) {
            evidence.put("confidence", task.confidence());
        }
        if (task.fallbackReason() != null && !task.fallbackReason().isBlank()) {
            evidence.put("fallbackReason", task.fallbackReason());
        }
        evidence.set("sourceCitations", parseCitations(task.sourceCitations()));
        root.set("generationEvidence", evidence);
        return root;
    }

    private JsonNode parseCitations(String sourceCitations) {
        if (sourceCitations == null || sourceCitations.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(sourceCitations);
        } catch (JsonProcessingException invalid) {
            return objectMapper.getNodeFactory().textNode(sourceCitations);
        }
    }

    private ModelKnowledgeProductionResult result(String jobCode, ModelTaskResponse task, GenerationSummary summary) {
        return result(jobCode, task, task.modelMode(), task.modelVersion(), task.promptVersion(), task.toolVersion(), summary);
    }

    private ModelKnowledgeProductionResult result(String jobCode, ModelTaskResponse task, String modelMode,
                                                  String modelVersion, String promptVersion, String toolVersion,
                                                  GenerationSummary summary) {
        return new ModelKnowledgeProductionResult(
            jobCode,
            task == null ? null : task.taskId(),
            modelMode,
            modelVersion,
            promptVersion,
            toolVersion,
            summary);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
