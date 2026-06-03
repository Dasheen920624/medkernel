package com.medkernel.engine.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.rule.RuleActionResult;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Component;

/**
 * CDSS B0 确定性命中器：读取标准上下文与已发布规则，生成可追溯推荐候选。
 *
 * <p>本组件只产出候选，不落库、不审计；事务、状态推进和疲劳治理由
 * {@link RecommendationEngineService} 统一处理。模型关闭时也依赖此组件保持主链路可用。
 */
@Component
public class RecommendationDeterministicMatcher {

    private final ContextSnapshotService snapshots;
    private final RuleDefinitionRepository ruleDefinitions;
    private final RuleVersionRepository ruleVersions;
    private final RuleDslEvaluator ruleEvaluator;
    private final PatientPathwayRepository patientPathways;
    private final PathwayTemplateRepository pathwayTemplates;
    private final ObjectMapper json;

    public RecommendationDeterministicMatcher(
            ContextSnapshotService snapshots,
            RuleDefinitionRepository ruleDefinitions,
            RuleVersionRepository ruleVersions,
            RuleDslEvaluator ruleEvaluator,
            PatientPathwayRepository patientPathways,
            PathwayTemplateRepository pathwayTemplates,
            ObjectMapper json) {
        this.snapshots = snapshots;
        this.ruleDefinitions = ruleDefinitions;
        this.ruleVersions = ruleVersions;
        this.ruleEvaluator = ruleEvaluator;
        this.patientPathways = patientPathways;
        this.pathwayTemplates = pathwayTemplates;
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
        for (RuleDefinition rule : ruleDefinitions.findPublishedByTenantId(tenantId)) {
            RuleVersion version = activePublishedVersion(rule, tenantId);
            RuleDslEvaluation evaluation = ruleEvaluator.evaluate(parseDsl(version), context);
            if (evaluation.hit()) {
                matched.add(toCard(request, snapshot, rule, version, evaluation));
            }
        }
        return List.copyOf(matched);
    }

    private RuleVersion activePublishedVersion(RuleDefinition rule, String tenantId) {
        if (!hasText(rule.activeVersionId())) {
            throw new ApiException(ErrorCode.ENG_RULE_003, "已发布规则缺少 activeVersionId: " + rule.ruleId());
        }
        RuleVersion version = ruleVersions.findByVersionIdAndTenantId(rule.activeVersionId(), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_003,
                "规则 active 版本不存在: " + rule.activeVersionId()));
        if (version.status() != RuleVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ENG_RULE_003,
                "规则 active 版本未发布: " + rule.activeVersionId());
        }
        return version;
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
            RuleDefinition rule,
            RuleVersion version,
            RuleDslEvaluation evaluation) {
        RecommendationRiskLevel risk = toRecommendationRisk(
            evaluation.severity() == null ? rule.riskLevel() : evaluation.severity());
        String actionMessage = firstActionMessage(evaluation);
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
            "规则 " + rule.ruleCode() + " v" + version.versionNo() + " 命中",
            explanationJson(request, snapshot, rule, version, evaluation),
            fatigueKey(request, rule),
            null,
            sources(request, snapshot, rule, version)
        );
    }

    private List<RecommendationSourceRequest> sources(
            RecommendationTriggerRequest request,
            ContextSnapshotResponse snapshot,
            RuleDefinition rule,
            RuleVersion version) {
        List<RecommendationSourceRequest> values = new ArrayList<>();
        values.add(new RecommendationSourceRequest(
            RecommendationSourceType.RULE,
            rule.ruleId(),
            String.valueOf(version.versionNo()),
            rule.name(),
            "rule_version:" + version.versionId(),
            null,
            hasText(version.changeSummary()) ? version.changeSummary() : "规则版本命中"
        ));
        values.add(new RecommendationSourceRequest(
            RecommendationSourceType.CONTEXT,
            snapshot.snapshotId(),
            snapshot.packageVersion(),
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

    private String explanationJson(
            RecommendationTriggerRequest request,
            ContextSnapshotResponse snapshot,
            RuleDefinition rule,
            RuleVersion version,
            RuleDslEvaluation evaluation) {
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
        root.set("ruleExplanation", evaluation.explanation());
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_RULE_005, "推荐解释 JSON 序列化失败: " + rule.ruleId());
        }
    }

    private String firstActionMessage(RuleDslEvaluation evaluation) {
        return evaluation.actions().stream()
            .map(RuleActionResult::message)
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
}
