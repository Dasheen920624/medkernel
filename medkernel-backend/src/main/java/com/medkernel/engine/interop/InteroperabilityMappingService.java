package com.medkernel.engine.interop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdshook.CdsHookCard;
import com.medkernel.engine.cdshook.CdsHookSource;
import com.medkernel.engine.cdshook.CdsHookSuggestion;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.pathway.PathwayEdgeRequest;
import com.medkernel.engine.pathway.PathwayEdgeType;
import com.medkernel.engine.pathway.PathwayMilestoneRequest;
import com.medkernel.engine.pathway.PathwayNodeRequest;
import com.medkernel.engine.pathway.PathwayTemplateCreateRequest;
import com.medkernel.engine.rule.RuleActionCode;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleCreateRequest;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.versioning.AssetTriggerBindingInput;
import com.medkernel.engine.versioning.AssetTriggerPurpose;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * P11 标准互操作映射服务。
 *
 * <p>服务只做规则 DSL / 路径模板与 CDS Hooks、CQL、Arden、FHIR PlanDefinition、GLIF 的确定性结构映射；
 * 不新建第二套规则或路径运行时，也不绕过既有引擎校验、发布和审计流程。
 */
@Service
public class InteroperabilityMappingService {

    private static final String RULE_DSL_EXTENSION = "medkernelRuleDsl";
    private static final String PATHWAY_DRAFT_EXTENSION = "medkernelPathwayDraft";
    private static final String PROVENANCE_EXTENSION = "medkernelProvenance";
    private static final Pattern CONTROLLED_CQL_STATEMENT = Pattern.compile(
        "^\\s*define\\s+\"([^\"]+)\"\\s*:\\s*hook\\s*=\\s*'([^']+)'\\s+and\\s+when\\s*=\\s*(\\{.*})\\s*$",
        Pattern.DOTALL);

    private final ObjectMapper json;

    /**
     * 注入 JSON 处理器，统一生成标准交换结构与 MedKernel 可逆扩展。
     */
    public InteroperabilityMappingService(ObjectMapper json) {
        this.json = json;
    }

    /**
     * 将规则草稿 DSL 导出为 CDS Hooks 服务声明、Card、CQL 和 Arden 概念映射。
     */
    public RuleCdsHooksMapping exportRuleToCdsHooks(RuleCreateRequest request) {
        if (request == null || request.dsl() == null || !request.dsl().isObject()) {
            throw invalidRule("规则导出必须提供 DSL 对象");
        }
        JsonNode dsl = request.dsl();
        ClinicalEventTriggerPoint hook = singleRuleTrigger(request.triggers());
        JsonNode condition = requiredObject(dsl, "when", "规则 DSL 缺少 when 条件");
        List<CdsHookCard> cards = exportCards(request.ruleCode(), requiredArray(dsl, "then"));
        ObjectNode cdsService = buildCdsService(request, hook, dsl);
        return new RuleCdsHooksMapping(
            hook,
            request.ruleCode(),
            request.name(),
            request.ruleType(),
            request.authoringMode(),
            request.riskLevel(),
            request.sourceRef(),
            condition,
            cdsService,
            cards,
            buildCql(request, hook, condition),
            buildArden(request, hook, cards));
    }

    /**
     * 从标准 CDS Hooks 映射回导规则草稿；完整语义以 MedKernel 扩展中的 DSL 为准。
     */
    public RuleCreateRequest importRuleFromCdsHooks(RuleCdsHooksMapping mapping) {
        if (mapping == null || mapping.cdsService() == null) {
            throw invalidRule("CDS Hooks 映射不能为空");
        }
        JsonNode dsl = mapping.cdsService().path("extension").path(RULE_DSL_EXTENSION);
        if (!dsl.isObject()) {
            throw invalidRule("CDS Hooks 映射缺少 MedKernel 规则 DSL 扩展");
        }
        return new RuleCreateRequest(
            mapping.ruleCode(),
            mapping.name(),
            mapping.ruleType(),
            mapping.authoringMode(),
            mapping.riskLevel(),
            List.of(ruleTrigger(mapping.hook())),
            null,
            mapping.sourceRef(),
            "从 CDS Hooks 标准映射回导",
            dsl,
            json.createObjectNode());
    }

