package com.medkernel.engine.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeEffectiveVersionResolver;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.rule.RuleActionResult;
import com.medkernel.engine.rule.RuleApplicabilityService;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuntimeReleaseRuleSelector;
import com.medkernel.engine.rule.RuntimeRuleReference;
import com.medkernel.engine.rule.RuntimeRuleSelection;
import com.medkernel.engine.safety.ClinicalRedlineMatcher;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Component;

/**
 * CDSS B0 确定性命中器：读取标准上下文与统一版本已发布规则，生成可追溯推荐候选。
 *
 * <p>本组件只产出候选，不落库、不审计；事务、状态推进和疲劳治理由
 * {@link RecommendationEngineService} 统一处理。模型关闭时也依赖此组件保持主链路可用。
 */
@Component
public class RecommendationDeterministicMatcher {

    private static final String KNOWLEDGE_SOURCE_PREFIX = "knowledge:";

    private final ContextSnapshotService snapshots;
    private final RuleDefinitionRepository ruleDefinitions;
    private final RuleVersionRepository ruleVersions;
    private final RuntimeReleaseRuleSelector runtimeRuleSelector;
    private final RuleDslEvaluator ruleEvaluator;
    private final RuleApplicabilityService applicabilityService;
    private final PatientPathwayRepository patientPathways;
    private final PathwayTemplateRepository pathwayTemplates;
    private final KnowledgeEffectiveVersionResolver effectiveKnowledgeVersions;
    private final ClinicalRedlineMatcher redlineMatcher;
    private final ObjectMapper json;

    public RecommendationDeterministicMatcher(
            ContextSnapshotService snapshots,
            RuleDefinitionRepository ruleDefinitions,
            RuleVersionRepository ruleVersions,
            RuntimeReleaseRuleSelector runtimeRuleSelector,
            RuleDslEvaluator ruleEvaluator,
            RuleApplicabilityService applicabilityService,
            PatientPathwayRepository patientPathways,
            PathwayTemplateRepository pathwayTemplates,
            KnowledgeEffectiveVersionResolver effectiveKnowledgeVersions,
            ClinicalRedlineMatcher redlineMatcher,
            ObjectMapper json) {
        this.snapshots = snapshots;
        this.ruleDefinitions = ruleDefinitions;
        this.ruleVersions = ruleVersions;
        this.runtimeRuleSelector = runtimeRuleSelector;
        this.ruleEvaluator = ruleEvaluator;
        this.applicabilityService = applicabilityService;
        this.patientPathways = patientPathways;
        this.pathwayTemplates = pathwayTemplates;
        this.effectiveKnowledgeVersions = effectiveKnowledgeVersions;
        this.redlineMatcher = redlineMatcher;
        this.json = json;
    }

    public List<RecommendationCardRequest> match(RecommendationTriggerRequest request) {
        if (request == null || !hasText(request.contextSnapshotId())) {
            return List.of();
        }
        String tenantId = tenantId();
        ContextSnapshotResponse snapshot = snapshots.findById(request.contextSnapshotId());
        JsonNode context = json.valueToTree(snapshot.resources());
        List<RecommendationCardRequest> matched = new ArrayList<>();
        for (EffectiveRuleVersion candidate : runtimeRules(tenantId, snapshot, request.triggerType())) {
            RuleDefinition rule = candidate.rule();
            RuleVersion version = candidate.version();
            JsonNode dsl = parseDsl(version);
            if (!applicabilityService.evaluate(
                    dsl, context, RequestContext.currentOrgScope(), version.versionId()).applicable()) {
                continue;
            }
            RuleDslEvaluation evaluation = ruleEvaluator.evaluate(
                dsl, context, tenantId, snapshot.runtimeReleaseId());
            if (evaluation.hit()) {
                matched.add(toCard(
                    request, snapshot, candidate, evaluation, tenantId));
            }
        }
        matched.addAll(redlineMatcher.match(request, snapshot, context));
        return List.copyOf(matched);
    }

    private List<EffectiveRuleVersion> runtimeRules(
            String tenantId,
            ContextSnapshotResponse snapshot,
            String triggerType) {
        RuntimeRuleSelection selection =
            runtimeRuleSelector.select(tenantId, snapshot.runtimeReleaseId(), triggerType);
        return selection.rules().stream()
            .map(reference -> loadRuntimeRuleVersion(selection.runtimeReleaseId(), reference))
            .toList();
    }

