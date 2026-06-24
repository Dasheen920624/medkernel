package com.medkernel.engine.knowledge.production.shadow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.production.generation.StrictB0TemplatePolicy;
import com.medkernel.engine.llm.eval.MedicalRegressionCase;
import com.medkernel.engine.llm.eval.MedicalRegressionCaseRepository;
import com.medkernel.engine.llm.eval.MedicalRegressionEvaluator;
import com.medkernel.engine.llm.provider.ProviderCompletion;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.RequestContext;

/**
 * 生成期影子评测服务（AIK-STD-06 + OPT-06）。
 *
 * <p>候选进入人工审核前，复用 LLM-07 医学回归基准集与评测器跑影子评测：无真实基准集则
 * {@code NOT_READY} 并阻断，评测失败则阻断，只有达标结果才允许继续提审。严格符合生成契约的 B0
 * 非模型待编著骨架不包含医学逻辑，不执行模型基准评测，以 {@code PENDING_REVIEW} 留痕后进入人工编著审核；
 * 任一非严格 B0 候选仍执行完整影子门禁。服务不触发临床提醒、不写病历或医嘱。
 */
@Service
public class KnowledgeShadowEvaluationService {

    public static final String SHADOW_GATE_CODE = "SHADOW_EVAL";
    private static final String ENABLED = "Y";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MedicalRegressionCaseRepository caseRepository;
    private final MedicalRegressionEvaluator evaluator;
    private final KnowledgeShadowRunRepository runRepository;
    private final StrictB0TemplatePolicy strictB0TemplatePolicy;

    public KnowledgeShadowEvaluationService(MedicalRegressionCaseRepository caseRepository,
                                            MedicalRegressionEvaluator evaluator,
                                            KnowledgeShadowRunRepository runRepository,
                                            StrictB0TemplatePolicy strictB0TemplatePolicy) {
        this.caseRepository = caseRepository;
        this.evaluator = evaluator;
        this.runRepository = runRepository;
        this.strictB0TemplatePolicy = strictB0TemplatePolicy;
    }

    @Transactional
    public KnowledgeShadowDecision evaluate(KnowledgeAssetEnvelope candidate, KnowledgeShadowContext context) {
        String capabilityCode = capabilityCode(context);
        JsonNode payload = parsePayload(candidate == null ? null : candidate.payload());
        if (strictB0TemplatePolicy.matches(payload)) {
            KnowledgeShadowRun saved = persist(candidate, context, capabilityCode,
                KnowledgeShadowRunStatus.PENDING_REVIEW, 0, 0, 0, 0, false, true,
                "严格 B0 非模型待编著骨架不执行模型影子评测，进入人工编著审核");
            return toDecision(saved);
        }
        if (lowRiskModelSourceBoundaryOnly(candidate, payload)) {
            KnowledgeShadowRun saved = persist(candidate, context, capabilityCode,
                KnowledgeShadowRunStatus.PENDING_REVIEW, 0, 0, 0, 0, false, true,
                "低风险模型来源边界候选不复用 Provider 上线回归提示词，进入人工审核重点核查来源、边界和不可推断声明");
            return toDecision(saved);
        }
        List<MedicalRegressionCase> cases = caseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            context.tenantId(), capabilityCode, ENABLED);
        if (cases.isEmpty()) {
            KnowledgeShadowRun saved = persist(candidate, context, capabilityCode,
                KnowledgeShadowRunStatus.NOT_READY, 0, 0, 0, 0, false, false,
                "未配置真实影子评测基准集：capability=" + capabilityCode);
            return toDecision(saved);
        }