    /**
     * 从受控 CQL 语句回导规则草稿；复杂 CQL 不进入内部 DSL，避免形成第二套规则引擎。
     */
    public RuleCreateRequest importRuleFromCql(CqlRuleImportRequest request) {
        if (request == null) {
            throw invalidRule("CQL 导入请求不能为空");
        }
        String statement = requiredText(request.statement(), "statement");
        String ruleCode = requiredText(request.ruleCode(), "ruleCode");
        String name = requiredText(request.name(), "name");
        String library = requiredText(request.library(), "library");
        String sourceRef = requiredText(request.sourceRef(), "sourceRef");
        if (request.ruleType() == null) {
            throw invalidRule("缺少字段: ruleType");
        }
        if (request.riskLevel() == null) {
            throw invalidRule("缺少字段: riskLevel");
        }
        Matcher matcher = CONTROLLED_CQL_STATEMENT.matcher(statement);
        if (!matcher.matches()) {
            throw unsupportedCql();
        }
        String statementRuleCode = matcher.group(1).trim();
        if (!ruleCode.equals(statementRuleCode)) {
            throw invalidRule("CQL 规则编码与请求 ruleCode 不一致");
        }
        ClinicalEventTriggerPoint hook = parseTrigger(matcher.group(2));
        JsonNode condition = parseControlledCqlCondition(matcher.group(3));

        ObjectNode dsl = json.createObjectNode();
        dsl.set("when", condition);
        dsl.putArray("then");
        ObjectNode explain = dsl.putObject("explain");
        explain.put("summary", "从 CQL 受控导入: " + library);

        ObjectNode parameterBindings = json.createObjectNode();
        ObjectNode cql = parameterBindings.putObject("cql");
        cql.put("library", library);
        cql.put("statement", statement);
        cql.put("status", "CONTROLLED_IMPORT");

        return new RuleCreateRequest(
            ruleCode,
            name,
            request.ruleType(),
            RuleAuthoringMode.DSL,
            request.riskLevel(),
            List.of(ruleTrigger(hook)),
            request.applicableOrgUnitId(),
            sourceRef,
            "CQL 受控导入: " + library,
            dsl,
            json.createObjectNode(),
            parameterBindings);
    }

    /**
     * 将路径模板草稿导出为 FHIR PlanDefinition 与 GLIF 概念结构。
     */
    public PathwayStandardMapping exportPathwayToPlanDefinition(PathwayTemplateCreateRequest request) {
        if (request == null) {
            throw invalidPathway("路径导出必须提供模板草稿");
        }
        ObjectNode planDefinition = buildPlanDefinition(request);
        ObjectNode glif = buildGlif(request);
        return new PathwayStandardMapping(planDefinition, glif);
    }

    /**
     * 从 PlanDefinition 映射回导路径模板草稿；完整语义以 MedKernel 扩展中的草稿为准。
     */
    public PathwayTemplateCreateRequest importPathwayFromPlanDefinition(PathwayStandardMapping mapping) {
        if (mapping == null || mapping.planDefinition() == null) {
            throw invalidPathway("PlanDefinition 映射不能为空");
        }
        JsonNode draft = mapping.planDefinition().path("extension").path(PATHWAY_DRAFT_EXTENSION);
        if (!draft.isObject()) {
            throw invalidPathway("PlanDefinition 映射缺少 MedKernel 路径草稿扩展");
        }
        return json.convertValue(draft, PathwayTemplateCreateRequest.class);
    }