    private EffectiveRuleVersion loadRuntimeRuleVersion(
            String runtimeReleaseId,
            RuntimeRuleReference reference) {
        RuleDefinition rule = ruleDefinitions
            .findByRuleIdAndTenantId(reference.ruleId(), reference.tenantId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_RULE_006,
                "机构生效版本中的推荐规则不存在：" + reference.ruleId()));
        RuleVersion version = ruleVersions
            .findByVersionIdAndTenantId(reference.versionId(), reference.tenantId())
            .filter(candidate -> rule.ruleId().equals(candidate.ruleId()))
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_RULE_006,
                "机构生效版本中的推荐规则版本不存在：" + reference.versionId()));
        return new EffectiveRuleVersion(
            rule,
            version,
            runtimeReleaseId,
            reference.assetVersionId(),
            reference.assetVersionNo(),
            reference.contentHash(),
            reference.sourceLayer());
    }

    private JsonNode parseDsl(RuleVersion version) {
        try {
            return json.readTree(version.dslJson());
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则 DSL 不是合法 JSON: " + version.versionId());
        }
    }

    private RecommendationCardRequest toCard(
            RecommendationTriggerRequest request,
            ContextSnapshotResponse snapshot,
            EffectiveRuleVersion candidate,
            RuleDslEvaluation evaluation,
            String requestTenantId) {
        RuleDefinition rule = candidate.rule();
        RuleVersion version = candidate.version();
        RecommendationRiskLevel risk = toRecommendationRisk(
            evaluation.severity() == null ? rule.riskLevel() : evaluation.severity());
        String actionMessage = firstActionMessage(evaluation);
        Optional<EffectiveKnowledgeVersion> knowledge = knowledgeSource(version.sourceRef(), requestTenantId);
        return new RecommendationCardRequest(
            "RULE." + rule.ruleCode() + ".v" + version.versionNo(),
            toCardType(rule.ruleType()),
            rule.name(),
            actionMessage,
            "请结合推荐解释确认处理",
            risk,
            toInterruptLevel(risk),
            requiresConfirmation(risk, evaluation),
            false,
            sourceSummary(candidate, knowledge),
            explanationJson(request, snapshot, candidate, evaluation, knowledge),
            fatigueKey(request, rule),
            null,
            CdssAutomationLevel.fromInterruptLevel(toInterruptLevel(risk)),
            sources(request, snapshot, candidate, knowledge)
        );
    }

    private List<RecommendationSourceRequest> sources(
            RecommendationTriggerRequest request,
            ContextSnapshotResponse snapshot,
            EffectiveRuleVersion candidate,
            Optional<EffectiveKnowledgeVersion> knowledge) {
        RuleDefinition rule = candidate.rule();
        RuleVersion version = candidate.version();
        List<RecommendationSourceRequest> values = new ArrayList<>();
        values.add(new RecommendationSourceRequest(
            RecommendationSourceType.RULE,
            rule.ruleId(),
            String.valueOf(version.versionNo()),
            rule.name(),
            "rule_version:" + version.versionId(),
            candidateContentHash(candidate),
            ruleSourceSummary(version, candidate)
        ));
        knowledge.map(this::toKnowledgeSource).ifPresent(values::add);
        values.add(new RecommendationSourceRequest(
            RecommendationSourceType.CONTEXT,
            snapshot.snapshotId(),
            snapshot.runtimeReleaseId(),
            "标准临床上下文",
            "context_snapshot:" + snapshot.snapshotId(),
            null,
            "本次评估上下文"
        ));
        pathwaySource(request).ifPresent(values::add);
        return List.copyOf(values);
    }

    private Optional<RecommendationSourceRequest> pathwaySource(RecommendationTriggerRequest request) {
        if (!hasText(request.patientPathwayId())) {
            return Optional.empty();
        }
        return patientPathways.findByPatientPathwayIdAndTenantId(request.patientPathwayId(), tenantId())
            .flatMap(pathway -> pathwayTemplates.findByTemplateIdAndTenantId(pathway.templateId(), tenantId())
                .map(template -> toPathwaySource(pathway, template)));
    }

    private RecommendationSourceRequest toPathwaySource(PatientPathway pathway, PathwayTemplate template) {
        return new RecommendationSourceRequest(
            RecommendationSourceType.PATHWAY,
            pathway.patientPathwayId(),
            template.templateVersion() == null ? null : String.valueOf(template.templateVersion()),
            template.name(),
            "pathway_template:" + template.templateId() + "#node:" + pathway.currentNodeCode(),
            null,
            "患者当前在径节点 " + pathway.currentNodeCode()
        );
    }

    private Optional<EffectiveKnowledgeVersion> knowledgeSource(String sourceRef, String requestTenantId) {
        return knowledgeIdentityCode(sourceRef)
            .flatMap(identityCode -> effectiveKnowledgeVersions.resolve(
                requestTenantId, identityCode, currentApplicableScope()))
            .map(resolved -> new EffectiveKnowledgeVersion(
                resolved.identity(), resolved.assetVersion().tenantId(), resolved.version()));
    }

    private Optional<String> knowledgeIdentityCode(String sourceRef) {
        if (!hasText(sourceRef)) {
            return Optional.empty();
        }
        String trimmed = sourceRef.trim();
        if (!trimmed.regionMatches(true, 0, KNOWLEDGE_SOURCE_PREFIX, 0, KNOWLEDGE_SOURCE_PREFIX.length())) {
            return Optional.empty();
        }
        String identityCode = trimmed.substring(KNOWLEDGE_SOURCE_PREFIX.length()).trim();
        return hasText(identityCode) ? Optional.of(identityCode) : Optional.empty();
    }

    private static String currentApplicableScope() {
        OrgScope scope = RequestContext.currentOrgScope();
        return scope == null || !hasText(scope.specialtyId())
            ? KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE
            : "specialty:" + scope.specialtyId().trim();
    }

    private RecommendationSourceRequest toKnowledgeSource(EffectiveKnowledgeVersion knowledge) {
        KnowledgeIdentity identity = knowledge.identity();
        KnowledgeAssetVersion version = knowledge.version();
        return new RecommendationSourceRequest(
            RecommendationSourceType.KNOWLEDGE,
            identity.identityCode(),
            version.versionNo(),
            identity.subject(),
            KnowledgeSourceLocator.citationLocator(knowledge.sourceTenantId(), version.id()),
            version.contentHash(),
            "已审核知识版本 " + version.versionNo()
        );
    }

    private String sourceSummary(
            EffectiveRuleVersion candidate,
            Optional<EffectiveKnowledgeVersion> knowledge) {
        RuleDefinition rule = candidate.rule();
        RuleVersion version = candidate.version();
        String summary = "规则 " + rule.ruleCode() + " v" + version.versionNo() + " 命中";
        if (hasText(candidate.runtimeReleaseId())) {
            summary += "，运行版本=" + candidate.runtimeReleaseId();
        }
        if (hasText(candidate.assetVersionId())) {
            summary += "，asset_version=" + candidate.assetVersionId();
        }
        if (hasText(candidate.sourceLayer())) {
            summary += "，来源层=" + candidate.sourceLayer();
        }
        String contentHash = candidateContentHash(candidate);
        if (hasText(contentHash)) {
            summary += "，content_hash=" + contentHash;
        }
        String baseSummary = summary;
        return knowledge
            .map(value -> baseSummary + "，引用知识 " + value.identity().identityCode()
                + " v" + value.version().versionNo())
            .orElse(baseSummary);
    }

    private String explanationJson(
            RecommendationTriggerRequest request,
            ContextSnapshotResponse snapshot,
            EffectiveRuleVersion candidate,
            RuleDslEvaluation evaluation,
            Optional<EffectiveKnowledgeVersion> knowledge) {
        RuleDefinition rule = candidate.rule();
        RuleVersion version = candidate.version();
        ObjectNode root = json.createObjectNode();
        root.put("matchType", "RULE");
        root.put("triggerCode", request.triggerCode());
        root.put("scenarioCode", request.scenarioCode());
        root.put("contextSnapshotId", snapshot.snapshotId());
        root.put("ruleId", rule.ruleId());
        root.put("ruleCode", rule.ruleCode());
        root.put("ruleVersionId", version.versionId());
        root.put("ruleVersionNo", version.versionNo());
        if (hasText(version.sourceRef())) {
            root.put("ruleSourceRef", version.sourceRef());
        }
        if (hasText(candidate.runtimeReleaseId())) {
            ObjectNode runtimeNode = json.createObjectNode();
            runtimeNode.put("runtimeReleaseId", candidate.runtimeReleaseId());
            if (hasText(candidate.assetVersionId())) {
                runtimeNode.put("assetVersionId", candidate.assetVersionId());
            }
            if (hasText(candidate.assetVersionNo())) {
                runtimeNode.put("assetVersionNo", candidate.assetVersionNo());
            }
            if (hasText(candidate.sourceLayer())) {
                runtimeNode.put("sourceLayer", candidate.sourceLayer());
            }
            String contentHash = candidateContentHash(candidate);
            if (hasText(contentHash)) {
                runtimeNode.put("contentHash", contentHash);
            }
            root.set("runtimeRelease", runtimeNode);
        }
        knowledge.ifPresent(value -> {
            ObjectNode knowledgeNode = json.createObjectNode();
            knowledgeNode.put("knowledgeIdentityId", value.identity().id());
            knowledgeNode.put("knowledgeIdentityCode", value.identity().identityCode());
            knowledgeNode.put("knowledgeSourceTenantId", value.sourceTenantId());
            knowledgeNode.put("knowledgeVersionId", value.version().id());
            knowledgeNode.put("knowledgeVersionNo", value.version().versionNo());
            if (hasText(value.version().contentHash())) {
                knowledgeNode.put("knowledgeContentHash", value.version().contentHash());
            }
            root.set("knowledge", knowledgeNode);
        });
        root.set("ruleExplanation", evaluation.explanation());
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_RULE_005, "推荐解释 JSON 序列化失败: " + rule.ruleId());
        }
    }

    private String firstActionMessage(RuleDslEvaluation evaluation) {
        return evaluation.actions().stream()
            .map(RuleActionResult::detail)
            .filter(RecommendationDeterministicMatcher::hasText)
            .findFirst()
            .orElse("规则命中，请结合上下文复核");
    }

    private boolean requiresConfirmation(RecommendationRiskLevel risk, RuleDslEvaluation evaluation) {
        return risk == RecommendationRiskLevel.HIGH
            || risk == RecommendationRiskLevel.CRITICAL
            || evaluation.actions().stream().anyMatch(RuleActionResult::requiresPhysicianConfirmation);
    }

    private RecommendationInterruptLevel toInterruptLevel(RecommendationRiskLevel risk) {
        return switch (risk) {
            case CRITICAL -> RecommendationInterruptLevel.STRONG_INTERRUPTIVE;
            case HIGH -> RecommendationInterruptLevel.WEAK_INTERRUPTIVE;
            case MEDIUM -> RecommendationInterruptLevel.INFO;
            case LOW -> RecommendationInterruptLevel.SILENT;
        };
    }

    private RecommendationRiskLevel toRecommendationRisk(RuleRiskLevel risk) {
        if (risk == null) {
            return RecommendationRiskLevel.LOW;
        }
        return switch (risk) {
            case CRITICAL -> RecommendationRiskLevel.CRITICAL;
            case HIGH -> RecommendationRiskLevel.HIGH;
            case MEDIUM -> RecommendationRiskLevel.MEDIUM;
            case LOW -> RecommendationRiskLevel.LOW;
        };
    }

    private RecommendationCardType toCardType(RuleType ruleType) {
        if (ruleType == null) {
            return RecommendationCardType.RISK;
        }
        return switch (ruleType) {
            case ORDER -> RecommendationCardType.MEDICATION;
            case LAB -> RecommendationCardType.LAB;
            case REPORT -> RecommendationCardType.EXAM;
            case FOLLOWUP -> RecommendationCardType.FOLLOWUP;
            case QUALITY, INSURANCE -> RecommendationCardType.QUALITY;
            case PATHWAY -> RecommendationCardType.PATHWAY;
            default -> RecommendationCardType.RISK;
        };
    }

    private String fatigueKey(RecommendationTriggerRequest request, RuleDefinition rule) {
        String scenario = hasText(request.scenarioCode()) ? request.scenarioCode() : request.triggerType();
        return scenario + ":" + rule.ruleCode();
    }

    private String tenantId() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String candidateContentHash(EffectiveRuleVersion candidate) {
        return candidate == null || !hasText(candidate.contentHash()) ? null : candidate.contentHash();
    }

    private static String ruleSourceSummary(RuleVersion version, EffectiveRuleVersion candidate) {
        String summary = hasText(version.changeSummary()) ? version.changeSummary() : "规则版本命中";
        List<String> runtimeFacts = new ArrayList<>();
        if (hasText(candidate.runtimeReleaseId())) {
            runtimeFacts.add("运行版本=" + candidate.runtimeReleaseId());
        }
        if (hasText(candidate.assetVersionId())) {
            runtimeFacts.add("asset_version=" + candidate.assetVersionId());
        }
        if (hasText(candidate.sourceLayer())) {
            runtimeFacts.add("来源层=" + candidate.sourceLayer());
        }
        String contentHash = candidateContentHash(candidate);
        if (hasText(contentHash)) {
            runtimeFacts.add("content_hash=" + contentHash);
        }
        return runtimeFacts.isEmpty() ? summary : summary + "；" + String.join("；", runtimeFacts);
    }

    private record EffectiveRuleVersion(
        RuleDefinition rule,
        RuleVersion version,
        String runtimeReleaseId,
        String assetVersionId,
        String assetVersionNo,
        String contentHash,
        String sourceLayer
    ) {
    }

    private record EffectiveKnowledgeVersion(
        KnowledgeIdentity identity,
        String sourceTenantId,
        KnowledgeAssetVersion version
    ) {
    }
}
