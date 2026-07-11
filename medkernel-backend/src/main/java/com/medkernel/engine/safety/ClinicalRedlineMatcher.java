package com.medkernel.engine.safety;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.recommendation.RecommendationCardRequest;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationSourceRequest;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.rule.RuleActionResult;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Component;

/**
 * OPT-04 临床安全红线确定性命中器。
 *
 * <p>只读取机构生效版本锁定的红线规则，按标准上下文执行 B0 条件 DSL，并把命中结果转换为强打断、
 * 不可疲劳抑制、可追溯的推荐候选卡；持久化、审计、疲劳信号由推荐服务统一处理。
 */
@Component
public class ClinicalRedlineMatcher {

    private final ContextSnapshotService snapshots;
    private final RuntimeReleaseClinicalRedlineSelector runtimeRedlines;
    private final RuleDslEvaluator evaluator;
    private final ObjectMapper json;

    public ClinicalRedlineMatcher(
            ContextSnapshotService snapshots,
            RuntimeReleaseClinicalRedlineSelector runtimeRedlines,
            RuleDslEvaluator evaluator,
            ObjectMapper json) {
        this.snapshots = snapshots;
        this.runtimeRedlines = runtimeRedlines;
        this.evaluator = evaluator;
        this.json = json;
    }

    public List<RecommendationCardRequest> match(RecommendationTriggerRequest request) {
        if (request == null || !hasText(request.contextSnapshotId())) {
            return List.of();
        }
        ContextSnapshotResponse snapshot = snapshots.findById(request.contextSnapshotId());
        return match(request, snapshot, json.valueToTree(snapshot.resources()));
    }

    public List<RecommendationCardRequest> match(
            RecommendationTriggerRequest request,
            ContextSnapshotResponse snapshot,
            JsonNode context) {
        if (request == null || snapshot == null || context == null) {
            return List.of();
        }
        String tenantId = tenantId();
        List<RecommendationCardRequest> matches = new ArrayList<>();
        for (ClinicalRedlineRule redline : runtimeRedlines.select(tenantId, snapshot.runtimeReleaseId())) {
            if (!triggerMatches(request, redline)) {
                continue;
            }
            assertRuntimeProtected(redline);
            RuleDslEvaluation evaluation = evaluateRedlineCondition(redline, context);
            if (evaluation.hit()) {
                matches.add(toCard(request, snapshot, redline, evaluation));
            }
        }
        return List.copyOf(matches);
    }

    private boolean triggerMatches(RecommendationTriggerRequest request, ClinicalRedlineRule redline) {
        return hasText(redline.triggerPoint()) && redline.triggerPoint().equals(request.triggerType());
    }