    private List<CdsHookCard> exportCards(String ruleCode, JsonNode then) {
        List<CdsHookCard> cards = new ArrayList<>();
        int index = 1;
        for (JsonNode action : then) {
            if (!action.isObject()) {
                throw invalidRule("规则 then 动作必须是对象");
            }
            RuleRiskLevel severity = parseSeverity(requiredText(action, "atSeverity"));
            RuleActionCode actionCode = parseActionCode(requiredText(action, "actionCode"));
            boolean requires = action.path("requiresPhysicianConfirmation").asBoolean(false)
                || requiresConfirmation(actionCode, severity);
            cards.add(new CdsHookCard(
                ruleCode + "-" + actionCode.name() + "-" + index,
                requiredText(action, "summary"),
                requiredText(action, "detail"),
                requiredText(action, "indicator"),
                source(action.path("source")),
                suggestions(action.path("suggestions")),
                overrideReasons(action.path("overrideReasons")),
                requires));
            index++;
        }
        return cards;
    }

    private ObjectNode buildCdsService(RuleCreateRequest request, ClinicalEventTriggerPoint hook, JsonNode dsl) {
        ObjectNode service = json.createObjectNode();
        service.put("id", request.ruleCode());
        service.put("hook", hook.wireValue());
        service.put("title", request.name());
        service.put("description", dsl.path("explain").path("summary").asText(request.name()));
        service.set("prefetch", json.createObjectNode());
        ObjectNode extension = service.putObject("extension");
        extension.set(RULE_DSL_EXTENSION, dsl.deepCopy());
        extension.put("sourceRef", request.sourceRef());
        addExportProvenance(service, extension, "RULE", request.ruleCode(), request.sourceRef(), dsl);
        return service;
    }

    private ClinicalEventTriggerPoint singleRuleTrigger(
            List<AssetTriggerBindingInput> triggers) {
        List<AssetTriggerBindingInput> executionTriggers = triggers == null
            ? List.of()
            : triggers.stream()
                .filter(binding -> binding != null
                    && binding.purpose() == AssetTriggerPurpose.RULE_EXECUTION)
                .toList();
        if (executionTriggers.size() != 1) {
            throw invalidRule("单个 CDS Hooks 映射必须且只能选择一个规则执行触发点");
        }
        return parseTrigger(executionTriggers.getFirst().triggerPoint());
    }

    private static AssetTriggerBindingInput ruleTrigger(
            ClinicalEventTriggerPoint triggerPoint) {
        return new AssetTriggerBindingInput(
            triggerPoint.wireValue(),
            AssetTriggerPurpose.RULE_EXECUTION,
            List.of()
        );
    }

    private ObjectNode buildCql(RuleCreateRequest request, ClinicalEventTriggerPoint hook, JsonNode condition) {
        ObjectNode cql = json.createObjectNode();
        cql.put("library", sanitizeCqlIdentifier(request.ruleCode()));
        cql.put("status", "DETERMINISTIC_EXPORT");
        cql.put("statement", "define \"" + request.ruleCode() + "\": hook = '" + hook.wireValue()
            + "' and when = " + compact(condition));
        cql.put("content_hash", contentHash(condition));
        cql.put("sourceRef", request.sourceRef());
        return cql;
    }

    private ObjectNode buildArden(RuleCreateRequest request, ClinicalEventTriggerPoint hook, List<CdsHookCard> cards) {
        ObjectNode arden = json.createObjectNode();
        arden.put("mlmName", sanitizeCqlIdentifier(request.ruleCode()));
        arden.put("status", "CONCEPTUAL_EXPORT");
        String firstSummary = cards.isEmpty() ? request.name() : cards.getFirst().summary();
        arden.put("mlm", "maintenance:\n  title: " + request.name()
            + "\n  mlmname: " + request.ruleCode()
            + "\n;;\nlogic:\n  evoke: " + hook.wireValue()
            + "\n  conclude: " + firstSummary + "\n;;");
        return arden;
    }

