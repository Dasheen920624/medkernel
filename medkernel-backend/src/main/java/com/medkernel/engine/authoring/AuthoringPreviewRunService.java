package com.medkernel.engine.authoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.rule.ConditionEvaluation;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.rule.ConditionEvidence;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 草稿规则与路径的真实快照即配即试。
 *
 * <p>本服务只读取 ACTIVE 标准上下文快照并执行草稿 DSL，不写规则执行日志、不创建患者路径。
 */
@Service
public class AuthoringPreviewRunService {

    private final ObjectMapper json;
    private final ContextSnapshotService snapshots;
    private final RuleDslEvaluator ruleEvaluator;
    private final ConditionEvaluator conditionEvaluator;

    public AuthoringPreviewRunService(
            ObjectMapper json,
            ContextSnapshotService snapshots,
            RuleDslEvaluator ruleEvaluator,
            ConditionEvaluator conditionEvaluator) {
        this.json = json.copy()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.snapshots = snapshots;
        this.ruleEvaluator = ruleEvaluator;
        this.conditionEvaluator = conditionEvaluator;
    }

    public AuthoringPreviewRunResponse run(AuthoringPreviewRunRequest request) {
        if (request == null) {
            throw validation("即配即试请求不能为空");
        }
        if (request.subject() == null) {
            throw validation("即配即试 subject 不能为空");
        }
        if (!hasText(request.snapshotId())) {
            throw validation("即配即试必须选择真实上下文快照");
        }
        if (request.dsl() == null || !request.dsl().isObject()) {
            throw validation("即配即试 DSL 必须是 JSON 对象");
        }

        ContextSnapshotResponse snapshot = snapshots.findById(request.snapshotId());
        ensureUsableSnapshot(snapshot, request);
        JsonNode context = json.valueToTree(snapshot.resources());
        return switch (request.subject()) {
            case RULE_CONDITION -> runRule(request, snapshot, context);
            case PATHWAY_GUARD -> runPathway(request, snapshot, context);
        };
    }

    private AuthoringPreviewRunResponse runRule(
            AuthoringPreviewRunRequest request,
            ContextSnapshotResponse snapshot,
            JsonNode context) {
        RuleDslEvaluation evaluation = ruleEvaluator.evaluate(request.dsl(), context);
        List<ConditionEvidence> conditionEvidence = readConditionEvidence(evaluation.explanation());
        String severity = evaluation.severity() == null ? null : evaluation.severity().name();
        return new AuthoringPreviewRunResponse(
            AuthoringPreviewSubject.RULE_CONDITION,
            snapshot.snapshotId(),
            snapshot.runtimeReleaseId(),
            evaluation.hit(),
            evaluation.hit(),
            evaluation.hit() ? "草稿规则命中真实快照" : "草稿规则未命中真实快照",
            severity,
            evaluation.actions(),
            evaluation.explanation(),
            conditionEvidence,
            snapshot.qualityStatus(),
            snapshot.missingFields(),
            snapshot.mappingStatus(),
            resourceCounts(snapshot.resources()),
            List.of(),
            null,
            null,
            traceId(request, snapshot)
        );
    }