    private JsonNode parseDsl(ClinicalRedlineRule redline) {
        try {
            return json.readTree(redline.conditionDsl());
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "红线 DSL 不是合法 JSON: " + redline.redlineId());
        }
    }

    private RuleDslEvaluation evaluateRedlineCondition(ClinicalRedlineRule redline, JsonNode context) {
        JsonNode dsl = parseDsl(redline);
        if (dsl.has("when") || dsl.has("then")) {
            return evaluator.evaluate(dsl, context);
        }
        return evaluator.evaluateConditionTree(dsl, context, redlineExplanation(redline));
    }

    private JsonNode redlineExplanation(ClinicalRedlineRule redline) {
        ObjectNode explanation = json.createObjectNode();
        explanation.put("summary", "临床安全红线条件命中：" + redline.title());
        explanation.put("redlineId", redline.redlineId());
        explanation.put("redlineKey", redline.redlineKey());
        explanation.put("redlineVersion", redline.redlineVersion());
        return explanation;
    }

    private RecommendationCardRequest toCard(
            RecommendationTriggerRequest request,
            ContextSnapshotResponse snapshot,
            ClinicalRedlineRule redline,
            RuleDslEvaluation evaluation) {
        return new RecommendationCardRequest(
            "REDLINE." + redline.redlineKey() + ".v" + redline.redlineVersion(),
            toCardType(redline.category()),
            redline.title(),
            firstActionMessage(redline, evaluation),
            "立即复核并按院内安全流程处理，不得自动执行医嘱",
            RecommendationRiskLevel.CRITICAL,
            RecommendationInterruptLevel.STRONG_INTERRUPTIVE,
            true,
            false,
            "临床安全红线 " + redline.redlineKey() + " v" + redline.redlineVersion() + " 命中",
            explanationJson(request, snapshot, redline, evaluation),
            "REDLINE:" + redline.redlineKey(),
            null,
            CdssAutomationLevel.INTERRUPTIVE,
            sources(snapshot, redline));
    }

    private List<RecommendationSourceRequest> sources(
            ContextSnapshotResponse snapshot,
            ClinicalRedlineRule redline) {
        List<RecommendationSourceRequest> values = new ArrayList<>();
        values.add(new RecommendationSourceRequest(
            RecommendationSourceType.REDLINE,
            redline.redlineId(),
            redline.redlineVersion(),
            redline.title(),
            "clinical_redline:" + redline.redlineId(),
            null,
            "临床安全红线命中"));
        if (redline.sourceVersionId() != null) {
            values.add(new RecommendationSourceRequest(
                RecommendationSourceType.KNOWLEDGE,
                "knowledge-version:" + redline.sourceVersionId(),
                redline.redlineVersion(),
                redline.evidenceSource(),
                "knowledge_version:" + redline.sourceVersionId(),
                null,
                redline.evidenceReference()));
        }
        values.add(new RecommendationSourceRequest(
            RecommendationSourceType.CONTEXT,
            snapshot.snapshotId(),
            snapshot.runtimeReleaseId(),
            "标准临床上下文",
            "context_snapshot:" + snapshot.snapshotId(),
            null,
            "本次红线评估上下文"));
        return List.copyOf(values);
    }

    private String explanationJson(
            RecommendationTriggerRequest request,
            ContextSnapshotResponse snapshot,
            ClinicalRedlineRule redline,
            RuleDslEvaluation evaluation) {
        ObjectNode root = json.createObjectNode();
        root.put("matchType", "CLINICAL_REDLINE");
        root.put("triggerCode", request.triggerCode());
        root.put("scenarioCode", request.scenarioCode());
        root.put("contextSnapshotId", snapshot.snapshotId());
        root.put("redlineId", redline.redlineId());
        root.put("redlineKey", redline.redlineKey());
        root.put("redlineVersion", redline.redlineVersion());
        root.put("category", redline.category().name());
        root.put("clinicalHazard", redline.clinicalHazard());
        root.put("riskMatrixId", redline.riskMatrixId());
        root.put("riskMatrixVersion", redline.riskMatrixVersion());
        root.put("releaseGate", redline.releaseGate());
        root.set("redlineExplanation", evaluation.explanation());
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_RULE_005, "红线解释 JSON 序列化失败: " + redline.redlineId());
        }
    }

    private void assertRuntimeProtected(ClinicalRedlineRule redline) {
        if (redline.lowerTenantOverrideAllowed()) {
            throw new ApiException(ErrorCode.CONFLICT, "安全红线禁止下级关闭 redlineId=" + redline.redlineId());
        }
        if (!hasText(redline.conditionDsl())) {
            throw new ApiException(ErrorCode.CONFLICT, "红线运行条件不能为空 redlineId=" + redline.redlineId());
        }
    }

    private RecommendationCardType toCardType(ClinicalRedlineCategory category) {
        if (category == null) {
            return RecommendationCardType.RISK;
        }
        return switch (category) {
            case DRUG_INTERACTION, DOSE_LIMIT, ANTIMICROBIAL_RESTRICTION,
                    SPECIAL_POPULATION_CONTRAINDICATION -> RecommendationCardType.MEDICATION;
            case CRITICAL_VALUE -> RecommendationCardType.LAB;
            case SURGERY_ANESTHESIA_TRANSFUSION -> RecommendationCardType.RISK;
        };
    }

    private String firstActionMessage(ClinicalRedlineRule redline, RuleDslEvaluation evaluation) {
        return evaluation.actions().stream()
            .map(RuleActionResult::detail)
            .filter(ClinicalRedlineMatcher::hasText)
            .findFirst()
            .orElse("命中临床安全红线：" + redline.title());
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