    private ObjectNode buildPlanDefinition(PathwayTemplateCreateRequest request) {
        ObjectNode plan = json.createObjectNode();
        plan.put("resourceType", "PlanDefinition");
        plan.put("id", request.templateCode());
        plan.put("status", "draft");
        plan.put("title", request.name());
        plan.set("type", coding("http://terminology.hl7.org/CodeSystem/plan-definition-type", "clinical-pathway"));
        ArrayNode topic = plan.putArray("topic");
        topic.add(coding("https://medkernel.local/fhir/CodeSystem/disease", request.diseaseCode()));
        ObjectNode extension = plan.putObject("extension");
        extension.set(PATHWAY_DRAFT_EXTENSION, json.valueToTree(request));
        extension.put("mappingLevel", "CONCEPTUAL");
        addExportProvenance(plan, extension, "PATHWAY", request.templateCode(), request.sourceRef(),
            json.valueToTree(request));

        ArrayNode goals = plan.putArray("goal");
        for (PathwayMilestoneRequest milestone : request.milestones()) {
            ObjectNode goal = goals.addObject();
            goal.put("id", milestone.milestoneCode());
            goal.put("description", milestone.name());
            goal.put("priority", milestone.phaseCode());
            goal.put("start", milestone.dayOffset());
            goal.put("targetMinutes", milestone.expectedOffsetMinutes());
            goal.set("achievementCriteria", copyOrNull(milestone.achievementCriteria()));
        }

        ArrayNode actions = plan.putArray("action");
        for (PathwayNodeRequest node : request.nodes()) {
            actions.add(actionForNode(node, request.edges()));
        }
        return plan;
    }

    private ObjectNode actionForNode(PathwayNodeRequest node, List<PathwayEdgeRequest> edges) {
        ObjectNode action = json.createObjectNode();
        action.put("id", node.nodeCode());
        action.put("title", node.name());
        action.set("type", coding("https://medkernel.local/fhir/CodeSystem/pathway-node-type", node.nodeType().name()));
        if (node.milestoneCode() != null && !node.milestoneCode().isBlank()) {
            action.put("goalId", node.milestoneCode());
        }
        if (node.timeWindowMinutes() != null) {
            ObjectNode duration = action.putObject("timingDuration");
            duration.put("value", node.timeWindowMinutes());
            duration.put("unit", "min");
        }
        if (node.responsibleRole() != null && !node.responsibleRole().isBlank()) {
            ArrayNode participants = action.putArray("participant");
            ObjectNode participant = participants.addObject();
            participant.put("role", node.responsibleRole());
            participant.put("accountableRole", node.accountableRole());
        }
        ArrayNode relatedActions = action.putArray("relatedAction");
        ArrayNode conditions = action.putArray("condition");
        for (PathwayEdgeRequest edge : edges) {
            if (!node.nodeCode().equals(edge.fromNodeCode())) {
                continue;
            }
            ObjectNode related = relatedActions.addObject();
            related.put("id", edge.edgeCode());
            related.put("targetId", edge.toNodeCode());
            related.put("relationship", "before-start");
            related.put("edgeType", edge.edgeType().name());
            related.put("priority", edge.priority());
            if (edge.condition() != null && !edge.condition().isNull()) {
                conditions.add(edgeCondition(edge));
            }
        }
        return action;
    }

    private ObjectNode edgeCondition(PathwayEdgeRequest edge) {
        ObjectNode condition = json.createObjectNode();
        condition.put("kind", "applicability");
        condition.put("edgeCode", edge.edgeCode());
        ObjectNode expression = condition.putObject("expression");
        expression.put("language", "text/medkernel-condition-tree");
        expression.put("description", "MedKernel 路径守卫条件树");
        ObjectNode extension = expression.putObject("extension");
        extension.set("medkernelGuard", edge.condition().deepCopy());
        return condition;
    }