    private AuthoringPreviewRunResponse runPathway(
            AuthoringPreviewRunRequest request,
            ContextSnapshotResponse snapshot,
            JsonNode context) {
        String startNodeCode = firstText(request.startNodeCode(), text(request.dsl(), "startNodeCode"));
        if (!hasText(startNodeCode)) {
            throw validation("路径草稿试运行必须提供 startNodeCode");
        }
        List<DraftEdge> outgoing = outgoingEdges(request.dsl(), startNodeCode);
        if (outgoing.isEmpty()) {
            throw validation("路径草稿没有从起点出发的流转边: " + startNodeCode);
        }

        List<ConditionEvidence> conditionEvidence = new ArrayList<>();
        DraftEdge fallback = null;
        DraftEdge selected = null;
        boolean selectedByGuard = false;
        List<String> requestedTargets = request.requestedNextNodeCodes();
        for (DraftEdge edge : outgoing) {
            if (!requestedTargets.isEmpty() && !requestedTargets.contains(edge.toNodeCode())) {
                continue;
            }
            if (edge.condition() == null || edge.condition().isMissingNode() || edge.condition().isNull()) {
                if (fallback == null) {
                    fallback = edge;
                }
                continue;
            }
            ConditionEvaluation evaluation = conditionEvaluator.evaluate(toConditionGroup(edge.condition()), context);
            conditionEvidence.addAll(evaluation.evidence());
            if (evaluation.matched()) {
                selected = edge;
                selectedByGuard = true;
                break;
            }
        }
        if (selected == null) {
            selected = fallback;
        }

        List<String> trajectory = selected == null
            ? List.of(startNodeCode)
            : List.of(startNodeCode, selected.toNodeCode());
        String finalStatus = selected == null
            ? PatientPathwayStatus.NODE_EXECUTING.name()
            : terminalNode(request.dsl(), selected.toNodeCode())
                ? PatientPathwayStatus.COMPLETED.name()
                : PatientPathwayStatus.NODE_EXECUTING.name();
        String outcome = selected == null
            ? "草稿路径没有命中从 " + startNodeCode + " 出发的路径边"
            : "草稿路径推进到 " + selected.toNodeCode();
        return new AuthoringPreviewRunResponse(
            AuthoringPreviewSubject.PATHWAY_GUARD,
            snapshot.snapshotId(),
            snapshot.runtimeReleaseId(),
            selected != null,
            null,
            outcome,
            null,
            List.of(),
            pathwayExplanation(selected, selectedByGuard),
            conditionEvidence,
            snapshot.qualityStatus(),
            snapshot.missingFields(),
            snapshot.mappingStatus(),
            resourceCounts(snapshot.resources()),
            trajectory,
            finalStatus,
            selected == null ? null : selected.edgeCode(),
            traceId(request, snapshot)
        );
    }

