package com.medkernel.engine.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.pathway.PathwayEdgeRepository;
import com.medkernel.engine.pathway.PathwayNode;
import com.medkernel.engine.pathway.PathwayNodeRepository;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import org.springframework.stereotype.Service;

/**
 * 基于关系库事实的规则跨域影响索引。
 */
@Service
class RelationalRuleImpactIndex implements RuleImpactIndex {

    private final PathwayTemplateRepository templates;
    private final PathwayNodeRepository nodes;
    private final PathwayEdgeRepository edges;
    private final PatientPathwayRepository patientPathways;
    private final ObjectMapper json;

    RelationalRuleImpactIndex(PathwayTemplateRepository templates,
                              PathwayNodeRepository nodes,
                              PathwayEdgeRepository edges,
                              PatientPathwayRepository patientPathways,
                              ObjectMapper json) {
        this.templates = templates;
        this.nodes = nodes;
        this.edges = edges;
        this.patientPathways = patientPathways;
        this.json = json;
    }

    @Override
    public RuleImpactIndexSnapshot analyze(String tenantId, RuleDefinition rule, RuleVersion version) {
        LinkedHashMap<String, ImpactedTemplate> impactedTemplates = impactedTemplates(tenantId, rule, version);
        List<RuleImpactObject> pathways = impactedTemplates.values().stream()
            .map(ImpactedTemplate::toImpactObject)
            .toList();
        List<RuleImpactObject> patients = impactedTemplates.values().stream()
            .flatMap(template -> patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc(
                    template.template().templateId(), tenantId).stream()
                .filter(this::isActiveRuntime)
                .map(runtime -> patientImpact(template.template(), runtime)))
            .toList();
        return new RuleImpactIndexSnapshot(pathways, patients, List.of(), List.of());
    }

    private LinkedHashMap<String, ImpactedTemplate> impactedTemplates(String tenantId,
                                                                      RuleDefinition rule,
                                                                      RuleVersion version) {
        Set<String> tokens = ruleReferenceTokens(rule, version);
        LinkedHashMap<String, ImpactedTemplate> result = new LinkedHashMap<>();
        nodes.findByTenantIdAndRuleReference(tenantId, rule.ruleId(), rule.ruleCode(), version.versionId()).stream()
            .filter(node -> containsRuleReference(tokens, node.dependencyJson())
                || containsRuleReference(tokens, node.configJson()))
            .forEach(node -> templates.findByTemplateIdAndTenantId(node.templateId(), tenantId)
                .ifPresent(template -> result.putIfAbsent(template.templateId(), new ImpactedTemplate(
                    template,
                    "路径模板节点引用规则 " + rule.ruleCode() + "（节点 " + node.nodeCode() + "）"))));
        edges.findByTenantIdAndRuleReference(tenantId, rule.ruleId(), rule.ruleCode(), version.versionId()).stream()
            .filter(edge -> containsRuleReference(tokens, edge.conditionJson()))
            .forEach(edge -> templates.findByTemplateIdAndTenantId(edge.templateId(), tenantId)
                .ifPresent(template -> result.putIfAbsent(template.templateId(), new ImpactedTemplate(
                    template,
                    "路径模板流转条件引用规则 " + rule.ruleCode() + "（边 " + edge.edgeCode() + "）"))));
        return result;
    }

    private RuleImpactObject patientImpact(PathwayTemplate template, PatientPathway runtime) {
        return new RuleImpactObject(
            "PATIENT_PATHWAY",
            runtime.patientPathwayId(),
            "患者 " + runtime.patientId() + " / 就诊 " + runtime.encounterId(),
            "路径模板 " + template.templateCode() + " 受规则引用影响；当前节点 "
                + runtime.currentNodeCode() + "，状态 " + runtime.status());
    }

    private boolean isActiveRuntime(PatientPathway runtime) {
        return runtime.status() == PatientPathwayStatus.ENTERED
            || runtime.status() == PatientPathwayStatus.NODE_EXECUTING
            || runtime.status() == PatientPathwayStatus.VARIANCE;
    }

    private Set<String> ruleReferenceTokens(RuleDefinition rule, RuleVersion version) {
        return Set.of(rule.ruleId(), rule.ruleCode(), version.versionId());
    }

    private boolean containsRuleReference(Set<String> tokens, String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return false;
        }
        try {
            return containsRuleReference(tokens, json.readTree(jsonText));
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean containsRuleReference(Set<String> tokens, JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return tokens.contains(node.asText());
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsRuleReference(tokens, item)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                if (containsRuleReference(tokens, fields.next().getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private record ImpactedTemplate(PathwayTemplate template, String reason) {
        RuleImpactObject toImpactObject() {
            return new RuleImpactObject(
                "PATHWAY_TEMPLATE",
                template.templateId(),
                template.name(),
                reason);
        }
    }
}