    private ObjectNode buildGlif(PathwayTemplateCreateRequest request) {
        ObjectNode glif = json.createObjectNode();
        glif.put("standard", "GLIF-CONCEPTUAL");
        glif.put("guidelineId", request.templateCode());
        glif.put("title", request.name());
        ObjectNode provenance = glif.putObject("provenance");
        provenance.put("content_hash", contentHash(json.valueToTree(request)));
        provenance.put("assetType", "PATHWAY");
        provenance.put("assetId", request.templateCode());
        provenance.put("sourceRef", request.sourceRef());
        ArrayNode phases = glif.putArray("phases");
        for (PathwayMilestoneRequest milestone : request.milestones()) {
            ObjectNode phase = phases.addObject();
            phase.put("phaseCode", milestone.phaseCode());
            phase.put("milestoneCode", milestone.milestoneCode());
            phase.put("name", milestone.name());
        }
        ArrayNode steps = glif.putArray("steps");
        for (PathwayNodeRequest node : request.nodes()) {
            ObjectNode step = steps.addObject();
            step.put("id", node.nodeCode());
            step.put("name", node.name());
            step.put("type", node.nodeType().name());
            step.put("milestoneCode", node.milestoneCode());
            step.put("terminal", Boolean.TRUE.equals(node.terminal()));
        }
        ArrayNode transitions = glif.putArray("transitions");
        ArrayNode decisions = glif.putArray("decisions");
        for (PathwayEdgeRequest edge : request.edges()) {
            ObjectNode transition = transitions.addObject();
            transition.put("id", edge.edgeCode());
            transition.put("from", edge.fromNodeCode());
            transition.put("to", edge.toNodeCode());
            transition.put("type", edge.edgeType().name());
            if (isDecisionEdge(edge.edgeType())) {
                ObjectNode decision = decisions.addObject();
                decision.put("id", edge.edgeCode());
                decision.put("sourceStep", edge.fromNodeCode());
                decision.put("targetStep", edge.toNodeCode());
                decision.set("guard", copyOrNull(edge.condition()));
            }
        }
        return glif;
    }

    private void addExportProvenance(ObjectNode standard,
                                     ObjectNode extension,
                                     String assetType,
                                     String assetId,
                                     String sourceRef,
                                     JsonNode content) {
        String hash = contentHash(content);
        ObjectNode provenance = extension.putObject(PROVENANCE_EXTENSION);
        provenance.put("content_hash", hash);
        provenance.put("assetType", assetType);
        provenance.put("assetId", assetId);
        provenance.put("sourceRef", sourceRef);

        ObjectNode meta = standard.path("meta").isObject()
            ? (ObjectNode) standard.path("meta")
            : standard.putObject("meta");
        ArrayNode tags = meta.path("tag").isArray()
            ? (ArrayNode) meta.path("tag")
            : meta.putArray("tag");
        ObjectNode tag = tags.addObject();
        tag.put("system", "https://medkernel.local/fhir/CodeSystem/interoperability-provenance");
        tag.put("code", "content_hash");
        tag.put("display", hash);
    }

    private boolean isDecisionEdge(PathwayEdgeType edgeType) {
        return edgeType == PathwayEdgeType.CONDITION
            || edgeType == PathwayEdgeType.RISK_STRATIFICATION
            || edgeType == PathwayEdgeType.PHYSICIAN_DECISION;
    }

    private ObjectNode coding(String system, String code) {
        ObjectNode wrapper = json.createObjectNode();
        ArrayNode coding = wrapper.putArray("coding");
        ObjectNode item = coding.addObject();
        item.put("system", system);
        item.put("code", code);
        return wrapper;
    }

    private CdsHookSource source(JsonNode source) {
        if (!source.isObject()) {
            throw invalidRule("规则动作缺少 source");
        }
        return new CdsHookSource(
            requiredText(source, "label"),
            optionalText(source, "url"),
            optionalText(source, "evidenceLevel"));
    }