        MedicalRegressionEvaluator.EvalVerdict verdict = evaluator.evaluate(cases,
            ignored -> new ProviderCompletion(candidate.payload(), "B0-candidate", null, sourceCitations(candidate)));
        KnowledgeShadowRunStatus status = KnowledgeShadowRunStatus.valueOf(verdict.status());
        boolean readyForReview = status == KnowledgeShadowRunStatus.PASSED
            || status == KnowledgeShadowRunStatus.PENDING_REVIEW;
        boolean degradationDetected = status == KnowledgeShadowRunStatus.FAILED || verdict.failed() > 0;
        KnowledgeShadowRun saved = persist(candidate, context, capabilityCode, status, verdict.total(),
            verdict.passed(), 0, verdict.failed(), degradationDetected, readyForReview, basis(status, verdict));
        return toDecision(saved);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeShadowRun> listResults(String jobCode) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return runRepository.findByTenantIdAndJobCodeOrderByIdAsc(tenantId, jobCode);
    }

    public static String capabilityCode(KnowledgeShadowContext context) {
        return "knowledge.production." + context.assetType().name().toLowerCase(Locale.ROOT);
    }

    private KnowledgeShadowRun persist(KnowledgeAssetEnvelope candidate, KnowledgeShadowContext context,
                                       String capabilityCode, KnowledgeShadowRunStatus status,
                                       int totalCases, int hitCount, int falsePositiveCount, int missCount,
                                       boolean degradationDetected, boolean readyForReview, String basis) {
        Instant now = Instant.now();
        return runRepository.save(new KnowledgeShadowRun(
            null, context.tenantId(), context.jobCode(), context.assetType(), context.targetIdentityId(),
            candidate.contentHash(), capabilityCode, status, totalCases, hitCount, falsePositiveCount, missCount,
            degradationDetected, readyForReview, basis, now, RequestContext.currentUserId().orElse(null)));
    }

    private KnowledgeShadowDecision toDecision(KnowledgeShadowRun run) {
        return new KnowledgeShadowDecision(run.id(), run.status(), run.readyForReview(), run.basis());
    }

    private String basis(KnowledgeShadowRunStatus status, MedicalRegressionEvaluator.EvalVerdict verdict) {
        if (status == KnowledgeShadowRunStatus.PASSED) {
            return "影子评测通过：" + verdict.passed() + "/" + verdict.total();
        }
        if (status == KnowledgeShadowRunStatus.PENDING_REVIEW) {
            return "影子评测通过但包含高风险/红线用例，进入人工审核重点复核："
                + verdict.passed() + "/" + verdict.total();
        }
        return "影子评测未达标：通过 " + verdict.passed() + "/" + verdict.total()
            + "，失败 " + verdict.failed()
            + "，假引用=" + verdict.fakeCitationDetected()
            + "，红线=" + verdict.redLineBreach();
    }

    private String sourceCitations(KnowledgeAssetEnvelope candidate) {
        if (candidate.sources().isEmpty()) {
            return "[]";
        }
        return OBJECT_MAPPER.valueToTree(candidate.sources().stream()
            .map(AssetSourceRef::sourceRef)
            .toList()).toString();
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(payload);
        } catch (JsonProcessingException invalidJson) {
            return null;
        }
    }

    private boolean lowRiskModelSourceBoundaryOnly(KnowledgeAssetEnvelope candidate, JsonNode payload) {
        if (candidate == null
                || candidate.assetType() != VersionedAssetType.KNOWLEDGE
                || candidate.riskLevel() != KnowledgeRiskLevel.LOW
                || payload == null
                || !payload.isObject()
                || !payload.path("aiGenerated").asBoolean(false)
                || !payload.path("modelOutput").isObject()
                || !redlineCheckNodes(payload).isEmpty()) {
            return false;
        }
        JsonNode modelOutput = payload.path("modelOutput");
        if (!modelOutput.path("clinicalActionable").isBoolean()
                || modelOutput.path("clinicalActionable").asBoolean()
                || text(modelOutput, "domain").isBlank()
                || text(modelOutput, "subject").isBlank()) {
            return false;
        }
        return validSourceReferences(candidate, modelOutput.path("sourceReferences"))
            && limitationsDeclareNonClinicalUse(modelOutput.path("limitations"))
            && sectionsDeclareSourceBoundary(modelOutput.path("sections"));
    }

    private boolean validSourceReferences(KnowledgeAssetEnvelope candidate, JsonNode references) {
        if (references == null || !references.isArray() || references.isEmpty()) {
            return false;
        }
        Set<String> envelopeRefs = candidate.sources().stream()
            .map(AssetSourceRef::sourceRef)
            .filter(ref -> ref != null && !ref.isBlank())
            .collect(Collectors.toSet());
        if (envelopeRefs.isEmpty()) {
            return false;
        }
        for (JsonNode reference : references) {
            String sourceRef = text(reference, "sourceRef");
            if (!reference.isObject()
                    || sourceRef.isBlank()
                    || text(reference, "authorityLevel").isBlank()
                    || !envelopeRefs.contains(sourceRef)) {
                return false;
            }
        }
        return true;
    }

    private boolean limitationsDeclareNonClinicalUse(JsonNode limitations) {
        if (limitations == null || !limitations.isArray() || limitations.isEmpty()) {
            return false;
        }
        for (JsonNode limitation : limitations) {
            String text = limitation.asText("");
            if (text.contains("不构成")
                    && (text.contains("诊断") || text.contains("处方") || text.contains("剂量")
                        || text.contains("阈值") || text.contains("医嘱"))) {
                return true;
            }
        }
        return false;
    }

    private boolean sectionsDeclareSourceBoundary(JsonNode sections) {
        if (sections == null || !sections.isObject() || sections.isEmpty()) {
            return false;
        }
        List<JsonNode> values = new ArrayList<>();
        sections.elements().forEachRemaining(values::add);
        return values.stream().allMatch(value -> {
            String text = value.asText("");
            return !text.isBlank()
                && (text.contains("不可推断") || text.contains("来源边界") || text.contains("正式临床内容"));
        });
    }

    private List<JsonNode> redlineCheckNodes(JsonNode payload) {
        List<JsonNode> result = new ArrayList<>();
        addArray(result, payload.path("clinicalRedlineChecks"));
        addArray(result, payload.path("clinicalSafety").path("redlineChecks"));
        addArray(result, payload.path("modelOutput").path("clinicalRedlineChecks"));
        addArray(result, payload.path("modelOutput").path("clinicalSafety").path("redlineChecks"));
        return result;
    }

    private void addArray(List<JsonNode> result, JsonNode node) {
        if (node != null && node.isArray()) {
            node.forEach(result::add);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }
}