    private void ensureUsableSnapshot(ContextSnapshotResponse snapshot, AuthoringPreviewRunRequest request) {
        if (snapshot == null
                || snapshot.status() != ContextSnapshotStatus.ACTIVE
                || snapshot.resources() == null) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_003, "即配即试只能使用 ACTIVE 真实上下文快照");
        }
    }

    private List<DraftEdge> outgoingEdges(JsonNode dsl, String startNodeCode) {
        JsonNode edges = dsl.path("edges");
        if (!edges.isArray()) {
            throw validation("路径草稿 DSL 缺少 edges 数组");
        }
        List<DraftEdge> result = new ArrayList<>();
        for (JsonNode edge : edges) {
            if (!edge.isObject()) {
                continue;
            }
            String fromNodeCode = text(edge, "fromNodeCode");
            if (!Objects.equals(fromNodeCode, startNodeCode)) {
                continue;
            }
            String toNodeCode = text(edge, "toNodeCode");
            if (!hasText(toNodeCode)) {
                throw validation("路径边缺少 toNodeCode");
            }
            result.add(new DraftEdge(
                firstText(text(edge, "edgeCode"), fromNodeCode + "-" + toNodeCode),
                fromNodeCode,
                toNodeCode,
                condition(edge),
                edge.path("priority").isIntegralNumber() ? edge.path("priority").asInt() : Integer.MAX_VALUE));
        }
        result.sort(Comparator.comparingInt(DraftEdge::priority));
        return result;
    }

    private JsonNode condition(JsonNode edge) {
        JsonNode condition = edge.get("condition");
        if (condition != null) {
            return condition;
        }
        JsonNode guard = edge.get("guard");
        if (guard != null) {
            return guard;
        }
        JsonNode conditionJson = edge.get("conditionJson");
        if (conditionJson != null && conditionJson.isTextual() && hasText(conditionJson.asText())) {
            try {
                return json.readTree(conditionJson.asText());
            } catch (Exception exception) {
                throw validation("路径边 conditionJson 不是合法 JSON");
            }
        }
        return null;
    }

    private JsonNode toConditionGroup(JsonNode condition) {
        if (condition.has("when")) {
            return condition.get("when");
        }
        if (condition.has("all") || condition.has("any") || condition.has("not")) {
            return condition;
        }
        ObjectNode group = json.createObjectNode();
        group.putArray("all").add(condition);
        return group;
    }

    private boolean terminalNode(JsonNode dsl, String nodeCode) {
        JsonNode nodes = dsl.path("nodes");
        if (!nodes.isArray()) {
            return false;
        }
        for (JsonNode node : nodes) {
            if (Objects.equals(text(node, "nodeCode"), nodeCode)) {
                return node.path("terminal").asBoolean(false)
                    || node.path("terminalFlag").asBoolean(false);
            }
        }
        return false;
    }

    private JsonNode pathwayExplanation(DraftEdge selected, boolean selectedByGuard) {
        ObjectNode explanation = json.createObjectNode();
        explanation.put("title", "路径草稿试运行");
        if (selected == null) {
            explanation.put("reason", "起点没有可推进路径边");
            return explanation;
        }
        explanation.put("selectedEdgeCode", selected.edgeCode());
        explanation.put("fromNodeCode", selected.fromNodeCode());
        explanation.put("toNodeCode", selected.toNodeCode());
        explanation.put("guardMatched", selectedByGuard);
        return explanation;
    }

    private List<ConditionEvidence> readConditionEvidence(JsonNode explanation) {
        JsonNode evidence = explanation == null ? null : explanation.path("conditionEvidence");
        if (evidence == null || !evidence.isArray()) {
            return List.of();
        }
        List<ConditionEvidence> result = new ArrayList<>();
        for (JsonNode item : evidence) {
            result.add(new ConditionEvidence(
                text(item, "fact"),
                text(item, "sourcePath"),
                text(item, "operator"),
                nodeOrNull(item.get("expected")),
                nodeOrNull(item.get("actual")),
                item.path("matched").asBoolean(false),
                item.path("missing").asBoolean(false),
                nodeOrNull(item.get("value")),
                text(item, "unit"),
                text(item, "source"),
                text(item, "formula"),
                text(item, "errorCode"),
                text(item, "errorMessage")
            ));
        }
        return result;
    }

    private JsonNode nodeOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node;
    }

    private Map<String, Integer> resourceCounts(ContextSnapshotResources resources) {
        if (resources == null) {
            return Map.of();
        }
        return Map.ofEntries(
            Map.entry("patient", resources.patient() == null ? 0 : 1),
            Map.entry("allergyIntolerances", resources.allergyIntolerances().size()),
            Map.entry("encounters", resources.encounters().size()),
            Map.entry("conditions", resources.conditions().size()),
            Map.entry("nursingAssessments", resources.nursingAssessments().size()),
            Map.entry("observations", resources.observations().size()),
            Map.entry("diagnosticReports", resources.diagnosticReports().size()),
            Map.entry("medications", resources.medications().size()),
            Map.entry("procedures", resources.procedures().size()),
            Map.entry("documents", resources.documents().size()),
            Map.entry("carePlans", resources.carePlans().size()),
            Map.entry("followUps", resources.followUps().size()),
            Map.entry("claims", resources.claims().size())
        );
    }

    private String traceId(AuthoringPreviewRunRequest request, ContextSnapshotResponse snapshot) {
        return firstText(request.traceId(), snapshot.traceId());
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return hasText(value) ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private record DraftEdge(
        String edgeCode,
        String fromNodeCode,
        String toNodeCode,
        JsonNode condition,
        int priority
    ) {}
}