    private List<CdsHookSuggestion> suggestions(JsonNode suggestions) {
        if (!suggestions.isArray()) {
            throw invalidRule("规则动作 suggestions 必须是数组");
        }
        List<CdsHookSuggestion> result = new ArrayList<>();
        for (JsonNode suggestion : suggestions) {
            result.add(new CdsHookSuggestion(
                requiredText(suggestion, "label"),
                requiredText(suggestion, "actionType"),
                copyOrNull(suggestion.path("payload"))));
        }
        return result;
    }

    private List<String> overrideReasons(JsonNode reasons) {
        if (!reasons.isArray()) {
            throw invalidRule("规则动作 overrideReasons 必须是数组");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode reason : reasons) {
            if (!reason.isTextual() || reason.asText().isBlank()) {
                throw invalidRule("规则动作 overrideReasons 仅允许非空文本");
            }
            result.add(reason.asText());
        }
        return result;
    }

    private RuleRiskLevel parseSeverity(String value) {
        try {
            return RuleRiskLevel.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidRule("规则风险级别无效: " + value);
        }
    }

    private RuleActionCode parseActionCode(String value) {
        try {
            return RuleActionCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidRule("规则动作码无效: " + value);
        }
    }

    private ClinicalEventTriggerPoint parseTrigger(String value) {
        try {
            ClinicalEventTriggerPoint trigger = ClinicalEventTriggerPoint.fromWireValue(value);
            if (trigger == null) {
                throw invalidRule("规则触发点无效: " + value);
            }
            return trigger;
        } catch (IllegalArgumentException exception) {
            throw invalidRule("规则触发点无效: " + value);
        }
    }

    private boolean requiresConfirmation(RuleActionCode actionCode, RuleRiskLevel severity) {
        return severity == RuleRiskLevel.HIGH
            || severity == RuleRiskLevel.CRITICAL
            || actionCode == RuleActionCode.BLOCK
            || actionCode == RuleActionCode.STRONG_REMINDER
            || actionCode == RuleActionCode.SUGGEST_ORDER;
    }

    private JsonNode parseControlledCqlCondition(String expression) {
        try {
            JsonNode condition = json.readTree(expression);
            if (!condition.isObject()) {
                throw unsupportedCql();
            }
            return condition;
        } catch (JsonProcessingException exception) {
            throw unsupportedCql();
        }
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalidRule("缺少字段: " + field);
        }
        return value.trim();
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw invalidRule("缺少字段: " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private JsonNode requiredObject(JsonNode node, String field, String message) {
        JsonNode value = node.path(field);
        if (!value.isObject()) {
            throw invalidRule(message);
        }
        return value.deepCopy();
    }

    private JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw invalidRule("缺少数组字段: " + field);
        }
        return value;
    }

    private JsonNode copyOrNull(JsonNode node) {
        return node == null || node.isMissingNode() ? json.nullNode() : node.deepCopy();
    }

    private String compact(JsonNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw invalidRule("标准映射序列化失败");
        }
    }

    private String contentHash(JsonNode node) {
        try {
            return Sha256ContentHash.sha256(json.writeValueAsString(node), "互操作导出内容不能为空");
        } catch (JsonProcessingException exception) {
            throw invalidRule("标准映射序列化失败");
        }
    }

    private String sanitizeCqlIdentifier(String value) {
        String normalized = value == null ? "MEDKERNEL_RULE" : value.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9_]", "_");
        return normalized.isBlank() ? "MEDKERNEL_RULE" : normalized;
    }

    private ApiException invalidRule(String message) {
        return new ApiException(ErrorCode.ENG_RULE_001, message);
    }

    private ApiException unsupportedCql() {
        return invalidRule("CQL 受控导入仅支持 define \"规则\": hook = '触发点' and when = {条件树}");
    }

    private ApiException invalidPathway(String message) {
        return new ApiException(ErrorCode.ENG_PATHWAY_001, message);
    }
}
